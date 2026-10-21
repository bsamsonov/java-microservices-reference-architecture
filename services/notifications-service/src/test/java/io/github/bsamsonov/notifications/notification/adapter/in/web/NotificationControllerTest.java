package io.github.bsamsonov.notifications.notification.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.bsamsonov.notifications.SecurityConfig;
import io.github.bsamsonov.notifications.notification.application.NotificationNotFoundException;
import io.github.bsamsonov.notifications.notification.application.NotificationService;
import io.github.bsamsonov.notifications.notification.domain.Notification;
import io.github.bsamsonov.notifications.notification.domain.NotificationChannel;
import io.github.bsamsonov.notifications.notification.domain.NotificationType;
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

import static io.github.bsamsonov.notifications.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** Web slice: contract (parameters, status codes, problem+json) and access policy, without DB and Kafka. */
@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    NotificationService notifications;

    @Test
    void findsNotificationsOfCustomer() {
        Notification n = Notification.create(UUID.randomUUID(), UUID.randomUUID(), "c-1",
                NotificationType.ORDER_CANCELLED, NotificationChannel.EMAIL, Instant.now());
        n.markSent(Instant.now());
        given(notifications.findByCustomer("c-1")).willReturn(List.of(n));

        assertThat(mvc.get().uri("/notifications").with(token("notifications:read")).param("customerId", "c-1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].status").isEqualTo("SENT");
    }

    @Test
    void customerIdIsRequiredByContract() {
        assertThat(mvc.get().uri("/notifications").with(token("notifications:read"))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownNotificationIsProblem404() {
        UUID id = UUID.randomUUID();
        given(notifications.get(id)).willThrow(new NotificationNotFoundException(id));

        assertThat(mvc.get().uri("/notifications/{id}", id).with(token("notifications:read")))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    // --- access policy (SecurityConfig) ---

    @Test
    void requestWithoutTokenIs401() {
        assertThat(mvc.get().uri("/notifications").param("customerId", "c-1"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenWithoutNotificationsPermissionIs403() {
        assertThat(mvc.get().uri("/notifications").with(token("orders:read")).param("customerId", "c-1"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void permissionsClaimOfKeycloakTokenBecomesAuthorities(@Autowired JwtAuthenticationConverter converter) {
        Jwt keycloakToken = Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("permissions", List.of("notifications:read")).build();

        assertThat(converter.convert(keycloakToken).getAuthorities()).map(GrantedAuthority::getAuthority)
                .contains("notifications:read");   // plus Spring's own FACTOR_BEARER
    }
}
