# ADR-0037: Outbox recovery: the staleness monitor instead of republish on restart

- **Status:** Accepted
- **Implemented:** yes
- **Date:** 2026-10-27
- **Drivers:** QD3
- **Amends:** ADR-0003, how a publication cut short by a stopped or crashed pod is recovered

## Context and drivers
ADR-0003 kept the Spring Modulith staleness monitor off and relied on `republish-outstanding-events-on-restart` for a publication
cut short by a crash. The broker outage experiment ([quality evidence](../architecture/quality-evidence.md#broker-outage)) showed two cases that the restart does not cover.
`orders-service` was restarted during the outage, as a rolling update or an eviction would do. 44 publications (42 to Kafka, 2 to the audit module; 652 orders in total) then stayed undelivered after Kafka came back, with no end in sight:
- 40 rows in `RESUBMITTED`: the old pod was sending them again when it was stopped. The restart does not republish `RESUBMITTED` rows, and `OutboxResubmission` takes only `FAILED` rows.
- 4 rows in `PUBLISHED`: the old pod created them while it was still serving during the rolling update, after the new pod had run its republish.

Nothing fails and the service looks healthy; only the alert `OutboxBacklogStale` kept firing. A stuck event breaks QD3.
Republish on restart also has its own cost (risk R1): with several replicas, a starting pod resends the rows the other replicas are still sending.
And Spring Boot's task executor, which runs the Kafka sends after commit, does not wait for them on shutdown by default.

## Considered options
1. **Staleness monitor instead of republish on restart** (`spring.modulith.events.staleness.*`): a `PUBLISHED`, `PROCESSING` or `RESUBMITTED` row older than the threshold is marked `FAILED`, and `OutboxResubmission` sends it again.
2. **Staleness monitor and republish on restart**: the same, plus a crashed pod's rows are resent as soon as it is back.
3. **Keep the monitor off and recover by hand**: the runbook of `OutboxBacklogStale` tells the operator to mark stuck rows `FAILED` in SQL.
4. **Own sweeper**: a scheduled job that marks rows `FAILED` by `last_resubmission_date` instead of the publication date.

## Decision
Option 1, in `services/orders-service/src/main/resources/application.yaml`:
- the staleness monitor at 5 minutes for all three states; the check runs every minute (Modulith default).
  5 minutes is longer than the longest Kafka send (`max.block.ms` 60 s plus `delivery.timeout.ms` 120 s) and equals the threshold of `OutboxBacklogStale`;
- `republish-outstanding-events-on-restart: false`. In Kubernetes it does not help a rolling update anyway (the new pod republishes before the old one stops),
  and with several replicas it causes duplicates;
- `spring.task.execution.shutdown.await-termination: true` (20 s): on a planned stop the sends already started wait for the broker,
  so a redeploy or a node drain leaves nothing in flight. Only a crash (`kill -9`, OOM, a lost node) relies on the monitor.

Every unfinished row therefore ends as `FAILED` and is resubmitted until Kafka accepts it; a row is deleted only after the broker's ack.
[`OutboxStalePublicationIntegrationTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/OutboxStalePublicationIntegrationTest.java)
inserts a 10-minute-old row in each of the three states and expects it in Kafka; with the monitor off it fails.

## Consequences
- **Good:** no publication stays stuck after a pod is stopped or crashes mid-send; recovery needs no restart and no operator.
  No duplicates from restarts of other replicas.
- **Cost:** after a crash, the events that were in flight arrive 5 to 6.5 minutes late; a consumer may see them after later events of the same order
  (ADR-0003 gives no ordering across retries). Modulith 2.1 counts the age from the publication date, not from the last attempt: during an outage longer than 5 minutes
  a row whose resubmission is still in flight is marked `FAILED` again and sent twice. In the experiment (9-minute outage, one restart) 100 of 315 events reached Kafka twice.
  Consumers deduplicate by `eventId` (ADR-0004), so this costs Kafka traffic, not correctness.
- An event whose class or listener was renamed while it waited in the table can never be delivered: it stays in the table and the alert fires (risk R2).

## When to choose differently
- A single replica that must recover from a crash faster than in 5 minutes: **option 2**, accepting duplicates once there are more replicas.
- Duplicates are expensive (a consumer that cannot deduplicate, a very large backlog): **own sweeper** (option 4) that ages a row by its last attempt.
- A Modulith release that ages `RESUBMITTED` by the last resubmission date makes option 1 duplicate-free; then nothing else changes.
- Listeners that may legitimately run longer than 5 minutes: raise the threshold above their longest run.

## Evidence
- [Quality evidence: broker outage](../architecture/quality-evidence.md#broker-outage): 44 publications stuck with the monitor off; with it on, none stuck and 100 duplicates out of 315 events;
  planned restarts and a crash with this configuration.
- [Spring Modulith reference: event publication staleness](https://docs.spring.io/spring-modulith/reference/events.html).
