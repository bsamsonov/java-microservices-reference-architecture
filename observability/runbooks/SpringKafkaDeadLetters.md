# SpringKafkaDeadLetters

**Meaning.** Service `application` published records from topic `topic` to `<topic>.DLT` in the last 10 minutes: after the retries (or at once, for an invalid record) it gave up on them.
**Impact.** These events are not processed (for notifications-service: no notification for those orders) until someone replays them. The consumer itself goes on.
**Check.**
1. "Spring services" dashboard, Outbox and dead letters row: how many records and the `exception` behind them. `SpringKafkaListenerFailures` usually fires at the same time.
2. Logs row: the WARN `Record sent to DLT` gives the key, the offset and the reason; its trace shows the failed attempts.
3. The record itself with the cause in its headers (`kafka_dlt-exception-cause-fqcn`, `kafka_dlt-exception-message`, `kafka_dlt-original-offset`; the stack trace header is long):
   compose: `docker run --rm --network blueprints_default apache/kafka:4.3.1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 --topic orders.events.DLT --from-beginning --formatter-property print.headers=true --formatter-property print.key=true --timeout-ms 10000`;
   k3d: `kubectl -n infra exec blueprints-dual-0 -- bin/kafka-console-consumer.sh --bootstrap-server localhost:9092` with the same options.
**Action.** An invalid record (`InvalidMessageException`): the producer broke the contract; fix the producer or the consumer's mapping. A transient cause
that outlasted the retries (database down): fix it, then replay the records from the DLT to the source topic; the consumer is idempotent by `eventId`, so a replay is safe.
There is no replay tool in the template yet.
