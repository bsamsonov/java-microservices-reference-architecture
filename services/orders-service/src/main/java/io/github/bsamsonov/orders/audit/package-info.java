/**
 * Audit module: records every business event of other modules into its own {@code audit} schema.
 * Has no public API; reacts to events only.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit")
package io.github.bsamsonov.orders.audit;
