package io.github.bsamsonov.notifications.notification.application;

import java.util.UUID;

import io.github.bsamsonov.notifications.notification.domain.NotificationType;

/** A business event the customer must hear about; {@code sourceEventId} is the idempotency key. */
public record NotifyCommand(UUID sourceEventId, UUID orderId, String customerId, NotificationType type) {
}
