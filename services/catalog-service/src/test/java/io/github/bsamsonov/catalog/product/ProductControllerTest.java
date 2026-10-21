package io.github.bsamsonov.catalog.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.bsamsonov.catalog.SecurityConfig;
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

import static io.github.bsamsonov.catalog.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** Web slice: contract (validation, status codes, problem+json) and access policy, without DB. */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ProductService products;

    @Test
    void createReturns201WithLocation() {
        Product product = new Product("A-1", "Mug", new BigDecimal("7.50"), Instant.now());
        given(products.create("A-1", "Mug", new BigDecimal("7.50"))).willReturn(product);

        assertThat(mvc.post().uri("/products").with(token("catalog:write")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"A-1","name":"Mug","price":7.50}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/products/" + product.getId());
    }

    @Test
    void lowercaseCodeAndNegativePriceAreRejectedByContract() {
        assertThat(mvc.post().uri("/products").with(token("catalog:write")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"a-1","name":"Mug","price":1}
                        """)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post().uri("/products").with(token("catalog:write")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"A-1","name":"Mug","price":-1}
                        """)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pageSizeAboveLimitIsRejectedByContract() {
        assertThat(mvc.get().uri("/products").with(token("catalog:read")).param("size", "500")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void staleVersionIsProblem409() {
        UUID id = UUID.randomUUID();
        given(products.update(id, "Mug", BigDecimal.ONE, true, 3)).willThrow(new ProductConflictException("stale"));

        assertThat(mvc.put().uri("/products/{id}", id).with(token("catalog:write")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Mug","price":1,"active":true,"version":3}
                        """))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void unknownProductIsProblem404() {
        UUID id = UUID.randomUUID();
        given(products.get(id)).willThrow(new ProductNotFoundException(id));

        assertThat(mvc.get().uri("/products/{id}", id).with(token("catalog:read")))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    // --- access policy (SecurityConfig) ---

    @Test
    void requestWithoutTokenIs401() {
        assertThat(mvc.get().uri("/products")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void readPermissionCannotChangeCatalog() {
        assertThat(mvc.post().uri("/products").with(token("catalog:read")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"A-1","name":"Mug","price":7.50}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void permissionsClaimOfKeycloakTokenBecomesAuthorities(@Autowired JwtAuthenticationConverter converter) {
        Jwt keycloakToken = Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("permissions", List.of("catalog:read", "catalog:write")).build();

        assertThat(converter.convert(keycloakToken).getAuthorities()).map(GrantedAuthority::getAuthority)
                .contains("catalog:read", "catalog:write");   // plus Spring's own FACTOR_BEARER
    }
}
