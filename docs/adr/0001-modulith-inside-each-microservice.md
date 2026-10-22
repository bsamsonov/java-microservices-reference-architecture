# ADR-0001: Microservices with a modular monolith inside (Spring Modulith)

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD1, QD2
- **Replaces:** D6 (the system-level part; the structure inside a module is ADR-0030)

## Context and drivers
The repository is a template for many services, built by developers working with an AI agent (QD2).
Each service must keep its internal structure without a reviewer policing it (QD2), and a new service must start from a known shape (QD1).
Service boundaries are expensive to move once there is a network, a database and a topic between them;
module boundaries inside one deployable are cheap to move, as long as something enforces them.

## Considered options
1. **Microservices, each one a modular monolith (Spring Modulith)**: services split by business capability; inside, modules by subdomain with checked boundaries.
2. **Fine-grained microservices without internal modules**: one subdomain per service, layered packages inside.
3. **One modular monolith**: a single deployable with Modulith modules, no network between them.

## Decision
Option 1, in two templates. `orders-service` (template 1) is a Modulith application: top-level packages are subdomains (`order`, `audit`),
modules talk through events, and `ModularityTest` (`ApplicationModules.verify()`) fails the build on a forbidden dependency.
`catalog-service` (template 2) is a small CRUD without Modulith for services that have one subdomain and no business rules.
A new subdomain starts as a module; it becomes a service only when it needs its own release cadence, scaling or team.

## Consequences
- **Good:** fewer services to deploy and operate; a module can be extracted later because it already talks through events and owns its schema (ADR-0007).
  Boundaries are enforced by tests, which an AI agent cannot argue with.
- **Cost:** two templates to maintain; Spring Modulith is one more framework to learn; modules of one service still share a JVM, a release and a failure domain.

## When to choose differently
- One team, one release cadence, no independent scaling: **one modular monolith** (option 3) is simpler and keeps the same module discipline.
- A service has one subdomain and no business rules: **no Modulith** (template 2, `catalog-service`).
- Many teams that release independently and own one subdomain each: **fine-grained services** (option 2), with the boundary moved to the network.

## Evidence
- [`ModularityTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/ModularityTest.java) and
  [`HexagonArchitectureTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/HexagonArchitectureTest.java): the boundaries are build checks.
- [Spring Modulith releases](https://github.com/spring-projects/spring-modulith/releases): an actively released Spring project, versioned with Spring Boot.
