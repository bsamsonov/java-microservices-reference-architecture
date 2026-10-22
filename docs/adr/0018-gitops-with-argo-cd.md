# ADR-0018: GitOps delivery with Argo CD

- **Status:** Accepted
- **Implemented:** no, see the [Roadmap](../../README.md#roadmap-what-is-deliberately-not-done)
- **Date:** 2026-10-22
- **Drivers:** QD6
- **Risks:** closes R7 when implemented

## Context and drivers
Today Kubernetes deployments are driven from the laptop through Tilt (risk R7). A change merged in git should reach the cluster without a person running commands,
and the cluster state should be readable from git. Teams and reviewers should see what is deployed and whether it is in sync.

## Considered options
1. **Argo CD**: an `Application` per platform part and per service (app-of-apps or `ApplicationSet`), with a UI.
2. **Flux**: GitOps Toolkit controllers, no UI server of its own, SOPS decryption built in.
3. **Push from CI** (`helm upgrade` in a pipeline).

## Decision
Option 1 (owner's decision, 2026-10-22, replacing Flux chosen on 2026-10-21). Each service is an `Application` of the shared chart with its values;
environments `dev` and `prod` are different values. CI builds the image and updates its tag in git; Argo CD syncs; a PostSync hook runs a smoke check.
Tilt stays for the developer's inner loop.

## Consequences
- **Good:** the most widely used GitOps tool, with a UI that shows sync and health per service; drift is visible and corrected.
- **Cost:** a heavier footprint than Flux (API server, repo server, Redis); no built-in SOPS, so encrypted secrets need a plugin or another tool (ADR-0019);
  image tag updates need either a CI commit or Argo CD Image Updater.

## When to choose differently
- No UI wanted, minimum components, SOPS out of the box, or a platform that ships Flux already (AKS, EKS Anywhere, GitLab): **Flux**.
- A single small cluster and a team that deploys rarely: **push from CI** is simpler, at the cost of no drift detection.

## Evidence
- [Argo CD at CNCF](https://www.cncf.io/projects/argo/) and [Flux at CNCF](https://www.cncf.io/projects/flux/): both Graduated.
- [Argo CD: secret management](https://argo-cd.readthedocs.io/en/stable/operator-manual/secret-management/): Argo CD leaves secret decryption to external tools.
