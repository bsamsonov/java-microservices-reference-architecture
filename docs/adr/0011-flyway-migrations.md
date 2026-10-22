# ADR-0011: Flyway SQL migrations, forward only

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD1, QD4, QD8

## Context and drivers
Each module owns its schema (ADR-0007), and its tables change with the code. The schema change must be a file in the diff,
applied the same way in tests, compose and Kubernetes, and checked against the mapping at startup (QD4).
An AI agent must be able to write a migration without learning a changelog format (QD8).

## Considered options
1. **Flyway** with plain SQL files: `V<n>__<module>_<what>.sql`.
2. **Liquibase** with changelogs (YAML, XML, JSON or SQL) and built-in rollback.
3. **Hibernate schema generation** (`ddl-auto: update`).

## Decision
Option 1. Migrations live in `src/main/resources/db/migration`, one module per file name prefix. An applied migration is never changed; a fix is a new migration.
`ddl-auto` stays `validate`, so the JPA mapping is checked against the migrated schema in every test run. Flyway comes from the Boot BOM (12.4.0).

## Consequences
- **Good:** migrations are plain SQL, readable by anyone who knows PostgreSQL; Boot runs them at startup with no extra setup; the order is in the file name.
- **Cost:** forward only: undo migrations are not part of the free edition, so a rollback is a new forward migration or a restore.
  SQL is database-specific. Two branches that add `V4` collide and must be renumbered before merge.

## When to choose differently
- Rollbacks must be scripted and tested per change, the same changelog must run on several database engines, or the team prefers changelogs in YAML or XML: **Liquibase** (5.0.3 as of 2026-10).
- Throwaway prototypes only: Hibernate schema generation; never for a schema that holds data.

## Evidence
- [`V1__orders_schema.sql`](../../services/orders-service/src/main/resources/db/migration/V1__orders_schema.sql) and the `@DataJpaTest` slices that run on the migrated schema.
- [Spring Boot: database initialization with Flyway or Liquibase](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
