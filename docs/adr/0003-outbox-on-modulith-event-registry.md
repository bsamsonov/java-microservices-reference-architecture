# ADR-0003: Transactional outbox on the Spring Modulith event publication registry

- **Status:** Accepted
- **Implemented:** yes, including the backlog gauges (`OutboxMetrics`) and the alert `OutboxBacklogStale`
- **Date:** 2026-10-22
- **Drivers:** QD3, QD7
- **Replaces:** D12
- **Amended by:** ADR-0037, staleness monitor turned on

## Context and drivers
An order and its `OrderPlaced` event must be stored atomically: no order without an event, no event without an order (QD3).
Writing to Postgres and Kafka in one transaction is not possible, so the event is stored in the same database transaction and sent after commit.
The solution must run on a laptop without extra infrastructure (QD7). Risk R1 records what remains open.

## Considered options
1. **Spring Modulith event publication registry** (`event_publication` table) with externalization to Kafka, scheduled resubmission and delete on completion.
2. **A dedicated outbox library** (Modulith externalization mode `outbox` with the Namastack or JobRunr starter): own polling, ordering and retries.
3. **Debezium CDC** on an outbox table: Kafka Connect reads the Postgres WAL and publishes the rows.

## Decision
Option 1. The event row is written in the business transaction and published after commit. A failed publication stays in the table;
`OutboxResubmission` resubmits it every 30 s, and several replicas do not resubmit the same row twice (conditional update `FAILED` → `RESUBMITTED`).
Completed rows are deleted (`completion-mode: delete`), so the table size is the outbox lag. A crash in the middle of a publication
is covered by `republish-outstanding-events-on-restart`; in Kubernetes a crashed pod restarts.
There is no attempt limit: a broker outage must not drop events. Failed in-process listeners (the audit module) are retried the same way.
The staleness monitor (`spring.modulith.events.staleness.*`) is off: in Modulith 2.1 it ages every status by the publication date,
so after an outage longer than the threshold it marks resubmissions already in flight as failed and they are sent twice.
Enable it when listeners can hang without the process dying.
On a database created before the switch to `delete`, run `delete from event_publication where completion_date is not null` once.
**Only services that publish events have the outbox.** A consumer-only service (`notifications-service`) and a CRUD service (`catalog-service`) have no
`event_publication` table; the `create-service` skill removes it when a service publishes nothing, and brings it back when it starts publishing.

## Consequences
- **Good:** no extra component or library; the outbox is part of the framework the services already use; an event published while Kafka is down arrives without a restart.
- **Cost:** delivery is at least once, and a restart with several replicas republishes rows still in flight on other replicas (absorbed by ADR-0004).
  No ordering guarantee per aggregate across retries. No metric or alert on the backlog yet (R1); planned in template 1, so every publishing service gets it:
  gauges for the number of unfinished publications and the age of the oldest one, and a `warning` alert when the oldest is older than a few minutes.

## When to choose differently
- Ordering per aggregate or high throughput matters: a **dedicated outbox library** (option 2), at the cost of one more library and its tables.
- Many services, producers in other languages, or changes made by other systems directly in the database: **Debezium CDC** (option 3),
  with Kafka Connect as part of the platform. It also gives the lowest latency and no polling in the service.

## Evidence
- [`OutboxRedeliveryIntegrationTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/OutboxRedeliveryIntegrationTest.java):
  broker paused, order placed, publication `FAILED`, broker resumed, event delivered and the table empty, with no restart.
- [Transactional outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html).
