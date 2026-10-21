package io.github.bsamsonov.notifications.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Notification aggregate: one per source event (the event id is the idempotency key).
 * Created through {@link #create}, rebuilt from storage through {@link #restore}.
 */
public final class Notification {

    private final UUID id;
    private final UUID sourceEventId;
    private final UUID orderId;
    private final String customerId;
    private final NotificationType type;
    private final NotificationChannel channel;
    private final Instant createdAt;
    private NotificationStatus status;
    private Instant sentAt;
    private String failureReason;

    private Notification(UUID id, UUID sourceEventId, UUID orderId, String customerId, NotificationType type,
                         NotificationChannel channel, Instant createdAt, NotificationStatus status,
                         Instant sentAt, String failureReason) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceEventId = Objects.requireNonNull(sourceEventId, "sourceEventId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.type = Objects.requireNonNull(type, "type");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = Objects.requireNonNull(status, "status");
        this.sentAt = sentAt;
        this.failureReason = failureReason;
    }

    public static Notification create(UUID sourceEventId, UUID orderId, String customerId, NotificationType type,
                                      NotificationChannel channel, Instant now) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        return new Notification(UUID.randomUUID(), sourceEventId, orderId, customerId, type, channel, now,
                NotificationStatus.PENDING, null, null);
    }

    public static Notification restore(UUID id, UUID sourceEventId, UUID orderId, String customerId,
                                       NotificationType type, NotificationChannel channel, Instant createdAt,
                                       NotificationStatus status, Instant sentAt, String failureReason) {
        return new Notification(id, sourceEventId, orderId, customerId, type, channel, createdAt, status,
                sentAt, failureReason);
    }

    public void markSent(Instant at) {
        requirePending();
        this.status = NotificationStatus.SENT;
        this.sentAt = Objects.requireNonNull(at, "at");
    }

    public void markFailed(String reason) {
        requirePending();
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    /** Message for the customer; derived from the type, so it is not stored. */
    public String text() {
        return switch (type) {
            case ORDER_PLACED -> "Your order " + orderId + " has been placed.";
            case ORDER_CANCELLED -> "Your order " + orderId + " has been cancelled.";
        };
    }

    private void requirePending() {
        if (status != NotificationStatus.PENDING) {
            throw new NotificationStateException("notification " + id + " is already " + status);
        }
    }

    public UUID id() { return id; }
    public UUID sourceEventId() { return sourceEventId; }
    public UUID orderId() { return orderId; }
    public String customerId() { return customerId; }
    public NotificationType type() { return type; }
    public NotificationChannel channel() { return channel; }
    public Instant createdAt() { return createdAt; }
    public NotificationStatus status() { return status; }
    public Instant sentAt() { return sentAt; }
    public String failureReason() { return failureReason; }
}
