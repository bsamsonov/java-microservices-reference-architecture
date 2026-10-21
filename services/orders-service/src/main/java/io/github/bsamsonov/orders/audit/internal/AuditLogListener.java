package io.github.bsamsonov.orders.audit.internal;

import java.sql.Timestamp;
import java.util.UUID;

import io.github.bsamsonov.orders.order.OrderCancelled;
import io.github.bsamsonov.orders.order.OrderPlaced;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Runs after the publishing transaction commits, in its own transaction; the event publication
 * registry retries it if it fails. Idempotent by {@code event_id} (unique), so redelivery is harmless.
 */
@Component
class AuditLogListener {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditLogListener(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @ApplicationModuleListener
    void on(OrderPlaced event) {
        record(event.eventId(), "OrderPlaced", event.orderId(), event, event.occurredAt());
    }

    @ApplicationModuleListener
    void on(OrderCancelled event) {
        record(event.eventId(), "OrderCancelled", event.orderId(), event, event.occurredAt());
    }

    private void record(UUID eventId, String type, UUID aggregateId, Object payload, java.time.Instant occurredAt) {
        jdbc.sql("""
                insert into audit.audit_log (event_id, event_type, aggregate_id, payload, occurred_at)
                values (:eventId, :type, :aggregateId, cast(:payload as jsonb), :occurredAt)
                on conflict (event_id) do nothing
                """)
                .param("eventId", eventId)
                .param("type", type)
                .param("aggregateId", aggregateId)
                .param("payload", json.writeValueAsString(payload))
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
    }
}
