# ADR-0027: Service mesh and service-to-service mTLS: deferred

- **Status:** Deferred
- **Date:** 2026-10-22
- **Drivers:** QD6, QD7

## Context and drivers
Services talk to each other only through Kafka today (ADR-0002); there is no synchronous call between services (risk R4).
NetworkPolicy already limits what a pod can reach (ADR-0023). A mesh adds mTLS, L7 policies, retries and traffic metrics, at the cost of
control-plane components, per-node or per-pod proxies and memory on a laptop (QD6).

## Considered options
1. **No mesh now**; NetworkPolicy for L3/L4 isolation; OAuth2 tokens for users.
2. **Istio ambient mode** (no sidecars, per-node proxies).
3. **Linkerd** (lightweight sidecars).
4. **Cilium** network policies with L7 rules (no mesh).

## Decision
Option 1. Revisit when any of the conditions below becomes true.

## Consequences
- **Good:** nothing to run or upgrade; traces and metrics already come from the services (ADR-0020).
- **Cost:** traffic inside the cluster is not encrypted; a service is not authenticated to another service; no L7 policy.

## When to choose differently
- Synchronous calls between services appear, or a third party joins the network: **mTLS**, through a mesh (Istio ambient, Linkerd) or client credentials (ADR-0023).
- Compliance requires encryption in transit inside the cluster: **a mesh**.
- Only L7 network rules are needed: **Cilium**, without a mesh.

## Evidence
- [Istio ambient mode overview](https://istio.io/latest/docs/ambient/overview/).
- [Kubernetes NetworkPolicy](https://kubernetes.io/docs/concepts/services-networking/network-policies/).
