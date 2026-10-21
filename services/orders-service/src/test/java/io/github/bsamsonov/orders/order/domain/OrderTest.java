package io.github.bsamsonov.orders.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    @Test
    void placedOrderSumsLineTotals() {
        Order order = Order.place("c-1", List.of(
                new OrderLine("A", 2, new BigDecimal("10.50")),
                new OrderLine("B", 1, new BigDecimal("4.00"))), NOW);

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.total()).isEqualByComparingTo("25.00");
    }

    @Test
    void orderWithoutLinesIsRejected() {
        assertThatThrownBy(() -> Order.place("c-1", List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lineWithNonPositiveQuantityIsRejected() {
        assertThatThrownBy(() -> new OrderLine("A", 0, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelledOrderCannotBeCancelledAgain() {
        Order order = Order.place("c-1", List.of(new OrderLine("A", 1, BigDecimal.ONE)), NOW);
        order.cancel("changed mind");

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> order.cancel("again")).isInstanceOf(OrderStateException.class);
    }
}
