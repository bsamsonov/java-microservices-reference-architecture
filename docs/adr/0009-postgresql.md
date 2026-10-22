# ADR-0009: PostgreSQL as the database for every service

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD5, QD6

## Context and drivers
Every service needs a relational database for its aggregates, its migrations and the outbox table (ADR-0003).
It must run the same way in compose, in Testcontainers and in Kubernetes (QD5) and stay small enough for a laptop (QD6).
No cloud account is allowed (constraint), so managed databases appear only as alternatives.

## Considered options
1. **PostgreSQL**: one engine for all services; CloudNativePG in Kubernetes.
2. **MySQL or MariaDB**.
3. **A managed cloud database** (RDS, Cloud SQL, Azure Database for PostgreSQL).

## Decision
Option 1. Services use plain SQL features PostgreSQL does well: `insert … on conflict do nothing` for idempotency (ADR-0004), `uuid`, `timestamptz`.
Kubernetes runs it through the CloudNativePG operator; compose and tests run the official image.

## Consequences
- **Good:** the most used database among developers; first-class support in Spring Modulith, Flyway and Testcontainers; a mature Kubernetes operator.
- **Cost:** the modes run different major versions today: 17 in compose and tests, 18.6 in k3d through the CNPG default image (risk R10).
  The fix is one pinned version, PostgreSQL 18, in all three places. SQL written for PostgreSQL is not portable as-is.

## When to choose differently
- The company runs and operates another engine (MySQL, Oracle, SQL Server): use it; the services depend on a few PostgreSQL features only.
- Production in a cloud: **a managed PostgreSQL** instead of an operator, unless the team wants to own operations.
- Horizontal write scaling across regions is a hard requirement: a distributed SQL database, at the cost of a different operational model.

## Evidence
- [Stack Overflow Developer Survey 2025](https://survey.stackoverflow.co/2025/technology): PostgreSQL is used by 55.6 % of all respondents and 58.2 % of professionals.
- [PostgreSQL versioning policy](https://www.postgresql.org/support/versioning/): 18 released on 2025-09-25; 17 supported until 2029-11-08.
