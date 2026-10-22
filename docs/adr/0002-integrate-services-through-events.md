# ADR-0002: Integrate services through domain events, not synchronous calls

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2, QD3
- **Replaces:** D2 (audit through domain events in Kafka)

## Context and drivers
`orders-service` must accept an order even when the services that react to it are down or slow (QD2).
Audit and notifications only react to what happened; they do not need to answer the caller.
The flow must still be traceable end to end (QD3).

## Considered options
1. **Domain events through Kafka**: the producer publishes a fact; consumers react on their own time.
2. **Synchronous HTTP calls** (`RestClient`, HTTP interfaces) from the producer to each consumer.
3. **Shared database tables** read by several services.

## Decision
Option 1. Inside a service, modules publish events with `ApplicationEventPublisher` and receive them with `@ApplicationModuleListener` after commit.
Between services, the same events are externalized to Kafka (`@Externalized`, topic `<module>.events`, key = aggregate id) through the outbox (ADR-0003).
Audit is a consumer of these events, with an `audit_log` table on its side, not a JPA audit trail (no Envers).
Trace context travels in Kafka headers, so one trace covers HTTP, the outbox and the consumer.

## Consequences
- **Good:** the producer does not depend on consumers being up; a new consumer needs no change in the producer; the events are visible in Kafka.
- **Cost:** eventual consistency; at-least-once delivery, so every consumer must be idempotent (ADR-0004); the event becomes a contract (ADR-0005).
  The template has no variant for a synchronous call to another service yet (risk R4).

## When to choose differently
- The caller needs the answer to continue (price, stock check, authorization): a **synchronous call** with timeouts, retries and a circuit breaker (R4).
- Strict consistency across two services in one step: keep both in **one service** (two modules) rather than a distributed transaction.
- A query across services for a screen: an API composition or a read model fed by events, not a shared table.

## Evidence
- [`OrdersFlowIntegrationTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/OrdersFlowIntegrationTest.java): HTTP → database → outbox → Kafka.
- [Spring Modulith: working with application events](https://docs.spring.io/spring-modulith/reference/events.html).
