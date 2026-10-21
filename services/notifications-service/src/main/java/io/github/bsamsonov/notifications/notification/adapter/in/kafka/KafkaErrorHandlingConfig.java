package io.github.bsamsonov.notifications.notification.adapter.in.kafka;

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
 */
@Configuration(proxyBeanMethods = false)
class KafkaErrorHandlingConfig {

    static final String DLT_SUFFIX = ".DLT";

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafka) {
        // partition -1: let Kafka choose, so the DLT may have fewer partitions than the source topic
        var dlt = new DeadLetterPublishingRecoverer(kafka,
                (record, e) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        // the recoverer itself is silent; without this line a dead letter is visible only in the DLT
        ConsumerRecordRecoverer recoverer = (record, e) -> {
            log.warn("Record sent to DLT: topic={}, key={}, offset={}, reason={}", record.topic(), record.key(),
                    record.offset(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            dlt.accept(record, e);
        };
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 3));
        handler.addNotRetryableExceptions(InvalidMessageException.class);
        return handler;
    }
}
