# ADR-0008: Java 25 and Spring Boot 4

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD1, QD2

## Context and drivers
A template is copied into services that will live for years (QD1). Starting on an older generation means a major upgrade
in every copy soon after it is created (risk R3: copies drift and have no upgrade path).
The stack must be mainstream enough that developers and AI agents know it (QD2).

## Considered options
1. **Java 25 LTS, Spring Boot 4** (Spring Framework 7, Spring Security 7, Spring Modulith 2).
2. **Java 21 LTS, Spring Boot 3.x**: the previous generation, the most common in running systems.
3. **Quarkus or Micronaut**: frameworks built around fast startup, low memory and native images.

## Decision
Option 1: the latest LTS and the latest Boot generation. Minor and patch versions come from the Boot BOM and follow its releases; the ADR fixes only the generation.
Base images are `eclipse-temurin:25-jre`.

## Consequences
- **Good:** no forced major upgrade for years; Oracle Premier support for Java 25 runs to 09.2030; one consistent generation across Spring projects.
- **Cost:** a new major means fewer answers online and some libraries arriving later.
  An AI agent may propose Boot 3 APIs and dependencies (`@MockBean` instead of `@MockitoBean`, `flyway-core` without `spring-boot-starter-flyway`,
  Jackson 2 imports); the build and the tests catch this, and `AGENTS.md` names the versions.

## When to choose differently
- The company platform, base images or app servers are on Java 21: **Java 21 LTS**, and the Boot version that platform supports.
- Startup time and memory dominate (functions, scale to zero, many tiny services): **Quarkus or Micronaut**, or Spring with a native image.
- The team knows another framework well: the architecture (modules, outbox, idempotency, contracts) carries over; the template code does not.

## Evidence
- [Oracle Java SE support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html): Java 25 is an LTS release.
- [`pom.xml` of orders-service](../../services/orders-service/pom.xml): `spring-boot-starter-parent` and `java.version`.
