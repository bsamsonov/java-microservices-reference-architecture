package io.github.bsamsonov.catalog;

import org.springframework.boot.SpringApplication;

/** Run the service locally with Postgres in Testcontainers: ./mvnw spring-boot:test-run */
public class TestCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.from(CatalogApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
