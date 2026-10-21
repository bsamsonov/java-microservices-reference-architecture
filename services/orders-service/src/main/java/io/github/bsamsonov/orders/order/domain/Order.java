package io.github.bsamsonov.orders.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Order aggregate. Holds the invariants; knows nothing about persistence or the web.
 * Created through {@link #place}, rebuilt from storage through {@link #restore}.
 */
public final class Order {

    private final UUID id;
    private final String customerId;
    private final List<OrderLine> lines;
    private final Instant createdAt;
    private OrderStatus status;
    private String cancellationReason;

    private Order(UUID id, String customerId, List<OrderLine> lines, Instant createdAt,
                  OrderStatus status, String cancellationReason) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.lines = List.copyOf(lines);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = Objects.requireNonNull(status, "status");
        this.cancellationReason = cancellationReason;
    }

    public static Order place(String customerId, List<OrderLine> lines, Instant now) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("order must have at least one line");
        }
        return new Order(UUID.randomUUID(), customerId, lines, now, OrderStatus.PLACED, null);
    }

    public static Order restore(UUID id, String customerId, List<OrderLine> lines, Instant createdAt,
                                OrderStatus status, String cancellationReason) {
        return new Order(id, customerId, lines, createdAt, status, cancellationReason);
    }

    public void cancel(String reason) {
        if (status == OrderStatus.CANCELLED) {
            throw new OrderStateException("order " + id + " is already cancelled");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    public BigDecimal total() {
        return lines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID id() { return id; }
    public String customerId() { return customerId; }
    public List<OrderLine> lines() { return lines; }
    public Instant createdAt() { return createdAt; }
    public OrderStatus status() { return status; }
    public String cancellationReason() { return cancellationReason; }
}
