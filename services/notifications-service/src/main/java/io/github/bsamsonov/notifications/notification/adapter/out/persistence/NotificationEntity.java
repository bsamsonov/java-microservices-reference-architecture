package io.github.bsamsonov.notifications.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.github.bsamsonov.notifications.notification.domain.NotificationChannel;
import io.github.bsamsonov.notifications.notification.domain.NotificationStatus;
import io.github.bsamsonov.notifications.notification.domain.NotificationType;

/** Persistence model. The table belongs to the {@code notifications} schema owned by this module. */
@Entity
@Table(name = "notifications", schema = "notifications")
class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "source_event_id", nullable = false, unique = true, updatable = false)
    private UUID sourceEventId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private long version;

    protected NotificationEntity() {
    }

    NotificationEntity(UUID id) {
        this.id = id;
    }

    UUID getId() { return id; }
    UUID getSourceEventId() { return sourceEventId; }
    UUID getOrderId() { return orderId; }
    String getCustomerId() { return customerId; }
    NotificationType getType() { return type; }
    NotificationChannel getChannel() { return channel; }
    NotificationStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    Instant getSentAt() { return sentAt; }
    String getFailureReason() { return failureReason; }

    void setSourceEventId(UUID sourceEventId) { this.sourceEventId = sourceEventId; }
    void setOrderId(UUID orderId) { this.orderId = orderId; }
    void setCustomerId(String customerId) { this.customerId = customerId; }
    void setType(NotificationType type) { this.type = type; }
    void setChannel(NotificationChannel channel) { this.channel = channel; }
    void setStatus(NotificationStatus status) { this.status = status; }
    void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
