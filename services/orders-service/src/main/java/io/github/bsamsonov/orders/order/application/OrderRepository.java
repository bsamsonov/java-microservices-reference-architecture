package io.github.bsamsonov.orders.order.application;

import java.util.Optional;
import java.util.UUID;

import io.github.bsamsonov.orders.order.domain.Order;

/** Outgoing port. Implemented by adapter.out.persistence. */
public interface OrderRepository {

    Optional<Order> findById(UUID id);

    void save(Order order);
}
