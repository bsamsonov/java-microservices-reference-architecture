package io.github.bsamsonov.catalog.product;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases and the transaction boundary. Optimistic locking: the client sends the version it has read;
 * a different version is a conflict. A concurrent update between read and save is caught by {@code @Version}
 * ({@code update … where version = ?} touches no row -> OptimisticLockingFailureException).
 */
@Service
@Transactional
class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository products;
    private final Clock clock;

    ProductService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    Product create(String code, String name, BigDecimal price) {
        if (products.existsByCode(code)) {
            throw new ProductConflictException("product with code " + code + " already exists");
        }
        Product product = products.save(new Product(code, name, price, clock.instant()));
        log.info("Product created: id={}, code={}", product.getId(), product.getCode());
        return product;
    }

    @Transactional(readOnly = true)
    Product get(UUID id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional(readOnly = true)
    Page<Product> list(int page, int size, boolean activeOnly) {
        PageRequest request = PageRequest.of(page, size, Sort.by("code"));
        return activeOnly ? products.findByActiveTrue(request) : products.findAll(request);
    }

    Product update(UUID id, String name, BigDecimal price, boolean active, long expectedVersion) {
        Product product = get(id);
        if (product.getVersion() != expectedVersion) {   // unboxed: a stored product always has a version
            throw new ProductConflictException("product " + id + " has version " + product.getVersion()
                    + ", request was based on " + expectedVersion);
        }
        product.update(name, price, active, clock.instant());
        Product saved = products.save(product);     // no dirty checking in JDBC: save is the update
        log.info("Product updated: id={}, version={}", id, saved.getVersion());
        return saved;
    }

    void delete(UUID id) {
        products.delete(get(id));
        log.info("Product deleted: id={}", id);
    }
}
