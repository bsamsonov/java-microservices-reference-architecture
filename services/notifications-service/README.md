# notifications-service: consumer of order events

Created with the `create-service` skill from the `orders-service` template. Decisions: `docs/adr/`.
It reads `orders.events` (`OrderPlaced`, `OrderCancelled`), creates a customer notification for every event and "sends" it by email.
The service publishes no events, so the outbox (`spring-modulith-starter-jdbc`, `events-kafka`, the `event_publication` table) is removed.

## Structure
```
io.github.bsamsonov.notifications
├── NotificationsApplication, OpenTelemetryLoggingConfig   root: bootstrap and cross-cutting configuration
└── notification/                module "Notifications"; no public API
    ├── domain/                  Notification (PENDING → SENT | FAILED, both final), type, channel
    ├── application/             NotificationService (idempotent notify, queries), ports NotificationRepository, NotificationSender
    └── adapter/
        ├── in/kafka/            OrderEventsListener, OrderEventMessages (own inbound contract), KafkaErrorHandlingConfig (retries, DLT)
        ├── in/web/              NotificationController implements NotificationsApi (from openapi.yaml), problem+json
        ├── out/persistence/     NotificationEntity, JpaNotificationRepository
        └── out/email/           LoggingEmailSender: provider stub that writes a log line
```
- **HTTP** (read only): `GET /notifications?customerId=…` (newest first), `GET /notifications/{id}` (404 as problem+json).
- **Inbound contract.** The event type comes from the `__TypeId__` header (the producer's fully qualified class name), but only its simple name is used.
  The JSON is read into the service's own records with just the needed fields; extra fields are ignored. Unknown types are skipped.
- **Idempotency.** The key is the event's `eventId`: an `existsBySourceEventId` check plus `unique (source_event_id)` in the database.
  If two identical events are processed concurrently, the loser fails on the constraint, is retried and then skipped.
  Delivery is at least once: if the commit fails after sending, the redelivery sends the email again.
- **Errors.** Transient ones (for example the database is down): 3 retries 1 s apart, then `orders.events.DLT`.
  An invalid message (broken JSON, no `__TypeId__`, no `customerId`) goes to the DLT immediately, without retries.
  The DLT record keeps the cause in the `kafka_dlt-exception-*` headers, and the service logs a WARN `Record sent to DLT`.
  A channel failure (`NotificationDeliveryException`) is a business outcome: the notification becomes FAILED and does not go to the DLT.
- **Database:** schema `notifications`, Flyway `V1`.
- **Observability:** `spring.kafka.listener.observation-enabled` continues the trace from the Kafka headers.
  One trace: `http post /orders` (orders-service) → `orders.events send` → `orders.events process` → `OrderEventsListener.on`.

## Tests (ADR-0031): `./mvnw verify`, 28 tests, all green
| Level | Class |
|---|---|
| Unit | `NotificationTest`: status transitions, finality |
| Web slice | `NotificationControllerTest` (`@WebMvcTest`, MockMvcTester): required `customerId`, 404 problem+json; 401, 403, mapping of the `permissions` claim |
| JPA slice | `JpaNotificationRepositoryTest`: mapping, ordering, database uniqueness of `source_event_id` |
| Module | `NotificationModuleTest` (`@ApplicationModuleTest`): SENT, a repeat is skipped, channel failure → FAILED (fake channel, not a mock) |
| Integration | `NotificationsFlowIntegrationTest`: records in `orders.events` → HTTP API; duplicate and unknown type; broken JSON, missing `customerId`, missing header → DLT |
| Architecture | `ModularityTest`, `HexagonArchitectureTest`, `ModuleSchemaTest` |

## Run
- Tests: `./mvnw verify` (needs Docker).
- Locally: `SERVER_PORT=8081 ./mvnw spring-boot:test-run` (Postgres and Kafka in Testcontainers).
- Compose: http://localhost:8081, database `notifications` (see `compose/README.md`).
- k3d: `spring_service('notifications-service')` in the `Tiltfile`, database through `platform/manifests/databases/notifications.yaml` (see `platform/README.md`).

## Verified in compose
- The topic history (5 older `OrderPlaced`) is processed on first start; `trace_id` is taken from the headers of the old messages.
- `POST /orders` → cancel → two SENT notifications for `c-live`; one trace across both services in Tempo, `Notification sent` lines with the same `trace_id` in Loki.
- A malformed message `broken-2` → `orders.events.DLT` with cause headers and a WARN in the log. No reprocessing after a restart: the offset is committed.
- Listener metric: `spring_kafka_listener_seconds_count{application="notifications-service"}`.

## Security (ADR-0023)
Every request carries a Keycloak token; access rules are in `SecurityConfig`, permissions in `openapi.yaml`.
How to get a token and add a permission: `security/README.md`.
