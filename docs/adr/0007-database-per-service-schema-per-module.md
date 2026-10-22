# ADR-0007: A database per service and a schema per module

- **Status:** Accepted (per-service database user planned)
- **Date:** 2026-10-22
- **Drivers:** QD4, QD5, QD6

## Context and drivers
A service that reads another service's tables couples their releases more tightly than any API.
Inside a service, modules must be extractable later (ADR-0001), so they must not share tables either.
At the same time the system must fit on a laptop (QD6) and run the same way in both modes (QD5).

## Considered options
1. **One Postgres server per mode, a database per service, a schema per module**, migrations per module.
2. **A Postgres server (cluster) per service**.
3. **One shared database** with tables of all services.

## Decision
Option 1. Compose runs one Postgres and creates a database per service (`compose/postgres-init/<db>.sql`);
k3d runs one CloudNativePG cluster and creates them with CNPG `Database` resources (`platform/manifests/databases/`).
Inside a service each module has its own schema (`orders`, `audit`) and its own Flyway migrations (`V<n>__<module>_<what>.sql`).
Reading or writing another module's or another service's tables is forbidden (`AGENTS.md`).
**Each service gets its own database user** that owns only its database (decided 2026-10-22, not implemented yet:
today all services connect as one user `app`, listed as a known gap in [`security/README.md`](../../security/README.md)).
Modules inside one service share the service's user: they share one transaction, which the outbox (ADR-0003) relies on.

## Consequences
- **Good:** one database server to run on a laptop; a module or a service can move to its own server by changing a URL; schemas make ownership visible.
- **Cost:** the services share one server, so they share its failure, its resources and its version.
  Until the per-service user is in place, isolation between services is a convention, not a permission.
  Schemas of modules inside a service stay a convention: no test checks the SQL a module runs.
  Cross-service reports need events or a read model, not joins.

## When to choose differently
- Production with different load, backup or availability needs per service: a **cluster per service** (option 2), or a managed database per service.
- One small team and one deployable: a **modular monolith** with one database and a schema per module (ADR-0001, option 3).
- Never a shared database between independently released services (option 3): it turns the schema into an unversioned contract.

## Evidence
- [`platform/manifests/databases/`](../../platform/manifests/databases/README.md) and [`compose/postgres-init/`](../../compose/postgres-init/README.md).
- [Database per service pattern](https://microservices.io/patterns/data/database-per-service.html).
