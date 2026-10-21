package io.github.bsamsonov.notifications;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Root package holds only bootstrap and cross-cutting configuration.
 * Business code lives in application modules: direct sub-packages ({@code notification}).
 */
@SpringBootApplication
public class NotificationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationsApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
