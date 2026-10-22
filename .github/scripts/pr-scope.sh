#!/usr/bin/env bash
# Fails when a PR changes files in more than one services/<name>/ directory. See ADR-0034.
# Usage: pr-scope.sh <base-ref> <head-ref>; CROSS_CUTTING=true turns the failure into a notice.
set -euo pipefail

base=${1:?usage: pr-scope.sh <base-ref> <head-ref>}
head=${2:?usage: pr-scope.sh <base-ref> <head-ref>}
cross_cutting=${CROSS_CUTTING:-false}

merge_base=$(git merge-base "$base" "$head")

# --no-renames: a file moved between services counts for both
mapfile -t services < <(
  git diff --name-only --no-renames "$merge_base" "$head" -- services \
    | awk -F/ 'NF > 2 && $1 == "services" { print $2 }' | sort -u
)

if ((${#services[@]} <= 1)); then
  echo "pr-scope: ${#services[@]} service(s) changed"
  exit 0
fi

list=$(printf '%s, ' "${services[@]}")
list=${list%, }

if [[ "$cross_cutting" == "true" ]]; then
  echo "pr-scope: services $list changed, allowed by the label cross-cutting"
  exit 0
fi

echo "pr-scope: FAILED"
echo "This PR changes services $list. Change one service per PR, or add the label cross-cutting when the change must land together (e.g. a shared contract). See ADR-0034."
exit 1
