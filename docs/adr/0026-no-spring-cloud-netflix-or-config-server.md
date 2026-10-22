# ADR-0026: No Spring Cloud service discovery or config server

- **Status:** Rejected (Eureka, Spring Cloud Config) for this platform
- **Date:** 2026-10-22
- **Drivers:** QD5, QD6

## Context and drivers
Many Spring microservice courses and older reference projects start with Eureka, a Config Server and a Spring Cloud Gateway.
This platform runs on Kubernetes and compose, which already provide names and configuration (QD5), and every extra server costs laptop memory (QD6).

## Considered options
1. **Platform-native**: Kubernetes Service DNS and compose service names for discovery; environment variables, ConfigMaps and Secrets for configuration.
2. **Spring Cloud Netflix Eureka and Spring Cloud Config Server**.

## Decision
Option 1, and option 2 is rejected. Services read every address and credential from environment variables (`AGENTS.md`), with `localhost` defaults in `application.yaml`.

## Consequences
- **Good:** two fewer servers to run and secure; no client libraries; the service does not know where it runs.
- **Cost:** configuration changes need a restart (no refresh scope); there is no client-side load balancing beyond what Kubernetes Services do.

## When to choose differently
- Services run on VMs or bare metal without a platform that provides discovery: **Eureka** (or Consul).
- Configuration must change at runtime without restarts across many services, with audit: **Config Server** or a configuration service of the platform.

## Evidence
- [Kubernetes: DNS for Services and Pods](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/).
- [Spring Cloud project page](https://spring.io/projects/spring-cloud).
