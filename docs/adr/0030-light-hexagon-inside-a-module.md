# ADR-0030: Inside a module: a light hexagon (domain, application, adapters)

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD2
- **Replaces:** D6 (the structure inside a module; the system level is ADR-0001)
- **Level:** service; details in the service READMEs and `AGENTS.md`

## Context and drivers
Business rules must be testable without Spring and must not depend on the database or the web layer (QD2).
At the same time a full hexagon everywhere doubles classes and mapping in simple modules, and an AI agent multiplies that boilerplate (QD2).

## Considered options
1. **Light hexagon per module**: `domain` (no Spring or JPA), `application` (use cases, ports), `adapter.in.web`, `adapter.out.persistence`; CRUD modules may skip the domain model.
2. **Full hexagon everywhere**: separate models and mappers in every module.
3. **Layered packages** (controller, service, repository) without a separate domain.

## Decision
Option 1. `HexagonArchitectureTest` enforces it: `domain` does not depend on Spring, JPA, `application` or `adapter`; `application` does not know `adapter`;
`adapter.in` does not touch `adapter.out`; `@Entity` only in `adapter.out.persistence`, `@RestController` only in `adapter.in.web`.
The simplified CRUD variant is marked in the module's `package-info.java`. Template 2 (`catalog-service`) uses package by feature instead (ADR-0001).

## Consequences
- **Good:** domain rules are plain Java with fast unit tests; persistence and web can change without touching them.
- **Cost:** JPA entities and mapping next to domain objects in template 1; two layouts in the repository (template 1 and template 2).

## When to choose differently
- A module without business rules: the simplified variant or template 2.
- A module whose rules are the core of the business and change often: keep the full separation, even at the cost of mapping.

## Evidence
- [`HexagonArchitectureTest`](../../services/orders-service/src/test/java/io/github/bsamsonov/orders/HexagonArchitectureTest.java).
- [Alistair Cockburn: hexagonal architecture](https://alistair.cockburn.us/hexagonal-architecture/).
