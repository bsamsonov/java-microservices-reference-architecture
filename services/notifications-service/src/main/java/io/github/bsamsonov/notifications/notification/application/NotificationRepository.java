package io.github.bsamsonov.notifications.notification.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.bsamsonov.notifications.notification.domain.Notification;

/** Outgoing port. Implemented by adapter.out.persistence. */
public interface NotificationRepository {

    Optional<Notification> findById(UUID id);

    /** Newest first. */
    List<Notification> findByCustomerId(String customerId);

    boolean existsBySourceEventId(UUID sourceEventId);

    void save(Notification notification);
}
