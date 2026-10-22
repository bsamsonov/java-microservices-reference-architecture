# ADR-0031: Test pyramid: unit, slices, module, integration, architecture

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2, QD4
- **Replaces:** D7
- **Level:** service; the table "what to add with a feature" is in `AGENTS.md`

## Context and drivers
The guarantees that matter (no lost events, idempotency, access rules, module boundaries) must be proven by tests that run on every build (QD2, QD4).
Integration with Postgres and Kafka must be tested against the real thing, not mocks.

## Considered options
1. **A pyramid by scope**: unit without Spring; slices (`@WebMvcTest`, `@DataJpaTest`/`@DataJdbcTest` on Testcontainers); module (`@ApplicationModuleTest`, `Scenario`);
   integration (`@SpringBootTest` with Testcontainers Postgres and Kafka); architecture (Modulith, ArchUnit).
2. **Mostly end-to-end tests** against a running environment.
3. **Mostly unit tests with mocks** for the database and Kafka.

## Decision
Option 1. Mocks only in the web slice (`@MockitoBean` for the use case); module and integration tests use a real database and Kafka.
Containers are configured once in `TestcontainersConfiguration` (`PostgresContainerConfig`, `KafkaContainerConfig`).
Deferred: mutation testing (PIT), starting with the domain module, in CI; contract tests between services (later Pact or Spring Cloud Contract); a smoke check after deploy (with ADR-0018).

## Consequences
- **Good:** each test proves one thing at the cheapest level that can prove it; the outbox and DLT behaviour is tested with a real broker.
- **Cost:** `./mvnw verify` needs Docker and takes minutes, not seconds; Testcontainers images must follow the platform versions (ADR-0017).

## When to choose differently
- No Docker in CI: run the slices and module tests against a shared test database, accepting slower feedback and shared state.
- Many consuming teams: add consumer-driven contract tests before more end-to-end tests.

## Evidence
- [`OrdersFlowIntegrationTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/OrdersFlowIntegrationTest.java),
  [`NotificationsFlowIntegrationTest`](../../services/notifications-service/src/test/java/io/github/bsamsonov/notifications/NotificationsFlowIntegrationTest.java).
- [Martin Fowler: the practical test pyramid](https://martinfowler.com/articles/practical-test-pyramid.html).
