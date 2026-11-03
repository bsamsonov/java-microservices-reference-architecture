# ADR-0039: JVM heap share and container memory limit

- **Status:** Accepted
- **Implemented:** yes
- **Date:** 2026-11-03
- **Drivers:** QD3, QD5
- **Amends:** ADR-0013, the JVM memory flag in the image

## Context and drivers
ADR-0013 starts the JVM with `-XX:MaxRAMPercentage=75`, and the chart gives each service a 512Mi memory limit.
That leaves about 128 MiB outside the heap for metaspace, code cache, thread stacks and direct buffers, which a Spring Boot service
with Kafka, Hibernate and OpenTelemetry already uses at rest. Under a steady 20 orders/s ([quality evidence](../architecture/quality-evidence.md#load))
both `orders-service` pods were `OOMKilled` within ten minutes, and 3.3 % of the requests failed.

## Considered options
1. **Limit 768Mi, heap 60 %**: about 460 MiB heap and 300 MiB for the rest.
2. **Keep 512Mi, heap 50 %**: 256 MiB heap; fits at rest, but leaves the heap small for a load peak.
3. **Fixed `-Xmx` per service**: exact, but every service needs its own number and loses the link to the limit.

## Decision
Option 1. The chart default is `limits.memory: 768Mi` (request unchanged at 384Mi), and every Dockerfile uses `-XX:MaxRAMPercentage=60`.
A service that needs more raises the limit in its `deploy/values.yaml`; the heap follows.

## Consequences
- The same 20 orders/s run had no restarts and no failed requests.
- More memory per pod on a laptop cluster: the limit is a ceiling, not a reservation, so scheduling is not affected.
- Not measured: the heap after a long run; the `SpringHeapHigh` alert covers it.
