#!/usr/bin/env bash
# One-command demo on the laptop (`make demo` from the repository root).
# Builds the three services, starts compose with the `apps` profile, places an order as alice, cancels it,
# waits for both notifications to be SENT, and prints links to the dashboard, the trace and Alertmanager.
# Needs Docker (with compose), Java 25, curl and jq. Everything else runs in containers.
set -euo pipefail

cd "$(dirname "$0")/.."
compose=(docker compose -f compose/compose.yaml --profile apps)
log=${TMPDIR:-/tmp}/blueprints-demo-build.log

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[31mdemo failed: %s\033[0m\n' "$*" >&2; exit 1; }

for tool in docker java curl jq; do
  command -v "$tool" >/dev/null || fail "$tool is not installed"
done
java -version 2>&1 | grep -qE 'version "(2[5-9]|[3-9][0-9])' || fail "Java 25 or newer is required (java -version)"

step "1/5 Build the jars (log: $log)"
: >"$log"
for s in orders notifications catalog; do
  echo "    $s-service"
  (cd "services/$s-service" && ./mvnw -q -B -DskipTests package) >>"$log" 2>&1 || fail "build of $s-service, see $log"
done

step "2/5 Start Postgres, Kafka, Keycloak, Grafana LGTM, Alertmanager and the services"
"${compose[@]}" build --quiet >>"$log" 2>&1 || fail "docker build, see $log"
"${compose[@]}" up -d --quiet-pull --wait --wait-timeout 300 || fail "compose did not become healthy: ${compose[*]} ps"
for port in 8080 8081 8082; do
  curl -sf "localhost:$port/actuator/health" | jq -e '.status == "UP"' >/dev/null || fail "service on port $port is not UP"
done
echo "    orders :8080, notifications :8081, catalog :8082 are UP"

step "3/5 Get a token for alice from Keycloak"
TOKEN=$(security/token.sh alice) || fail "no token from Keycloak (security/token.sh alice)"
auth=(-H "Authorization: Bearer $TOKEN")
echo "    ok (permissions: orders:read, orders:write, notifications:read, catalog:read)"

step "4/5 Place an order and cancel it"
customer="demo-$(date +%s)"
order=$(curl -sf "${auth[@]}" -H 'Content-Type: application/json' -X POST localhost:8080/orders \
  -d "{\"customerId\":\"$customer\",\"lines\":[{\"productCode\":\"P-1\",\"quantity\":2,\"unitPrice\":10.00}]}") \
  || fail "POST /orders"
order_id=$(jq -r .id <<<"$order")
echo "    POST /orders                      -> $(jq -r '.status + ", total " + (.total|tostring)' <<<"$order"), id $order_id"
cancelled=$(curl -sf "${auth[@]}" -H 'Content-Type: application/json' -X POST "localhost:8080/orders/$order_id/cancel" \
  -d '{"reason":"changed my mind"}') || fail "POST /orders/$order_id/cancel"
echo "    POST /orders/{id}/cancel          -> $(jq -r .status <<<"$cancelled")"

step "5/5 Wait for notifications-service to consume both events from Kafka (orders.events)"
for _ in $(seq 1 30); do
  notifications=$(curl -sf "${auth[@]}" "localhost:8081/notifications?customerId=$customer" || echo '[]')
  [[ $(jq '[.[] | select(.status == "SENT")] | length' <<<"$notifications") -ge 2 ]] && break
  sleep 1
done
jq -r '.[] | "    GET /notifications  " + .type + " -> " + .status' <<<"$notifications" | sort -r
[[ $(jq '[.[] | select(.status == "SENT")] | length' <<<"$notifications") -ge 2 ]] || fail "expected two SENT notifications"

# The trace id comes from the log line "Order placed: id=..." in Loki (logs carry trace_id, ADR-0021).
trace_id=""
for _ in $(seq 1 30); do
  trace_id=$(curl -sf -u admin:admin -G localhost:3000/api/datasources/proxy/uid/loki/loki/api/v1/query_range \
    --data-urlencode "query={service_name=\"orders-service\"} |= \"Order placed: id=$order_id\"" \
    --data-urlencode "since=15m" | jq -r '.data.result[0].stream.trace_id // empty' || true)
  [[ -n $trace_id ]] && break
  sleep 1
done

explore() { # $1 = datasource uid, $2 = query
  jq -rn --arg ds "$1" --arg q "$2" '{a: {datasource: $ds, queries: [{refId: "A", datasource: {uid: $ds}, query: $q}],
    range: {from: "now-1h", to: "now"}}} | tojson | @uri' | sed 's|^|http://localhost:3000/explore?schemaVersion=1\&panes=|'
}

printf '\n\033[32mDemo is up.\033[0m Grafana login: admin / admin\n\n'
echo "  Dashboard     http://localhost:3000/d/spring-services"
if [[ -n $trace_id ]]; then
  echo "  Trace         $(explore tempo "$trace_id")"
  echo "                (POST /orders -> outbox -> Kafka -> notifications-service, trace id $trace_id)"
else
  echo "  Trace         not found in Loki yet; search Tempo in Grafana Explore for service orders-service"
fi
echo "  Logs          $(explore loki "{service_name=~\"orders-service|notifications-service\"} |= \"$order_id\"")"
echo "  Alertmanager  http://localhost:9093"
echo "  Prometheus    http://localhost:9090/alerts"
echo
echo "  Try the API:  TOKEN=\$(security/token.sh alice); curl -H \"Authorization: Bearer \$TOKEN\" localhost:8080/orders/$order_id"
echo "  Stop:         make down"
