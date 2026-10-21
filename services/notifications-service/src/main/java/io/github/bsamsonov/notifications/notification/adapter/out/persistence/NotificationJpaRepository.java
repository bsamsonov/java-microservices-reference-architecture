package io.github.bsamsonov.notifications.notification.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    boolean existsBySourceEventId(UUID sourceEventId);
}
