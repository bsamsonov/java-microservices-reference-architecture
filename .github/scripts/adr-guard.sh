#!/usr/bin/env bash
# Fails when a PR changes an ADR that is final (Accepted, Rejected, Superseded) in the base ref.
# Allowed on a final ADR: the "Status", "Implemented" and "Amended by" lines. See ADR-0034.
# Usage: adr-guard.sh <base-ref> <head-ref>; ADR_AMEND=true turns failures into a warning.
set -euo pipefail

base=${1:?usage: adr-guard.sh <base-ref> <head-ref>}
head=${2:?usage: adr-guard.sh <base-ref> <head-ref>}
amend=${ADR_AMEND:-false}

merge_base=$(git merge-base "$base" "$head")
adr_re='^docs/adr/[0-9]{4}-[^/]+\.md$'

# Status value of an ADR in the base version; empty when the line is missing
status_of() {
  git show "$merge_base:$1" | sed -n 's/^- \*\*Status:\*\* *//p' | head -n 1
}

is_final() {
  case "$(status_of "$1")" in
    Accepted* | Rejected* | Superseded*) return 0 ;;
    *) return 1 ;;
  esac
}

# The ADR text without the lines that may change on a final ADR
body_of() {
  git show "$1:$2" | grep -v -e '^- \*\*Status:\*\*' -e '^- \*\*Implemented:\*\*' -e '^- \*\*Amended by:\*\*' || true
}

failures=()

while IFS=$'\t' read -r code path new_path; do
  [[ -n "$code" ]] || continue
  [[ "$path" =~ $adr_re ]] || continue
  case "$code" in
    A*) continue ;;
    D*)
      is_final "$path" || continue
      failures+=("$path is $(status_of "$path"): do not delete it. Write a new ADR that supersedes it and set this one to \"Superseded by ADR-NNNN\". See ADR-0034.")
      ;;
    R*)
      is_final "$path" || continue
      failures+=("$path is $(status_of "$path"): do not rename it to $new_path. Write a new ADR that supersedes it, or add the label adr-amend. See ADR-0034.")
      ;;
    *)
      is_final "$path" || continue
      if ! diff -q <(body_of "$merge_base" "$path") <(body_of "$head" "$path") > /dev/null; then
        failures+=("$path is $(status_of "$path"): write a new ADR that supersedes it (set this one to \"Superseded by ADR-NNNN\") or amends it (add \"- **Amended by:** ADR-NNNN\" here), or add the label adr-amend for a typo fix. See ADR-0034.")
      fi
      ;;
  esac
done < <(git diff --name-status -M "$merge_base" "$head" -- docs/adr)

if ((${#failures[@]} == 0)); then
  echo "adr-guard: no final ADR was changed"
  exit 0
fi

if [[ "$amend" == "true" ]]; then
  echo "adr-guard: WARNING, final ADRs changed, allowed by the label adr-amend:"
  printf '  %s\n' "${failures[@]}"
  exit 0
fi

echo "adr-guard: FAILED"
printf '  %s\n' "${failures[@]}"
exit 1
