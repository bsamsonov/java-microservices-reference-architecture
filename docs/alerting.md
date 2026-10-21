# Alerting: how it works and how to add an alert

The decision and its rationale: D10 in `docs/decisions.md`.

## Flow

```
observability/slo/*.yaml ──pyrra generate──► observability/prometheus/generated/*.yaml ─┐
observability/prometheus/rules/*.rules.yaml ─────────────────────────────────────────────┼─► Prometheus ─► Alertmanager ─► receivers
                                                                                         │   (evaluates)   (groups,
                      compose: files are mounted as they are                             │                  inhibits, sends)
                      k3d: render-rules.sh wraps them into PrometheusRule ───────────────┘
Grafana: dashboards + Alertmanager data source (alert list and silences in the UI). Grafana has no rules of its own.
```

## Where things live

| Path | What |
|---|---|
| `observability/slo/` | service SLOs (Pyrra format); burn-rate alerts are generated from them |
| `observability/prometheus/rules/` | hand-written rules; `tests/` holds their unit tests (promtool) |
| `observability/prometheus/generated/` | generated from `slo/`; do not edit by hand, do commit |
| `observability/alertmanager/` | routing and the notification template; shared by compose and k3d |
| `observability/runbooks/<AlertName>.md` | what to do when the alert fires; one file per alert |
| `observability/check-alerting.sh` | generation and all checks; run it before committing |

## Severity levels (`severity`)

| Level | When | Where to |
|---|---|---|
| `critical` | users suffer now, the SLO budget runs out within days | `pager` and `chat` |
| `warning` | look at it during working hours; causes (Kafka, DB pool, heap) | `chat` |
| `info` | a hint for investigation, no notification | nowhere, visible in the alert list |

Only SLO alerts are `critical`: people are woken up by a symptom the user sees, not by a cause.
When the `critical` and `warning` variants of the same alert and service fire together, Alertmanager shows only `critical`.

## Required in every rule

- labels `severity` and `team`; aggregation `by (namespace, application, …)` so the alert points at a service;
- annotations `summary` (one line with a number) and `description`; for SLO alerts they are set in the SLO itself through the
  `pyrra.dev/summary` and `pyrra.dev/description` annotations (Pyrra copies them into every alert). The template only sees the series labels,
  so `short`, `long` and `exhaustion` (rule labels) cannot be put into the text;
- a file `observability/runbooks/<AlertName>.md` following `_TEMPLATE.md`;
- a test in `prometheus/rules/tests/`: one case where the rule fires and one where it stays silent.

`check-alerting.sh` checks all of this (pint and the runbook check) except the tests; their presence is checked in review.

## How to add

**An alert for every Spring service.** A rule in `observability/prometheus/rules/spring-services.rules.yaml`,
a test, a runbook, then `observability/check-alerting.sh`.

**An alert for one service.** A new file `observability/prometheus/rules/<service>.rules.yaml` filtered by
`{application="<service>"}`; the rest is the same.

**A different SLO target for one service.** Copy `observability/slo/http-availability.yaml` to `<service>-availability.yaml`
with `{application="<service>"}` in the metrics, its own `target` and `pyrra.dev/*` annotations; exclude the service from the shared SLO (`application!="<service>"`).

**Apply.** compose: `docker compose ... restart lgtm` (Prometheus reads the rules at startup).
k3d: `make -C platform alerting`.

## Where to look

| | compose | k3d |
|---|---|---|
| Rules and their state | http://localhost:9090/alerts | Grafana → Alerting → Alert rules (Prometheus data source) |
| Firing alerts, silences | http://localhost:9093 | http://alertmanager.localhost:8080 |
| The same in Grafana | Alerting → Alert list, Alertmanager data source | the same |

## Turning notifications on

The receivers in `observability/alertmanager/alertmanager.yaml` are empty for now: alerts are visible but go nowhere.
To turn one on, uncomment its block. Tokens go into files, not into the config:
in compose into the mounted folder, in k3d into a Secret attached through `alertmanagerSpec.secrets`.
`heartbeat` needs an external dead man's switch (for example healthchecks.io) that expects Watchdog every minute.

## Verified
- `observability/check-alerting.sh`: all 6 checks green (31 rules, tests for 5 rules and 2 SLOs, `amtool`, `pint`, a runbook for every alert).
- compose: the `otel-lgtm` Prometheus loaded 6 groups (31 rules, `health=ok`); Watchdog reached Alertmanager and was routed to `heartbeat`;
  Grafana sees it through the Alertmanager data source. The upstream part of `compose/alerting/prometheus.yaml` matches the config of the current image.
- k3d: 3 `PrometheusRule` objects loaded (`health=ok`), the kube-prometheus-stack Alertmanager runs on the config from the Secret,
  Watchdog → `heartbeat`, critical → `pager` and `chat`. All three services expose the `le="0.5"` bucket; the latency burn rate under load is 0.
- Failure drill in both modes. Shared databases were left alone: a throwaway pair "own Postgres + a copy of `catalog-service`
  with `SPRING_APPLICATION_NAME=catalog-exp`" ran next to them (in compose via `docker run` on the `blueprints_default` network, in k3d in namespace `alert-exp`
  with a Deployment, Service and ServiceMonitor). Postgres was stopped while 20 threads sent `POST /products`: every response was 500 after 30 s
  (Hikari `connectionTimeout`), with 20 threads waiting in the pool. Timeline from the moment the database stopped:

  | Alert | compose | k3d | Routed to |
  |---|---|---|---|
  | `HttpAvailabilityBudgetBurn` (critical, windows 5m/1h) | firing after 3.5 min | after 3.7 min | `pager` and `chat` |
  | `SpringDbPoolExhausted` (warning, `for: 5m`) | after 6.8 min | after 5.5 min | `chat` |
  | `SpringErrorLogLines` (info) | after 6.8 min | after 5.5 min | `null` |

  Once the database was back, all three resolved within a minute (under load the 5-minute burn rate drops below the threshold quickly).
  The slow tier (`warning`, windows 2h/1d, `for: 1h`) did not fire within 15 minutes, so its inhibition by the critical tier was not observed live
  (only `amtool check-config` and the rule unit tests cover it).
- Found during the drill: SLO alerts had no `summary`, so the notification line would have been empty. It now comes from the
  `pyrra.dev/summary` and `pyrra.dev/description` annotations in `observability/slo/*.yaml`, and `pint` requires a `summary` on every alert.
- The runbook link in a notification is built as `https://github.com/bsamsonov/java-microservices-reference-architecture/blob/main/observability/runbooks/<alertname>.md`
  (`observability/alertmanager/blueprints.tmpl`).
