# OutboxBacklogStale

**Meaning.** An event of service `application` has waited more than 5 minutes in the transactional outbox (`event_publication`) and has not reached Kafka.
**Impact.** The order is saved, but consumers (notifications) learn about it late. Nothing is lost: the row stays until delivery (ADR-0003).
**Check.**
1. "Spring services" dashboard, Outbox row: backlog size and the age of the oldest publication. A growing backlog means every event waits, not one.
2. Is Kafka reachable? Producer errors in the service log (`Logs` row, level WARN/ERROR), broker pods or the `kafka` container.
3. Rows that keep failing: `select listener_id, status, completion_attempts, publication_date from event_publication order by publication_date limit 20;`
   Only `FAILED` rows are resubmitted (every 30 s by default); a row that stays `PROCESSING` waits for a restart of the service.
**Action.** Kafka down: bring it back, the backlog drains by itself within one resubmission interval. One listener keeps failing (high `completion_attempts`):
find the cause through its trace and fix it; do not delete rows by hand, each one is an undelivered event.
