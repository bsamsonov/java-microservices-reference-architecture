package io.github.bsamsonov.orders.order.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.bsamsonov.orders.SecurityConfig;
import io.github.bsamsonov.orders.order.application.OrderNotFoundException;
import io.github.bsamsonov.orders.order.application.OrderService;
import io.github.bsamsonov.orders.order.domain.Order;
import io.github.bsamsonov.orders.order.domain.OrderLine;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static io.github.bsamsonov.orders.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** Web slice: contract (validation, status codes, problem+json) and access policy, without DB and Kafka. */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    OrderService orders;

    @Test
    void placeOrderReturns201WithLocation() {
        Order order = Order.place("c-1", List.of(new OrderLine("A", 2, new BigDecimal("5.00"))), Instant.now());
        given(orders.place(any())).willReturn(order);

        assertThat(mvc.post().uri("/orders").with(token("orders:write")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":"c-1","lines":[{"productCode":"A","quantity":2,"unitPrice":5.00}]}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/orders/" + order.id())
                .bodyJson().extractingPath("$.total").isEqualTo(10.0);
    }

    @Test
    void emptyLinesAreRejectedByContract() {
        assertThat(mvc.post().uri("/orders").with(token("orders:write")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":"c-1","lines":[]}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownOrderIsProblem404() {
        UUID id = UUID.randomUUID();
        given(orders.get(id)).willThrow(new OrderNotFoundException(id));

        assertThat(mvc.get().uri("/orders/{id}", id).with(token("orders:read")))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    // --- access policy (SecurityConfig) ---

    @Test
    void requestWithoutTokenIs401() {
        assertThat(mvc.get().uri("/orders/{id}", UUID.randomUUID()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void readPermissionCannotPlaceOrders() {
        assertThat(mvc.post().uri("/orders").with(token("orders:read")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":"c-1","lines":[{"productCode":"A","quantity":1,"unitPrice":1.00}]}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void permissionsClaimOfKeycloakTokenBecomesAuthorities(@Autowired JwtAuthenticationConverter converter) {
        Jwt keycloakToken = Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("permissions", List.of("orders:read", "orders:write")).build();

        assertThat(converter.convert(keycloakToken).getAuthorities()).map(GrantedAuthority::getAuthority)
                .contains("orders:read", "orders:write");   // plus Spring's own FACTOR_BEARER
    }
}
