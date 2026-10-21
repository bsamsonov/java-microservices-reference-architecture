# Java microservices reference architecture

A runnable reference architecture and service template for Java microservices: Spring Boot 4.1, Spring Modulith 2.1, Java 25,
Postgres, Kafka, OpenTelemetry, Keycloak, Prometheus and Grafana. It runs on a laptop with Docker Compose and on Kubernetes (k3d + Helm)
from the same service artifacts, and it is built to be extended by a developer working with an AI coding agent.

## What is inside

| Part | Where |
|---|---|
| Three services that exercise the platform: `orders` (Spring Modulith, domain model, transactional outbox → Kafka), `notifications` (idempotent Kafka consumer with retries and DLT), `catalog` (small CRUD on Spring Data JDBC) | [`services/`](services/) |
| Two service templates and a `create-service` skill that scaffolds a new service from them | [`.claude/skills/create-service/`](.claude/skills/create-service/SKILL.md) |
| Architecture overview: quality drivers, C4 diagrams, runtime scenarios, risks | [`docs/architecture/`](docs/architecture/README.md) |
| Decisions with rationale and cost (D1–D11) | [`docs/decisions.md`](docs/decisions.md) |
| Laptop mode: Postgres, Kafka, Keycloak, Grafana LGTM, Alertmanager | [`compose/`](compose/README.md) |
| Kubernetes mode: k3d, CloudNativePG, Strimzi, Keycloak, kube-prometheus-stack, Loki, Tempo, OTel Collector, NetworkPolicy | [`platform/`](platform/README.md) |
| Dashboards, Prometheus rules, SLOs (Pyrra), Alertmanager routing, runbooks | [`observability/`](observability/README.md), [`docs/alerting.md`](docs/alerting.md) |
| Security: Keycloak realm as code, every service is an OAuth2 resource server, default-deny network policies | [`security/`](security/README.md) |
| Rules for AI agents, enforced by architecture tests | [`AGENTS.md`](AGENTS.md) |

## Quick start (laptop)

Requires Docker and Java 25.

```bash
for s in orders notifications catalog; do (cd services/$s-service && ./mvnw -q -DskipTests package); done
docker compose -f compose/compose.yaml --profile apps up -d --build

TOKEN=$(security/token.sh alice)
curl -s -X POST localhost:8080/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"c-1","lines":[{"productCode":"P-1","quantity":2,"unitPrice":10.00}]}'
```

Then open Grafana at http://localhost:3000 (admin/admin): the "Spring services" dashboard, the trace of the request in Tempo
and its log lines in Loki. Kubernetes mode: `make -C platform up`, then `tilt ci` (see [`platform/README.md`](platform/README.md)).

## Tests

`./mvnw verify` in each service runs unit, slice, module, integration (Testcontainers) and architecture tests.
`observability/check-alerting.sh` checks the alert rules, SLOs, Alertmanager config and runbooks.

## License

[MIT](LICENSE)
