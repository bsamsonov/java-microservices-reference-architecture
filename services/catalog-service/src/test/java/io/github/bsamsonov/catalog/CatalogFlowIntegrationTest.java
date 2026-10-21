package io.github.bsamsonov.catalog;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static io.github.bsamsonov.catalog.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end inside the service on real Postgres: the full CRUD cycle with optimistic locking. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogFlowIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void productLifecycle() {
        MvcTestResult created = mvc.post().uri("/products").with(token("catalog:write")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"FLOW-1","name":"Mug","price":7.50}
                        """).exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.version").isEqualTo(0);
        String location = created.getResponse().getHeader("Location");

        assertThat(mvc.post().uri("/products").with(token("catalog:write")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"FLOW-1","name":"Another mug","price":1}
                        """)).hasStatus(HttpStatus.CONFLICT);

        String update = """
                {"name":"Big mug","price":9.00,"active":false,"version":%d}
                """;
        assertThat(mvc.put().uri(location).with(token("catalog:write")).contentType(MediaType.APPLICATION_JSON).content(update.formatted(0)))
                .hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(1);
        // second client still holds version 0
        assertThat(mvc.put().uri(location).with(token("catalog:write")).contentType(MediaType.APPLICATION_JSON).content(update.formatted(0)))
                .hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(mvc.get().uri(location).with(token("catalog:read"))).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("Big mug");
        assertThat(mvc.get().uri("/products").with(token("catalog:read")).param("activeOnly", "true")).hasStatusOk()
                .bodyJson().extractingPath("$.items[*].code").asArray().doesNotContain("FLOW-1");

        assertThat(mvc.delete().uri(location).with(token("catalog:write"))).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri(location).with(token("catalog:read"))).hasStatus(HttpStatus.NOT_FOUND);
    }
}
