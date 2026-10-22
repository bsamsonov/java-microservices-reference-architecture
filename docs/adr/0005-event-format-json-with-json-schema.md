# ADR-0005: Event format: JSON now, JSON Schema and AsyncAPI next; registry and binary formats deferred

- **Status:** Accepted (JSON in force; JSON Schema and AsyncAPI planned)
- **Date:** 2026-10-22
- **Drivers:** QD2, QD8

## Context and drivers
Events are JSON produced by Modulith externalization. The consumer has its own copy of the message records
and picks the type from the `__TypeId__` header, which holds the producer's Java class name (risk R2).
Nothing checks that a producer change keeps consumers working. Four separate choices are involved, and they can be made one at a time:
the contract description, the schema language, the wire format and where compatibility is checked.

## Considered options
1. **JSON, described by JSON Schema in an AsyncAPI document, compatibility checked in CI**.
2. **JSON without a schema** (the current state).
3. **Avro or Protobuf with a schema registry** (Confluent, Apicurio, Karapace).

## Decision
Keep JSON on the wire. Next step: describe `orders.events` in AsyncAPI with JSON Schema payloads, add an explicit event-type header instead of the class name,
and check compatibility in producer tests. Evolution rule: adding an optional field is allowed; removing or renaming one is not;
an incompatible change is a new event type. A schema registry and a binary format are deferred, with the conditions below.
None of these steps undoes the previous one: AsyncAPI can describe Avro payloads, and a registry can store JSON Schema.

## Consequences
- **Good:** messages stay readable in Kafka tools and logs; no registry to run on a laptop; the contract becomes a file, like `openapi.yaml`.
- **Cost:** until the schema exists (R2), renaming a record or adding a consumer in another language can break integration silently.
  CI checks catch a breaking change only in repositories that run them; a producer elsewhere is not stopped at publish time.

## When to choose differently
- Many teams or services consume one topic and compatibility must be enforced when a schema is registered: add a **schema registry with JSON Schema**.
  Note: the Confluent serializers prefix the payload with a magic byte and a schema id, so every consumer needs the matching deserializer.
- Message size and throughput matter, or consumers in other languages want generated classes: **Avro or Protobuf** with a registry.
- Events leave the organisation: publish the AsyncAPI document and consider CloudEvents attributes for the envelope.

## Evidence
- [`OrderEventsListener`](../../services/notifications-service/src/main/java/io/github/bsamsonov/notifications/notification/adapter/in/kafka/OrderEventsListener.java): type resolution from `__TypeId__` today.
- [AsyncAPI 3 specification](https://www.asyncapi.com/docs/reference/specification/v3.0.0): the message `schemaFormat` field accepts JSON Schema, Avro and others.
