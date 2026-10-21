package io.github.bsamsonov.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** Full infrastructure for tests and for local run without the cluster (see TestCatalogApplication). */
@TestConfiguration(proxyBeanMethods = false)
@Import(PostgresContainerConfig.class)
public class TestcontainersConfiguration {
}
