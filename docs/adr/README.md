# Architecture decision records

Each record states one choice, the options that were considered, what it costs and when to choose differently.
The format is [MADR 4](https://adr.github.io/madr/) with two additions: the quality drivers it serves
(QD1–QD7 in [`docs/architecture/README.md`](../architecture/README.md#2-quality-drivers)) and one or two links of evidence.
New records start from [`template.md`](template.md).

The earlier short list D1–D12 (still named in some commit messages) is mapped to ADRs in the column "Was".

## Start here
Eight records carry the story of the repository; the rest follow from them.

| ADR | Why read it |
|---|---|
| [0001](0001-modulith-inside-each-microservice.md) | Service boundaries vs module boundaries: microservices with a modular monolith inside |
| [0002](0002-integrate-services-through-events.md) | Services integrate through domain events in Kafka |
| [0003](0003-outbox-on-modulith-event-registry.md) | How an event survives a broker outage: the outbox |
| [0005](0005-event-format-json-with-json-schema.md) | The event contract and how it evolves without breaking consumers |
| [0007](0007-database-per-service-schema-per-module.md) | Who owns which data |
| [0017](0017-two-run-modes-same-artifacts.md) | One artifact on a laptop and in Kubernetes |
| [0024](0024-architecture-rules-as-tests.md) | Architecture rules are tests that fail the build |
| [0025](0025-ai-agent-as-a-participant.md) | Built to be extended by a developer working with an AI agent |

## Rules
- Records are dated when written. ADR-0001 to ADR-0036 were written on 2026-10-22 for decisions taken while the templates were built;
  a record that replaces an earlier decision names its number D-n.
- An ADR names a generation (Java 25, Spring Boot 4, Kafka 4), not a minor or patch version: exact versions live in `pom.xml`,
  `compose/compose.yaml` and `platform/`, and change without a new ADR. A move to a new generation is a new ADR or an update of the old one.
- A decision is never changed silently. To change it, write a new ADR and mark the old one "Superseded by ADR-NNNN"; do not rewrite history.
- An `Accepted`, `Rejected` or `Superseded` ADR is frozen: a pull request may change only its `Status` and `Implemented` lines and add `Amended by` lines.
  A partial change is a new ADR that `Amends` the old one; a full change `Supersedes` it.
  CI (`adr-guard`, [ADR-0034](0034-repository-rules-as-code.md)) fails on any other edit; the label `adr-amend` lets a typo or link fix through.
- A rule that can be checked is checked by a test or a script, and the ADR links to it.

## Index

| ADR | Decision | Group | Status | Drivers | Was |
|---|---|---|---|---|---|
| [0001](0001-modulith-inside-each-microservice.md) | Microservices with a modular monolith inside (Spring Modulith) | System | Accepted | QD1, QD2 | D6 (in part) |
| [0002](0002-integrate-services-through-events.md) | Integrate services through domain events, not synchronous calls | System | Accepted | QD3, QD5 | D2 |
| [0003](0003-outbox-on-modulith-event-registry.md) | Transactional outbox on the Spring Modulith event publication registry | System | Accepted | QD3, QD7 | D12 |
| [0004](0004-idempotent-consumers-retries-and-dlt.md) | Idempotent consumers, bounded retries and a dead-letter topic | System | Accepted | QD3, QD5 |  |
| [0005](0005-event-format-json-with-json-schema.md) | Event format: JSON now, JSON Schema and AsyncAPI next; registry and binary formats deferred | System | Accepted (JSON in force; JSON Schema and AsyncAPI planned) | QD2, QD3 |  |
| [0006](0006-api-first-openapi.md) | API-first: OpenAPI file, generated interfaces | System | Accepted | QD1, QD2 |  |
| [0007](0007-database-per-service-schema-per-module.md) | A database per service and a schema per module | System | Accepted | QD2, QD6, QD7 |  |
| [0008](0008-java-25-spring-boot-4.md) | Java 25 and Spring Boot 4 | Stack | Accepted | QD1, QD2 |  |
| [0009](0009-postgresql.md) | PostgreSQL as the database for every service | Stack | Accepted | QD6, QD7 |  |
| [0010](0010-kafka-as-event-broker.md) | Apache Kafka as the event broker | Stack | Accepted | QD3, QD6, QD7 | D3 (in part) |
| [0011](0011-flyway-migrations.md) | Flyway SQL migrations, forward only | Stack | Accepted | QD1, QD2 |  |
| [0012](0012-maven-build.md) | Maven with the wrapper as the build tool | Stack | Accepted | QD1, QD2 | D5 |
| [0013](0013-container-image-dockerfile.md) | Container image from a layered Dockerfile over a prebuilt jar | Stack | Accepted | QD6, QD7 |  |
| [0014](0014-helm-shared-service-chart.md) | One shared Helm chart for every Spring service | Platform | Accepted | QD1, QD6 | D1 |
| [0015](0015-operators-for-postgres-and-kafka.md) | Postgres and Kafka in Kubernetes through operators (CloudNativePG, Strimzi) | Platform | Accepted | QD6, QD7 | D3 |
| [0016](0016-k3d-and-tilt-for-local-kubernetes.md) | k3d and Tilt for local Kubernetes and the inner dev loop | Platform | Accepted | QD1, QD7 | D4 |
| [0017](0017-two-run-modes-same-artifacts.md) | Two run modes, Docker Compose and Kubernetes, with the same artifacts and pinned versions | Platform | Accepted | QD6, QD7 |  |
| [0018](0018-gitops-with-argo-cd.md) | GitOps delivery with Argo CD | Platform | Accepted (not implemented yet, see the [Roadmap](../../README.md#roadmap-what-is-deliberately-not-done)) | QD6 |  |
| [0019](0019-secrets-in-git-for-gitops.md) | Secrets for GitOps: SOPS with age, decrypted for Argo CD | Platform | Accepted (not implemented yet, with ADR-0018) | QD4, QD6 |  |
| [0020](0020-opentelemetry-through-spring-boot.md) | Telemetry through Spring Boot's OpenTelemetry support (Micrometer, OTLP), not the Java agent | Observability | Accepted | QD5, QD6 |  |
| [0021](0021-dashboards-as-code.md) | Dashboards as code, one set for both modes, filtered by the application label | Observability | Accepted | QD1, QD5, QD6 | D9 |
| [0022](0022-alerting-prometheus-rules-alertmanager-pyrra.md) | Alerting: Prometheus rules, Alertmanager, SLOs through Pyrra | Observability | Accepted | QD2, QD5 | D10 |
| [0023](0023-keycloak-resource-server-network-policy.md) | Security baseline: Keycloak, token validation in every service, default-deny NetworkPolicy | Security | Accepted | QD2, QD4 | D11 |
| [0024](0024-architecture-rules-as-tests.md) | Architecture rules are tests (fitness functions), decisions are ADRs | Process | Accepted | QD2 |  |
| [0025](0025-ai-agent-as-a-participant.md) | The AI agent is a participant: AGENTS.md, a scaffolding skill, rules enforced by tests | Process | Accepted | QD1, QD2 |  |
| [0026](0026-no-spring-cloud-netflix-or-config-server.md) | No Spring Cloud service discovery or config server | Rejected / deferred | Rejected (Eureka, Spring Cloud Config) for this platform | QD6, QD7 |  |
| [0027](0027-service-mesh-deferred.md) | Service mesh and service-to-service mTLS: deferred | Rejected / deferred | Deferred | QD4, QD7 |  |
| [0028](0028-api-gateway-deferred.md) | API gateway: deferred, an ingress controller is enough | Rejected / deferred | Deferred | QD4, QD7 |  |
| [0029](0029-multi-cluster-and-cloud-deferred.md) | Multi-cluster, high availability and cloud infrastructure as code: deferred | Rejected / deferred | Deferred | QD7 |  |
| [0030](0030-light-hexagon-inside-a-module.md) | Inside a module: a light hexagon (domain, application, adapters) | Service level | Accepted | QD2 | D6 (in part) |
| [0031](0031-test-pyramid.md) | Test pyramid: unit, slices, module, integration, architecture | Service level | Accepted | QD2, QD3 | D7 |
| [0032](0032-jpa-for-rich-domain-jdbc-for-crud.md) | Persistence: JPA in template 1, Spring Data JDBC in template 2 | Service level | Accepted | QD1, QD2 | D8 |
| [0033](0033-lgtm-observability-backends.md) | Observability backends: Grafana LGTM (Loki, Grafana, Tempo, Prometheus) | Observability | Accepted | QD5, QD6, QD7 |  |
| [0034](0034-repository-rules-as-code.md) | Repository rules as code: protected main, frozen ADRs, one service per pull request | Process | Accepted | QD2 |  |
| [0035](0035-security-scanning-in-ci.md) | Security scanning in CI: gitleaks, CodeQL and dependency review | Process | Accepted | QD2, QD4 |  |
| [0036](0036-conventional-commits.md) | Commit messages: Conventional Commits from 2026-10-22, checked in CI | Process | Accepted | QD1, QD2 |  |

## Notes
- Schema registry: covered by [ADR-0005](0005-event-format-json-with-json-schema.md) as a deferred step.
- Numbers follow the order of writing, not the groups; use the Group column.
