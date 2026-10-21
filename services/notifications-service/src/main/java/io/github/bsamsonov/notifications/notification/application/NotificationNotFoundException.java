package io.github.bsamsonov.notifications.notification.application;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID id) {
        super("notification " + id + " not found");
    }
}
