package io.github.bsamsonov.notifications.notification;

import java.util.UUID;

import io.github.bsamsonov.notifications.TestcontainersConfiguration;
import io.github.bsamsonov.notifications.notification.application.NotificationDeliveryException;
import io.github.bsamsonov.notifications.notification.application.NotificationSender;
import io.github.bsamsonov.notifications.notification.application.NotificationService;
import io.github.bsamsonov.notifications.notification.application.NotifyCommand;
import io.github.bsamsonov.notifications.notification.domain.Notification;
import io.github.bsamsonov.notifications.notification.domain.NotificationStatus;
import io.github.bsamsonov.notifications.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots only the notification module on real Postgres and Kafka. The delivery channel is replaced
 * by a hand-written fake (not a mock) that rejects customers whose id starts with "reject".
 */
@ApplicationModuleTest
@Import({TestcontainersConfiguration.class, NotificationModuleTest.FakeChannel.class})
class NotificationModuleTest {

    @Autowired
    NotificationService notifications;

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeChannel {

        @Bean
        @Primary
        NotificationSender fakeSender() {
            return notification -> {
                if (notification.customerId().startsWith("reject")) {
                    throw new NotificationDeliveryException("mailbox does not exist");
                }
            };
        }
    }

    @Test
    void eventBecomesSentNotification() {
        notifications.notify(new NotifyCommand(UUID.randomUUID(), UUID.randomUUID(), "c-1", NotificationType.ORDER_PLACED));

        assertThat(notifications.findByCustomer("c-1")).singleElement()
                .extracting(Notification::status).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void redeliveredEventIsSkipped() {
        var command = new NotifyCommand(UUID.randomUUID(), UUID.randomUUID(), "c-2", NotificationType.ORDER_CANCELLED);

        notifications.notify(command);
        notifications.notify(command);

        assertThat(notifications.findByCustomer("c-2")).hasSize(1);
    }

    @Test
    void rejectedDeliveryIsRecordedAsFailed() {
        notifications.notify(new NotifyCommand(UUID.randomUUID(), UUID.randomUUID(), "reject-1",
                NotificationType.ORDER_PLACED));

        assertThat(notifications.findByCustomer("reject-1")).singleElement()
                .satisfies(n -> {
                    assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
                    assertThat(n.failureReason()).isEqualTo("mailbox does not exist");
                });
    }
}
