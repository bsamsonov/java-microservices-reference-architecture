package io.github.bsamsonov.catalog.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import io.github.bsamsonov.catalog.PostgresContainerConfig;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JDBC slice on real Postgres with Flyway migrations: mapping, queries, constraints, version.
 * JDBC has no ddl-auto validate, so this test is what keeps the mapping in sync with the schema (D8):
 * it writes and reads every column.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class ProductRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    ProductRepository repository;

    @Test
    void savesAndLoadsProduct() {
        Product saved = repository.save(new Product("R-1", "Mug", new BigDecimal("7.50"), NOW));

        Product loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getCode()).isEqualTo("R-1");
        assertThat(loaded.getName()).isEqualTo("Mug");
        assertThat(loaded.getPrice()).isEqualByComparingTo("7.50");
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(loaded.getUpdatedAt()).isEqualTo(NOW);
        assertThat(loaded.getVersion()).isZero();
        assertThat(repository.existsByCode("R-1")).isTrue();
    }

    @Test
    void codeIsUniqueInTheDatabase() {
        repository.save(new Product("R-2", "Mug", BigDecimal.ONE, NOW));

        assertThatThrownBy(() -> repository.save(new Product("R-2", "Copy", BigDecimal.ONE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateBumpsVersion() {
        Product product = repository.save(new Product("R-3", "Mug", BigDecimal.ONE, NOW));
        long before = product.getVersion();

        product.update("Big mug", BigDecimal.TEN, true, NOW);
        Product updated = repository.save(product);

        assertThat(updated.getVersion()).isEqualTo(before + 1);
        assertThat(repository.findById(product.getId()).orElseThrow().getName()).isEqualTo("Big mug");
    }

    @Test
    void staleCopyCannotOverwriteNewerRow() {
        Product product = repository.save(new Product("R-4", "Mug", BigDecimal.ONE, NOW));
        Product first = repository.findById(product.getId()).orElseThrow();
        Product second = repository.findById(product.getId()).orElseThrow();

        first.update("first", BigDecimal.ONE, true, NOW);
        repository.save(first);
        second.update("second", BigDecimal.ONE, true, NOW);

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void activeOnlyPageIsSortedByCode() {
        repository.save(new Product("R-B", "b", BigDecimal.ONE, NOW));
        repository.save(new Product("R-A", "a", BigDecimal.ONE, NOW));
        Product inactive = repository.save(new Product("R-0", "inactive", BigDecimal.ONE, NOW));
        inactive.update("inactive", BigDecimal.ONE, false, NOW);
        repository.save(inactive);

        var page = repository.findByActiveTrue(PageRequest.of(0, 10, Sort.by("code")));

        assertThat(page.getContent()).extracting(Product::getCode).containsExactly("R-A", "R-B");
    }
}
