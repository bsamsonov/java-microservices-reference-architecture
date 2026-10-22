## What and why
<!-- 2-5 lines. Link the issue or the roadmap item. -->

## How it was verified
<!-- tests added or changed; for infrastructure or configuration: compose with --profile apps, the POST scenario, trace and logs in Grafana -->

## Checklist
- [ ] One logical unit per commit, messages in English, Conventional Commits (`type(scope): subject`)
- [ ] `./mvnw verify` green in every changed service; tests from the table in `AGENTS.md`
- [ ] Contracts first: `openapi.yaml`, event record, new migration, topic in `deploy/values.yaml`
- [ ] One service per PR, or the label `cross-cutting` with a reason
- [ ] Decisions: a new ADR that supersedes or amends; accepted ADRs are not rewritten
- [ ] No secrets, tokens or personal data in code, logs or fixtures
