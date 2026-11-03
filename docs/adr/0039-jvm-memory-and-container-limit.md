# ADR-0039: JVM heap as a share of the container limit, both set in the chart

- **Status:** Accepted
- **Implemented:** yes
- **Date:** 2026-11-03
- **Drivers:** QD3, QD5
- **Amends:** ADR-0013, the JVM memory flag moves from the image to the chart

## Context and drivers
ADR-0013 starts the JVM with `-XX:MaxRAMPercentage=75` in every Dockerfile, and the chart gives each service a 512Mi memory limit.
The JVM treats the container limit as the machine memory, so the heap could take 384 MiB, leaving about 128 MiB for everything else:
metaspace, code cache, thread stacks and direct buffers of a Spring Boot service with Kafka, Hibernate and OpenTelemetry.
Under a steady 20 orders/s ([quality evidence](../architecture/quality-evidence.md#load)) both `orders-service` pods were `OOMKilled`
by the kernel within ten minutes (the heap itself was not full), and 3.3 % of the requests failed.

The heap share only makes sense relative to the limit, but the two lived in different files: the share in three copies of the Dockerfile,
the limit in the chart. Changing one without the other is how the limit was outgrown.

## Considered options
1. **Limit 768Mi, heap 60 %, both in the chart values**: about 460 MiB heap and 300 MiB for the rest; one file to change.
2. **The same numbers, the heap share kept in the Dockerfiles**: works, but every change touches all services and drifts from the limit.
3. **Keep 512Mi, heap 50 %**: 256 MiB heap; fits at rest, but leaves the heap small for a load peak.
4. **Fixed `-Xmx` per service**: exact, but a second number per service that does not follow the limit.

## Decision
Option 1. `charts/spring-service/values.yaml` holds `resources` (request 384Mi, limit 768Mi) and, next to it,
`jvmOptions: "-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"`, rendered as `JAVA_TOOL_OPTIONS` (it replaces the image default).
The Dockerfiles keep only `-XX:+ExitOnOutOfMemoryError`. A service that needs more memory raises `limits.memory` in its `deploy/values.yaml`; the heap follows.

## Consequences
- The same 20 orders/s run had no restarts and no failed requests.
- **60 % is a starting point, not a measurement.** Only "no OOM in 10 minutes at 20 orders/s" is shown. The memory outside the heap
  (Native Memory Tracking, `container_memory_working_set_bytes` against the limit) is not measured yet; `SpringHeapHigh` covers the heap only.
- **Burstable on purpose, for the laptop.** The scheduler reserves the request (384Mi), but a pod may use up to the limit (768Mi).
  When many pods grow at once, the node runs out and the kubelet evicts or the kernel kills Burstable pods first.
  In production set `requests.memory` equal to `limits.memory` (Guaranteed for memory).
- Compose has no memory limits, so there the JVM uses its default heap (25 % of the host memory).
- The heap no longer shows in the Dockerfile; the comment there points to the chart.
