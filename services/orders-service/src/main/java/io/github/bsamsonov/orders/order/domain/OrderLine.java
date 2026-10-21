package io.github.bsamsonov.orders.order.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderLine(String productCode, int quantity, BigDecimal unitPrice) {

    public OrderLine {
        if (productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("productCode must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
