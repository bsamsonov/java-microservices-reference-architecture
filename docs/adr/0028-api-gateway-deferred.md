# ADR-0028: API gateway: deferred, an ingress controller is enough

- **Status:** Deferred
- **Date:** 2026-10-22
- **Drivers:** QD6, QD7

## Context and drivers
External traffic enters through the k3d ingress (`*.localhost:8080`). Each service validates tokens itself (ADR-0023), so a gateway is not needed for authentication.
There is no public API, no rate limiting requirement and no external client to version the API for.

## Considered options
1. **Ingress controller only**, routing by host; authentication in the services.
2. **An API gateway** (Spring Cloud Gateway, Kong, Envoy Gateway) for routing, rate limiting, token relay and aggregation.
3. **Kubernetes Gateway API** with an implementation that later grows gateway features.

## Decision
Option 1. Revisit on the conditions below.

## Consequences
- **Good:** one component less; no single point of failure in front of the services; each service's access policy stays next to its code.
- **Cost:** no central rate limiting, request quotas or API key management; each service exposes its own host.

## When to choose differently
- A public API with quotas, API keys or monetisation, or a mobile client that needs aggregation: **an API gateway**.
- New ingress work on Kubernetes: prefer the **Gateway API** over Ingress, so the move to a gateway is a change of implementation, not of resources.

## Evidence
- [Kubernetes Gateway API](https://gateway-api.sigs.k8s.io/).
- [`security/README.md`](../../security/README.md), section "Not covered (on purpose)".
