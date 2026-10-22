# ADR-0032: Persistence: JPA in template 1, Spring Data JDBC in template 2

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD1, QD8
- **Replaces:** D8
- **Level:** service

## Context and drivers
Template 1 has a rich domain mapped to tables; template 2 is a small CRUD without business rules.
Hibernate's features (first-level cache, cascades, lazy loading, dirty checking) help the first and are a frequent source of bugs in the second, also for AI agents (QD8).

## Considered options
1. **JPA (Hibernate) in template 1, Spring Data JDBC in template 2**.
2. **JPA everywhere**.
3. **jOOQ** or plain `JdbcClient` everywhere.

## Decision
Option 1. Template 1 (`orders-service`, `notifications-service`): Spring Data JPA, `ddl-auto: validate`, `open-in-view: false`, domain model separate from entities,
`JdbcClient` for queries that need SQL (`insert … on conflict do nothing`). Template 2 (`catalog-service`): Spring Data JDBC, an aggregate is a row
(or a root with nested collections), changes are stored only by an explicit `save`, optimistic locking with `@Version`. Test slices: `@DataJpaTest` and `@DataJdbcTest`.

## Consequences
- **Good:** the most widespread stack for the rich domain; predictable behaviour for CRUD with no session or lazy loading.
- **Cost:** two persistence stacks and two test slices in one repository. JDBC has no `ddl-auto: validate`, so a repository test that writes and reads every column checks the mapping.

## When to choose differently
- SQL-heavy reporting or type-safe SQL as the main access path: **jOOQ**.
- A team that knows only JPA and has no CRUD services: **JPA everywhere**.

## Evidence
- [Spring Data JDBC reference](https://docs.spring.io/spring-data/relational/reference/jdbc.html).
- [`catalog-service` README](../../services/catalog-service/README.md).
