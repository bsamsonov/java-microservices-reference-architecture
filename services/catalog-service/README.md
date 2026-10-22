# catalog-service: template 2 (a small CRUD microservice)

Decisions: ADR-0001 (template 2) and ADR-0032 in `docs/adr/`. A product catalog: create, read, paging, update with optimistic locking, delete.
The template for a service without business rules that needs no events.
Once invariants, several subdomains or Kafka events appear, use template 1 (`orders-service`).

## How it differs from template 1
| | Template 1 (`orders-service`) | Template 2 (`catalog-service`) |
|---|---|---|
| Modularity | Spring Modulith, `ModularityTest` | package by feature, ArchUnit (`ArchitectureTest`) |
| Inside a feature | `domain` / `application` / `adapter` | one package: controller, service, aggregate, repository; all package-private |
| Model | domain aggregate + separate JPA entity | the Spring Data JDBC aggregate is the model |
| Persistence (ADR-0032) | Spring Data JPA, `ddl-auto: validate` | Spring Data JDBC, explicit `save`, no session or lazy loading |
| Events | outbox → Kafka | none (no Kafka) |
| Tests | unit, web, JPA, module, flow, architecture | web, JDBC, flow, architecture |

## Structure
```
io.github.bsamsonov.catalog
├── CatalogApplication, OpenTelemetryLoggingConfig   root: bootstrap and cross-cutting configuration
└── product/                     feature; package-info.java marks the CRUD variant of ADR-0030
    ├── ProductController        implements ProductsApi (generated from openapi.yaml), aggregate → DTO mapping
    ├── ProductExceptionHandler  problem+json: 404, 409 (duplicate code, stale version, races), 400 for parameters
    ├── ProductService           transaction boundary, duplicate and version checks
    ├── Product                  Spring Data JDBC aggregate, @Version Long
    └── ProductRepository        ListCrudRepository + ListPagingAndSortingRepository
```
- **HTTP:** `POST /products` (201, 409 on a duplicate `code`), `GET /products?page&size&activeOnly` (sorted by `code`, `size` ≤ 100),
  `GET|PUT|DELETE /products/{id}`. `PUT` takes the `version` the client has read; a stale version gives 409.
- **Optimistic locking in two layers.** The service compares the request version with the current one. If an update slips in between the read and `save`,
  `@Version` catches it: `update … where version = ?` matches no row and `OptimisticLockingFailureException` is thrown (→ 409).
- **Schema:** `products`, Flyway `V1`. JDBC has no `ddl-auto: validate`, so `ProductRepositoryTest` checks the mapping against the schema by writing and reading every column.
- **Pitfall:** the generated interface is `@Validated`, so violations of the `page` and `size` constraints arrive as
  `ConstraintViolationException`. Without the handler in `ProductExceptionHandler` the client would get 500 instead of 400.
- **Observability** as in template 1: `/actuator/prometheus`, traces and logs over OTLP, INFO logs `Product created/updated/deleted` with the id.

## Tests: `./mvnw verify`, 18 tests, all green
| Level | Class |
|---|---|
| Web slice | `ProductControllerTest` (`@WebMvcTest`, MockMvcTester): 201 + Location, body and parameter validation, 404/409 problem+json; 401, 403, mapping of the `permissions` claim |
| JDBC slice | `ProductRepositoryTest` (`@DataJdbcTest` + Postgres Testcontainers + Flyway): all columns, `code` uniqueness, version increment, stale copy, paging |
| Integration | `CatalogFlowIntegrationTest`: create → duplicate 409 → update → stale version 409 → filter → delete → 404 |
| Architecture | `ArchitectureTest`: the controller does not touch the repository, transactions only in the service, no cycles between features, the aggregate stays inside its feature |

There is no domain unit test: the CRUD variant has no domain rules (ADR-0031).

## Run
- Tests: `./mvnw verify` (needs Docker).
- Locally: `SERVER_PORT=8082 ./mvnw spring-boot:test-run` (Postgres in Testcontainers).
- Compose: http://localhost:8082, database `catalog` (see `compose/README.md`).
- k3d: `spring_service('catalog-service')` in the `Tiltfile`, database through `platform/manifests/databases/catalog.yaml` (see `platform/README.md`).

## Verified in compose
Create → 201 (version 0); duplicate `code` → 409; update → version 1; stale version → 409 with the reason; `size=500` → 400.
The `Product updated` line reaches Loki with a `trace_id`, the `http put /products/{productId}` trace is in Tempo, and `http_server_requests` by status is in Prometheus.
There are no SQL spans in the trace (template 1 has none either): they need `datasource-micrometer`.

## Security (ADR-0023)
Every request carries a Keycloak token; access rules are in `SecurityConfig`, permissions in `openapi.yaml`.
How to get a token and add a permission: `security/README.md`.
