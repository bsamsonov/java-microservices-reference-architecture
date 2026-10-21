package io.github.bsamsonov.orders.order;

import java.math.BigDecimal;
import java.util.List;

import io.github.bsamsonov.orders.TestcontainersConfiguration;
import io.github.bsamsonov.orders.order.application.OrderService;
import io.github.bsamsonov.orders.order.application.PlaceOrderCommand;
import io.github.bsamsonov.orders.order.domain.OrderLine;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots only the orders module (no audit module beans) and checks the events it publishes. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class OrderModuleTest {

    @Autowired
    OrderService orders;

    @Test
    void placingOrderPublishesOrderPlaced(Scenario scenario) {
        scenario.stimulate(() -> orders.place(new PlaceOrderCommand("c-1",
                        List.of(new OrderLine("A", 3, BigDecimal.ONE)))))
                .andWaitForEventOfType(OrderPlaced.class)
                .toArriveAndVerify(event -> {
                    assertThat(event.customerId()).isEqualTo("c-1");
                    assertThat(event.total()).isEqualByComparingTo("3");
                });
    }

    @Test
    void cancellingOrderPublishesOrderCancelledWithCustomer(Scenario scenario) {
        var order = orders.place(new PlaceOrderCommand("c-2", List.of(new OrderLine("A", 1, BigDecimal.ONE))));

        scenario.stimulate(() -> orders.cancel(order.id(), "changed mind"))
                .andWaitForEventOfType(OrderCancelled.class)
                .toArriveAndVerify(event -> {
                    assertThat(event.orderId()).isEqualTo(order.id());
                    assertThat(event.customerId()).isEqualTo("c-2");
                });
    }
}
