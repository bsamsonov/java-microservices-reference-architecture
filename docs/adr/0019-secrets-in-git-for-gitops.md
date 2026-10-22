# ADR-0019: Secrets for GitOps: SOPS with age, decrypted for Argo CD

- **Status:** Proposed (needs the owner's decision; implemented together with ADR-0018)
- **Date:** 2026-10-22
- **Drivers:** QD7, QD5
- **Risks:** part of R8 (secrets are not encrypted)

## Context and drivers
Database, Keycloak and Grafana credentials are plain Kubernetes Secrets today; compose uses local demo values.
With GitOps (ADR-0018) everything the cluster needs comes from git, so secrets must be stored there encrypted, or fetched from a vault.
The k3d cluster is recreated often (`make up` from scratch), so anything bound to one cluster's key is lost with it.
Argo CD has no built-in decryption.

## Considered options
1. **SOPS with age**: files encrypted in git; Argo CD decrypts them through KSOPS (Kustomize plugin) or helm-secrets in its repo server.
2. **Sealed Secrets**: a controller in the cluster decrypts `SealedSecret` objects encrypted with the cluster's public key; Argo CD needs nothing extra.
3. **External Secrets Operator** with a vault (OpenBao or Vault, a cloud secret manager): git holds only references.

## Decision (proposed)
Option 1. The age key lives outside the cluster (developer machine, CI secret) and is loaded into a new cluster as one Secret, so recreating k3d costs nothing.
Encrypted files can be read outside the cluster for review and recovery. The same files can feed compose later if needed.
Compose keeps its local demo values.

## Consequences
- **Good:** one tool for all secrets, independent of the cluster; it also works with Flux unchanged if the GitOps choice is revisited.
- **Cost:** Argo CD repo server needs a plugin (KSOPS or helm-secrets) configured in its Helm values; key rotation and key distribution are manual.

## When to choose differently
- One long-lived cluster, minimum tooling, no wish to touch Argo CD's repo server: **Sealed Secrets** (back up the sealing key).
- Secrets already live in a vault or a cloud secret manager, rotation is done there, or non-Kubernetes workloads share them: **External Secrets Operator**.
- GitOps through Flux: SOPS is built in, no plugin needed.

## Evidence
- [Argo CD: secret management](https://argo-cd.readthedocs.io/en/stable/operator-manual/secret-management/).
- [Flux: Mozilla SOPS guide](https://fluxcd.io/flux/guides/mozilla-sops/) (the built-in alternative).
