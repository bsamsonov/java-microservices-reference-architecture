package io.github.bsamsonov.orders;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds the Logback {@code OTEL} appender to the root logger and connects it to the OpenTelemetry SDK
 * configured by Spring Boot, so logs go over OTLP together with trace_id/span_id.
 * Added in code rather than in logback-spring.xml: a custom Logback file disables Spring Boot's own console
 * setup, and with it {@code logging.structured.format.console} (JSON logs in k8s).
 */
@Configuration(proxyBeanMethods = false)
class OpenTelemetryLoggingConfig {

    @Bean
    InitializingBean openTelemetryAppenderInstaller(OpenTelemetry openTelemetry) {
        return () -> {
            var context = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            if (root.getAppender("OTEL") == null) {
                var appender = new OpenTelemetryAppender();
                appender.setName("OTEL");
                appender.setContext(context);
                appender.start();
                root.addAppender(appender);
            }
            OpenTelemetryAppender.install(openTelemetry);
        };
    }
}
