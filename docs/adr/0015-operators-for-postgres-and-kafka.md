# ADR-0015: Postgres and Kafka in Kubernetes through operators (CloudNativePG, Strimzi)

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD5, QD6
- **Replaces:** D3 (Strimzi; CloudNativePG was added with the same reasoning)

## Context and drivers
The Kubernetes mode shows how a platform runs stateful infrastructure; no cloud account is allowed (constraint), so managed services are not an option here.
Databases and topics for a new service should be declared as resources, next to the service, not created by hand (QD1, QD5).

## Considered options
1. **Operators**: CloudNativePG for Postgres, Strimzi (KRaft) for Kafka; `Database` and `KafkaTopic` as custom resources.
2. **Plain StatefulSets or generic Helm charts** for Postgres and Kafka.
3. **Managed services** (RDS or Cloud SQL, MSK or Confluent Cloud).

## Decision
Option 1. `platform/manifests/postgres.yaml` (one CNPG `Cluster`) and `kafka.yaml` (Strimzi, KRaft, Kafka 4.3.1 on Strimzi 1.3.0).
A service's database is a CNPG `Database` in `platform/manifests/databases/`; its topics are `KafkaTopic` objects rendered by the service chart (ADR-0014).
Both operators expose metrics to Prometheus (PodMonitor).

## Consequences
- **Good:** day-2 operations (failover, upgrades, users, topics) are declarative; the same approach a production platform on Kubernetes would use; both are CNCF projects.
- **Cost:** two operators and their CRDs to keep current (Strimzi 1.x dropped `v1beta2`); single instances and ephemeral Kafka storage in k3d (risk R6);
  the CNPG image is not pinned, so k3d runs a different PostgreSQL major than compose (risk R10).

## When to choose differently
- Production in a cloud: **managed Postgres and Kafka**; the services do not change, only the addresses and secrets.
- A team that needs commercial support for Kafka on Kubernetes: Confluent for Kubernetes.
- A throwaway demo cluster: a plain chart is enough, but it shows less.

## Evidence
- [Strimzi releases](https://github.com/strimzi/strimzi-kafka-operator/releases): 1.3.0 supports Kafka 4.2.0–4.3.1;
  [CloudNativePG at CNCF](https://www.cncf.io/projects/cloudnativepg/) (Sandbox since 2025-01).
- [`platform/README.md`](../../platform/README.md), section "Verified: the platform".
