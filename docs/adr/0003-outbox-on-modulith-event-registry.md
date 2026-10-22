# ADR-0003: Transactional outbox on the Spring Modulith event publication registry

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2, QD6
- **Replaces:** D12 (the delivery details stay there until part 2 of the ADR migration)

## Context and drivers
An order and its `OrderPlaced` event must be stored atomically: no order without an event, no event without an order (QD2).
Writing to Postgres and Kafka in one transaction is not possible, so the event is stored in the same database transaction and sent after commit.
The solution must run on a laptop without extra infrastructure (QD6). Risk R1 records what remains open.

## Considered options
1. **Spring Modulith event publication registry** (`event_publication` table) with externalization to Kafka, scheduled resubmission and delete on completion.
2. **A dedicated outbox library** (Modulith externalization mode `outbox` with the Namastack or JobRunr starter): own polling, ordering and retries.
3. **Debezium CDC** on an outbox table: Kafka Connect reads the Postgres WAL and publishes the rows.

## Decision
Option 1. The event row is written in the business transaction and published after commit. A failed publication stays in the table;
`OutboxResubmission` resubmits it every 30 s, and several replicas do not resubmit the same row twice (conditional update `FAILED` → `RESUBMITTED`).
Completed rows are deleted (`completion-mode: delete`), so the table size is the outbox lag. A crash in the middle of a publication
is covered by `republish-outstanding-events-on-restart`. The details and why the staleness monitor is off are in D12.

## Consequences
- **Good:** no extra component or library; the outbox is part of the framework the services already use; an event published while Kafka is down arrives without a restart.
- **Cost:** delivery is at least once, and a restart with several replicas republishes rows still in flight on other replicas (absorbed by ADR-0004).
  No ordering guarantee per aggregate across retries. No metric or alert on the backlog yet (R1).

## When to choose differently
- Ordering per aggregate or high throughput matters: a **dedicated outbox library** (option 2), at the cost of one more library and its tables.
- Many services, producers in other languages, or changes made by other systems directly in the database: **Debezium CDC** (option 3),
  with Kafka Connect as part of the platform. It also gives the lowest latency and no polling in the service.

## Evidence
- [`OutboxRedeliveryIntegrationTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/OutboxRedeliveryIntegrationTest.java):
  broker paused, order placed, publication `FAILED`, broker resumed, event delivered and the table empty, with no restart.
- [Transactional outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html).
