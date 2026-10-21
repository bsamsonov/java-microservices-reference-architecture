# SpringKafkaConsumerLag

**Meaning.** The service has been more than 1000 records behind topic `topic` for 10 minutes.
**Impact.** Events are processed late; if the lag keeps growing, hours late.
**Check.**
1. "Spring services" dashboard, Kafka row: lag and p95 processing time.
2. Is `SpringKafkaListenerFailures` firing at the same time? Retries slow down consumption.
**Action.** Slow processing: find the cause through the trace. Load growth: add partitions and service replicas.
