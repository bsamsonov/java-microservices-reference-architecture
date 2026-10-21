/**
 * Notification module: turns order events from Kafka into customer notifications.
 * Has no public API: it publishes no events; everything in sub-packages is internal.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notifications")
package io.github.bsamsonov.notifications.notification;
