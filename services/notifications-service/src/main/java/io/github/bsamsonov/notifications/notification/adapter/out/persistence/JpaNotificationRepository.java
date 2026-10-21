package io.github.bsamsonov.notifications.notification.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.bsamsonov.notifications.notification.application.NotificationRepository;
import io.github.bsamsonov.notifications.notification.domain.Notification;

import org.springframework.stereotype.Repository;

/** Implements the application port; the only place that maps domain ⇄ JPA. */
@Repository
class JpaNotificationRepository implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    JpaNotificationRepository(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpa.findById(id).map(JpaNotificationRepository::toDomain);
    }

    @Override
    public List<Notification> findByCustomerId(String customerId) {
        return jpa.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(JpaNotificationRepository::toDomain).toList();
    }

    @Override
    public boolean existsBySourceEventId(UUID sourceEventId) {
        return jpa.existsBySourceEventId(sourceEventId);
    }

    @Override
    public void save(Notification n) {
        NotificationEntity entity = jpa.findById(n.id()).orElseGet(() -> new NotificationEntity(n.id()));
        entity.setSourceEventId(n.sourceEventId());
        entity.setOrderId(n.orderId());
        entity.setCustomerId(n.customerId());
        entity.setType(n.type());
        entity.setChannel(n.channel());
        entity.setStatus(n.status());
        entity.setCreatedAt(n.createdAt());
        entity.setSentAt(n.sentAt());
        entity.setFailureReason(n.failureReason());
        jpa.save(entity);
    }

    private static Notification toDomain(NotificationEntity e) {
        return Notification.restore(e.getId(), e.getSourceEventId(), e.getOrderId(), e.getCustomerId(), e.getType(),
                e.getChannel(), e.getCreatedAt(), e.getStatus(), e.getSentAt(), e.getFailureReason());
    }
}
