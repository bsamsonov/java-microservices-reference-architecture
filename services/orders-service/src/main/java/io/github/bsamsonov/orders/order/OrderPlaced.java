package io.github.bsamsonov.orders.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

/**
 * Published after an order is stored. Delivered to other modules in-process and to Kafka
 * topic {@code orders.events} (key = orderId) through the event publication registry (outbox).
 * {@code eventId} lets consumers deduplicate redeliveries.
 */
@Externalized("orders.events::#{#this.orderId().toString()}")
public record OrderPlaced(UUID eventId, UUID orderId, String customerId, BigDecimal total, Instant occurredAt) {
}
