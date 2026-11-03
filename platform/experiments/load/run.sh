#!/usr/bin/env bash
# Runs the k6 order scenario against the k3d ingress from the laptop and writes the k6 metrics
# into the cluster Prometheus, next to the service metrics.
#
# Usage:  platform/experiments/load/run.sh [plateau|stress|smoke]   (RATE=20 by default)
# Output: summary in $OUT_DIR (default: current directory).
set -euo pipefail

profile=${1:-plateau}
dir=$(cd "$(dirname "$0")" && pwd)
out=${OUT_DIR:-$PWD}
image=grafana/k6:2.3.0

# Prometheus has no ingress; reach it through a port-forward for the remote write.
kubectl -n observability port-forward svc/kps-prometheus 9090:9090 >/dev/null 2>&1 &
pf=$!
trap 'kill $pf 2>/dev/null' EXIT
sleep 2

# --network host: the ingress listens on localhost:8080; *.localhost names are mapped explicitly.
docker run --rm --network host \
  --add-host orders.localhost:127.0.0.1 --add-host keycloak.localhost:127.0.0.1 \
  -u "$(id -u):$(id -g)" -v "$dir:/scripts:ro" -v "$out:/out" \
  -e PROFILE="$profile" -e RATE="${RATE:-20}" \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
  -e K6_PROMETHEUS_RW_TREND_STATS='p(50),p(95),p(99),max' \
  "$image" run -q --no-usage-report -o experimental-prometheus-rw \
  --tag testid="$profile-$(date +%Y%m%d-%H%M)" \
  --summary-export "/out/summary-$profile.json" /scripts/orders.js
