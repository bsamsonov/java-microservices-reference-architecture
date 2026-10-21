package io.github.bsamsonov.notifications;

import org.springframework.boot.SpringApplication;

/** Run the service locally with Postgres and Kafka in Testcontainers: ./mvnw spring-boot:test-run */
public class TestNotificationsApplication {

    public static void main(String[] args) {
        SpringApplication.from(NotificationsApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
