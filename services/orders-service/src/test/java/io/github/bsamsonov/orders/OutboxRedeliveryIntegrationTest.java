package io.github.bsamsonov.orders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static io.github.bsamsonov.orders.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Outbox delivery survives a broker outage without a restart of the service (ADR-0003):
 * the Kafka send fails, the publication stays in event_publication, and the scheduled
 * resubmission delivers it once the broker is back. The backlog gauges (OutboxMetrics) show the wait and drop to zero.
 */
@SpringBootTest(properties = {
        // fail fast while the broker is paused, instead of the default 60 s / 120 s
        "spring.kafka.producer.properties[max.block.ms]=2000",
        "spring.kafka.producer.properties[request.timeout.ms]=2000",
        "spring.kafka.producer.properties[delivery.timeout.ms]=4000",
        "spring.kafka.producer.properties[linger.ms]=0",
        // the observation of KafkaTemplate asks the admin client for the cluster id, 30 s by default
        "spring.kafka.admin.operation-timeout=2s",
        "spring.kafka.admin.properties[request.timeout.ms]=2000",
        "spring.kafka.admin.properties[default.api.timeout.ms]=2000",
        "outbox.resubmission.interval=2s",
        "outbox.resubmission.min-age=1s",
        "outbox.metrics.interval=1s"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OutboxRedeliveryIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    MeterRegistry meters;

    @Test
    void eventPlacedWhileKafkaIsDownIsDeliveredWhenKafkaIsBack() {
        // the observed send asks KafkaAdmin for the cluster id; against a paused broker its Admin.close()
        // waits with no timeout, so the send never fails. Fetched now, the id is cached for the whole test
        assertThat(kafkaAdmin.clusterId()).isNotNull();
        var docker = kafka.getDockerClient();
        docker.pauseContainerCmd(kafka.getContainerId()).exec();
        UUID id;
        try {
            MvcTestResult placed = mvc.post().uri("/orders").with(token("orders:write")).contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"customerId":"c-outage","lines":[{"productCode":"A","quantity":1,"unitPrice":5.00}]}
                            """).exchange();
            assertThat(placed).hasStatus(HttpStatus.CREATED);
            id = UUID.fromString(placed.getResponse().getHeader("Location").substring("/orders/".length()));

            // the Kafka externalization failed; the event waits in the outbox
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(
                    jdbc.sql("select count(*) from event_publication where status = 'FAILED' and serialized_event like :id")
                            .param("id", "%" + id + "%").query(Long.class).single()).isPositive());

            // the backlog is visible in the metrics behind the alert OutboxBacklogStale
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(pending()).isGreaterThanOrEqualTo(1);
                assertThat(oldestAgeSeconds()).isPositive();
            });
        } finally {
            docker.unpauseContainerCmd(kafka.getContainerId()).exec();
        }

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
                assertThat(received).filteredOn(r -> id.toString().equals(r.key())).isNotEmpty();
            });
        }

        // completion mode DELETE: a delivered publication leaves the table
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                jdbc.sql("select count(*) from event_publication").query(Long.class).single()).isZero());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(pending()).isZero();
            assertThat(oldestAgeSeconds()).isZero();
        });
    }

    private double pending() {
        return meters.get("outbox.publications.pending").gauge().value();
    }

    private double oldestAgeSeconds() {
        return meters.get("outbox.publications.oldest.age").timeGauge().value(TimeUnit.SECONDS);
    }
}
