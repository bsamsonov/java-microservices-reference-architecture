package io.github.bsamsonov.orders;

import org.junit.jupiter.api.Test;

import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Module boundaries: other modules may use only the top-level package of a module (its API). */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(OrdersApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    @Test
    void writeModuleDocumentation() {
        // PlantUML/C4 diagrams and module canvases -> target/spring-modulith-docs
        new Documenter(MODULES).writeDocumentation();
    }
}
