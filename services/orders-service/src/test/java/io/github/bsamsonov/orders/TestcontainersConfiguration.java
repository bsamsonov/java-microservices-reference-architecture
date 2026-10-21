package io.github.bsamsonov.orders;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** Full infrastructure for tests and for local run without the cluster (see TestOrdersApplication). */
@TestConfiguration(proxyBeanMethods = false)
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
public class TestcontainersConfiguration {
}
