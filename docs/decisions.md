# Decisions (ADR-lite)

Each decision records the choice, why it was made and what it costs. Prior art reviewed before D1–D7: open-source Spring microservice
reference projects (among them the eazybytes microservices course and the HMCTS Java services).

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | Kubernetes packaging | **Helm** (one shared chart + per-service values) | the most common choice in Java teams; good prior art |
| D2 | Audit | **Domain events → Kafka** (+ an `audit_log` table at the consumer) | natural for microservices, visible in Kafka; no Envers |
| D3 | Kafka in the cluster | **Strimzi** (KRaft) | production approach through an operator |
| D4 | Local cluster and dev loop | **k3d + Tilt** | lightweight cluster, fast rebuild loop |
| D5 | Build | **Maven** (wrapper) | used by most reference projects and Java teams |
| D6 | Modularity inside a service | **Two templates**: Spring Modulith + domain model + light hexagon; small CRUD service | see below |
| D7 | Test pyramid | **Unit → slices → module → integration → architecture** | see below |
| D8 | Persistence | **JPA** in template 1, **Spring Data JDBC** in template 2 (CRUD) | JPA is the standard for a rich domain; JDBC is simpler and more predictable for CRUD |
| D9 | Dashboards | **JSON in `observability/grafana/dashboards/`**, one set for compose and k3d | a new service appears on the dashboard without edits |
| D10 | Alerting | **Prometheus rules + Alertmanager**, SLOs through Pyrra (`pyrra generate`) | short rules, `promtool test` and `pint`, independent of Grafana |
| D11 | Security | **Keycloak (OIDC) + every service is an OAuth2 Resource Server**, permissions in the `permissions` claim, NetworkPolicy in Kubernetes | the standard Spring stack; a service's access policy fits in one method; no gateway, no mesh |

To revoke a decision, do not delete its row: mark it "revoked (date, reason)".

### D6. Modularity and architecture inside a service: two templates
Two templates: (1) `orders-service`: Spring Modulith + domain model + light hexagon (the reference, built first);
(2) `catalog-service`: a simplified template for one small microservice (no Modulith, a CRUD module without a separate domain model).

**Modules by subdomain (bounded context) come first; the hexagon lives inside a module, in a light form.**
- Top-level packages are subdomains (`orders`, `audit`, …), not layers. This is Spring Modulith:
  a module has a public API (top-level package + `@NamedInterface`), everything else is internal.
  Modules talk through an API call or through events (`ApplicationEventPublisher` → outbox → Kafka).
- Inside a module, ports and adapters (hexagonal) in a light form:
  `domain` (no Spring/JPA), `application` (use cases, inbound ports), `adapter.in.web`, `adapter.out.persistence`,
  `adapter.out.messaging`. Separate JPA entities and mapping to the domain only where the domain is
  non-trivial; CRUD modules may use the simplified variant without a separate domain model.
- The boundaries are enforced by tests, not by review: `ApplicationModules.of(...).verify()` (Spring Modulith)
  + ArchUnit hexagon rules (`HexagonArchitectureTest`) + every module owns its
  tables (its own schema, Flyway migrations per module); reading another module's tables is forbidden.
- Contracts are files: `api/openapi.yaml` (API-first, generated interfaces),
  event schemas, migrations. A contract change is visible in the diff.

Why not "full hexagon everywhere": it doubles the classes and the mapping in a simple CRUD, AI agents multiply
boilerplate, and the real isolation comes from the module boundary, not from a layer.

### D7. Test pyramid
Deferred: mutation testing (PIT), to be added later, starting with the domain module, in CI.

1. Unit: domain and use cases, no Spring, fast.
2. Slices: `@WebMvcTest` (controller + validation + errors per OpenAPI), `@DataJpaTest` on Testcontainers Postgres.
3. Module: `@ApplicationModuleTest` (Spring Modulith): the module starts alone, published events are checked (`Scenario`).
4. Integration: `@SpringBootTest` + Testcontainers (Postgres, Kafka): outbox → Kafka → consumer, idempotency.
5. Architecture: Modulith `verify()` + ArchUnit.
6. Contract: API responses checked against `openapi.yaml`, event schemas; later Pact or Spring Cloud Contract between services.
7. Smoke in the cluster: after deploying through Tilt/Helm, health, one end-to-end request, the trace is visible in Tempo.

### D8. Persistence: JPA in the reference service, Spring Data JDBC in template 2
- **Template 1** (`orders-service`, `notifications-service`): Spring Data JPA (Hibernate), `ddl-auto: validate`, `open-in-view: false`.
  The domain model is separate from the JPA entities, mapping lives in `adapter.out.persistence`. Individual queries that need SQL
  (`insert … on conflict do nothing`) use `JdbcClient`.
- **Template 2** (`catalog-service`, CRUD): Spring Data JDBC. An aggregate is a table row (or a root with nested collections);
  no session, lazy loading or dirty checking: a change is stored only by an explicit `save`, so behaviour is predictable.
  Optimistic locking through `@Version` (`Long`: `null` means a new row). Test slice: `@DataJdbcTest`.
- Cost: the repository has two persistence stacks, and the templates have different test slices. JDBC has no `ddl-auto: validate`,
  so a repository test that writes and reads every column checks the mapping against the schema.
- Why: a CRUD service without business rules does not need Hibernate's features (first-level cache, cascades, lazy loading),
  while their side effects are a frequent source of bugs, including for AI agents. The reference service with a rich domain stays on JPA:
  it is the most widespread stack.

### D9. Dashboards and alerts as code: one set of files for both modes (the dashboard part is in force)
- Dashboards are JSON in `observability/grafana/dashboards/`. Compose mounts them into `grafana/otel-lgtm`, k3d loads them through the Grafana sidecar
  of kube-prometheus-stack (`make -C platform dashboards`). The data source uids (`prometheus`, `loki`, `tempo`) are the same in both modes.
- The dashboard is shared by every Spring service and filtered by the `application` label (set through `management.metrics.tags`):
  a new service appears on the dashboard and under the alerts without edits.
- For the same queries to work with OTLP push (compose) and scraping (Kubernetes), the service sends OTLP metrics
  in seconds (`management.otlp.metrics.export.base-time-unit: seconds`) and publishes histogram buckets
  for `http.server.requests` and `spring.kafka.listener` (needed for p95).
- The original alerting part (Grafana-managed rules) **is replaced by D10**.

### D10. Alerting: rules in Prometheus, notifications through Alertmanager, SLOs through Pyrra
Replaces the alerting part of D9. The options were compared and tried on a separate prototype; the conclusions are recorded here.
- **Where rules live.** Plain Prometheus rule files in `observability/prometheus/rules/`. Compose mounts them into the Prometheus
  of `otel-lgtm`, k3d wraps them into `PrometheusRule` (`platform/alerting/render-rules.sh`). One file for both modes, as in D9.
- **Symptoms through SLOs.** `observability/slo/` (Pyrra format): 99.5 % availability and ≤ 500 ms latency for 99 % of requests, over 4 weeks,
  grouped by `application`, so a new service falls under the SLOs without edits. Multi-window burn-rate alerts
  are generated by `pyrra generate` into `observability/prometheus/generated/`; Pyrra does not run as a service.
- **Levels.** `critical` (SLOs only) → pager and chat; `warning` (causes: Kafka, DB pool, heap) → chat; `info` → nowhere.
  Labels `severity` and `team` are mandatory, the runbook is `observability/runbooks/<AlertName>.md`.
- **Notifications.** Alertmanager: a separate container in compose, the kube-prometheus-stack one in k3d; one config,
  `observability/alertmanager/`. Receivers are empty for now. Grafana shows alerts and silences through the Alertmanager data source.
- **Checks.** `observability/check-alerting.sh`: generation, `promtool check` and `test`, `amtool`, `pint`, runbook presence.
- Why not Grafana-managed: a Prometheus rule takes 5–10 lines instead of 25–30, it has unit tests (`promtool`)
  and convention checks in CI (`pint`), alerts do not depend on Grafana and its database, kube-prometheus-stack already ships
  cluster rules (kubernetes-mixin), and the files carry over to VictoriaMetrics, Thanos or Mimir.
  Grafana-managed wins when alerts are built on logs or SQL, or when the project moves to Grafana Cloud. Neither applies here.
- Cost: compose gets an Alertmanager container and its own copy of the Prometheus config for `otel-lgtm`
  (to be compared on every image upgrade); services need `management.metrics.distribution.slo.http.server.requests: 500ms`,
  because without it there is no exact `le="0.5"` bucket for the latency SLO.
- Prometheus Operator is neither needed nor possible in compose (it works through the Kubernetes API). The `spec` of a `PrometheusRule`
  is the same as a rule file, so the file is the source of truth.

### D11. Security: Keycloak, token validation in every service, NetworkPolicy (level B)
How it works and how to use it: `security/README.md`. Three levels were considered: A (decision only, no code), B (the working minimum, chosen),
C (the full set: service-to-service authentication, gateway, Kafka ACLs, Vault, image signing; deferred).
- **User sign-in.** Keycloak 26 (realm `blueprints` as code: `security/keycloak/realm-blueprints.json`, one file for compose and k3d).
  Permissions are realm roles named `<service>:<action>` (`orders:write`), granted through groups; a mapper puts them into the `permissions` claim.
- **Validation in the service.** OAuth2 Resource Server: signature, issuer, audience `blueprints-api` and expiry are configured in `application.yaml`
  (`authorities-claim-name: permissions`, `authority-prefix: ""`); access rules live in `SecurityConfig` in the root package.
  Keys are fetched by `jwk-set-uri` from inside the network, while the issuer is Keycloak's public address (`KC_HOSTNAME`), so a token obtained outside is accepted inside.
- **Network (k3d).** Namespace `apps` is closed by default; the chart gives each service a NetworkPolicy: ingress from the ingress controller and Prometheus, egress to `infra`, OTel and DNS.
- **Tests.** Web slice with `@Import(SecurityConfig.class)`: 401 and 403 for every rule, plus a check of the `permissions` claim mapping.
  Test requests use `TestTokens.token("<permission>")`; tests need no Keycloak.
- Why: Keycloak is the most widespread self-hosted OIDC provider; Resource Server is standard Spring Security with almost no code.
  Alternatives: Authentik, Zitadel, Ory (less common in Java projects); managed providers (Auth0, Cognito, Entra ID) cannot run locally;
  validating tokens in a gateway instead of the services creates a single point of failure and trusts the internal network.
- **Not included (level C, later or as a separate project):** service-to-service authentication and mTLS, API gateway, Kafka SASL/ACL,
  resource ownership checks, encrypted secrets in git (SOPS + age, together with GitOps), Vault, image signing. The list with revisit conditions is in `security/README.md`.
