# ADR-0012: Maven with the wrapper as the build tool

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD1, QD8
- **Replaces:** D5

## Context and drivers
Each service is built on its own, from a template copied many times (QD1). Developers and an AI agent change the build often:
a dependency, a plugin option, a generator setting. The build should be easy to read and hard to make clever (QD8).

## Considered options
1. **Maven** with the wrapper (`./mvnw`), one `pom.xml` per service.
2. **Gradle** (Kotlin or Groovy DSL) with the wrapper.

## Decision
Option 1. Versions come from the Spring Boot parent and BOMs; the OpenAPI generator, Flyway and tests are plugin configuration, not scripts.

## Consequences
- **Good:** the most used build tool in the Java ecosystem. A POM is declarative: the build is data in a fixed structure, not a program,
  so an AI agent finds the place to change and produces the same shape every time; `mvn help:effective-pom` shows the resolved build.
  One lifecycle (`verify`) for every service.
- **Cost:** XML is verbose; custom build logic needs a plugin; no build cache or incremental compilation out of the box, so large builds are slower.
  Versions are repeated in every service's POM until a parent POM or BOM exists (risk R3).

## When to choose differently
- The code is in Kotlin, or the team already writes Gradle builds: **Gradle**.
- A large multi-project build where incremental builds and a remote build cache save real time: **Gradle**.
- Build logic that is genuinely a program (custom tasks, generated variants): **Gradle**, where code is the native form.

## Evidence
- [JetBrains, The State of Java 2025](https://lp.jetbrains.com/the-state-of-java-2025): 67 % of respondents use Maven.
- [`pom.xml` of orders-service](../../services/orders-service/pom.xml).
