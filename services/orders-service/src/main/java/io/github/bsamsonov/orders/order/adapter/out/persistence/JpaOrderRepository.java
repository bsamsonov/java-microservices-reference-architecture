package io.github.bsamsonov.orders.order.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import io.github.bsamsonov.orders.order.application.OrderRepository;
import io.github.bsamsonov.orders.order.domain.Order;
import io.github.bsamsonov.orders.order.domain.OrderLine;

import org.springframework.stereotype.Repository;

/** Implements the application port; the only place that maps domain ⇄ JPA. */
@Repository
class JpaOrderRepository implements OrderRepository {

    private final OrderJpaRepository jpa;

    JpaOrderRepository(OrderJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpa.findById(id).map(JpaOrderRepository::toDomain);
    }

    @Override
    public void save(Order order) {
        OrderEntity entity = jpa.findById(order.id()).orElseGet(() -> new OrderEntity(order.id()));
        entity.setCustomerId(order.customerId());
        entity.setStatus(order.status());
        entity.setCreatedAt(order.createdAt());
        entity.setCancellationReason(order.cancellationReason());
        entity.getLines().clear();
        order.lines().forEach(l -> entity.getLines()
                .add(new OrderLineEmbeddable(l.productCode(), l.quantity(), l.unitPrice())));
        jpa.save(entity);
    }

    private static Order toDomain(OrderEntity e) {
        return Order.restore(e.getId(), e.getCustomerId(),
                e.getLines().stream().map(l -> new OrderLine(l.productCode(), l.quantity(), l.unitPrice())).toList(),
                e.getCreatedAt(), e.getStatus(), e.getCancellationReason());
    }
}
