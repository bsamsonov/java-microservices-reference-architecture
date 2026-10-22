# ADR-0021: Dashboards as code, one set for both modes, filtered by the application label

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD1, QD5, QD6
- **Replaces:** D9 (the dashboard part; its alerting part was replaced by D10, now ADR-0022)

## Context and drivers
A new service must appear on the dashboards without anyone editing them (QD1). The same dashboard must work with OTLP push in compose
and with scraping in Kubernetes (QD6), and a broken panel must be visible in review (QD5).

## Considered options
1. **Grafana dashboard JSON in git** (`observability/grafana/dashboards/`), provisioned in both modes; one shared dashboard filtered by `application`.
2. **Dashboards generated from code** (Grafana Foundation SDK, Grafonnet).
3. **Dashboards edited in the UI only**, stored in Grafana's database.

## Decision
Option 1. Compose mounts the JSON into `grafana/otel-lgtm`; k3d loads it as a ConfigMap picked up by the Grafana sidecar (`make -C platform dashboards`).
Data source uids (`prometheus`, `loki`, `tempo`) are the same in both modes. The "Spring services" dashboard groups by the `application` label.
For identical queries, every service sets in `application.yaml`: `management.metrics.tags.application`,
`management.otlp.metrics.export.base-time-unit: seconds`, percentiles histograms for `http.server.requests` (and `spring.kafka.listener`), and the 500 ms SLO bucket.
A dashboard is edited in the UI, exported and saved over the file, keeping its `uid`.

## Consequences
- **Good:** one dashboard for any number of services; changes are reviewed as diffs; rebuilding the stack loses nothing.
- **Cost:** exported JSON is long and noisy in diffs; each service must keep the four metric settings (the template has them);
  a new panel must be checked in both modes.

## When to choose differently
- Many similar dashboards that differ by parameters: **generate them** (Foundation SDK, Grafonnet).
- A Grafana Cloud stack with its own provisioning: keep the JSON, deliver it through the cloud's API or Terraform.

## Evidence
- [`observability/README.md`](../../observability/README.md): what the service must provide and the known name mismatch.
- [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/).
