-- Module: audit. Owns schema "audit". event_id is unique: listener inserts are idempotent.
create schema if not exists audit;

create table audit.audit_log (
    id           bigint generated always as identity primary key,
    event_id     uuid        not null unique,
    event_type   varchar(64) not null,
    aggregate_id uuid        not null,
    payload      jsonb       not null,
    occurred_at  timestamptz not null,
    recorded_at  timestamptz not null default now()
);

create index audit_log_aggregate_idx on audit.audit_log (aggregate_id);
