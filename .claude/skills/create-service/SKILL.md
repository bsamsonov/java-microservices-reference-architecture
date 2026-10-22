---
name: create-service
description: Creates a new Spring service in this repository from one of two templates (services/orders-service — Modulith and a domain model; services/catalog-service — a simple CRUD on Spring Data JDBC). It copies the template, renames packages, artifact, database, schema and topic, replaces the domain, writes tests per ADR-0031, runs ./mvnw verify and wires the service into compose, Tilt and the platform. Only business logic is discussed; infrastructure comes from the template. Use when the user says "create service X", "new service", "add a microservice", "scaffold a service", "make a service from the template".
---

# create-service

The service rules (modules, hexagon, contracts, tests, definition of done, prohibitions) are in `AGENTS.md` and are not repeated here.
This skill sets the order of work. There are two templates (ADR-0001, ADR-0032):
| Template | Sample | When |
|---|---|---|
| 1 | `services/orders-service` | invariants and states, several subdomains, Kafka events (published or consumed) |
| 2 | `services/catalog-service` | CRUD without business rules or events: Spring Data JDBC, no Modulith, one feature = one package |

The template follows from the answers in step 0; do not impose it on the user, name it together with the reason.
If a template 2 service later grows rules or events, move it to template 1 instead of extending template 2.

## 0. Business questions (one round)
Do not ask about infrastructure: it comes from the template. Ask in one message and offer a default for every item:
1. **Name** of the service in kebab-case (`payments`, `loyalty-points`); the `-service` suffix is added automatically.
2. **Aggregate(s) and invariants**: which states exist, which transitions are forbidden. No business rules and no events → template 2.
   No rules but events are needed → template 1 with a CRUD module (ADR-0030, rule 5 in `AGENTS.md`): no `domain/`, marked in `package-info.java`.
3. **Use cases → HTTP**: commands and queries, error codes (404, 409, 422).
4. **Events**: what the module publishes and who listens. The topic is `<schema>.events`, the key is the aggregate id.
   Separately: does the service consume other services' events, and which ones? If so, use the consumer variant (step 2b) and decide what to do with invalid messages (default: retries, then DLT).
5. **The `audit` module**: keep it (a log of all events) or remove it. Default: keep.

If the user has already described the domain, skip the questions and list the assumptions in the reply.

## 1. Skeleton (mechanical, by script)
```bash
.claude/skills/create-service/scaffold.sh <name>                  # template 1, from the repository root
.claude/skills/create-service/scaffold.sh --from catalog <name>   # template 2
```
The script copies the git-tracked files of the template into `services/<name>-service` and renames:
the artifact, the root package `io.github.bsamsonov.<name without dashes>`, the classes `<Name>Application`,
`Test<Name>Application`, `<Name>FlowIntegrationTest`, the database name `<name with _>` (in `application.yaml` and `deploy/values.yaml`) and the ingress host.
Module-level names and the README stay as in the template and change together with the domain: in template 1 these are the `order` module, the `orders` schema and the `orders.events` topic (step 2);
in template 2 the `product` feature and the `products` schema (step 2c).
A dry run is possible: `scaffold.sh [--from catalog] <name> <temp-dir>`; a copy of either template passes `verify`.

## 2. Domain (template 1): contracts first, then code
Work in `services/<name>-service`. The `order` module is renamed to the module of the new subdomain `<module>`
(singular, like `order`). The schema and the topic prefix are plural, like `orders`.
1. **`pom.xml`**: in `apiPackage` and `modelPackage` replace `.order.` with `.<module>.`.
2. **`openapi.yaml`**: paths, tag (tag `Payments` produces the `PaymentsApi` interface), request and response schemas, `Problem` responses.
3. **Migration**: `V1__orders_schema.sql` becomes `V1__<schema>_schema.sql`. `V2` (audit) and `V3` (outbox) stay as they are.
   The service is new, so `V1` may be rewritten: "never change an applied migration" applies to a service that is already running.
4. **Events**: a `record` in the module root with `UUID eventId`, `UUID <aggregate>Id`, …, `Instant occurredAt`;
   annotated `@Externalized("<schema>.events::#{#this.<aggregate>Id().toString()}")`.
5. **Code**: `domain/` → `application/` (use case, repository port, commands) → `adapter/in/web` (controller implements `<Tag>Api`,
   exception handler with problem+json) → `adapter/out/persistence` (entities with `schema = "<schema>"`, domain ⇄ JPA mapping).
   A business event needs an INFO log line with the aggregate id.
6. **`audit`**: if the module stays, `AuditLogListener` gets one `on(...)` per new event.
   If it goes, delete the `audit` package, `V2__audit_schema.sql` and the audit checks in the flow test; Flyway tolerates the version gap.
7. Delete the `Order*` files. Check that nothing of the template is left:
   `grep -rniE '\border|orders' src deploy pom.xml` returns nothing (except where the word belongs to the new domain).

## 2b. Consumer variant: the service reads another service's topic
Sample: `services/notifications-service`. Differences from the reference:
- **Inbound adapter** `adapter/in/kafka/`: `@KafkaListener` on `ConsumerRecord<String, String>`. The event type is taken by simple name
  from the `__TypeId__` header (Modulith puts the producer's fully qualified class name there). The JSON is read into the service's **own** records with the needed fields;
  producer classes are not imported. Unknown types are skipped (`debug` log).
- **Errors**: a `DefaultErrorHandler` bean (Boot attaches it to the container factory) with a `DeadLetterPublishingRecoverer` to `<topic>.DLT`
  (partition `-1`) and a WARN log on transfer: the recoverer itself logs nothing. Invalid messages need
  their own exception in `addNotRetryableExceptions`. A failure of an external channel is handled in the domain (for example a FAILED status), not through the DLT.
- **Idempotency**: the inbound `eventId` is stored in a `unique` column, plus an `exists…` check in the use case.
- **Configuration**: `spring.kafka.consumer.group-id: ${spring.application.name}`, `auto-offset-reset: earliest`,
  `spring.kafka.listener.observation-enabled: true` (the trace continues from the headers). A producer is needed only for the DLT,
  with the default (String) serializers; the reference's `ByteArraySerializer` is not needed.
- **Publishes no events** → remove the outbox: `spring-modulith-starter-jdbc`, `spring-modulith-events-kafka`, `V3__event_publication.sql`,
  the `spring.modulith.events` and `outbox` blocks, `OutboxResubmission` and `OutboxRedeliveryIntegrationTest`; the `audit` module is not needed either. If the service starts publishing later, bring them back from the reference.
- **`values.yaml`**: the source topic belongs to the producer; the consumer declares only `<topic>.DLT`.
- If the producer's event lacks data (as `customerId` in `OrderCancelled` once did), add the field at the producer
  in a separate commit: adding a field is compatible. Old messages without the field go to the DLT, as expected.

## 2c. Template 2: CRUD on Spring Data JDBC
Sample: `services/catalog-service`. The `product` package is renamed to the feature package `<feature>`, the `products` schema to `<schema>`.
1. **`pom.xml`**: `apiPackage`/`modelPackage` = `io.github.bsamsonov.<pkg>.<feature>.api(.model)`.
2. **`openapi.yaml`** → **migration** `V1__<schema>_schema.sql` (snake_case columns, `version bigint not null`) → **code**:
   an aggregate with `@Table(schema = "<schema>", name = …)`, `@Id UUID`, `@Version Long` (`null` = new row; with `long` an insert looks like an update),
   a `ListCrudRepository` + `ListPagingAndSortingRepository` repository, a service (`@Transactional`, duplicate and version checks),
   a controller, an exception handler. Feature classes are package-private.
3. **No dirty checking**: every change is stored by an explicit `repository.save(...)`, and the returned instance (with the new version) is used afterwards.
4. **No `ddl-auto: validate`**: the repository test writes and reads every column, otherwise a mapping error surfaces only at runtime.
5. Keep the `ConstraintViolationException` → 400 handler: without it, constraints on query parameters produce 500.
6. A second feature is a second package next to `product`, built the same way. The `ArchitectureTest` rule "the aggregate stays inside its feature"
   is written for `product` today: when a feature is added, generalise it to all feature packages instead of deleting it.

## 2d. Access permissions (all templates, ADR-0023)
How it works: `security/README.md`. Permissions are named `<schema>:read` and `<schema>:write` (like `orders:read`).
1. **`security/keycloak/realm-blueprints.json`**: realm roles `<schema>:read`, `<schema>:write`; grant them to a group
   (`customers` or a new one), otherwise no user has the permissions and every request gets 403.
2. **`SecurityConfig`** (root package, copied by the script): replace the template's paths and permissions (`/orders/**` or `/products/**`) with your own.
3. **`openapi.yaml`**: every operation gets `description: 'Requires permission `<schema>:…`.'` and `401`/`403` responses (the `security` block and `securitySchemes` are already there).
4. **Tests**: web-slice requests use `.with(token("<schema>:write"))`, the claim-mapping test uses your permissions; 401 and 403 for every rule.
5. A consumer without HTTP writes (like notifications) gets only `<schema>:read`.

## 3. Tests (ADR-0031)
**Template 2:** `ProductControllerTest` (`@WebMvcTest`), `ProductRepositoryTest` (`@DataJdbcTest`), `<Name>FlowIntegrationTest` (the full CRUD cycle over HTTP on Postgres);
`ArchitectureTest` stays unchanged, there is no domain unit test. The rest of this section is about template 1.

Every test class of the reference is rewritten for the new domain; the set of classes stays the same: aggregate unit test, `@WebMvcTest`,
`@DataJpaTest`, `@ApplicationModuleTest` with `Scenario`, and `<Name>FlowIntegrationTest` (HTTP → database → audit → Kafka topic `<schema>.events`, empty outbox).
`ModularityTest`, `HexagonArchitectureTest` and the Testcontainers configuration stay unchanged.
The CRUD variant has no domain unit test; the other tests remain.
In the consumer variant the module publishes nothing, so `Scenario` is not needed: the module test calls the use case and checks the state.
Outbound ports (the delivery channel) are replaced in the module test by a hand-written `@Primary` fake bean; mocks are not allowed there.
The flow test sends records in the producer's format (with the `__TypeId__` header) to the topic and checks: the result through HTTP,
that a duplicate and an unknown type are skipped, that broken JSON, a record without a required field and one without the header go to `<topic>.DLT`.

## 4. Build
`./mvnw verify` in the service folder; save the output outside the repository. If an architecture test fails, fix the code, never weaken the rule.
Common mistakes: `apiPackage` forgotten in `pom.xml`; `schema` in `@Table` does not match the migration (`ddl-auto: validate` in template 1, the repository test in template 2);
the SpEL in `@Externalized` refers to an accessor that does not exist.
Write Java files with the Write tool or a quoted heredoc (`<<'EOF'`): an unquoted heredoc turns `"$[0].status"` into `"0.status"`.

## 5. Wiring into the infrastructure (from the root)
| File | What to do |
|---|---|
| `services/<svc>/deploy/values.yaml` | template 1: `kafka.topics`: `<schema>.events` instead of `orders.events`; template 2: no Kafka, nothing to change |
| `Tiltfile` | add the line `spring_service('<svc>')` |
| `compose/compose.yaml` | copy the block of your template (`orders-service` or `catalog-service`; the latter has no Kafka) as `<svc>`: `build`, `image: <svc>:compose`, the next free host port (8080–8082 are taken), the database in `SPRING_DATASOURCE_URL` |
| `compose/postgres-init/<db>.sql` | `create database <db> owner app;` (see the README in that folder) |
| `platform/manifests/databases/<db>.yaml` | a `Database` CR from the README in that folder |
| `compose/README.md` | a row for the service in the address table |
| `compose/compose.yaml` (service block) | `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` is already in the copied block; check it |
| Keycloak | after a realm change: `docker compose -f compose/compose.yaml up -d --force-recreate keycloak` |

If the `pgdata` volume already exists, the init script does not run; create the database by hand:
`docker compose -f compose/compose.yaml exec postgres createdb -U app <db>`.
To run two services locally at once: `SERVER_PORT=8081 ./mvnw spring-boot:run`.

## 6. Check in compose
```bash
(cd services/<svc> && ./mvnw -DskipTests package)
docker compose -f compose/compose.yaml --profile apps up -d --build
TOKEN=$(security/token.sh alice)                         # a user from a group with the service's permissions
curl -i -X POST localhost:<port>/<path> -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '<body>'   # expect 201
curl -i -X POST localhost:<port>/<path> -H 'Content-Type: application/json' -d '<body>'   # no token: 401
```
Then check (as in the "Verified" sections of `compose/README.md`):
the metric `http_server_requests_seconds_count{application="<svc>"}` is in Prometheus (`localhost:9090`);
the service is covered by the SLOs: `http_server_requests_seconds:burnrate5m{application="<svc>"}` exists (rules from `observability/prometheus/generated/`);
in Grafana (http://localhost:3000, Explore) Tempo shows the trace `http post /<path>` → `<schema>.events send`,
and Loki has the business log line with its `trace_id`.
Queries without the UI: Loki and Tempo are reachable through the Grafana proxy, `curl -u admin:admin localhost:3000/api/datasources/proxy/uid/{loki|tempo}/…`
(for example `/api/traces/<trace_id>` in Tempo). For a consumer, check that the trace has spans of both services (`… send` → `… process`).
Kafka CLI: the `kafka-native` image has no scripts, so use
`docker run --rm --network blueprints_default apache/kafka:4.3.1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 …`. Deploy to k3d (`make -C platform up`, `tilt ci`) only when the user asks.

## 7. Documentation and commits
- Rewrite `services/<svc>/README.md` following the reference: structure, tests with their count, how to run.
- Add a row for the service to the "Repository map" table in `AGENTS.md`.
- Commits in English, one logical unit each: `Add <svc>: <one phrase about the domain>`, then `Wire <svc> into compose, Tilt and platform`.

## Report to the user
Briefly: which domain assumptions were made, which endpoints and events appeared, the `verify` result (test count),
the result of the compose check and what was not checked.
