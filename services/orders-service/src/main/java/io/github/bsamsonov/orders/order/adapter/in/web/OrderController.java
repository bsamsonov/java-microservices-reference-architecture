package io.github.bsamsonov.orders.order.adapter.in.web;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;

import io.github.bsamsonov.orders.order.adapter.in.web.api.OrdersApi;
import io.github.bsamsonov.orders.order.adapter.in.web.api.model.CancelOrderRequestDto;
import io.github.bsamsonov.orders.order.adapter.in.web.api.model.OrderLineDto;
import io.github.bsamsonov.orders.order.adapter.in.web.api.model.OrderResponseDto;
import io.github.bsamsonov.orders.order.adapter.in.web.api.model.OrderStatusDto;
import io.github.bsamsonov.orders.order.adapter.in.web.api.model.PlaceOrderRequestDto;
import io.github.bsamsonov.orders.order.application.OrderService;
import io.github.bsamsonov.orders.order.application.PlaceOrderCommand;
import io.github.bsamsonov.orders.order.domain.Order;
import io.github.bsamsonov.orders.order.domain.OrderLine;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the interface generated from api/openapi.yaml; maps DTO ⇄ domain. */
@RestController
class OrderController implements OrdersApi {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public ResponseEntity<OrderResponseDto> placeOrder(PlaceOrderRequestDto request) {
        Order order = orders.place(new PlaceOrderCommand(request.getCustomerId(),
                request.getLines().stream()
                        .map(l -> new OrderLine(l.getProductCode(), l.getQuantity(), l.getUnitPrice()))
                        .toList()));
        return ResponseEntity.created(URI.create("/orders/" + order.id())).body(toDto(order));
    }

    @Override
    public ResponseEntity<OrderResponseDto> getOrder(UUID orderId) {
        return ResponseEntity.ok(toDto(orders.get(orderId)));
    }

    @Override
    public ResponseEntity<OrderResponseDto> cancelOrder(UUID orderId, CancelOrderRequestDto request) {
        return ResponseEntity.ok(toDto(orders.cancel(orderId, request.getReason())));
    }

    static OrderResponseDto toDto(Order order) {
        return new OrderResponseDto()
                .id(order.id())
                .customerId(order.customerId())
                .status(OrderStatusDto.fromValue(order.status().name()))
                .total(order.total())
                .createdAt(order.createdAt().atOffset(ZoneOffset.UTC))
                .cancellationReason(order.cancellationReason())
                .lines(order.lines().stream()
                        .map(l -> new OrderLineDto().productCode(l.productCode())
                                .quantity(l.quantity()).unitPrice(l.unitPrice()))
                        .toList());
    }
}
