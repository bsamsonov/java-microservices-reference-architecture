# ADR-0006: API-first: OpenAPI file, generated interfaces

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD1, QD2

## Context and drivers
An HTTP contract change must be visible in a diff and reviewed before the code (QD2).
An AI agent working from a file produces a predictable controller; generating the interface removes a class of mismatches between docs and code (QD2).
Errors and access rules (401, 403, permissions) are part of the contract (QD4).

## Considered options
1. **API-first**: `src/main/resources/api/openapi.yaml` is the source; `openapi-generator` creates the `<Tag>Api` interface and `*Dto` classes at build time.
2. **Code-first**: controllers are written by hand and springdoc generates the OpenAPI document from them.
3. **No published contract**: controllers only, docs by hand.

## Decision
Option 1. The controller implements the generated interface, so a contract change that the code does not follow fails compilation.
Generated sources live in `target/generated-sources` and are never edited or committed. Errors are `application/problem+json`.
Each endpoint lists its permission and its `401` and `403` responses in the file.

## Consequences
- **Good:** the contract is reviewed as a file; clients can be generated from it; the controller cannot drift from the documentation.
- **Cost:** the generator's conventions (DTO naming, nullable handling, validation annotations) shape the code;
  a generator upgrade can change the generated code; developers edit YAML before Java.

## When to choose differently
- One consumer, a quick prototype, or an internal endpoint that changes daily: **code-first** with springdoc.
- Several services share types: still API-first, but with shared schema components and a contract test between producer and consumer.

## Evidence
- [`openapi.yaml` of orders-service](../../services/orders-service/src/main/resources/api/openapi.yaml).
- [openapi-generator, Spring generator options](https://openapi-generator.tech/docs/generators/spring/).
