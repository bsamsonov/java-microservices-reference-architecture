# ADR-0023: Security baseline: Keycloak, token validation in every service, default-deny NetworkPolicy

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2, QD4
- **Replaces:** D11

## Context and drivers
Every endpoint must require a valid token with the right permission (QD4), the rule must be easy for an agent to follow and checked by tests (QD2),
and a compromised pod must not reach everything in the cluster. Three levels were considered: A (decision only), B (working minimum), C (full set).

## Considered options
1. **Level B**: Keycloak (OIDC) issues tokens; every service is an OAuth2 Resource Server; NetworkPolicy closes the `apps` namespace by default.
2. **Token validation in an API gateway** only, services trust the internal network.
3. **Level C**: B plus service-to-service authentication or mTLS, a gateway, Kafka ACLs, Vault, image signing.

## Decision
Option 1. The realm is code (`security/keycloak/realm-blueprints.json`, one file for both modes). Permissions are realm roles `<service>:<action>`,
granted through groups and put into the `permissions` claim. Each service checks signature, issuer, audience `blueprints-api` and expiry
(`application.yaml`) and declares access per endpoint in `SecurityConfig`. Keys are fetched inside the network, the issuer is Keycloak's public address.
In k3d the chart gives each service a NetworkPolicy: ingress from the ingress controller and Prometheus, egress to `infra`, OTel and DNS.
Web-slice tests check 401 and 403 for every rule; tests need no Keycloak (`TestTokens`).
A new endpoint means four edits: realm role and group, `SecurityConfig`, the permission and responses in `openapi.yaml`, the 401 and 403 tests.

## Consequences
- **Good:** standard Spring Security with almost no code; a service's access policy fits in one method; no single point of failure in front of the services.
- **Cost:** gaps accepted on purpose and listed with revisit conditions in `security/README.md`: ownership checks (planned next: `customerId` from the token, 404 for another customer's order), service-to-service auth, Kafka ACLs,
  encrypted secrets (ADR-0019), Keycloak in dev mode with the password grant (risk R8).

## When to choose differently
- The company already has an identity provider (Entra ID, Okta, Cognito, Auth0): use it; the services only change the issuer and the claim mapping.
- A lighter self-hosted provider: Zitadel or Authentik.
- Synchronous calls between services or a third party in the path: add **service-to-service auth** (client credentials or mTLS), see ADR-0027.
- L7 policies or mTLS everywhere: Cilium or a service mesh (ADR-0027).

## Evidence
- [`security/README.md`](../../security/README.md): how it works, the threat model and the gaps.
- [Spring Security: OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
