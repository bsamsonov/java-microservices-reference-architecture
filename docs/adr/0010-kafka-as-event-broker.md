# ADR-0010: Apache Kafka as the event broker

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2, QD5, QD6
- **Replaces:** D3 in part (the broker); running it through Strimzi is ADR-0015

## Context and drivers
Services integrate through events (ADR-0002). Events must survive consumer downtime, a new consumer should be able to read history,
and events of one aggregate must stay in order (QD2). The broker must run on a laptop and in Kubernetes (QD5, QD6).

## Considered options
1. **Apache Kafka** (KRaft, no ZooKeeper): a partitioned log with retention and replay.
2. **RabbitMQ**: a message broker with queues, routing and acknowledgements per message.
3. **NATS JetStream**: a light server with persistent streams.
4. **Redpanda**: a Kafka-compatible broker in one binary, without the JVM.
5. **Apache Pulsar**: a log with tiered storage and multi-tenancy, more components to run.

## Decision
Option 1. Topic per producing module (`<module>.events`), key = aggregate id, so one aggregate's events stay in one partition and in order.
Dead letters go to `<topic>.DLT` (ADR-0004). Compose runs `apache/kafka-native`; Kubernetes runs Strimzi (KRaft); tests use Testcontainers.

## Consequences
- **Good:** replay and retention for new consumers and for recovery; the most common event backbone in Java teams; first-class support
  in Spring for Apache Kafka and Spring Modulith externalization; a CNCF operator (Strimzi) for Kubernetes.
- **Cost:** more resources and concepts (partitions, consumer groups, offsets) than a queue broker; partition count is a long-lived decision.
  The modes run different versions today: 4.0.0 in compose and tests, 4.3.1 in k3d, client 4.2.1 from the Boot BOM (risk R10); the fix is one pinned 4.2.x or 4.3.x.

## When to choose differently
- Work queues, per-message routing, priorities and no need for replay: **RabbitMQ**.
- A light broker at the edge or many small services with request-reply and streams: **NATS JetStream**.
- The Kafka API with fewer resources and one binary: **Redpanda** (check license terms for the features used).
- Very long retention with tiered storage and many tenants: **Pulsar**.
- In a cloud: a managed Kafka (MSK, Confluent Cloud) instead of Strimzi.

## Evidence
- [Apache Kafka downloads](https://kafka.apache.org/community/downloads/): 4.3.1 released on 2026-06-25.
- [Strimzi releases](https://github.com/strimzi/strimzi-kafka-operator/releases): Strimzi 1.3.0 supports Kafka 4.2.0–4.3.1.
