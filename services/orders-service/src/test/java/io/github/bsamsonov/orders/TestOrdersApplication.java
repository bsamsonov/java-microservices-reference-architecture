package io.github.bsamsonov.orders;

import org.springframework.boot.SpringApplication;

/** Run the service locally with Postgres and Kafka in Testcontainers: ./mvnw spring-boot:test-run */
public class TestOrdersApplication {

    public static void main(String[] args) {
        SpringApplication.from(OrdersApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
