package io.github.bsamsonov.notifications.notification.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import io.github.bsamsonov.notifications.PostgresContainerConfig;
import io.github.bsamsonov.notifications.notification.domain.Notification;
import io.github.bsamsonov.notifications.notification.domain.NotificationChannel;
import io.github.bsamsonov.notifications.notification.domain.NotificationStatus;
import io.github.bsamsonov.notifications.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JPA slice on real Postgres with Flyway migrations: mapping domain ⇄ table, queries, unique event id. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainerConfig.class, JpaNotificationRepository.class})
class JpaNotificationRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    JpaNotificationRepository repository;

    @Autowired
    NotificationJpaRepository jpa;

    private static Notification notification(UUID eventId, String customerId, Instant at) {
        return Notification.create(eventId, UUID.randomUUID(), customerId, NotificationType.ORDER_PLACED,
                NotificationChannel.EMAIL, at);
    }

    @Test
    void savesAndRestoresSentNotification() {
        Notification n = notification(UUID.randomUUID(), "c-1", NOW);
        n.markSent(NOW.plusSeconds(1));

        repository.save(n);
        Notification loaded = repository.findById(n.id()).orElseThrow();

        assertThat(loaded.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(loaded.sentAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(loaded.sourceEventId()).isEqualTo(n.sourceEventId());
        assertThat(repository.existsBySourceEventId(n.sourceEventId())).isTrue();
    }

    @Test
    void findsByCustomerNewestFirst() {
        Notification older = notification(UUID.randomUUID(), "c-2", NOW);
        Notification newer = notification(UUID.randomUUID(), "c-2", NOW.plusSeconds(5));
        repository.save(older);
        repository.save(newer);
        repository.save(notification(UUID.randomUUID(), "c-other", NOW));

        assertThat(repository.findByCustomerId("c-2")).extracting(Notification::id)
                .containsExactly(newer.id(), older.id());
    }

    @Test
    void secondNotificationForTheSameEventIsRejectedByTheDatabase() {
        UUID eventId = UUID.randomUUID();
        repository.save(notification(eventId, "c-3", NOW));
        repository.save(notification(eventId, "c-3", NOW));

        assertThatThrownBy(() -> jpa.flush()).isInstanceOf(DataIntegrityViolationException.class);
    }
}
