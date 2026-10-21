#!/usr/bin/env bash
# Prints every rule file as a PrometheusRule (the Prometheus Operator format) to stdout.
# A PrometheusRule `spec` is exactly the content of a rule file, so the script only adds a header and indents.
#   ./render-rules.sh ../observability/prometheus | kubectl apply -f -
set -euo pipefail
dir=$1

for file in "$dir"/rules/*.rules.yaml "$dir"/generated/*.yaml; do
  name=$(basename "$file" .yaml)
  name=${name%.rules}
  cat <<HEADER
---
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: blueprints-$name
  namespace: observability
  labels:
    app.kubernetes.io/part-of: blueprints
spec:
HEADER
  sed 's/^/  /' "$file"
done
