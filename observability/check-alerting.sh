#!/usr/bin/env bash
# Generates SLO rules and checks all alerting files. Run from anywhere inside the repo.
# Every tool runs in a pinned container, nothing to install.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

PYRRA=ghcr.io/pyrra-dev/pyrra:v0.10.2
PROMETHEUS=prom/prometheus:v3.15.0
ALERTMANAGER=prom/alertmanager:v0.34.1
PINT=ghcr.io/cloudflare/pint:0.89.3

# docker run with the repo mounted at /w; arguments: [docker options] image command...
in_docker() { docker run --rm -u "$(id -u):$(id -g)" -v "$PWD:/w" -w /w "$@"; }

RULES="observability/prometheus/rules/*.rules.yaml observability/prometheus/generated/*.yaml compose/alerting/*.rules.yaml"

echo "1. SLO -> rules (pyrra generate)"
rm -f observability/prometheus/generated/*.yaml
in_docker "$PYRRA" generate \
  --config-files='observability/slo/*.yaml' \
  --prometheus-folder=observability/prometheus/generated/ 2>/dev/null

echo "2. Rule syntax (promtool check rules)"
in_docker --entrypoint promtool "$PROMETHEUS" check rules $RULES

echo "3. Rule tests (promtool test rules)"
in_docker --entrypoint promtool "$PROMETHEUS" test rules observability/prometheus/rules/tests/*.test.yaml

echo "4. Alertmanager config (amtool check-config)"
in_docker --entrypoint amtool "$ALERTMANAGER" check-config observability/alertmanager/alertmanager.yaml

echo "5. Label conventions (pint lint)"
in_docker "$PINT" pint --config observability/prometheus/.pint.hcl --no-color lint $RULES

echo "6. Every alert has a runbook"
alerts=$(grep -ho -- '- alert: [A-Za-z]*' $RULES | awk '{print $3}' | sort -u)
missing=0
for alert in $alerts; do
  if [[ ! -f observability/runbooks/$alert.md ]]; then
    echo "   missing observability/runbooks/$alert.md"
    missing=1
  fi
done
exit $missing
