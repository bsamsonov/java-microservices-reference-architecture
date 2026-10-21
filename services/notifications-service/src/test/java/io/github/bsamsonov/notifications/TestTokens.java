package io.github.bsamsonov.notifications;

import java.util.Arrays;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Requests with an access token in MockMvc tests, without Keycloak: the token counts as already validated.
 * Signature, issuer and audience checks are Spring's job; the tests check the access policy (SecurityConfig).
 */
public final class TestTokens {

    private TestTokens() {
    }

    /** A request from a user whose token carries these permissions, e.g. {@code token("notifications:read")}. */
    public static RequestPostProcessor token(String... permissions) {
        return jwt().authorities(Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new)
                .toArray(GrantedAuthority[]::new));
    }
}
