package io.github.bsamsonov.orders.order.application;

import java.time.Clock;
import java.util.UUID;

import io.github.bsamsonov.orders.order.OrderCancelled;
import io.github.bsamsonov.orders.order.OrderPlaced;
import io.github.bsamsonov.orders.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the orders module. The transaction boundary lives here: the order row and the
 * event publication record (outbox) are committed together, Kafka delivery happens after commit.
 */
@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OrderService(OrderRepository orders, ApplicationEventPublisher events, Clock clock) {
        this.orders = orders;
        this.events = events;
        this.clock = clock;
    }

    public Order place(PlaceOrderCommand command) {
        Order order = Order.place(command.customerId(), command.lines(), clock.instant());
        orders.save(order);
        events.publishEvent(new OrderPlaced(UUID.randomUUID(), order.id(), order.customerId(),
                order.total(), clock.instant()));
        log.info("Order placed: id={}, customer={}, total={}", order.id(), order.customerId(), order.total());
        return order;
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order cancel(UUID id, String reason) {
        Order order = get(id);
        order.cancel(reason);
        orders.save(order);
        events.publishEvent(new OrderCancelled(UUID.randomUUID(), order.id(), order.customerId(), reason,
                clock.instant()));
        log.info("Order cancelled: id={}", order.id());
        return order;
    }
}
