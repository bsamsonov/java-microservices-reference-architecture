# How this repository was built with an AI coding agent

Goal 3 of the [architecture overview](architecture/README.md#1-goals) is to show how an architect delivers a platform like this one
with an AI coding agent. The human makes and records the decisions, the agent implements them, and the build checks the rules.

## Who did what

| | Human (architect, owner) | AI coding agent |
|---|---|---|
| Goals and drivers | set the three goals and the order of the quality drivers QD1–QD7 | proposed drafts; the owner's review reordered the drivers and added goal 3 |
| Decisions | chose between options and accepted or rejected each ADR, for example Argo CD instead of Flux ([ADR-0018](adr/0018-gitops-with-argo-cd.md)) and a database user per service ([ADR-0007](adr/0007-database-per-service-schema-per-module.md)) | researched options and current versions, wrote the ADR drafts with costs and "when to choose differently" |
| Code and configuration | reviewed the results and the evidence; did not write most lines | wrote the services, tests, Helm chart, compose and k3d setup, alert rules and scripts |
| Verification | decided what counts as done ([`AGENTS.md`](../AGENTS.md#done-when)) | ran `./mvnw verify`, the compose and k3d scenarios and `observability/check-alerting.sh`, and reported what was checked and what was not |
| Documents | reviewed and corrected them | wrote READMEs, ADRs, runbooks and this page |

Work went in short loops: a question, research of the options, the owner's decision, an ADR, implementation by the agent,
the automated checks, a manual run in compose or k3d, the owner's review. A decision is never changed silently: the ADR is written or updated first
(rule at the top of `AGENTS.md`).

## How the build catches the agent's mistakes (QD2)

An agent writes code fast and does not get tired. It also forgets a rule it read an hour ago. Review alone does not scale to its output, so every rule
an agent is likely to break has a test ([ADR-0024](adr/0024-architecture-rules-as-tests.md)), and `./mvnw verify` fails with a message that says what to do instead.

| Check | What it stops | Where |
|---|---|---|
| `ModularityTest` (Spring Modulith) | a module using another module's internal package | template 1 services |
| `HexagonArchitectureTest` (ArchUnit) | Spring or JPA in `domain`, `application` knowing `adapter`, `@Entity` outside persistence, `@RestController` outside web | template 1 services |
| `ModuleSchemaTest` | a `@Table` without a schema, a schema used by two modules, a migration touching another module's schema | template 1 services, [ADR-0007](adr/0007-database-per-service-schema-per-module.md) |
| `ArchitectureTest` (ArchUnit) | the package-by-feature rules of the CRUD template | `catalog-service` |
| web-slice tests with tokens | an endpoint without a permission: each rule has a 401 and a 403 test | every `*ControllerTest` |
| `promtool test rules`, `pint`, runbook check | an alert that does not fire on its test input; an alert without `severity`, `team`, `summary` or a runbook | [`observability/check-alerting.sh`](../observability/check-alerting.sh) |
| CI | all of the above on every pull request | `.github/workflows/ci.yml` |

Examples from this repository:

- Each rule of `ModuleSchemaTest` was seen failing on an injected violation before the commit (commit `71610e3`),
  because a test that has never failed may check nothing.
- The outbox fix started with a red `OutboxRedeliveryIntegrationTest`: Kafka paused, an order placed, Kafka resumed, and the event
  must arrive without a restart. It turned green once `OutboxResubmission` was written (commit `488ff62`, [ADR-0003](adr/0003-outbox-on-modulith-event-registry.md)).
- The first SLO alerts had no `summary`, so the notification had no text. The fix added `summary` to the SLO files
  and a `pint` rule that requires it on every alert ([`.pint.hcl`](../observability/prometheus/.pint.hcl)).
- A manual run in compose showed that a page size above the limit returned 500 instead of 400: the constraint on the
  generated OpenAPI interface had no exception handler. `ProductExceptionHandler` now maps it, and `pageSizeAboveLimitIsRejectedByContract` keeps it so.
- In a fault run, a broken Kafka message did not fire the listener alert. The error counter is created with the value 1,
  so `rate()` sees no growth on the first failure. The rule now also fires on a series that appeared in the last 10 minutes,
  and a `promtool` case with exactly that input keeps it so ([`spring-services.rules.yaml`](../observability/prometheus/rules/spring-services.rules.yaml)).

## AGENTS.md and the create-service skill

[`AGENTS.md`](../AGENTS.md) is the single source of rules for agents and people. `CLAUDE.md` only imports it, so another agent tool reads the same file
([ADR-0025](adr/0025-ai-agent-as-a-participant.md)). Its sections follow the order in which an agent needs them:
the repository map, commands, the service architecture with the test behind each rule, contracts (OpenAPI first, events, migrations, outbox, access),
observability, the table "which test to add for which change", "done when" and "never".

The skill [`.claude/skills/create-service/`](../.claude/skills/create-service/SKILL.md) answers "create service X":

1. one round of business questions (name, aggregates and invariants, use cases, events), with no questions about infrastructure;
2. a template choice: template 1 for business rules and events, template 2 for plain CRUD ([ADR-0032](adr/0032-jpa-for-rich-domain-jdbc-for-crud.md));
3. `scaffold.sh` copies the template with renames (package, artifact, database, user, ingress host); a copy of either template passes `verify` as is;
4. the agent replaces the domain, writes the tests from the table in `AGENTS.md`, runs `verify` and wires the service into compose, Tilt and the platform.

`notifications-service` and `catalog-service` were built through this skill. The new service appears on the shared dashboard and is covered by the alerts and SLOs
without edits, because they group by the `application` label ([ADR-0021](adr/0021-dashboards-as-code.md)).

## Limits

- The QD1 target, "from 'create service X' to a green build in both modes within one working day", has not been timed.
  A comparison of several models on the same `create-service` task is planned.
- Some rules have no test, and the build does not check them: the logging rules (no personal data, business events at INFO) and the OpenAPI-first order.
  For an agent such a rule is only a request.
- The build stops a module from reading another module's table. Whether the module boundary is in the right place is a design question, and it stays with the architect.
- The agent's reports need checking. "Verified" in the documents means a run whose output was read, and some checks were done once, on one laptop
  (see the "Status" column of the [quality drivers](architecture/README.md#2-quality-drivers)).
- ADR-0001 to ADR-0036 were written on one day for decisions taken while the templates were built ([ADR index](adr/README.md#rules)).
  They record the reasoning after the fact.
- The skill uses one agent tool's format. `AGENTS.md` is portable; in other tools the skill becomes a checklist.
