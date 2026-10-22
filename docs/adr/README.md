# Architecture decision records

Each record states one choice, the options that were considered, what it costs and, most importantly, **when to choose differently**.
The format is [MADR 4](https://adr.github.io/madr/) with two additions: the quality drivers it serves
(QD1–QD8 in [`docs/architecture/README.md`](../architecture/README.md#2-quality-drivers)) and one or two links of evidence.
New records start from [`template.md`](template.md).

The earlier short list D1–D12 is mapped to ADRs in the column "Was" (and in [`docs/decisions.md`](../decisions.md), kept so old references resolve).

## Rules
- A decision is never changed silently. To change it, write a new ADR and mark the old one "Superseded by ADR-NNNN"; do not rewrite history.
- An accepted ADR is edited only to fix facts or links.
- A rule that can be checked is checked by a test or a script, and the ADR links to it.

## Index

| ADR | Decision | Group | Status | Drivers | Was |
|---|---|---|---|---|---|
| [0001](0001-modulith-inside-each-microservice.md) | Microservices with a modular monolith inside (Spring Modulith) | System | Accepted | QD1, QD4, QD8 | D6 (in part) |
| [0002](0002-integrate-services-through-events.md) | Integrate services through domain events, not synchronous calls | System | Accepted | QD2, QD3 | D2 |
| [0003](0003-outbox-on-modulith-event-registry.md) | Transactional outbox on the Spring Modulith event publication registry | System | Accepted | QD2, QD6 | D12 |
| [0004](0004-idempotent-consumers-retries-and-dlt.md) | Idempotent consumers, bounded retries and a dead-letter topic | System | Accepted | QD2, QD3 |  |
| [0005](0005-event-format-json-with-json-schema.md) | Event format: JSON now, JSON Schema and AsyncAPI next; registry and binary formats deferred | System | Accepted (JSON in force; JSON Schema and AsyncAPI planned) | QD2, QD8 |  |
| [0006](0006-api-first-openapi.md) | API-first: OpenAPI file, generated interfaces | System | Accepted | QD1, QD4, QD8 |  |
| [0007](0007-database-per-service-schema-per-module.md) | A database per service and a schema per module | System | Accepted (per-service database user planned) | QD4, QD5, QD6 |  |
| [0008](0008-java-25-spring-boot-4.md) | Java 25 and Spring Boot 4.1 | Stack | Accepted | QD1, QD8 |  |
| [0009](0009-postgresql.md) | PostgreSQL as the database for every service | Stack | Accepted | QD5, QD6 |  |
| [0010](0010-kafka-as-event-broker.md) | Apache Kafka as the event broker | Stack | Accepted | QD2, QD5, QD6 | D3 (in part) |
| [0011](0011-flyway-migrations.md) | Flyway SQL migrations, forward only | Stack | Accepted | QD1, QD4, QD8 |  |
| [0012](0012-maven-build.md) | Maven with the wrapper as the build tool | Stack | Accepted | QD1, QD8 | D5 |
| [0013](0013-container-image-dockerfile.md) | Container image from a layered Dockerfile over a prebuilt jar | Stack | Accepted | QD5, QD6 |  |
| [0014](0014-helm-shared-service-chart.md) | One shared Helm chart for every Spring service | Platform | Accepted | QD1, QD5 | D1 |
| [0015](0015-operators-for-postgres-and-kafka.md) | Postgres and Kafka in Kubernetes through operators (CloudNativePG, Strimzi) | Platform | Accepted | QD5, QD6 | D3 |
| [0016](0016-k3d-and-tilt-for-local-kubernetes.md) | k3d and Tilt for local Kubernetes and the inner dev loop | Platform | Accepted | QD6, QD1 | D4 |
| [0017](0017-two-run-modes-same-artifacts.md) | Two run modes, Docker Compose and Kubernetes, with the same artifacts and pinned versions | Platform | Accepted | QD5, QD6 |  |
| [0018](0018-gitops-with-argo-cd.md) | GitOps delivery with Argo CD | Platform | Accepted (not implemented yet, roadmap B5) | QD5 |  |
| [0019](0019-secrets-in-git-for-gitops.md) | Secrets for GitOps: SOPS with age, decrypted for Argo CD | Platform | Accepted (implemented with ADR-0018) | QD7, QD5 |  |
| [0020](0020-opentelemetry-through-spring-boot.md) | Telemetry through Spring Boot's OpenTelemetry support (Micrometer, OTLP), not the Java agent | Observability | Accepted | QD3, QD5 |  |
| [0021](0021-dashboards-as-code.md) | Dashboards as code, one set for both modes, filtered by the application label | Observability | Accepted | QD1, QD3, QD5 | D9 |
| [0022](0022-alerting-prometheus-rules-alertmanager-pyrra.md) | Alerting: Prometheus rules, Alertmanager, SLOs through Pyrra | Observability | Accepted | QD3, QD4 | D10 |
| [0023](0023-keycloak-resource-server-network-policy.md) | Security baseline: Keycloak, token validation in every service, default-deny NetworkPolicy | Security | Accepted | QD7, QD8 | D11 |
| [0024](0024-architecture-rules-as-tests.md) | Architecture rules are tests (fitness functions), decisions are ADRs | Process | Accepted | QD4, QD8 |  |
| [0025](0025-ai-agent-as-a-participant.md) | The AI agent is a participant: AGENTS.md, a scaffolding skill, rules enforced by tests | Process | Accepted | QD8, QD1 |  |
| [0026](0026-no-spring-cloud-netflix-or-config-server.md) | No Spring Cloud service discovery or config server | Rejected / deferred | Rejected (Eureka, Spring Cloud Config) for this platform | QD5, QD6 |  |
| [0027](0027-service-mesh-deferred.md) | Service mesh and service-to-service mTLS: deferred | Rejected / deferred | Deferred | QD6, QD7 |  |
| [0028](0028-api-gateway-deferred.md) | API gateway: deferred, an ingress controller is enough | Rejected / deferred | Deferred | QD6, QD7 |  |
| [0029](0029-multi-cluster-and-cloud-deferred.md) | Multi-cluster, high availability and cloud infrastructure as code: deferred | Rejected / deferred | Deferred | QD6 |  |
| [0030](0030-light-hexagon-inside-a-module.md) | Inside a module: a light hexagon (domain, application, adapters) | Service level | Accepted | QD4, QD8 | D6 (in part) |
| [0031](0031-test-pyramid.md) | Test pyramid: unit, slices, module, integration, architecture | Service level | Accepted | QD2, QD4 | D7 |
| [0032](0032-jpa-for-rich-domain-jdbc-for-crud.md) | Persistence: JPA in template 1, Spring Data JDBC in template 2 | Service level | Accepted | QD1, QD8 | D8 |

## Not written yet
- **Log, trace and metric backends: LGTM (Grafana, Loki, Tempo, Prometheus) vs ELK / OpenSearch vs one columnar store (ClickStack, SigNoz).**
  The owner wants to discuss it before the ADR. Today the stack is LGTM in both modes (ADR-0017, ADR-0021).
- Schema registry: covered by [ADR-0005](0005-event-format-json-with-json-schema.md) as a deferred step.
