package io.github.bsamsonov.notifications;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static io.github.bsamsonov.notifications.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end inside the service on real Postgres and Kafka: records in orders.events (shaped like
 * orders-service output, type in the __TypeId__ header) -> listener -> DB -> HTTP API;
 * redelivery is idempotent, invalid records end up in orders.events.DLT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationsFlowIntegrationTest {

    private static final String TOPIC = "orders.events";
    private static final String PLACED = "io.github.bsamsonov.orders.order.OrderPlaced";
    private static final String CANCELLED = "io.github.bsamsonov.orders.order.OrderCancelled";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaContainer kafkaContainer;

    private void send(String type, UUID orderId, String json) {
        var record = new ProducerRecord<String, String>(TOPIC, orderId.toString(), json);
        if (type != null) {
            record.headers().add("__TypeId__", type.getBytes(StandardCharsets.UTF_8));
        }
        kafka.send(record).join();
    }

    private static String placed(UUID eventId, UUID orderId, String customerId) {
        return """
                {"eventId":"%s","orderId":"%s","customerId":"%s","total":9.99,"occurredAt":"2026-10-21T10:00:00Z"}
                """.formatted(eventId, orderId, customerId);
    }

    @Test
    void orderEventsBecomeNotificationsOncePerEvent() {
        UUID orderId = UUID.randomUUID();
        UUID placedEventId = UUID.randomUUID();
        send(PLACED, orderId, placed(placedEventId, orderId, "c-77"));
        send(PLACED, orderId, placed(placedEventId, orderId, "c-77"));          // redelivery
        send(CANCELLED, orderId, """
                {"eventId":"%s","orderId":"%s","customerId":"c-77","reason":"r","occurredAt":"2026-10-21T10:01:00Z"}
                """.formatted(UUID.randomUUID(), orderId));
        send("io.github.bsamsonov.orders.order.OrderShipped", orderId, "{}");  // unknown type: skipped

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(mvc.get().uri("/notifications").with(token("notifications:read")).param("customerId", "c-77"))
                        .hasStatusOk()
                        .bodyJson()
                        .extractingPath("$[*].type").asArray()
                        .containsExactlyInAnyOrder("ORDER_PLACED", "ORDER_CANCELLED"));
        assertThat(mvc.get().uri("/notifications").with(token("notifications:read")).param("customerId", "c-77"))
                .bodyJson().extractingPath("$[*].status").asArray().containsOnly("SENT");
    }

    @Test
    void invalidRecordsGoToDeadLetterTopic() {
        UUID brokenJson = UUID.randomUUID();
        UUID noCustomer = UUID.randomUUID();
        UUID noType = UUID.randomUUID();
        send(PLACED, brokenJson, "{not json");
        // OrderCancelled as published before customerId was added to the event
        send(CANCELLED, noCustomer, """
                {"eventId":"%s","orderId":"%s","reason":"r","occurredAt":"2026-10-21T10:01:00Z"}
                """.formatted(UUID.randomUUID(), noCustomer));
        send(null, noType, placed(UUID.randomUUID(), noType, "c-78"));

        List<ConsumerRecord<String, String>> dead = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(TOPIC + ".DLT"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(dead::add);
                assertThat(dead).extracting(ConsumerRecord::key)
                        .contains(brokenJson.toString(), noCustomer.toString(), noType.toString());
            });
        }
        // the recoverer adds the reason, so the DLT can be triaged without logs
        assertThat(dead).filteredOn(r -> brokenJson.toString().equals(r.key())).singleElement()
                .satisfies(r -> assertThat(r.headers().lastHeader("kafka_dlt-exception-message")).isNotNull());
        assertThat(mvc.get().uri("/notifications").with(token("notifications:read")).param("customerId", "c-78"))
                .bodyJson().extractingPath("$").asArray().isEmpty();
    }
}
