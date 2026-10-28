# ADR-0038: Rollout and eviction without failed requests

- **Status:** Accepted
- **Implemented:** yes
- **Date:** 2026-10-28
- **Drivers:** QD3, QD5, QD7

## Context and drivers
The broker outage experiment ([quality evidence](../architecture/quality-evidence.md#broker-outage)) showed that a planned restart of a service in k3d
costs one or two HTTP requests: a `502`, or a `504` after the 30 s ingress timeout. The chart (ADR-0014) had no `preStop` delay and the default rollout strategy,
so the ingress still sent requests to a pod that had already started to shut down. Every service had one replica and no PodDisruptionBudget (risk R6),
so a voluntary eviction (a node drain, a cluster upgrade) took the service down until a new pod was ready.
A redeploy is the most frequent disruption a service sees; it must not fail requests (QD5) or leave events in flight (QD3, ADR-0037).
The laptop budget (QD7) limits how many replicas the platform can run.

## Considered options
1. **Chart defaults for every service:** `preStop` sleep, a grace period that covers Spring's shutdown, `maxSurge: 1` / `maxUnavailable: 0`;
   two replicas and a PodDisruptionBudget only where a service needs them.
2. **The same, with two replicas and a PDB for every service.**
3. **Leave the chart as is** and accept a failed request per redeploy; clients retry.
4. **Ingress-level retries** (Traefik retry middleware) instead of draining the pod.

## Decision
Option 1, in `charts/spring-service`:
- `lifecycle.preStop.sleep` 10 s (native sleep action, no shell in the image): the pod leaves the Service endpoints and the ingress
  before Spring receives `SIGTERM`. Spring Boot's graceful shutdown (on by default) then finishes requests in progress.
- `terminationGracePeriodSeconds: 75`: `preStop` 10 s + web server drain (`spring.lifecycle.timeout-per-shutdown-phase`, 30 s by default)
  + the task executor await of ADR-0037 (20 s) + margin. A normal stop takes a few seconds after `preStop`; the period is a ceiling.
- `RollingUpdate` with `maxSurge: 1`, `maxUnavailable: 0`: a new pod is Ready before an old one stops, also with one replica.
- `podDisruptionBudget.minAvailable: 1`, rendered only when `replicaCount > 1`: with one replica such a PDB would block a drain forever.
- `orders-service` runs 2 replicas (its `deploy/values.yaml`): it takes the public writes. The other services stay at one replica;
  a redeploy is still clean for them, an eviction is not.

## Consequences
- **Good:** a rolling restart and a drain of `orders-service` under load fail no request and leave no event behind (evidence below).
  A new service gets the same behaviour from the chart; it only sets `replicaCount: 2` when it needs to survive an eviction.
- **Cost:** a pod takes at least 10 s longer to stop, so a rollout of N pods takes about N × 10 s longer. A second `orders-service` replica costs ~400 MB on the laptop.
  Two replicas run the outbox jobs (`OutboxResubmission`, the staleness monitor) twice; a row may be sent by both, which consumers absorb by `eventId` (ADR-0004).
- A crash (`kill -9`, OOM, a lost node) is not covered: requests to the dead pod fail until readiness drops it, fewer with two replicas.

## When to choose differently
- Production with several nodes: two or more replicas and a PDB for every service (option 2), plus topology spread across nodes.
- An ingress or mesh that removes endpoints before the pod is signalled (for example with readiness gates): the `preStop` sleep can shrink.
- Long requests or listeners: raise `timeout-per-shutdown-phase` and the grace period together.
- Retries at the ingress (option 4) only for idempotent requests: `POST /orders` is not idempotent until it accepts an `Idempotency-Key`.

## Evidence
- [Quality evidence: rollout and eviction](../architecture/quality-evidence.md#rollout-and-eviction): rolling restarts and an eviction of both `orders-service` pods under load.
- [Kubernetes: container lifecycle hooks, sleep action](https://kubernetes.io/docs/concepts/containers/container-lifecycle-hooks/).
- [Spring Boot reference: graceful shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html).
