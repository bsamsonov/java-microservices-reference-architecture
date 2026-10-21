package io.github.bsamsonov.orders.order.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.github.bsamsonov.orders.PostgresContainerConfig;
import io.github.bsamsonov.orders.order.domain.Order;
import io.github.bsamsonov.orders.order.domain.OrderLine;
import io.github.bsamsonov.orders.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** JPA slice on real Postgres with Flyway migrations: mapping domain ⇄ tables. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainerConfig.class, JpaOrderRepository.class})
class JpaOrderRepositoryTest {

    @Autowired
    JpaOrderRepository repository;

    @Test
    void savesAndRestoresOrderWithLinesInOrder() {
        Order order = Order.place("c-1", List.of(
                new OrderLine("A", 1, new BigDecimal("1.10")),
                new OrderLine("B", 3, new BigDecimal("2.00"))), Instant.now().truncatedTo(ChronoUnit.MICROS));

        repository.save(order);
        Order loaded = repository.findById(order.id()).orElseThrow();

        assertThat(loaded.lines()).extracting(OrderLine::productCode).containsExactly("A", "B");
        assertThat(loaded.total()).isEqualByComparingTo("7.10");
        assertThat(loaded.createdAt()).isEqualTo(order.createdAt());
    }

    @Test
    void updatesStatusOnCancel() {
        Order order = Order.place("c-1", List.of(new OrderLine("A", 1, BigDecimal.ONE)), Instant.now());
        repository.save(order);

        order.cancel("test");
        repository.save(order);

        assertThat(repository.findById(order.id()).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
    }
}
