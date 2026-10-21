package io.github.bsamsonov.orders.order.adapter.out.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
record OrderLineEmbeddable(
        @Column(name = "product_code", nullable = false) String productCode,
        @Column(name = "quantity", nullable = false) int quantity,
        @Column(name = "unit_price", nullable = false) BigDecimal unitPrice) {
}
