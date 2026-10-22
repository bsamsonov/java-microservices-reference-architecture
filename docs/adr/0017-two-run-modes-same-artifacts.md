# ADR-0017: Two run modes, Docker Compose and Kubernetes, with the same artifacts and pinned versions

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD5, QD6

## Context and drivers
Daily work needs a light stack on a laptop; showing the platform needs Kubernetes. A k3d cluster needs about 20 GB of free disk and more memory,
while compose runs the whole system in about 3.5 GB of images (QD6). Whatever runs in one mode must behave the same in the other (QD5).

## Considered options
1. **Two modes**: compose for daily work, k3d for the platform; services do not know which one they run in.
2. **Kubernetes only**, also for daily work.
3. **Compose only**, Kubernetes described but not run.

## Decision
Option 1. The same jar and image run in both modes; only environment variables differ (`SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `MANAGEMENT_OPENTELEMETRY_*`).
Dashboards, rules, SLOs, the Alertmanager config and the Keycloak realm are one set of files for both modes (ADR-0021, ADR-0022, ADR-0023).
Rule: **infrastructure versions are pinned and equal in compose, Testcontainers and the cluster**; an upgrade changes all three in one commit.

## Consequences
- **Good:** fast daily loop; the Kubernetes mode is real, not a slide; every observability file is tested in two environments.
- **Cost:** two sets of infrastructure definitions to keep in step; differences between `otel-lgtm` and kube-prometheus-stack (OTLP push vs scraping)
  must be absorbed by the service config. Every infrastructure upgrade touches compose, the Testcontainers configs and the cluster manifests together.
  The rule was broken once (risk R10: Kafka and PostgreSQL majors differed between compose and k3d, `otel-lgtm` was `latest`) and is now met; the versions live in `compose/compose.yaml`,
  the Testcontainers configs and `platform/manifests/`.

## When to choose differently
- The team develops against a shared remote cluster: **Kubernetes only**, with Tilt or a remote dev environment.
- The target is not Kubernetes (VMs, a PaaS): **compose only** for local work, and the deployment tool of that target.

## Evidence
- [`compose/compose.yaml`](../../compose/compose.yaml) and [`platform/README.md`](../../platform/README.md): the same scenario verified in both modes.
- [Architecture overview, risk R10 (closed)](../architecture/README.md#10-risks-and-technical-debt); the rule is in `AGENTS.md`.
