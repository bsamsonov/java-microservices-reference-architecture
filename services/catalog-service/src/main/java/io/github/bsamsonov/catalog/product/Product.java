package io.github.bsamsonov.catalog.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Product: aggregate and model at once (CRUD variant), mapped by Spring Data JDBC (ADR-0032): one row, no session,
 * no lazy loading; a change is stored only by an explicit {@code save}. Columns follow the default snake_case naming.
 * {@code code} is the immutable business key.
 */
@Table(schema = "products", name = "products")
class Product {

    @Id
    private UUID id;
    private String code;
    private String name;
    private BigDecimal price;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    /** {@code null} until the first insert: that is how Spring Data JDBC tells insert from update with a preset id. */
    @Version
    private Long version;

    protected Product() {
    }

    Product(String code, String name, BigDecimal price, Instant now) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.price = price;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String name, BigDecimal price, boolean active, Instant now) {
        this.name = name;
        this.price = price;
        this.active = active;
        this.updatedAt = now;
    }

    UUID getId() { return id; }
    String getCode() { return code; }
    String getName() { return name; }
    BigDecimal getPrice() { return price; }
    boolean isActive() { return active; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    Long getVersion() { return version; }
}
