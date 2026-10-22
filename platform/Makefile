# Local platform: k3d + Strimzi Kafka + CloudNativePG + Prometheus/Grafana + Loki + Tempo + OTel Collector.
# Usage: make up | make status | make grafana | make dashboards | make alerting | make down
CLUSTER := blueprints
HELM    := helm upgrade --install --wait --timeout 10m

.PHONY: up down cluster repos operators infra db-secrets db-privileges security observability dashboards alerting status grafana apps-namespace

up: cluster repos operators infra security observability dashboards alerting apps-namespace status

cluster:
	k3d cluster list $(CLUSTER) >/dev/null 2>&1 || k3d cluster create --config k3d/cluster.yaml
	kubectl create namespace infra --dry-run=client -o yaml | kubectl apply -f -
	kubectl create namespace observability --dry-run=client -o yaml | kubectl apply -f -

repos:
	helm repo add strimzi https://strimzi.io/charts/ --force-update
	helm repo add cnpg https://cloudnative-pg.github.io/charts --force-update
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
	helm repo add grafana https://grafana.github.io/helm-charts --force-update
	helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts --force-update
	helm repo update

# Prometheus CRDs (ServiceMonitor/PodMonitor) come with kube-prometheus-stack, so it goes before CNPG cluster.
operators:
	$(HELM) kps prometheus-community/kube-prometheus-stack -n observability -f values/kube-prometheus-stack.yaml
	$(HELM) strimzi strimzi/strimzi-kafka-operator -n infra --set watchAnyNamespace=true
	$(HELM) cnpg cnpg/cloudnative-pg -n infra

infra: db-secrets
	kubectl apply -f manifests/kafka.yaml -f manifests/postgres.yaml
	kubectl -n infra wait kafka/blueprints --for=condition=Ready --timeout=10m
	kubectl -n infra wait cluster.postgresql.cnpg.io/pg --for=condition=Ready --timeout=10m
	kubectl apply -f manifests/databases/
	$(MAKE) db-privileges

# Service databases (ADR-0007): manifests/databases/<name>.yaml declares database and owner <db>; the secret pg-<name> holds
# user <db> and a random password. It is created once and never overwritten (until SOPS, ADR-0019). The role itself is in postgres.yaml.
db-secrets:
	for f in manifests/databases/*.yaml; do \
	  secret=pg-$$(basename $$f .yaml); user=$$(sed -n 's/^  owner: *//p' $$f); \
	  kubectl -n infra get secret $$secret >/dev/null 2>&1 || kubectl -n infra create secret generic $$secret \
	    --type=kubernetes.io/basic-auth --from-literal=username=$$user --from-literal=password=$$(openssl rand -hex 16); \
	  kubectl -n infra label secret $$secret --overwrite cnpg.io/reload=true blueprints.io/db-user=true >/dev/null; \
	done

# Only the owner may connect to a service database: PUBLIC loses CONNECT and TEMPORARY (idempotent).
db-privileges:
	primary=$$(kubectl -n infra get pod -l cnpg.io/cluster=pg,cnpg.io/instanceRole=primary -o name); \
	for f in manifests/databases/*.yaml; do \
	  db=$$(sed -n 's/^  owner: *//p' $$f); \
	  kubectl -n infra wait database.postgresql.cnpg.io/$$(basename $$f .yaml) --for=jsonpath='{.status.applied}'=true --timeout=2m; \
	  kubectl -n infra exec $$primary -c postgres -- psql -qc "revoke connect, temporary on database $$db from public"; \
	done

# Keycloak with the same realm file as compose (security/keycloak); pods restart to re-import a changed realm.
security:
	kubectl -n infra create configmap keycloak-realm --from-file=../security/keycloak/ \
	  --dry-run=client -o yaml | kubectl apply -f -
	kubectl apply -f manifests/keycloak.yaml
	kubectl -n infra rollout status deployment/keycloak --timeout=5m

observability:
	$(HELM) loki grafana/loki -n observability -f values/loki.yaml
	$(HELM) tempo grafana/tempo -n observability -f values/tempo.yaml
	$(HELM) otel-collector open-telemetry/opentelemetry-collector -n observability -f values/otel-collector.yaml

# Same files as compose: observability/grafana/dashboards; the Grafana sidecar picks the ConfigMap up by label.
dashboards:
	kubectl -n observability create configmap blueprints-dashboards --from-file=../observability/grafana/dashboards/ \
	  --dry-run=client -o yaml \
	  | kubectl label --local -f - grafana_dashboard=1 -o yaml \
	  | kubectl annotate --local -f - grafana_folder=Blueprints -o yaml \
	  | kubectl apply --server-side -f -

# ADR-0022. The same files as compose: observability/alertmanager/ and observability/prometheus/.
alerting:
	# Alertmanager config: Secret read by kube-prometheus-stack (alertmanagerSpec.configSecret)
	kubectl -n observability create secret generic alertmanager-blueprints \
	  --from-file=../observability/alertmanager/ --dry-run=client -o yaml \
	  | kubectl apply -f -
	# Rules: recreate all, so a rule deleted from the repo disappears from the cluster
	kubectl -n observability delete prometheusrule -l app.kubernetes.io/part-of=blueprints
	./alerting/render-rules.sh ../observability/prometheus | kubectl apply -f -

status:
	kubectl get pods -n infra
	kubectl get pods -n observability

grafana:
	@echo "http://grafana.localhost:8080  (admin/admin)"

down:
	k3d cluster delete $(CLUSTER)

# Namespace for services: closed by default (NetworkPolicy) + copy of the DB credentials
# (secrets can't be read across namespaces).
apps-namespace:
	kubectl create namespace apps --dry-run=client -o yaml | kubectl apply -f -
	kubectl apply -f manifests/apps/default-deny.yaml
	kubectl -n infra get secret -l blueprints.io/db-user=true -o json \
	  | jq '{apiVersion: "v1", kind: "List", items: [.items[] | {apiVersion: "v1", kind: "Secret", type, data, metadata: {name: .metadata.name, namespace: "apps"}}]}' \
	  | kubectl apply -f -
