#!/usr/bin/env bash
# Self-tests for adr-guard.sh and pr-scope.sh: every case runs in a throwaway git repo.
# Usage: .github/scripts/tests/run.sh
set -euo pipefail

scripts=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

passed=0
failed=0
repo=""

adr() { # adr <title> <status line value>
  printf '# ADR: %s\n\n- **Status:** %s\n- **Date:** 2026-10-22\n\n## Decision\nOption 1.\n' "$1" "$2"
}

# A repo with a base commit on main and a branch "pr" checked out
new_repo() {
  repo=$(mktemp -d -p "$work")
  cd "$repo"
  git init -q -b main
  git config user.name test
  git config user.email test@example.com
  mkdir -p docs/adr services/a services/b charts docs
  adr accepted Accepted > docs/adr/0001-accepted.md
  adr rejected "Rejected (not needed)" > docs/adr/0002-rejected.md
  adr superseded "Superseded by ADR-0003" > docs/adr/0003-superseded.md
  adr proposed Proposed > docs/adr/0004-proposed.md
  adr deferred Deferred > docs/adr/0005-deferred.md
  adr impl Accepted > docs/adr/0006-implemented.md
  sed -i '/^- \*\*Status:/a - **Implemented:** no, roadmap B5' docs/adr/0006-implemented.md
  echo "index" > docs/adr/README.md
  echo "template" > docs/adr/template.md
  echo "a" > services/a/app.yaml
  echo "b" > services/b/app.yaml
  echo "c" > charts/values.yaml
  echo "d" > docs/guide.md
  echo "root" > Makefile
  git add -A
  git commit -q -m base
  git switch -q -c pr
}

commit_all() {
  git add -A
  git commit -q -m change
}

# run <script> [ENV=value ...]: sets $code and $out
run() {
  local script=$1
  shift
  code=0
  out=$(env "$@" bash "$scripts/$script" main pr 2>&1) || code=$?
}

# expect <name> <exit code> [message part]
expect() {
  local name=$1 want=$2 part=${3:-}
  if [[ "$code" != "$want" ]]; then
    echo "FAIL: $name: exit $code, want $want"
    echo "$out" | sed 's/^/      /'
    failed=$((failed + 1))
  elif [[ -n "$part" && "$out" != *"$part"* ]]; then
    echo "FAIL: $name: output lacks \"$part\""
    echo "$out" | sed 's/^/      /'
    failed=$((failed + 1))
  else
    echo "ok:   $name"
    passed=$((passed + 1))
  fi
}

# ---- adr-guard ----

new_repo
adr new Proposed > docs/adr/0007-new.md
commit_all
run adr-guard.sh
expect "adr-guard: new ADR is ok" 0

new_repo
echo "more" >> docs/adr/0004-proposed.md
commit_all
run adr-guard.sh
expect "adr-guard: edit Proposed is ok" 0

new_repo
echo "more" >> docs/adr/0005-deferred.md
commit_all
run adr-guard.sh
expect "adr-guard: edit Deferred is ok" 0

new_repo
echo "more" >> docs/adr/0001-accepted.md
commit_all
run adr-guard.sh
expect "adr-guard: edit Accepted body fails" 1 "docs/adr/0001-accepted.md is Accepted"

new_repo
echo "more" >> docs/adr/0001-accepted.md
commit_all
run adr-guard.sh
expect "adr-guard: failure message points to ADR-0034" 1 "See ADR-0034"

new_repo
sed -i 's/^- \*\*Status:\*\* .*/- **Status:** Superseded by ADR-0099/' docs/adr/0001-accepted.md
commit_all
run adr-guard.sh
expect "adr-guard: Accepted -> Superseded status line is ok" 0

new_repo
sed -i '/^- \*\*Status:/a - **Implemented:** no, roadmap B5' docs/adr/0001-accepted.md
commit_all
run adr-guard.sh
expect "adr-guard: add Implemented line to Accepted is ok" 0

new_repo
sed -i 's/^- \*\*Implemented:\*\*.*/- **Implemented:** yes/' docs/adr/0006-implemented.md
commit_all
run adr-guard.sh
expect "adr-guard: change Implemented line on Accepted is ok" 0

new_repo
sed -i '/^- \*\*Implemented:/d' docs/adr/0006-implemented.md
commit_all
run adr-guard.sh
expect "adr-guard: remove Implemented line on Accepted is ok" 0

new_repo
sed -i '/^- \*\*Implemented:/d' docs/adr/0006-implemented.md
echo "more" >> docs/adr/0006-implemented.md
commit_all
run adr-guard.sh
expect "adr-guard: Implemented change plus body edit fails" 1 "0006-implemented.md"

new_repo
sed -i '/^- \*\*Date:/a - **Amended by:** ADR-0099, the Java version' docs/adr/0001-accepted.md
commit_all
run adr-guard.sh
expect "adr-guard: add Amended by line to Accepted is ok" 0

new_repo
sed -i '/^- \*\*Date:/a - **Amended by:** ADR-0099' docs/adr/0001-accepted.md
echo "more" >> docs/adr/0001-accepted.md
commit_all
run adr-guard.sh
expect "adr-guard: Amended by plus body edit fails" 1 "amends it"

new_repo
git rm -q docs/adr/0001-accepted.md
commit_all
run adr-guard.sh
expect "adr-guard: delete Accepted fails" 1 "do not delete"

new_repo
git mv docs/adr/0001-accepted.md docs/adr/0001-renamed.md
commit_all
run adr-guard.sh
expect "adr-guard: rename Accepted fails" 1 "do not rename"

new_repo
git rm -q docs/adr/0004-proposed.md
git mv docs/adr/0005-deferred.md docs/adr/0005-postponed.md
commit_all
run adr-guard.sh
expect "adr-guard: delete Proposed and rename Deferred are ok" 0

new_repo
echo "more" >> docs/adr/0002-rejected.md
commit_all
run adr-guard.sh
expect "adr-guard: edit Rejected body fails" 1 "0002-rejected.md"

new_repo
echo "more" >> docs/adr/0003-superseded.md
commit_all
run adr-guard.sh
expect "adr-guard: edit Superseded body fails" 1 "0003-superseded.md"

new_repo
echo "more" >> docs/adr/0001-accepted.md
commit_all
run adr-guard.sh ADR_AMEND=true
expect "adr-guard: ADR_AMEND=true passes a body edit" 0 "WARNING"

new_repo
echo "more" >> docs/adr/0001-accepted.md
commit_all
run adr-guard.sh ADR_AMEND=false
expect "adr-guard: ADR_AMEND=false still fails" 1

new_repo
echo "more" >> docs/adr/README.md
echo "more" >> docs/adr/template.md
commit_all
run adr-guard.sh
expect "adr-guard: README and template edits are ok" 0

new_repo
echo "more" >> docs/guide.md
commit_all
run adr-guard.sh
expect "adr-guard: no ADR change is ok" 0

# ---- pr-scope ----

new_repo
echo "x" >> services/a/app.yaml
commit_all
run pr-scope.sh
expect "pr-scope: one service is ok" 0

new_repo
echo "x" >> services/a/app.yaml
echo "x" >> services/b/app.yaml
commit_all
run pr-scope.sh
expect "pr-scope: two services fail" 1 "This PR changes services a, b."

new_repo
echo "x" >> services/a/app.yaml
echo "x" >> services/b/app.yaml
commit_all
run pr-scope.sh CROSS_CUTTING=true
expect "pr-scope: two services with CROSS_CUTTING=true are ok" 0 "a, b"

new_repo
echo "x" >> services/a/app.yaml
echo "x" >> charts/values.yaml
echo "x" >> docs/guide.md
echo "x" >> Makefile
commit_all
run pr-scope.sh
expect "pr-scope: one service plus charts, docs and root files is ok" 0

new_repo
echo "x" >> charts/values.yaml
echo "x" >> docs/guide.md
commit_all
run pr-scope.sh
expect "pr-scope: no services is ok" 0

new_repo
git mv services/a/app.yaml services/b/moved.yaml
commit_all
run pr-scope.sh
expect "pr-scope: a file moved between services fails" 1

new_repo
echo "x" >> services/a/app.yaml
git commit -q -am "first"
echo "x" >> services/b/app.yaml
commit_all
run pr-scope.sh
expect "pr-scope: services in separate commits fail" 1

echo
echo "passed: $passed, failed: $failed"
((failed == 0))
