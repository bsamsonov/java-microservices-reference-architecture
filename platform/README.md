# platform: local Kubernetes

Decisions: `docs/adr/` (ADR-0015 operators, ADR-0016 k3d + Tilt, ADR-0017 two run modes). Postgres runs on the CloudNativePG operator
(the same operator approach as Strimzi, with a PodMonitor for Prometheus out of the box).

| Component | In-cluster address | For services |
|---|---|---|
| Kafka (Strimzi, KRaft) | `blueprints-kafka-bootstrap.infra:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| Postgres (CNPG) | `pg-rw.infra:5432`, a user and secret `pg-<db>` per service (`manifests/databases/`) | `SPRING_DATASOURCE_*` |
| OTel Collector | `otel-collector.observability:4317/4318` | `OTEL_EXPORTER_OTLP_ENDPOINT` (traces and logs) |
| Prometheus | `kps-prometheus.observability:9090` | the service exposes `/actuator/prometheus`, the chart creates a ServiceMonitor |
| Grafana | http://grafana.localhost:8080 (admin/admin) | Prometheus, Loki and Tempo data sources with trace ↔ log links, Alertmanager |
| Alertmanager | http://alertmanager.localhost:8080 | config: Secret `alertmanager-blueprints` from `../observability/alertmanager/` |
| Keycloak (`make security`) | http://keycloak.localhost:8080 (admin/admin), in cluster `keycloak.infra:8080` | `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_*` in `deploy/values.yaml` |
| Image registry | `localhost:5050` (in cluster `blueprints-registry:5050`) | |

Commands: `make up`, `make status`, `make down`. Requires docker, k3d, kubectl and helm.

## Verified: the platform
`make up` from scratch passes (about 8 minutes with an empty image cache):
- all pods in `infra` and `observability` are Running; Kafka `Ready=True` (Kafka 4.3.1, Strimzi 1.3.0), CNPG Postgres `pg-1` Running;
- Grafana http://grafana.localhost:8080: Prometheus, Loki and Tempo data sources are healthy;
- Prometheus scrapes Postgres metrics (`up{namespace="infra"} = 1` through the PodMonitor).

## Verified: services in the cluster
`make up`, then `tilt ci` from the repository root: the three services (`orders`, `notifications`, `catalog`) run in namespace `apps`, all Ready.
Stop the compose stack first (`docker compose -f compose/compose.yaml --profile apps stop`):
the k3d ingress takes port 8080, and both stacks at once do not fit into laptop memory.
- every service has its own Postgres user (CNPG managed role, secret `pg-<db>`) and database (CNPG `Database`, `APPLIED=true`);
  CONNECT is revoked from PUBLIC, so `catalog` gets `permission denied for database "orders"` (verified 2026-10-22); KafkaTopic `orders.events` (3 partitions) and `orders.events.DLT` are Ready;
- ingress `*.localhost:8080`: CRUD in catalog, an order and its cancellation in orders → two SENT notifications in notifications;
- Tempo: one trace across `orders-service` and `notifications-service`; Loki: logs of both services by `trace_id`
  (in OTLP logs it is structured metadata: `{service_name=~".+"} | trace_id="…"`);
- Prometheus: the ServiceMonitors of all three services are `up`, `http_server_requests_seconds_count` is flowing;
- a malformed message in `orders.events` ends up in `orders.events.DLT` with the cause in a header;
- console logs in the pods are ECS JSON (`traceId`, `spanId`).

## Verified: security (ADR-0023)
- Keycloak in `infra` (dev mode, realm from `../security/keycloak/`); tokens with `iss=http://keycloak.localhost:8080/realms/blueprints` are accepted by the services.
- Through the ingress: no token → 401, bob → order 403, alice → order and cancel → two SENT, alice → product 403, bob → product 201.
- NetworkPolicy: namespace `apps` is closed (`manifests/apps/default-deny.yaml`); a service may reach DNS, `infra` and OTel on 4318.
  From the orders pod Postgres and OTel are reachable, catalog and Grafana are not (control: without the policies catalog is reachable). Prometheus still scrapes.
- The Keycloak pod and the policies are created by `make up` (targets `security` and `apps-namespace`).

Dashboards (`make dashboards`, part of `make up`): a ConfigMap from `../observability/grafana/dashboards/` is picked up by the Grafana sidecar;
the "Spring services" dashboard is in the Blueprints folder (details in `../observability/README.md`).

Alerts (ADR-0022, `make alerting`, part of `make up`): Secret `alertmanager-blueprints` from `../observability/alertmanager/`
and `PrometheusRule` objects from `../observability/prometheus/` (wrapper: `alerting/render-rules.sh`; rules are recreated on every run,
so a rule deleted from the repository disappears from the cluster too). Cluster rules (kubernetes-mixin) come with kube-prometheus-stack.
`kubeControllerManager`, `kubeScheduler`, `kubeProxy` and `kubeEtcd` are disabled in the values: k3s does not expose their metrics,
and otherwise `KubeControllerManagerDown`, `KubeSchedulerDown` and `KubeProxyDown` fire forever. On a running cluster: `make operators alerting`.

## Pitfalls found along the way
- Strimzi 1.x dropped `kafka.strimzi.io/v1beta2`, so the manifests use `v1`.
- CNPG deprecated `spec.monitoring.enablePodMonitor`, so the PodMonitor is declared explicitly.
- OTel Collector renamed its exporters: `otlp` → `otlp_grpc`, `otlphttp` → `otlp_http`.
- A custom `logback-spring.xml` disables Spring Boot's console setup, so `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`
  had no effect and Kubernetes logs were plain text. The OTel appender is now attached in code (`OpenTelemetryLoggingConfig`) and the XML is gone.
  After deleting a resource run `./mvnw clean …`: without `clean` the old file stays in `target/classes` and lands in the jar.
- After `k3d cluster stop` / `start` the Kafka pod (`blueprints-dual-0`) appears only after about 5 minutes: the first Strimzi
  reconciliation waits for its timeout. Storage is ephemeral, so messages are lost; the topic operator recreates topics from KafkaTopic.
  While the broker is down the outbox keeps events in `event_publication`; `OutboxResubmission` resends them every 30 s once the broker is back, without a restart (ADR-0003).
- Right after `tilt ci` the ingress may answer 502 while pods are replaced; it passes after a few seconds.
- With the host disk 90 % full the kubelet evicted every pod (default threshold 10–15 %). `k3d/cluster.yaml`
  lowers the threshold to 2 GB free and keeps a single node. Keep at least 20 GB free for the cluster and the service images.
