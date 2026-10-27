# orders-service: the reference service (template 1: Spring Modulith + domain model)

Decisions: `docs/adr/`. Spring Boot 4.1.1, Spring Modulith 2.1.1, Java 25, Maven.

## Structure
```
io.github.bsamsonov.orders
├── OrdersApplication, OpenTelemetryLoggingConfig   root: bootstrap and cross-cutting configuration only
├── order/                       module "Orders"; module API = the OrderPlaced and OrderCancelled events
│   ├── domain/                  Order (aggregate, invariants), OrderLine, OrderStatus; no Spring/JPA
│   ├── application/             OrderService (use cases, transaction boundary), port OrderRepository
│   └── adapter/
│       ├── in/web/              OrderController implements OrdersApi (generated from openapi.yaml), problem+json
│       └── out/persistence/     OrderEntity, JpaOrderRepository (domain ⇄ JPA mapping)
└── audit/                       module "Audit": listens to events, writes audit.audit_log (idempotent by event_id)
```
- API contract: `src/main/resources/api/openapi.yaml`; the interface and DTOs are generated at build time.
- Database: each module has its own schema (`orders`, `audit`), Flyway `V1`/`V2`; `V3` is the Spring Modulith outbox table.
- Events: `@Externalized("orders.events::#{orderId}")` → Kafka through the event publication registry
  (the event is stored in the same transaction as the order, delivered after commit; ADR-0003: a failed delivery is resubmitted
  by `OutboxResubmission` every `outbox.resubmission.interval` without a restart, a delivered row is deleted;
  ADR-0037: a row left in flight by a crashed pod is marked `FAILED` after 5 minutes by the Modulith staleness monitor; no republish on restart;
  on a planned stop the task executor waits for the sends already started).
  `OutboxMetrics` exports the backlog: `outbox_publications_pending` and `outbox_publications_oldest_age_seconds`, behind the alert `OutboxBacklogStale`.
- Observability: `/actuator/prometheus`, health probes, traces and logs over OTLP (`OpenTelemetryLoggingConfig`),
  trace context in Kafka headers; `/actuator/modulith` shows the module map.

## Tests (ADR-0031): `./mvnw verify`, 26 tests, all green
| Level | Class |
|---|---|
| Unit | `OrderTest` |
| Web slice | `OrderControllerTest` (`@WebMvcTest`, MockMvcTester): contract and access — 401 without a token, 403 without the permission, mapping of the `permissions` claim |
| JPA slice | `JpaOrderRepositoryTest` (`@DataJpaTest` + Postgres Testcontainers + Flyway) |
| Module | `OrderModuleTest` (`@ApplicationModuleTest`, `Scenario`) |
| Integration | `OrdersFlowIntegrationTest`: HTTP → database → audit → Kafka, 409 on a repeated cancel, empty outbox |
| Integration | `OutboxRedeliveryIntegrationTest`: Kafka paused → order placed → publication `FAILED` and the backlog gauges up → Kafka resumed → event in `orders.events`, outbox empty, gauges at 0, no restart |
| Integration | `OutboxStalePublicationIntegrationTest`: a 10-minute-old row in `PUBLISHED`, `PROCESSING` or `RESUBMITTED`, as a crashed pod leaves it → marked `FAILED` → event in `orders.events`, outbox empty |
| Architecture | `ModularityTest` (Modulith verify + docs in `target/spring-modulith-docs`), `HexagonArchitectureTest` (ArchUnit), `ModuleSchemaTest` (a schema per module, migrations by schema) |

`ModularityTest` was checked to fail when the `audit` module touches an internal class of `order`.

## Run
- Tests: `./mvnw verify` (needs Docker).
- Locally without a cluster: `./mvnw spring-boot:test-run` (Postgres and Kafka in Testcontainers).
- In the cluster: `make -C platform up`, then `tilt up` / `tilt ci` from the repository root (shared chart `charts/spring-service` + `deploy/values.yaml`).

## Verified in the cluster
`make -C platform up`, then `tilt ci`: the pod is Ready, an order sent through `orders.localhost:8080` goes to Kafka and reaches notifications-service
(details in `platform/README.md`).

## Security (ADR-0023)
Every request carries a Keycloak token; access rules are in `SecurityConfig`, permissions in `openapi.yaml`.
How to get a token and add a permission: `security/README.md`.
