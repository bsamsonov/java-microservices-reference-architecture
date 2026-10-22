# Architecture decision records

Each record states one choice, the options that were considered, what it costs and, most importantly, **when to choose differently**.
The format is [MADR 4](https://adr.github.io/madr/) with two additions: the quality drivers it serves
(QD1–QD8 in [`docs/architecture/README.md`](../architecture/README.md#2-quality-drivers)) and one or two links of evidence.
New records start from [`template.md`](template.md).

The earlier short list D1–D12 lives in [`docs/decisions.md`](../decisions.md) until every entry has an ADR; the column "Was" maps them.

## Rules
- A decision is never changed silently. To change it, write a new ADR and mark the old one "Superseded by ADR-NNNN"; do not rewrite history.
- An accepted ADR is edited only to fix facts or links.
- A rule that can be checked is checked by a test or a script, and the ADR links to it.

## Index

| ADR | Decision | Group | Status | Drivers | Was |
|---|---|---|---|---|---|
| [0001](0001-modulith-inside-each-microservice.md) | Microservices with a modular monolith inside (Spring Modulith) | System | Accepted | QD1, QD4, QD8 | D6 |
| [0002](0002-integrate-services-through-events.md) | Integrate services through domain events, not synchronous calls | System | Accepted | QD2, QD3 | D2 |
| [0003](0003-outbox-on-modulith-event-registry.md) | Transactional outbox on the Spring Modulith event publication registry | System | Accepted | QD2, QD6 | D12 |
| [0004](0004-idempotent-consumers-retries-and-dlt.md) | Idempotent consumers, bounded retries and a dead-letter topic | System | Accepted | QD2, QD3 | |
| [0005](0005-event-format-json-with-json-schema.md) | Event format: JSON now, JSON Schema and AsyncAPI next; registry and binary formats deferred | System | Accepted (partly planned) | QD2, QD8 | |
| [0006](0006-api-first-openapi.md) | API-first: OpenAPI file, generated interfaces | System | Accepted | QD1, QD4, QD8 | |
| [0007](0007-database-per-service-schema-per-module.md) | A database per service and a schema per module | System | Accepted | QD4, QD5, QD6 | |
