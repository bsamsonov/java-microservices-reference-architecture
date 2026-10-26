# observability: dashboards and alerts as code (ADR-0021, ADR-0022)

One set of files for both infrastructure modes.

| File | What | Compose (`grafana/otel-lgtm`) | k3d (kube-prometheus-stack) |
|---|---|---|---|
| `grafana/dashboards/*.json` | Grafana dashboards | mounted into the container, provider `grafana/compose-dashboards.yaml` | ConfigMap `blueprints-dashboards` labelled `grafana_dashboard=1` |
| `prometheus/rules/*.rules.yaml`, `prometheus/generated/*.yaml` | Prometheus rules (causes and SLOs) | mounted into the `otel-lgtm` Prometheus (`compose/alerting/prometheus.yaml`) | `PrometheusRule` (`make -C platform alerting`) |
| `slo/*.yaml` | SLOs in Pyrra format; `prometheus/generated/` is generated from them | — | — |
| `alertmanager/` | routing and the notification template | `alertmanager` container (http://localhost:9093) | Secret `alertmanager-blueprints` (http://alertmanager.localhost:8080) |
| `runbooks/<AlertName>.md` | what to do when the alert fires | — | — |

In both modes the dashboards live in the **Blueprints** folder. After editing the files:
compose: `docker compose -f compose/compose.yaml restart lgtm alertmanager`; k3d: `make -C platform dashboards alerting`
(the sidecar picks the ConfigMap up by itself). Before committing rules run `observability/check-alerting.sh`.

## The "Spring services" dashboard (`spring-services`)
The `Service` variable comes from the `application` label, so a new service shows up on the dashboard without edits.
Rows: HTTP (rate, share of 5xx, p95, per-endpoint breakdown), Kafka (producer, listener with errors, p95, lag, Modulith events), the outbox backlog (template 1),
the Hikari connection pool, JVM (heap, GC, CPU, threads, WARN/ERROR log lines) and WARN/ERROR logs from Loki with a link to the trace.

## Alerts
How alerting works, the severity levels, the rule list and how to add an alert or an SLO are described in `docs/alerting.md`.
In short: symptoms (5xx and latency) are caught by burn-rate SLO alerts (`HttpAvailabilityBudgetBurn`, `HttpLatencyBudgetBurn`);
causes (`SpringKafkaListenerFailures`, `SpringKafkaConsumerLag`, `OutboxBacklogStale`, `SpringDbPoolExhausted`, `SpringHeapHigh`) are `warning`;
`SpringErrorLogLines` is `info`. Firing alerts are visible in Alertmanager and in Grafana (Alerting → Alert list, Alertmanager data source).
Notification receivers are empty placeholders for now.

## What the service must provide
The queries are the same for OTLP push (compose) and scraping (Kubernetes), so every service has this in `application.yaml`:
- `management.metrics.tags.application`: the label used for filtering;
- `management.otlp.metrics.export.base-time-unit: seconds`: OTLP timers default to milliseconds, but the names must match (`*_seconds_*`);
- `management.metrics.distribution.percentiles-histogram` for `http.server.requests` (and `spring.kafka.listener` for Kafka services): buckets for p95;
- `management.metrics.distribution.slo.http.server.requests: 500ms`: an exact `le="0.5"` bucket for the latency SLO.

The only known name mismatch is a gauge with a unit in its name: `jvm_threads_live_threads` when scraped and `jvm_threads_live` over OTLP;
the query uses `{__name__=~"jvm_threads_live(_threads)?"}`. When adding a panel, check its query in both modes.

The error counter (`spring_kafka_listener_seconds_count{error="…"}`) appears only on the first error and starts at 1,
so `rate()` alone never sees it. The rule counts both new series (`unless … offset 10m`) and the growth of existing ones.

## Verified
The dashboard loads into the Blueprints folder in both modes, and all its queries return data under load. Alerting checks are listed in `docs/alerting.md`.

## How to change a dashboard
Edit it in the Grafana UI (compose has `allowUiUpdates: true`), export the JSON (Share → Export, without "Export for sharing externally")
and save it over the file in `grafana/dashboards/`. Keep the dashboard `uid`: runbooks link to it.
