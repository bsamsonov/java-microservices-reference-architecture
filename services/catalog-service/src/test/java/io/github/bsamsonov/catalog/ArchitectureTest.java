package io.github.bsamsonov.catalog;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Template 2 rules (no Spring Modulith): feature packages, controller -> service -> repository.
 * Messages are written as instructions: they tell the developer (or the AI agent) what to do instead.
 */
@AnalyzeClasses(packages = "io.github.bsamsonov.catalog", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().dependOnClassesThat().areAssignableTo(org.springframework.data.repository.Repository.class)
            .because("controllers call the service, which owns the transaction; put the query into the service");

    @ArchTest
    static final ArchRule transactionsLiveInServices = noClasses()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .because("the transaction boundary is the @Service; a controller only maps DTOs");

    @ArchTest
    static final ArchRule featuresAreFreeOfCycles = slices().matching("io.github.bsamsonov.catalog.(*)..")
            .should().beFreeOfCycles()
            .because("features depend on each other one way only; extract shared code or use template 1 with events");

    @ArchTest
    static final ArchRule entitiesStayInsideTheirFeature = noClasses()
            .that().resideOutsideOfPackage("io.github.bsamsonov.catalog.product..")
            .should().dependOnClassesThat().areAnnotatedWith(org.springframework.data.relational.core.mapping.Table.class)
            .because("another feature reads products through ProductService, never through the aggregate or table");
}
