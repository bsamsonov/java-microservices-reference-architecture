# ADR-0034: Repository rules as code: protected main, frozen ADRs, one service per pull request

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2

## Context and drivers
ADR-0024 makes the architecture rules fail the build, but nothing stopped a change from skipping the build: `main` took direct pushes,
an accepted ADR could be rewritten in place, and one commit could touch every service at once. An AI agent pushes as fast as it writes (QD2),
so the rules about how a change reaches `main` must be enforced by GitHub and CI, the same way the code rules are enforced by tests.
The repository has one maintainer, so a rule that needs a second person to approve would block every change.

## Considered options
1. **GitHub ruleset plus two CI checks, no required approval**: the ruleset guards `main`; `adr-guard` and `pr-scope` guard what a pull request changes.
2. **Classic branch protection with required reviews and CODEOWNERS approval**: the usual team setup.
3. **Conventions in `AGENTS.md` only**: the agent is told, nothing checks it.

## Decision
Option 1.
- **Ruleset `main`** ([`.github/rulesets/main.json`](../../.github/rulesets/main.json), no bypass, applies to the maintainer as well):
  changes only through a pull request, merged only by rebase, linear history, no force push or deletion, review threads resolved,
  and the CI checks are required on a branch that is up to date with `main`. The repository settings allow rebase merges only and delete merged branches.
- **`adr-guard`** (`.github/scripts/adr-guard.sh`, workflow `governance.yml`): an ADR whose status is `Accepted`, `Rejected` or `Superseded` is frozen.
  A pull request may change only its `Status` line (to mark it superseded) and its `Implemented` line, which records progress
  (`- **Implemented:** no, roadmap B5`). Anything else fails with "write a new ADR that supersedes it". `Proposed` and `Deferred` records stay editable.
  The label `adr-amend` lets a typo or a broken link through, visibly.
- **`pr-scope`** (`.github/scripts/pr-scope.sh`): a pull request changes at most one `services/<name>/`. Shared folders (`charts/`, `observability/`,
  `compose/`, `docs/`) do not count. The label `cross-cutting` lets a change that must land together through, such as a fix applied to every template.
  Inside a service the module boundaries are already checked by `ModularityTest`, `HexagonArchitectureTest` and `ModuleSchemaTest` (ADR-0024).
- **`CODEOWNERS`** names an owner per service and for `docs/adr/`, `.github/` and `AGENTS.md`. Code owner review is not required:
  with one maintainer it would block every pull request. A team that adopts the template turns it on.

## Consequences
- **Good:** every change to `main` has passed CI; a decision changes only through a new record, so the history of decisions stays readable;
  a pull request is small enough to review and to revert; the agent learns the rules from the failing check, as with the architecture tests.
- **Cost:** no second pair of eyes is required, so a careless maintainer can still merge a bad change that passes CI;
  fixing a typo in an accepted ADR needs a label; a change across services needs a label and a reason; the ruleset lives in GitHub, and
  `.github/rulesets/main.json` is a copy that must be applied by hand after a change.

## When to choose differently
- Two or more maintainers: require one approval and code owner review (option 2), keep `adr-guard` and `pr-scope`.
- A monorepo with teams per service: per-team CODEOWNERS with required review, and `pr-scope` relaxed to one team instead of one service.
- A private repository on a free plan, where rulesets are not available: option 3 plus the same checks as required local hooks.

## Evidence
- [GitHub: about rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets).
- `.github/scripts/tests/run.sh`: fixtures for each case of both checks.
