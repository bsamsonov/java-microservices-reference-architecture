-- Module: notification. Owns schema "notifications"; other modules must not read these tables.
-- source_event_id is unique: a redelivered Kafka event cannot create a second notification.
create schema if not exists notifications;

create table notifications.notifications (
    id              uuid         primary key,
    source_event_id uuid         not null unique,
    order_id        uuid         not null,
    customer_id     varchar(64)  not null,
    type            varchar(32)  not null,
    channel         varchar(16)  not null,
    status          varchar(16)  not null,
    created_at      timestamptz  not null,
    sent_at         timestamptz,
    failure_reason  varchar(255),
    version         bigint       not null default 0
);

create index notifications_customer_idx on notifications.notifications (customer_id, created_at desc);
