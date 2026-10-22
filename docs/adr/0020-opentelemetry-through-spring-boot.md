# ADR-0020: Telemetry through Spring Boot's OpenTelemetry support (Micrometer, OTLP), not the Java agent

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD5, QD6

## Context and drivers
Every request must be traceable from HTTP through the outbox and Kafka to the consumer, with `trace_id` in every log line (QD5).
The telemetry setup must be the same in both modes and visible in the service's configuration (QD6).

## Considered options
1. **Spring Boot native**: Micrometer Observation for metrics and traces, `spring-boot-starter-opentelemetry`, OTLP export; the OTel Logback appender for logs.
2. **OpenTelemetry Java agent** attached to the JVM: zero-code instrumentation of many libraries.
3. **OpenTelemetry Spring Boot starter** from opentelemetry-java-instrumentation: OTel instrumentation configured through `application.yaml`.

## Decision
Option 1. Traces and logs go over OTLP to the address in `MANAGEMENT_OPENTELEMETRY_*`; trace context travels in Kafka headers
(`spring.kafka.template.observation-enabled`). Metrics are scraped from `/actuator/prometheus` in Kubernetes and pushed over OTLP in compose
(`MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=true`). The OTel appender is attached in code (`OpenTelemetryLoggingConfig`).

## Consequences
- **Good:** one dependency set managed by the Boot BOM; configuration in `application.yaml`; no agent in the image; works with native images;
  one model (Observation) for metrics and traces, consistent with Actuator.
- **Cost:** metric and attribute names follow Micrometer, which does not always match OTel semantic conventions
  (example: `jvm_threads_live_threads` when scraped, `jvm_threads_live` over OTLP; queries absorb it). The OTel SDK version follows the Boot BOM
  and usually lags the one the agent is built on by a few minor versions. Libraries without Micrometer instrumentation are not traced automatically.

## When to choose differently
- Instrumenting applications you cannot change, or wanting the widest library coverage with OTel conventions out of the box: the **Java agent**.
- OTel instrumentation without an agent (native image, yaml configuration): the **OTel Spring Boot starter**.
- Never both the agent and Micrometer tracing without configuration: spans are created twice.

## Evidence
- [Spring Boot: observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html) and
  [OpenTelemetry Java agent](https://opentelemetry.io/docs/zero-code/java/agent/).
- [`docs/architecture/README.md`, scenario 7.1](../architecture/README.md#71-order-placed--notification-outbox-kafka-idempotent-consumer): one trace across both services, logs found by `trace_id`.
