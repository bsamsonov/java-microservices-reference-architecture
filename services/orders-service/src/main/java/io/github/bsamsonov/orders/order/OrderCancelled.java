package io.github.bsamsonov.orders.order;

import java.time.Instant;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

/** Published after an order is cancelled. Same topic and key as {@link OrderPlaced}. */
@Externalized("orders.events::#{#this.orderId().toString()}")
public record OrderCancelled(UUID eventId, UUID orderId, String customerId, String reason, Instant occurredAt) {
}
