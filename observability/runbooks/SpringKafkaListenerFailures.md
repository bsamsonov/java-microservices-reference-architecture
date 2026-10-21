# SpringKafkaListenerFailures

**Meaning.** The service's Kafka listener failed to process records from `messaging_source_name` in the last 10 minutes.
**Impact.** After retries the records go to the DLT (`<topic>.DLT`): the event stays unprocessed until it is replayed.
**Check.**
1. Service logs: the WARN about moving a record to the DLT contains the cause.
2. The DLT records themselves: headers carry the exception text.
**Action.** Malformed message: fix the producer. Bug in the code: fix it and replay the records from the DLT.
