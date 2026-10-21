package io.github.bsamsonov.notifications.notification.adapter.in.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound contract: our own copy of the orders-service events (JSON in topic orders.events).
 * Only the fields this service needs; unknown fields are ignored, so the producer may add more.
 */
final class OrderEventMessages {

    private OrderEventMessages() {
    }

    record OrderPlaced(UUID eventId, UUID orderId, String customerId, Instant occurredAt) {
    }

    record OrderCancelled(UUID eventId, UUID orderId, String customerId, Instant occurredAt) {
    }
}
