# ADR-0004: Idempotent consumers, bounded retries and a dead-letter topic

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2, QD3

## Context and drivers
The outbox (ADR-0003) and Kafka deliver at least once, so a consumer sees duplicates after restarts and rebalances.
A message that can never be processed must not block its partition, and a failure must be visible (QD2, QD3).

## Considered options
1. **Idempotent handler keyed by `eventId`, a few in-place retries, then `<topic>.DLT`**.
2. **Exactly-once processing** with Kafka transactions (read-process-write).
3. **Non-blocking retry topics** (`@RetryableTopic`: `orders.events-retry-1`, `-retry-2`, …, then the DLT).

## Decision
Option 1. Every event carries `UUID eventId`. The consumer stores it under a unique constraint (`source_event_id` in `notifications`)
and skips a duplicate, or uses `insert … on conflict do nothing`. `DefaultErrorHandler` retries three times with a one-second pause,
then `DeadLetterPublishingRecoverer` sends the original record to `orders.events.DLT`; a message that cannot be parsed goes there without retries.
A move to the DLT is logged at WARN.

## Consequences
- **Good:** duplicates are harmless; a poison message stops nothing; the rule is simple enough for an AI agent to repeat in every new handler (QD8).
- **Cost:** a unique key per consumer table; in-place retries hold the partition for a few seconds; nothing replays the DLT yet,
  and there is no alert on DLT traffic.

## When to choose differently
- The side effect is a write to Kafka only (consume, transform, produce): **Kafka transactions** (option 2) give exactly-once inside Kafka.
  They do not cover a database or an e-mail, which is why they are not used here.
- Long outages of a downstream system, where retries must wait minutes without blocking the partition: **retry topics** (option 3).
- Ordering per key matters more than throughput: keep blocking retries (option 1), because retry topics break ordering.

## Evidence
- [`NotificationsFlowIntegrationTest`](../../services/notifications-service/src/test/java/io/github/bsamsonov/notifications/NotificationsFlowIntegrationTest.java): duplicate and DLT cases;
  [`KafkaErrorHandlingConfig`](../../services/notifications-service/src/main/java/io/github/bsamsonov/notifications/notification/adapter/in/kafka/KafkaErrorHandlingConfig.java).
- [Spring for Apache Kafka: error handling and dead-letter publishing](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html).
