package io.github.bsamsonov.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.springframework.modulith.core.ApplicationModule;

/**
 * Each module owns one database schema and touches no other (ADR-0007). Read from the sources, no Spring context:
 * the schemas are the ones created by the migrations; a module uses a schema when one of its {@code @Table} mappings
 * or SQL strings names it. Unqualified or dynamically built SQL is not seen.
 */
class ModuleSchemaTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    /** Migrations of framework tables (Spring Modulith outbox) that belong to no module. */
    private static final Set<String> FRAMEWORK_MIGRATIONS = Set.of("event_publication");

    /** A string literal or text block (group 1), or a comment, which is dropped. */
    private static final Pattern LITERAL_OR_COMMENT = Pattern.compile(
            "(\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\")|//[^\\n]*|/\\*[\\s\\S]*?\\*/");
    private static final Pattern TABLE = Pattern.compile("@Table\\s*\\(([^)]*)\\)");
    private static final Pattern SCHEMA_ATTRIBUTE = Pattern.compile("schema\\s*=\\s*\"(\\w+)\"");
    private static final Pattern SQL = Pattern.compile("(?i)\\b(select|insert|update|delete|merge|from|join|into|table)\\b");
    private static final Pattern CREATE_SCHEMA = Pattern.compile("(?i)create\\s+schema\\s+(?:if\\s+not\\s+exists\\s+)?(\\w+)");
    private static final Pattern QUALIFIED_NAME = Pattern.compile("\\b(\\w+)\\.\\w+");

    private final Set<String> schemas = schemasCreatedByMigrations();

    @Test
    void everyEntityNamesItsSchema() {
        var violations = new ArrayList<String>();
        forEachModuleSource((module, file, code) -> {
            Matcher table = TABLE.matcher(code);
            while (table.find()) {
                if (!SCHEMA_ATTRIBUTE.matcher(table.group(1)).find()) {
                    violations.add(file + ": @Table without schema; map the entity to the schema of module " + module);
                }
            }
        });
        assertThat(violations).as("entities must name their module's schema (ADR-0007)").isEmpty();
    }

    @Test
    void aSchemaIsUsedByOneModuleOnly() {
        Map<String, Set<String>> modulesBySchema = new TreeMap<>();
        forEachModuleSource((module, file, code) ->
                usedSchemas(code).forEach(schema -> modulesBySchema.computeIfAbsent(schema, s -> new TreeSet<>()).add(module)));

        var shared = modulesBySchema.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> "schema " + e.getKey() + " is used by modules " + e.getValue())
                .toList();
        assertThat(shared)
                .as("a module reads and writes only its own schema; get other data through the owning module's API or an event (ADR-0007)")
                .isEmpty();
    }

    @Test
    void aMigrationTouchesOnlyTheSchemaInItsName() throws IOException {
        var violations = new ArrayList<String>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                String description = name.substring(name.indexOf("__") + 2, name.length() - ".sql".length());
                String sql = Files.readString(file);
                if (FRAMEWORK_MIGRATIONS.stream().anyMatch(description::startsWith)) {
                    continue;
                }
                String owner = schemas.stream().filter(s -> description.startsWith(s + "_")).findFirst().orElse(null);
                if (owner == null) {
                    violations.add(name + ": name it V<n>__<schema>_<what>.sql with one of the schemas " + schemas);
                    continue;
                }
                qualifiedSchemas(sql).stream().filter(s -> !s.equals(owner))
                        .forEach(s -> violations.add(name + ": touches schema " + s + "; a migration changes only schema " + owner));
            }
        }
        assertThat(violations).as("migrations per module schema (ADR-0007)").isEmpty();
    }

    private Set<String> usedSchemas(String code) {
        var used = new TreeSet<String>();
        Matcher table = TABLE.matcher(code);
        while (table.find()) {
            Matcher schema = SCHEMA_ATTRIBUTE.matcher(table.group(1));
            if (schema.find()) {
                used.add(schema.group(1));
            }
        }
        for (String literal : literals(code)) {
            if (SQL.matcher(literal).find()) {
                used.addAll(qualifiedSchemas(literal));
            }
        }
        return used;
    }

    private Set<String> qualifiedSchemas(String text) {
        var found = new TreeSet<String>();
        Matcher name = QUALIFIED_NAME.matcher(text);
        while (name.find()) {
            if (schemas.contains(name.group(1))) {
                found.add(name.group(1));
            }
        }
        return found;
    }

    private static List<String> literals(String code) {
        var literals = new ArrayList<String>();
        Matcher m = LITERAL_OR_COMMENT.matcher(code);
        while (m.find()) {
            if (m.group(1) != null) {
                literals.add(m.group(1));
            }
        }
        return literals;
    }

    /** Source without comments, so a schema named in a comment does not count. */
    private static String withoutComments(String code) {
        return LITERAL_OR_COMMENT.matcher(code).replaceAll(m -> m.group(1) != null ? Matcher.quoteReplacement(m.group(1)) : "");
    }

    private static Set<String> schemasCreatedByMigrations() {
        var created = new TreeSet<String>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.toList()) {
                Matcher m = CREATE_SCHEMA.matcher(Files.readString(file));
                while (m.find()) {
                    created.add(m.group(1).toLowerCase());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return created;
    }

    private interface ModuleSourceVisitor {
        void visit(String module, Path file, String code);
    }

    private static void forEachModuleSource(ModuleSourceVisitor visitor) {
        for (ApplicationModule module : ModularityTest.MODULES) {
            Path dir = SOURCES.resolve(module.getBasePackage().getName().replace('.', '/'));
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    visitor.visit(module.getIdentifier().toString(), file, withoutComments(Files.readString(file)));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
