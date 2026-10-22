# ADR-0033: Observability backends: Grafana LGTM (Loki, Grafana, Tempo, Prometheus)

- **Status:** Accepted (owner, 2026-10-22)
- **Date:** 2026-10-22
- **Drivers:** QD5, QD6, QD7

## Context and drivers
Services emit traces and logs over OTLP and expose metrics for Prometheus (ADR-0020). Something must store them and show one request across all three:
a trace from HTTP through Kafka to the consumer, and its log lines found by `trace_id` (QD5). The same backends must serve both modes (QD6)
and fit on a laptop (QD7). Because services speak only OTLP and Prometheus, the backend is a platform choice: the OTel Collector decides where data goes,
and changing the backend does not touch service code.

## Considered options
1. **Grafana LGTM**: Loki for logs, Tempo for traces, Prometheus for metrics, Grafana for all of them.
2. **Elastic Stack (ELK)**: Elasticsearch for logs and traces (Elastic APM, Elastic's OpenTelemetry distribution EDOT), Kibana; metrics usually stay in Prometheus.
3. **OpenSearch**: the Apache 2.0 fork under the Linux Foundation, with its observability stack (Data Prepper, OpenSearch Dashboards).
4. **One columnar store for all signals**: ClickStack (ClickHouse and HyperDX) or SigNoz.

## Decision
Option 1. Compose runs `grafana/otel-lgtm`, pinned (all in one, for development); k3d runs kube-prometheus-stack, Loki, Tempo and an OTel Collector
that sends traces to Tempo and logs to Loki (`platform/values/`). Grafana has the three data sources with the same uids in both modes,
and links traces to logs both ways. Dashboards are ADR-0021, alerts ADR-0022.

## Consequences
- **Good:** one UI for metrics, logs and traces with trace ↔ log links; Loki indexes only labels and stores log text compressed, so it is cheap to store and light to run;
  one image for the laptop; the dashboards, rules and SLOs work in both modes.
- **Cost:** a broad text search across all logs without narrowing by labels is slow in Loki, and high-cardinality labels hurt it;
  Grafana, Loki and Tempo are AGPLv3, which matters only if the stack is embedded into a product; `otel-lgtm` is a development image, not for production.

## When to choose differently
- The company already runs Elastic or OpenSearch with a team that operates it: **send OTLP there** by changing the Collector exporters; services stay the same.
- Full-text search and ad hoc analytics over log content, or logs used for security analytics (SIEM): **Elastic Stack** or **OpenSearch**.
- Apache 2.0 required for every component: **OpenSearch** (and Prometheus, Jaeger).
- High-cardinality events and one query language over all signals: **ClickStack** or **SigNoz**.

## Evidence
- [`platform/values/otel-collector.yaml`](../../platform/values/otel-collector.yaml): the backends are two exporters in the Collector, not code in the services.
- [Grafana Loki: labels](https://grafana.com/docs/loki/latest/get-started/labels/) (what Loki indexes) and
  [Elastic Distributions of OpenTelemetry](https://www.elastic.co/docs/reference/opentelemetry) (the OTLP path into Elastic).
