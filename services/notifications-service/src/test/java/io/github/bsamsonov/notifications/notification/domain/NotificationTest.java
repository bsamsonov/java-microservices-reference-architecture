package io.github.bsamsonov.notifications.notification.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private static Notification pending() {
        return Notification.create(UUID.randomUUID(), UUID.randomUUID(), "c-1", NotificationType.ORDER_PLACED,
                NotificationChannel.EMAIL, NOW);
    }

    @Test
    void newNotificationIsPending() {
        Notification n = pending();

        assertThat(n.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.text()).contains(n.orderId().toString()).contains("placed");
    }

    @Test
    void blankCustomerIsRejected() {
        assertThatThrownBy(() -> Notification.create(UUID.randomUUID(), UUID.randomUUID(), " ",
                NotificationType.ORDER_PLACED, NotificationChannel.EMAIL, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sentIsFinal() {
        Notification n = pending();
        n.markSent(NOW);

        assertThat(n.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.sentAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> n.markFailed("late")).isInstanceOf(NotificationStateException.class);
        assertThatThrownBy(() -> n.markSent(NOW)).isInstanceOf(NotificationStateException.class);
    }

    @Test
    void failedIsFinal() {
        Notification n = pending();
        n.markFailed("mailbox full");

        assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.failureReason()).isEqualTo("mailbox full");
        assertThatThrownBy(() -> n.markSent(NOW)).isInstanceOf(NotificationStateException.class);
    }
}
