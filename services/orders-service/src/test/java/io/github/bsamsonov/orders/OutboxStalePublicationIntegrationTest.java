package io.github.bsamsonov.orders;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.kafka.KafkaContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A publication left in flight by a pod that was stopped or crashed mid-send is delivered with no restart (ADR-0037).
 * In the broker outage experiment such rows stayed RESUBMITTED or PUBLISHED for good: the scheduled resubmission
 * takes only FAILED rows. The staleness monitor marks them FAILED; there is no republish on restart.
 */
@SpringBootTest(properties = {
        "spring.modulith.events.staleness.check-interval=1s",
        "outbox.resubmission.interval=2s",
        "outbox.resubmission.min-age=1s"
})
@Import(TestcontainersConfiguration.class)
class OutboxStalePublicationIntegrationTest {

    private static final String KAFKA_LISTENER =
            "org.springframework.modulith.events.support.EventExternalizerModuleListener.externalize(java.lang.Object)";

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer kafka;

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "PROCESSING", "RESUBMITTED"})
    void publicationLeftInFlightByAStoppedPodIsDelivered(String status) {
        var orderId = UUID.randomUUID();
        var publishedAt = Instant.now().minus(Duration.ofMinutes(10));
        // what a stopped pod leaves behind: a status of a publication in flight, no completion
        jdbc.sql("""
                        insert into event_publication (id, listener_id, event_type, serialized_event, publication_date,
                                                       status, completion_attempts, last_resubmission_date)
                        values (:id, :listener, :type, :event, :publishedAt, :status, 1, :resubmittedAt)
                        """)
                .param("id", UUID.randomUUID())
                .param("listener", KAFKA_LISTENER)
                .param("type", "io.github.bsamsonov.orders.order.OrderPlaced")
                .param("event", """
                        {"eventId":"%s","orderId":"%s","customerId":"c-stale","total":5.00,"occurredAt":"%s"}
                        """.formatted(UUID.randomUUID(), orderId, publishedAt).strip())
                .param("publishedAt", OffsetDateTime.ofInstant(publishedAt, ZoneOffset.UTC))
                .param("status", status)
                .param("resubmittedAt", "RESUBMITTED".equals(status)
                        ? OffsetDateTime.ofInstant(publishedAt.plus(Duration.ofMinutes(4)), ZoneOffset.UTC) : null)
                .update();

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("orders.events"));
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                assertThat(received).filteredOn(r -> orderId.toString().equals(r.key())).isNotEmpty();
            });
        }

        // completion mode DELETE: the delivered publication leaves the table
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                jdbc.sql("select count(*) from event_publication").query(Long.class).single()).isZero());
    }
}
