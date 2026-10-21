package io.github.bsamsonov.orders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static io.github.bsamsonov.orders.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end inside the service on real Postgres and Kafka:
 * HTTP -> domain -> DB + outbox -> audit module (in-process) and Kafka topic orders.events.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrdersFlowIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer kafka;

    @Test
    void placeAndCancelOrderIsAuditedAndPublishedToKafka() {
        MvcTestResult placed = mvc.post().uri("/orders").with(token("orders:write")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":"c-42","lines":[{"productCode":"A","quantity":1,"unitPrice":9.99}]}
                        """).exchange();
        assertThat(placed).hasStatus(HttpStatus.CREATED);
        UUID id = UUID.fromString(placed.getResponse().getHeader("Location").substring("/orders/".length()));

        String cancel = """
                {"reason":"customer request"}
                """;
        assertThat(mvc.post().uri("/orders/{id}/cancel", id).with(token("orders:write")).contentType(MediaType.APPLICATION_JSON).content(cancel))
                .hasStatus(HttpStatus.OK);
        assertThat(mvc.post().uri("/orders/{id}/cancel", id).with(token("orders:write")).contentType(MediaType.APPLICATION_JSON).content(cancel))
                .hasStatus(HttpStatus.CONFLICT);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                jdbc.sql("select event_type from audit.audit_log where aggregate_id = :id order by id")
                        .param("id", id).query(String.class).list())
                .containsExactly("OrderPlaced", "OrderCancelled"));

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("orders.events"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                assertThat(received).filteredOn(r -> id.toString().equals(r.key())).hasSize(2);
            });
        }
        assertThat(received).filteredOn(r -> id.toString().equals(r.key()))
                .allSatisfy(r -> assertThat(r.value()).contains(id.toString()));

        // outbox drained: every event publication (audit listener + Kafka externalization) completed
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                jdbc.sql("select count(*) from event_publication where completion_date is null")
                        .query(Long.class).single()).isZero());
    }
}
