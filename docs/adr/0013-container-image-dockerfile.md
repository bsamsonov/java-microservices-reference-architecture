# ADR-0013: Container image from a layered Dockerfile over a prebuilt jar

- **Status:** Accepted
- **Date:** 2026-10-22
- **Drivers:** QD6, QD7
- **Risks:** R9, accepted with this decision

## Context and drivers
The same image must run in compose and in Kubernetes (QD6). The jar is already built and tested by `./mvnw` and by Tilt,
and a rebuild in the dev loop must be fast on a laptop (QD7). The image should use a known base and run as non-root.

## Considered options
1. **Dockerfile over a prebuilt jar**: the jar is extracted into Spring Boot layers (`-Djarmode=tools extract --layers`), dependencies first.
2. **Multi-stage Dockerfile that runs Maven inside**: `docker build` works from a clean clone.
3. **Cloud Native Buildpacks** (`./mvnw spring-boot:build-image`): no Dockerfile, the builder chooses and patches the base.
4. **Jib** (Maven plugin): builds and pushes layered images without a Docker daemon.

## Decision
Option 1. `services/<name>/Dockerfile` copies `target/<name>-*.jar`, extracts the layers and runs `JarLauncher` on `eclipse-temurin:25-jre`
as user 10001 with `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`. Compose (`--build`) and Tilt use the same file.

## Consequences
- **Good:** the jar is built once, with tests, by the same Maven command everywhere; image rebuilds after a code change touch only the application layer;
  the base image, user and JVM options are visible in one short file.
- **Cost:** `docker build` from a clean clone fails until `./mvnw package` has run (R9); a base image CVE needs a rebuild and redeploy of every service;
  every service carries its own copy of the Dockerfile.

## When to choose differently
- Base-layer patches across many services without touching each Dockerfile: **Buildpacks**.
- CI without a Docker daemon, or images pushed straight from the Maven build: **Jib**.
- Contributors or CI must build the image with Docker alone: **multi-stage Dockerfile** with a Maven stage and a dependency cache mount.

## Evidence
- [`Dockerfile` of orders-service](../../services/orders-service/Dockerfile).
- [Spring Boot: efficient container images and Dockerfiles](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html).
