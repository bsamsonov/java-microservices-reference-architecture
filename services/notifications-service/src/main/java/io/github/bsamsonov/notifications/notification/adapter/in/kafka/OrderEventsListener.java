package io.github.bsamsonov.notifications.notification.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.github.bsamsonov.notifications.notification.application.NotificationService;
import io.github.bsamsonov.notifications.notification.application.NotifyCommand;
import io.github.bsamsonov.notifications.notification.domain.NotificationType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter for topic orders.events. The event type comes from the {@code __TypeId__} header
 * (fully qualified class name of the producer); only its simple name is used, so a package rename
 * on the producer side does not break this service. Unknown types are skipped.
 * Exceptions go to the error handler (see {@link KafkaErrorHandlingConfig}).
 */
@Component
class OrderEventsListener {

    static final String TOPIC = "orders.events";
    static final String TYPE_HEADER = "__TypeId__";

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final NotificationService notifications;
    private final JsonMapper json;

    OrderEventsListener(NotificationService notifications, JsonMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @KafkaListener(topics = TOPIC)
    void on(ConsumerRecord<String, String> record) {
        String type = eventType(record);
        switch (type) {
            case "OrderPlaced" -> {
                var event = parse(record, OrderEventMessages.OrderPlaced.class);
                notifications.notify(command(event.eventId(), event.orderId(), event.customerId(),
                        NotificationType.ORDER_PLACED, record));
            }
            case "OrderCancelled" -> {
                var event = parse(record, OrderEventMessages.OrderCancelled.class);
                notifications.notify(command(event.eventId(), event.orderId(), event.customerId(),
                        NotificationType.ORDER_CANCELLED, record));
            }
            default -> log.debug("Event type skipped: type={}, key={}", type, record.key());
        }
    }

    private static String eventType(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(TYPE_HEADER);
        if (header == null) {
            throw new InvalidMessageException("no " + TYPE_HEADER + " header, key=" + record.key(), null);
        }
        String className = new String(header.value(), StandardCharsets.UTF_8);
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private <T> T parse(ConsumerRecord<String, String> record, Class<T> type) {
        try {
            return json.readValue(record.value(), type);
        } catch (JacksonException e) {
            throw new InvalidMessageException("unreadable " + type.getSimpleName() + ", key=" + record.key(), e);
        }
    }

    private static NotifyCommand command(UUID eventId, UUID orderId, String customerId, NotificationType type,
                                         ConsumerRecord<String, String> record) {
        if (eventId == null || orderId == null || customerId == null || customerId.isBlank()) {
            throw new InvalidMessageException("invalid " + type + ", key=" + record.key()
                    + ": eventId, orderId and customerId are required", null);
        }
        return new NotifyCommand(eventId, orderId, customerId, type);
    }
}
