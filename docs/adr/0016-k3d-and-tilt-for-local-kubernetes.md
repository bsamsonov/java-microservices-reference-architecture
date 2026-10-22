# ADR-0016: k3d and Tilt for local Kubernetes and the inner dev loop

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD6, QD1
- **Replaces:** D4

## Context and drivers
The Kubernetes mode must run on one laptop (QD6) and a code change must reach the cluster in seconds, not through a manual build-push-deploy.

## Considered options
1. **k3d** (k3s in Docker, with a local registry) and **Tilt** (`spring_service('<name>')` in the `Tiltfile`).
2. **kind** with **Skaffold**.
3. **minikube**, or Docker Desktop Kubernetes, with manual `helm upgrade`.

## Decision
Option 1. `platform/k3d/cluster.yaml` creates a single-node cluster with a registry at `localhost:5050` and ingress on port 8080.
Tilt builds the jar with Maven, the image with the service Dockerfile (ADR-0013), and deploys the shared chart (ADR-0014); `tilt ci` does it once and exits.
Tilt is the developer's loop; delivery from git is Argo CD's job (ADR-0018).

## Consequences
- **Good:** a lightweight cluster that starts in minutes; one `Tiltfile` line per service; the same chart and values as a real deployment.
- **Cost:** k3s differs from upstream Kubernetes in places (no control-plane metrics, so some kube-prometheus-stack rules are off);
  the cluster and the compose stack do not fit in memory at the same time; Tilt is one more tool to install.

## When to choose differently
- CI clusters that must match upstream Kubernetes conformance closely: **kind**.
- A team already on Google's tooling or Cloud Code: **Skaffold**.
- No need for Kubernetes in the inner loop: stay on compose (ADR-0017).

## Evidence
- [`Tiltfile`](../../Tiltfile) and [`platform/README.md`](../../platform/README.md), section "Verified: services in the cluster".
- [k3d](https://k3d.io/) and [Tilt documentation](https://docs.tilt.dev/).
