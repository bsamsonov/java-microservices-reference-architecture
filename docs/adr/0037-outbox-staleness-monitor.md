# ADR-0037: Outbox: a publication stuck in flight is marked failed after 5 minutes

- **Status:** Accepted
- **Implemented:** yes
- **Date:** 2026-10-27
- **Drivers:** QD3
- **Amends:** ADR-0003, the staleness monitor is turned on

## Context and drivers
ADR-0003 kept the Spring Modulith staleness monitor off and relied on `republish-outstanding-events-on-restart` for a publication
cut short by a crash. The broker outage experiment ([quality evidence](../architecture/quality-evidence.md#broker-outage)) showed two cases that the restart does not cover.
`orders-service` was restarted during the outage, as a rolling update or an eviction would do. 44 publications (42 to Kafka, 2 to the audit module; 652 orders in total) then stayed undelivered after Kafka came back, with no end in sight:
- 40 rows in `RESUBMITTED`: the old pod was sending them again when it was stopped. The restart does not republish `RESUBMITTED` rows, and `OutboxResubmission` takes only `FAILED` rows.
- 4 rows in `PUBLISHED`: the old pod created them while it was still serving during the rolling update, after the new pod had run its republish.

Nothing fails and the service looks healthy; only the alert `OutboxBacklogStale` kept firing. A stuck event breaks QD3.

## Considered options
1. **Turn on the staleness monitor** (`spring.modulith.events.staleness.*`): a `PUBLISHED`, `PROCESSING` or `RESUBMITTED` row older than the threshold is marked `FAILED`, and `OutboxResubmission` sends it again.
2. **Keep it off and recover by hand**: the runbook of `OutboxBacklogStale` tells the operator to mark stuck rows `FAILED` in SQL.
3. **Own sweeper**: a scheduled job that marks rows `FAILED` by `last_resubmission_date` instead of the publication date.

## Decision
Option 1, with 5 minutes for all three states in `services/orders-service/src/main/resources/application.yaml`; the check runs every minute (Modulith default).
5 minutes is longer than the longest Kafka send (`max.block.ms` 60 s plus `delivery.timeout.ms` 120 s) and equals the threshold of `OutboxBacklogStale`.
`republish-outstanding-events-on-restart` stays: it delivers a crashed pod's rows right after the restart instead of 5 minutes later.
[`OutboxStalePublicationIntegrationTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/OutboxStalePublicationIntegrationTest.java)
inserts a 10-minute-old `RESUBMITTED` row and expects it in Kafka; with the monitor off the test fails.

## Consequences
- **Good:** no publication stays stuck after a pod is stopped mid-send; recovery needs no restart and no operator.
- **Cost:** Modulith 2.1 counts the age from the publication date, not from the last attempt. During an outage longer than 5 minutes
  a row whose resubmission is still in flight is marked `FAILED` again and sent twice. In the experiment (9-minute outage, one restart) 100 of 315 events reached Kafka twice.
  Consumers deduplicate by `eventId` (ADR-0004), so this costs Kafka traffic, not correctness.

## When to choose differently
- Duplicates are expensive (a consumer that cannot deduplicate, a very large backlog): **own sweeper** (option 3) that ages a row by its last attempt.
- A Modulith release that ages `RESUBMITTED` by the last resubmission date makes option 1 duplicate-free; then nothing else changes.
- Listeners that may legitimately run longer than 5 minutes: raise the threshold above their longest run.

## Evidence
- [Quality evidence: broker outage](../architecture/quality-evidence.md#broker-outage): 44 publications stuck with the monitor off; with it on, none stuck and 100 duplicates out of 315 events.
- [Spring Modulith reference: event publication staleness](https://docs.spring.io/spring-modulith/reference/events.html).
