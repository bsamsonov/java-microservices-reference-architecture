package io.github.bsamsonov.notifications.notification.adapter.out.email;

import io.github.bsamsonov.notifications.notification.application.NotificationSender;
import io.github.bsamsonov.notifications.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Stand-in for an email provider: writes a log line instead of sending. Replace it with a real
 * client (SMTP, SES, ...) in this package; throw NotificationDeliveryException when the provider rejects.
 * The text is not logged: it may contain personal data.
 */
@Component
class LoggingEmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(Notification notification) {
        log.info("Email sent (stub): notification={}, customer={}", notification.id(), notification.customerId());
    }
}
