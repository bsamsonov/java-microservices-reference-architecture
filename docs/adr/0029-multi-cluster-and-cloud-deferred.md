# ADR-0029: Multi-cluster, high availability and cloud infrastructure as code: deferred

- **Status:** Deferred
- **Date:** 2026-10-22
- **Drivers:** QD6

## Context and drivers
The platform runs on one laptop with no cloud account (constraint, QD6). Single replicas, no PodDisruptionBudget, no autoscaling and ephemeral Kafka storage
are recorded as risk R6: the setup is not representative of production availability, and that is accepted.

## Considered options
1. **One local cluster**; production concerns described, not built.
2. **Several clusters** (per environment or region) managed by Argo CD from one place.
3. **Cloud infrastructure in Terraform or OpenTofu** (managed Kubernetes, databases, Kafka).

## Decision
Option 1. Production-shaped options already in the design: managed Postgres and Kafka (ADR-0015), Argo CD that can manage several clusters (ADR-0018),
chart options for replicas and disruption budgets when needed.

## Consequences
- **Good:** the repository runs anywhere Docker runs, with no bill.
- **Cost:** no evidence for failover, scaling or multi-region behaviour; reviewers must take those parts from the ADRs, not from a running system.

## When to choose differently
- A real deployment target exists: **Terraform or OpenTofu** for the cloud resources, managed data services, two or more replicas with a disruption budget.
- Several environments or regions: **multi-cluster** through Argo CD `ApplicationSet`.

## Evidence
- [Architecture overview, risk R6](../architecture/README.md#10-risks-and-technical-debt).
- [ADR-0015](0015-operators-for-postgres-and-kafka.md) and [ADR-0018](0018-gitops-with-argo-cd.md): the alternatives they name.
