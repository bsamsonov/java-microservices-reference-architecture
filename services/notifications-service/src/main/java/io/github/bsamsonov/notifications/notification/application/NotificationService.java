package io.github.bsamsonov.notifications.notification.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import io.github.bsamsonov.notifications.notification.domain.Notification;
import io.github.bsamsonov.notifications.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the notification module. {@link #notify} is idempotent by the source event id:
 * a redelivered event is skipped; two concurrent deliveries hit the unique constraint, the loser
 * is retried by the Kafka error handler and then skipped. Delivery is at-least-once: if the commit
 * fails after sending, the redelivered event sends again.
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationSender sender;
    private final Clock clock;

    NotificationService(NotificationRepository notifications, NotificationSender sender, Clock clock) {
        this.notifications = notifications;
        this.sender = sender;
        this.clock = clock;
    }

    public void notify(NotifyCommand command) {
        if (notifications.existsBySourceEventId(command.sourceEventId())) {
            log.info("Duplicate event skipped: eventId={}", command.sourceEventId());
            return;
        }
        Notification notification = Notification.create(command.sourceEventId(), command.orderId(),
                command.customerId(), command.type(), NotificationChannel.EMAIL, clock.instant());
        try {
            sender.send(notification);
            notification.markSent(clock.instant());
            log.info("Notification sent: id={}, order={}, type={}", notification.id(), notification.orderId(),
                    notification.type());
        } catch (NotificationDeliveryException e) {
            notification.markFailed(e.getMessage());
            log.warn("Notification failed: id={}, order={}, reason={}", notification.id(), notification.orderId(),
                    e.getMessage());
        }
        notifications.save(notification);
    }

    @Transactional(readOnly = true)
    public Notification get(UUID id) {
        return notifications.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Notification> findByCustomer(String customerId) {
        return notifications.findByCustomerId(customerId);
    }
}
