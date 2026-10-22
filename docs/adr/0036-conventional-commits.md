# ADR-0036: Commit messages: Conventional Commits from 2026-10-22, checked in CI

- **Status:** Accepted (owner, 2026-10-22)
- **Date:** 2026-10-22
- **Drivers:** QD1, QD2

## Context and drivers
Until 2026-10-22 commit subjects were plain imperative sentences ("Add ADR-0034: ..."). They read well, but a tool cannot tell
a feature from a fix or from an infrastructure change, so there is no changelog and no version that follows from the history.
An AI agent writes most commits (QD1), and a rule it can break silently is broken (QD2), so the format must be checked, not only described.

## Considered options
1. **Conventional Commits, checked in CI for every commit of a pull request**, starting now; the earlier history stays as it is.
2. **The same, enforced by a local `commit-msg` hook** (lefthook): fast feedback, but only after `lefthook install`, and `--no-verify` skips it.
3. **Keep plain sentences**.

## Decision
Option 1. `.github/scripts/commit-msg.sh` (job `commit-messages` in `governance.yml`, required by the ruleset) checks every non-merge commit
of a pull request: `<type>(<scope>): <subject>`, at most 72 characters; `!` before the colon marks a breaking change; a `Revert "..."` subject
written by `git revert` passes. The history before the switch is not rewritten: `main` takes no force push (ADR-0034), and the SHAs are referenced from ADRs and docs.

Types and scopes. A change to the infrastructure is not a feature: it goes through the same branch, pull request and checks, with its own type.

| Change | Type | Scope |
|---|---|---|
| behaviour of a service: endpoint, rule, event, migration | `feat`, `fix`, `perf`, `refactor` | the service: `orders-service` |
| tests only | `test` | the service |
| how it is built and run: `pom.xml`, Dockerfile, `compose/`, `charts/`, `platform/`, `Tiltfile`, `security/keycloak/` | `build` | `compose`, `charts`, `platform`, `security`, or the service |
| dashboards, alert rules, SLOs, runbooks in `observability/` | `feat` or `fix` when an alert changes, `docs` for a runbook only | `observability` |
| CI and repository rules in `.github/` | `ci` | `governance`, `ci` |
| ADRs, architecture docs, READMEs | `docs` | `adr`, `architecture`, or the service |
| Dependabot updates, housekeeping | `chore` or `build` | the ecosystem: `deps` |

Infrastructure folders do not count in `pr-scope` (ADR-0034), so an infrastructure change passes on its own or together with one service.
When it must change every service at once (a new chart value in each `services/<name>/deploy/values.yaml`), the pull request takes the label `cross-cutting`.
`ci.yml` checks the chart (`helm lint`, `kubeconform`) and the alerting rules on every pull request; a running compose or k3d stack is not part of CI,
so `AGENTS.md` ("Done when", item 4) asks for the compose check by hand.

## Consequences
- **Good:** the type says what a change is before the diff is opened; a changelog and a version can be generated from `main` (release-please or similar, not set up);
  the agent learns the format from the failing check.
- **Cost:** `git log` mixes two styles before and after 2026-10-22; a wrong message is fixed by rewording the branch and pushing it again;
  Dependabot is configured to write `build(deps): ...` and `ci(deps): ...` (`.github/dependabot.yml`); its long group subjects are exempt from the length limit,
  and its Maven and Docker pull requests, which touch every service, carry the label `cross-cutting`.

## When to choose differently
- A team that squashes every pull request into one commit: check the pull request title instead of each commit.
- No release tooling planned and a single author: option 3 is enough.

## Evidence
- [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).
- `.github/scripts/tests/run.sh`: the `commit-msg` cases.
