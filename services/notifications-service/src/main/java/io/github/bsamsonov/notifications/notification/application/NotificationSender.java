package io.github.bsamsonov.notifications.notification.application;

import io.github.bsamsonov.notifications.notification.domain.Notification;

/** Outgoing port to the delivery channel (email provider). Implemented by adapter.out.email. */
public interface NotificationSender {

    /** @throws NotificationDeliveryException when the channel rejects the message */
    void send(Notification notification);
}
