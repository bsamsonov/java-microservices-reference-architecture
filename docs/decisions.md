# Decisions: moved to ADRs

The short list D1–D12 was replaced by architecture decision records in [`docs/adr/`](adr/README.md) on 2026-10-22.
This file stays so that older references ("D12" in a commit message) resolve. Do not add entries here.

| Was | Now | Decision |
|---|---|---|
| D1 | [ADR-0014](adr/0014-helm-shared-service-chart.md) | Kubernetes packaging: Helm, one shared chart |
| D2 | [ADR-0002](adr/0002-integrate-services-through-events.md) | Integration and audit through domain events in Kafka |
| D3 | [ADR-0015](adr/0015-operators-for-postgres-and-kafka.md) | Kafka in the cluster through Strimzi (broker choice: ADR-0010) |
| D4 | [ADR-0016](adr/0016-k3d-and-tilt-for-local-kubernetes.md) | k3d and Tilt |
| D5 | [ADR-0012](adr/0012-maven-build.md) | Maven |
| D6 | [ADR-0001](adr/0001-modulith-inside-each-microservice.md) | Modularity: Modulith inside services, two templates (inside a module: ADR-0030) |
| D7 | [ADR-0031](adr/0031-test-pyramid.md) | Test pyramid |
| D8 | [ADR-0032](adr/0032-jpa-for-rich-domain-jdbc-for-crud.md) | JPA in template 1, Spring Data JDBC in template 2 |
| D9 | [ADR-0021](adr/0021-dashboards-as-code.md) | Dashboards as code |
| D10 | [ADR-0022](adr/0022-alerting-prometheus-rules-alertmanager-pyrra.md) | Alerting: Prometheus rules, Alertmanager, Pyrra |
| D11 | [ADR-0023](adr/0023-keycloak-resource-server-network-policy.md) | Security: Keycloak, resource server, NetworkPolicy |
| D12 | [ADR-0003](adr/0003-outbox-on-modulith-event-registry.md) | Outbox delivery |
