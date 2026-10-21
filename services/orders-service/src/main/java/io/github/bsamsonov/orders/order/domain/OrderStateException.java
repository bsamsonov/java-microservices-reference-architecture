package io.github.bsamsonov.orders.order.domain;

/** The requested transition is not allowed in the current order state (maps to HTTP 409). */
public class OrderStateException extends RuntimeException {

    public OrderStateException(String message) {
        super(message);
    }
}
