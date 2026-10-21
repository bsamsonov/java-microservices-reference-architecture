package io.github.bsamsonov.notifications.notification.domain;

/** PENDING -> SENT or PENDING -> FAILED; both outcomes are final. */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
