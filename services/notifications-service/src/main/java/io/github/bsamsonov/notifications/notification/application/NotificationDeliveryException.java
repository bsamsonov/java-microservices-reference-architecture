package io.github.bsamsonov.notifications.notification.application;

/** The channel rejected the message. Recorded as FAILED, not retried through Kafka. */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message) {
        super(message);
    }
}
