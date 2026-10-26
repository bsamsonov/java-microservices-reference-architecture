package io.github.bsamsonov.notifications.notification.adapter.in.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Listener failures: retry in place (transient errors such as DB unavailable), then publish the
 * original record to {@code <topic>.DLT} and move on. Invalid messages skip the retries.
 * Spring Boot plugs this {@code CommonErrorHandler} bean into the listener container factory.
 * Every record published to a DLT counts in {@code kafka.dlt.records} (alert {@code SpringKafkaDeadLetters}, ADR-0004).
 */
@Configuration(proxyBeanMethods = false)
class KafkaErrorHandlingConfig {

    static final String DLT_SUFFIX = ".DLT";
    static final String DLT_RECORDS = "kafka.dlt.records";

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafka, MeterRegistry meters) {
        // partition -1: let Kafka choose, so the DLT may have fewer partitions than the source topic
        var dlt = new DeadLetterPublishingRecoverer(kafka,
                (record, e) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        // the recoverer itself is silent; without this line a dead letter is visible only in the DLT
        ConsumerRecordRecoverer recoverer = (record, e) -> {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Record sent to DLT: topic={}, key={}, offset={}, reason={}", record.topic(), record.key(),
                    record.offset(), cause.getMessage());
            dlt.accept(record, e);
            // counted after the publish: a failed publish throws, and the record is retried instead
            Counter.builder(DLT_RECORDS)
                    .description("Records published to a dead letter topic, by source topic")
                    .tag("topic", record.topic())
                    .tag("exception", cause.getClass().getSimpleName())
                    .register(meters)
                    .increment();
        };
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 3));
        handler.addNotRetryableExceptions(InvalidMessageException.class);
        return handler;
    }
}
