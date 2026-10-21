package io.github.bsamsonov.orders.order.application;

import java.util.List;

import io.github.bsamsonov.orders.order.domain.OrderLine;

public record PlaceOrderCommand(String customerId, List<OrderLine> lines) {
}
