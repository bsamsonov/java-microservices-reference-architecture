/**
 * Product feature. CRUD variant of ADR-0030 (AGENTS.md, rule 5): no separate domain model, the Spring Data JDBC
 * aggregate {@link io.github.bsamsonov.catalog.product.Product} is the model (ADR-0032). One package holds controller, service,
 * entity and repository; classes are package-private, so other features can use only what is public.
 * When real business rules appear, move to template 1 (orders-service): domain/application/adapter.
 */
package io.github.bsamsonov.catalog.product;
