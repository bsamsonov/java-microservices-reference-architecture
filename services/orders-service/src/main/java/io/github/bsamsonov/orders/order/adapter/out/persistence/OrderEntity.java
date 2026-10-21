package io.github.bsamsonov.orders.order.adapter.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.github.bsamsonov.orders.order.domain.OrderStatus;

/** Persistence model. Tables belong to the {@code orders} schema owned by this module. */
@Entity
@Table(name = "orders", schema = "orders")
class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Version
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", schema = "orders", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    private List<OrderLineEmbeddable> lines = new ArrayList<>();

    protected OrderEntity() {
    }

    OrderEntity(UUID id) {
        this.id = id;
    }

    UUID getId() { return id; }
    String getCustomerId() { return customerId; }
    OrderStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    String getCancellationReason() { return cancellationReason; }
    List<OrderLineEmbeddable> getLines() { return lines; }

    void setCustomerId(String customerId) { this.customerId = customerId; }
    void setStatus(OrderStatus status) { this.status = status; }
    void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
}
