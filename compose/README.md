# compose: laptop mode

Infrastructure and observability in Docker Compose: about 3.5 GB of images (`grafana/otel-lgtm` alone is 2.7 GB).
The Kubernetes mode lives in `../platform/` (k3d + Helm). Services do not know which mode they run in: every address comes from environment variables.

| Component | Address from the host | Address from containers |
|---|---|---|
| Postgres 18 (db/user/pass `orders`/`app`/`app`) | `localhost:5432` | `postgres:5432` |
| Kafka 4.3 (KRaft, single node) | `localhost:9092` | `kafka:19092` |
| Grafana (admin/admin) | http://localhost:3000 | |
| OTLP gRPC / HTTP | `localhost:4317` / `localhost:4318` | `lgtm:4317` / `lgtm:4318` |
| Prometheus (rules and alerts: `/alerts`) | http://localhost:9090 | |
| Alertmanager | http://localhost:9093 | `alertmanager:9093` |
| Keycloak (admin/admin; token: `security/token.sh alice`) | http://localhost:8180 | `keycloak:8080` (signing keys) |
| orders-service (profile `apps`) | http://localhost:8080 | `orders-service:8080` |
| notifications-service (profile `apps`) | http://localhost:8081 | `notifications-service:8080` |
| catalog-service (profile `apps`) | http://localhost:8082 | `catalog-service:8080` |

All credentials here are local demo values.

```bash
# infrastructure only; run the service from the IDE or with ./mvnw spring-boot:run (defaults point to localhost)
docker compose -f compose/compose.yaml up -d

# infrastructure plus the services in containers (build the jars first)
(cd services/orders-service && ./mvnw -DskipTests package)
(cd services/notifications-service && ./mvnw -DskipTests package)
(cd services/catalog-service && ./mvnw -DskipTests package)
docker compose -f compose/compose.yaml --profile apps up -d --build

docker compose -f compose/compose.yaml --profile apps down      # add -v to drop the data as well
```

Metrics. In compose the service pushes them over OTLP itself (`MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=true`).
Push is off by default; in Kubernetes Prometheus scrapes `/actuator/prometheus`.
Traces and logs go over OTLP HTTP in both modes.

Dashboards. The "Spring services" dashboard from `../observability/grafana/` is mounted into otel-lgtm; Grafana shows it in the Blueprints folder.

Alerts (D10). otel-lgtm gets its own copy of the Prometheus config, `alerting/prometheus.yaml`: the image config plus `rule_files` and the Alertmanager address.
Rules come from `../observability/prometheus/`; `alerting/watchdog.rules.yaml` exists only in compose (in k3d Watchdog ships with kube-prometheus-stack).
Alertmanager is a separate container configured from `../observability/alertmanager/`; Grafana uses it as a data source (`alerting/grafana-alertmanager-datasource.yaml`).
When the `otel-lgtm` image is upgraded, compare the copy with the original: `docker run --rm --entrypoint cat grafana/otel-lgtm:0.37.0 /otel-lgtm/prometheus.yaml`.
After editing the files: `docker compose -f compose/compose.yaml restart lgtm alertmanager`. Details in `../observability/README.md` and `../docs/alerting.md`.

## Verified: security (D11)
- No token or a tampered token gives 401; bob (catalog-managers) cannot place an order or read notifications (403);
  alice cannot change the catalog (403); an order and its cancellation by alice produce two SENT notifications; `/actuator/prometheus` stays open.
- The `http post /orders/{orderId}/cancel` trace contains the `authenticate bearertoken` and `authorize request` spans and reaches notifications-service.
- When k3d is running, its ingress holds port 8080 and orders-service in compose does not start
  (workaround used for the check: `docker compose … run -p 8083:8080 orders-service`).

## Verified: dashboards and alerts
- With `base-time-unit: seconds` metric names match the scraped ones (`http_server_requests_seconds_count`), and p95 buckets are present.
- Every dashboard query returns data under load (about 28 requests per second per service).

## Verified: telemetry end to end
Sent `POST /orders` (4 orders), then queried the Prometheus, Tempo and Loki APIs through the Grafana proxy.
- Metrics: `http_server_requests_seconds_count{uri="/orders",status="201"}` is in Prometheus with the tags `service_name`, `deployment_environment=compose` and `module_name=Orders`.
- Trace: a single Tempo trace contains `http post /orders` (SERVER) → `OrderController.placeOrder` → `AuditLogListener.on(OrderPlaced)` → `orders.events send` (PRODUCER),
  so the context survives the outbox all the way to Kafka.
- Logs: the `Order placed: id=…` line reaches Loki with a `trace_id` label, and that `trace_id` leads to the same trace in Tempo.
- Outbox: every row in `event_publication` got a `completion_date` (since D12 a delivered row is deleted instead). The `orders.events` topic is created automatically.
