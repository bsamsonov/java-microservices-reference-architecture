package io.github.bsamsonov.notifications.notification.domain;

/** The requested transition is not allowed in the current notification state. */
public class NotificationStateException extends RuntimeException {

    public NotificationStateException(String message) {
        super(message);
    }
}
