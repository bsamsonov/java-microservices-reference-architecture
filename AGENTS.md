# AGENTS.md: rules for AI agents in this repository

The repository holds Java service templates: Spring Boot 4.1, Spring Modulith 2.1, Java 25, Maven, Postgres, Kafka, OpenTelemetry, Helm/k3d and Docker Compose.
The goal of the templates: on "create service X", assemble a service quickly from a sample. Only the business logic is discussed; the infrastructure comes from the template.
Decisions D1–D11 and their rationale are in `docs/decisions.md`. A decision is never changed silently: update `docs/decisions.md` first.

## Repository map
| Path | What |
|---|---|
| `services/orders-service/` | the reference service (template 1: Modulith, domain model, light hexagon); copy it |
| `services/catalog-service/` | template 2: a small CRUD without Modulith or events, Spring Data JDBC (D8); copy it for services without business rules |
| `services/notifications-service/` | consumer of `orders.events`: Kafka listener, idempotency by `eventId`, retries and DLT; the sample for services that read other services' events |
| `.claude/skills/create-service/` | the "create service X" skill: template choice, order of work and `scaffold.sh [--from catalog]` (a renamed copy of a template) |
| `charts/spring-service/` | the shared Helm chart for every Spring service; each service has its own `deploy/values.yaml` |
| `observability/` | the "Spring services" dashboard (D9), Prometheus rules, SLOs, Alertmanager and runbooks (D10); shared by compose and k3d |
| `docs/alerting.md` | how alerting works and how to add an alert or an SLO |
| `security/` | security (D11): Keycloak realm as code, `token.sh`, README on how it works and how to add a permission |
| `compose/` | laptop mode: Postgres, Kafka, Keycloak, Grafana LGTM, services in the `apps` profile |
| `platform/` | Kubernetes mode: k3d, CloudNativePG, Strimzi, Keycloak, kube-prometheus-stack, Loki, Tempo, OTel Collector, NetworkPolicy |
| `Tiltfile` | dev loop in k3d: `spring_service('<name>')` per service |
| `docs/architecture/` | architecture overview (arc42-lite): quality drivers QD1–QD8, C4 in Mermaid, scenarios, risks R1–R9 |
| `docs/decisions.md` | decisions (ADR-lite) |

## Commands
All commands run from the service folder (`services/<name>`) unless stated otherwise.

| Task | Command |
|---|---|
| All tests (needs Docker) | `./mvnw verify` |
| Build the jar without tests | `./mvnw -DskipTests package` |
| Run with infrastructure in Testcontainers | `./mvnw spring-boot:test-run` |
| Infrastructure on the laptop (from the root) | `docker compose -f compose/compose.yaml up -d` |
| The same plus the services in containers (from the root) | `./mvnw -DskipTests package` in each service, then `docker compose -f compose/compose.yaml --profile apps up -d --build` |
| Kubernetes (from the root) | `make -C platform up`, then `tilt ci` (or `tilt up` with the UI) |
| Check alerting (from the root, needs Docker) | `observability/check-alerting.sh` |

Grafana: http://localhost:3000 (admin/admin), Alertmanager: http://localhost:9093. Service: http://localhost:8080, module map: `/actuator/modulith`.
Keycloak: http://localhost:8180 (admin/admin). API calls need a token: `TOKEN=$(security/token.sh alice)`, `curl -H "Authorization: Bearer $TOKEN" …`.

## Service architecture (D6): template 1
Template 2 (`catalog-service`) is simpler: package by feature (controller, service, Spring Data JDBC aggregate, repository), no Modulith;
its rules are checked by `ArchitectureTest`, details in the service README. The rules below apply to template 1.
```
io.github.bsamsonov.<service>
├── <Service>Application            bootstrap and cross-cutting configuration only
├── <module>/                       module = subdomain (@ApplicationModule in package-info.java)
│   ├── <Event>.java                the module's public API: domain events (record)
│   ├── domain/                     aggregates and invariants; no Spring, no JPA
│   ├── application/                use cases (@Service, @Transactional) and ports (interfaces)
│   └── adapter/
│       ├── in/web/                 controller implements <Tag>Api (generated from openapi.yaml)
│       └── out/persistence/        JPA entities, Spring Data, port implementation, domain ⇄ JPA mapping
└── <other-module>/internal/        a module without a public API only reacts to events
```
Rules. Tests enforce them: a violation fails `./mvnw verify`.
1. **Modules by subdomain, not by layer.** A module's public API is only the types in its root package. Subpackages are internal and must not be used by other modules (checked by `ModularityTest`).
2. **Modules talk through events.** The source publishes an event through `ApplicationEventPublisher` inside the transaction; the receiver takes it with `@ApplicationModuleListener`, which runs after commit, in its own transaction, with retries. Calling another module's API is allowed only when it is needed synchronously.
3. **Hexagon inside a module** (checked by `HexagonArchitectureTest`):
   - `domain` does not depend on Spring, JPA, `application` or `adapter`;
   - `application` does not know about `adapter`;
   - `adapter.in` does not touch `adapter.out`;
   - `@Entity` lives only in `adapter.out.persistence`, `@RestController` only in `adapter.in.web`.
4. **Each module has its own database schema** (`orders`, `audit`, …). Other modules' tables are never read or written.
5. **A CRUD module without business rules** may skip the separate domain model. This is the simplified variant of D6; it is marked in the module's `package-info.java`.

## Contracts: the file first, then the code
- **HTTP.** Edit `src/main/resources/api/openapi.yaml` first. The `<Tag>Api` interface and DTOs with the `Dto` suffix are generated at build time into `target/generated-sources`. Generated code is never edited or committed. Errors are returned as `application/problem+json`.
- **Events.** An event is a `record` in the module's root package and always has `UUID eventId` and `Instant occurredAt`. It goes to Kafka through `@Externalized("<module>.events::#{#this.<aggregateId>().toString()}")`: topic `<module>.events`, key = aggregate id. When an event is published to a topic for the first time, add the topic to `deploy/values.yaml` (`kafka.topics`).
- **Migrations.** Flyway, files `src/main/resources/db/migration/V<n>__<module>_<what>.sql`. An applied migration is never changed; write a new one instead. `ddl-auto` stays `validate`.
- **Outbox (D12).** The `event_publication` table comes from Spring Modulith, its migration is `V3`. The event is written in the same transaction as the data and delivered after commit.
  A failed delivery stays in the table and `OutboxResubmission` resubmits it on a schedule; a delivered row is deleted (`completion-mode: delete`), so the table holds only the backlog.
- **Idempotency.** Every event handler must survive redelivery. Usually a unique `event_id` and `on conflict do nothing`.
- **Access (D11).** Every endpoint requires a permission `<service>:<action>`. A new endpoint means four edits (details in `security/README.md`):
  role and group in `security/keycloak/realm-blueprints.json`, a line in `SecurityConfig`, the permission and `401`/`403` responses in `openapi.yaml`, tests for 401 and 403.

## Observability
- **Logs.** SLF4J. A business event is logged at INFO with the aggregate id (`Order placed: id=…`). Personal data and secrets never go to the log. `trace_id` is added automatically.
- **Traces and logs** go over OTLP; addresses come from the `MANAGEMENT_OPENTELEMETRY_*` environment variables. Trace context travels in Kafka headers (`spring.kafka.template.observation-enabled`).
- **Metrics.** In Kubernetes Prometheus scrapes `/actuator/prometheus`. In compose the service pushes them over OTLP: `MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=true`, off by default.
- **Dashboards** live in `observability/grafana/`, **alerts** are Prometheus rules in `observability/prometheus/` and SLOs in `observability/slo/` (D10).
  Everything filters by the `application` label: a new service is covered by the dashboard, alerts and SLOs without edits.
  For that, keep `management.metrics.tags.application`, `management.otlp.metrics.export.base-time-unit: seconds`,
  `percentiles-histogram` and `slo.http.server.requests: 500ms` in `application.yaml` (details in `observability/README.md`). Check a new panel or rule in both modes.
- **A new alert**: labels `severity` and `team`, runbook `observability/runbooks/<AlertName>.md`, a test in `prometheus/rules/tests/`,
  then `observability/check-alerting.sh` (the order is in `docs/alerting.md`). `critical` is for SLO alerts only; causes are `warning`.
  Files in `observability/prometheus/generated/` are generated from `slo/` and never edited by hand.
- **Addresses of external systems** come only from environment variables (`SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, etc.). Defaults in `application.yaml` point to `localhost`. The service does not know whether it runs in compose or Kubernetes.

## Tests (D7): what to add with a feature
| Change | Test |
|---|---|
| domain rule | unit test without Spring (`OrderTest`) |
| endpoint, validation, error codes | `@WebMvcTest` and `MockMvcTester` (`OrderControllerTest`); requests with `.with(token("<permission>"))` |
| access rule | the same class: 401 without a token and 403 with another permission (`@Import(SecurityConfig.class)`) |
| mapping, queries, migration | `@DataJpaTest` on Testcontainers Postgres (`JpaOrderRepositoryTest`); in template 2 `@DataJdbcTest` (`ProductRepositoryTest`) |
| use case publishes an event | `@ApplicationModuleTest` and `Scenario` (`OrderModuleTest`) |
| end-to-end scenario through HTTP, database, event and Kafka | `@SpringBootTest` and Testcontainers (`OrdersFlowIntegrationTest`) |
| handler of other services' Kafka events (duplicate, DLT) | `@SpringBootTest` and Testcontainers (`NotificationsFlowIntegrationTest`) |
| new module or package | nothing to write: `ModularityTest` and `HexagonArchitectureTest` pick it up |

Test containers are configured in `TestcontainersConfiguration`, which imports `PostgresContainerConfig` (Postgres 18) and `KafkaContainerConfig` (Kafka 4.3). Mocks are allowed only in the web slice (`@MockitoBean` for the use case). Module and integration tests use a real database and Kafka.

Infrastructure versions (Postgres, Kafka, otel-lgtm) are pinned and equal in compose, Testcontainers and the cluster manifests: change all places together, never use `latest`.

## Done when
1. `./mvnw verify` is green. A failing architecture test is fixed in the code; a rule is never weakened for a feature.
2. Contracts are updated: `openapi.yaml`, the event, the migration, the topic in `deploy/values.yaml`.
3. The tests from the table above exist.
4. If infrastructure or configuration changed: the service starts in compose (`--profile apps`), the `POST` scenario answers, and the trace and logs are visible in Grafana.
5. The service README (structure, tests) is updated, and so are `docs/decisions.md` and `docs/architecture/` if a decision or a driver changed.
6. The commit message is in English, one logical unit per commit.

## Never
- Use another module's internal packages or its tables.
- Edit generated code or applied migrations.
- Put Spring or JPA annotations into `domain`.
- Hardcode addresses, passwords or secrets outside `application.yaml` (local defaults only) and `compose.yaml`. In Kubernetes secrets come through `secretEnv` in the values.
- Disable or skip tests (`-DskipTests` before a commit, `@Disabled`) to get a green build.
- Open an endpoint without a permission (`permitAll`) except health and `/actuator/prometheus`; log tokens.
- Write non-English text anywhere in the repository: code, comments, docs, commit messages.
