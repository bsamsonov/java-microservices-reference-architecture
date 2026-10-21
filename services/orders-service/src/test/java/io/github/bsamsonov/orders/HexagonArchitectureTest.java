package io.github.bsamsonov.orders;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hexagon inside every module. Messages are written as instructions: they tell the developer
 * (or the AI agent) what to do instead.
 */
@AnalyzeClasses(packages = "io.github.bsamsonov.orders", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "..application..", "..adapter..")
            .because("domain holds business rules only; put Spring/JPA code into application or adapter.out.persistence");

    @ArchTest
    static final ArchRule applicationDoesNotKnowAdapters = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .because("use cases talk to the outside through ports (interfaces in application), adapters implement them");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotUseOutbound = noClasses().that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
            .because("controllers call use cases in application, never repositories or clients directly");

    @ArchTest
    static final ArchRule entitiesLiveInPersistenceAdapter = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage("..adapter.out.persistence..")
            .because("JPA entities are a persistence detail; the domain model is separate");

    @ArchTest
    static final ArchRule controllersLiveInWebAdapter = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage("..adapter.in.web..")
            .because("HTTP is an inbound adapter");
}
