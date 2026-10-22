#!/usr/bin/env bash
# Fails when a commit of the PR does not follow Conventional Commits (ADR-0036). History before the switch is not checked.
# Usage: commit-msg.sh <base-ref> <head-ref>
set -euo pipefail

base=${1:?usage: commit-msg.sh <base-ref> <head-ref>}
head=${2:?usage: commit-msg.sh <base-ref> <head-ref>}

max_len=72
pattern='^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9._-]+\))?!?: [^ ].*$'
failures=()

while IFS=$'\t' read -r sha subject author; do
  [[ -n "$sha" ]] || continue
  # git revert writes 'Revert "<subject>"'; keep it as git made it
  [[ "$subject" == 'Revert "'* ]] && continue
  if [[ ! "$subject" =~ $pattern ]]; then
    failures+=("${sha:0:7} \"$subject\": expected <type>(<scope>): <subject>")
  # Dependabot writes long group subjects ("bump the ... group across 3 directories"); only its format is checked
  elif ((${#subject} > max_len)) && [[ "$author" != *'dependabot[bot]'* ]]; then
    failures+=("${sha:0:7} \"$subject\": ${#subject} chars, at most $max_len")
  fi
done < <(git log --no-merges --format='%H%x09%s%x09%ae' "$(git merge-base "$base" "$head")..$head")

if ((${#failures[@]} == 0)); then
  echo "commit-msg: every commit follows Conventional Commits"
  exit 0
fi

echo "commit-msg: FAILED"
printf '  %s\n' "${failures[@]}"
cat <<'MSG'
Types: feat fix docs style refactor perf test build ci chore revert; scope: the service or area, e.g. fix(orders-service): ...
Reword with `git rebase -i` and push the branch again. See ADR-0036.
MSG
exit 1
