package io.github.bsamsonov.notifications.notification.adapter.in.web;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import io.github.bsamsonov.notifications.notification.adapter.in.web.api.NotificationsApi;
import io.github.bsamsonov.notifications.notification.adapter.in.web.api.model.NotificationChannelDto;
import io.github.bsamsonov.notifications.notification.adapter.in.web.api.model.NotificationResponseDto;
import io.github.bsamsonov.notifications.notification.adapter.in.web.api.model.NotificationStatusDto;
import io.github.bsamsonov.notifications.notification.adapter.in.web.api.model.NotificationTypeDto;
import io.github.bsamsonov.notifications.notification.application.NotificationService;
import io.github.bsamsonov.notifications.notification.domain.Notification;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the interface generated from api/openapi.yaml; maps domain -> DTO. */
@RestController
class NotificationController implements NotificationsApi {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public ResponseEntity<List<NotificationResponseDto>> findNotifications(String customerId) {
        return ResponseEntity.ok(notifications.findByCustomer(customerId).stream()
                .map(NotificationController::toDto).toList());
    }

    @Override
    public ResponseEntity<NotificationResponseDto> getNotification(UUID notificationId) {
        return ResponseEntity.ok(toDto(notifications.get(notificationId)));
    }

    static NotificationResponseDto toDto(Notification n) {
        return new NotificationResponseDto()
                .id(n.id())
                .orderId(n.orderId())
                .customerId(n.customerId())
                .type(NotificationTypeDto.fromValue(n.type().name()))
                .channel(NotificationChannelDto.fromValue(n.channel().name()))
                .status(NotificationStatusDto.fromValue(n.status().name()))
                .text(n.text())
                .createdAt(n.createdAt().atOffset(ZoneOffset.UTC))
                .sentAt(n.sentAt() == null ? null : n.sentAt().atOffset(ZoneOffset.UTC))
                .failureReason(n.failureReason());
    }
}
