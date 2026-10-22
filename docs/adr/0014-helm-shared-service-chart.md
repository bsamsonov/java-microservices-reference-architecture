# ADR-0014: One shared Helm chart for every Spring service

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD1, QD6
- **Replaces:** D1

## Context and drivers
Every service needs the same Kubernetes objects: Deployment with probes, Service, Ingress, ServiceMonitor, NetworkPolicy and its Kafka topics.
A new service must get them without writing manifests (QD1), and the values must carry only what differs: image, environment, secrets, topics.

## Considered options
1. **Helm, one shared chart** (`charts/spring-service`) and a `deploy/values.yaml` per service.
2. **Kustomize**: a base and per-service overlays (patches).
3. **Plain manifests per service**, copied from the template.

## Decision
Option 1. The chart renders `deployment`, `service`, `ingress`, `servicemonitor`, `networkpolicy` and `kafkatopics` (Strimzi `KafkaTopic`);
the service's `deploy/values.yaml` sets the image, `env`, `secretEnv`, resources and `kafka.topics`. Tilt renders the chart with these values (ADR-0016);
Argo CD will deploy the same chart (ADR-0018). Platform components come from their upstream Helm charts (`platform/Makefile`). The local tool is Helm 4.

## Consequences
- **Good:** a new service is one values file; a fix in the chart reaches every service; Helm is the packaging most Java teams and vendors use.
- **Cost:** Go templates are harder to read than YAML patches; a chart change affects all services at once and has no tests yet
  (`helm lint` and `kubeconform` are planned in CI). Helm 3 is at the end of its support, so the README must name Helm 4.

## When to choose differently
- Only environment overlays of plain manifests, no templating: **Kustomize**.
- Services differ so much that the chart grows a flag for everything: split the chart, or give the odd service its own manifests.

## Evidence
- [`charts/spring-service`](../../charts/spring-service/values.yaml) and [`services/orders-service/deploy/values.yaml`](../../services/orders-service/deploy/values.yaml).
- [Helm 4 released](https://helm.sh/blog/helm-4-released/).
