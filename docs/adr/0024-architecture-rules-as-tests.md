# ADR-0024: Architecture rules are tests (fitness functions), decisions are ADRs

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2

## Context and drivers
Rules that live only in a document are broken first by whoever did not read it, and an AI agent produces code faster than a reviewer reads it (QD2).
A rule must fail the build with a message that says what to do instead (QD2). Decisions need a place where their reasons and limits are kept.

## Considered options
1. **Executable checks for every rule that can be checked, ADRs for the reasons**.
2. **Rules in a wiki or a document, enforced by review**.
3. **Static analysis platforms only** (Sonar quality profiles, custom lint rules).

## Decision
Option 1. Checks in the repository:
`ModularityTest` (Spring Modulith `verify()`), `HexagonArchitectureTest` and `ArchitectureTest` (ArchUnit), 401 and 403 tests per access rule,
`OutboxRedeliveryIntegrationTest` for the delivery guarantee, `observability/check-alerting.sh` (`promtool`, `amtool`, `pint`, runbooks) and `.gitleaks.toml` for secrets.
A failing architecture test is fixed in the code; a rule is never weakened for a feature (`AGENTS.md`).
Decisions are ADRs in this folder (MADR, with "when to choose differently"); a decision changes through a new ADR.
CI that runs all checks on every push is planned (roadmap B4).

## Consequences
- **Good:** the rules hold without a reviewer; an agent learns them from the failure message; reasons survive people leaving.
- **Cost:** tests to maintain when the structure changes on purpose; some rules cannot be checked (business naming, SQL across schemas, see ADR-0007);
  until CI exists the checks run only when someone runs them.

## When to choose differently
- A large organisation with a central quality platform: run the same rules there, keep the tests close to the code anyway.
- A prototype that will be thrown away: a short README is enough.

## Evidence
- [ArchUnit](https://www.archunit.org/) and [Spring Modulith: verifying application module structure](https://docs.spring.io/spring-modulith/reference/verification.html).
- [`HexagonArchitectureTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/HexagonArchitectureTest.java).
