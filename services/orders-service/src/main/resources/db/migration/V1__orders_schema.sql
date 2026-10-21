-- Module: order. Owns schema "orders"; other modules must not read these tables.
create schema if not exists orders;

create table orders.orders (
    id                  uuid primary key,
    customer_id         varchar(64)  not null,
    status              varchar(16)  not null,
    created_at          timestamptz  not null,
    cancellation_reason varchar(255),
    version             bigint       not null
);

create table orders.order_lines (
    order_id     uuid          not null references orders.orders (id) on delete cascade,
    line_no      int           not null,
    product_code varchar(64)   not null,
    quantity     int           not null check (quantity > 0),
    unit_price   numeric(19,2) not null check (unit_price >= 0),
    primary key (order_id, line_no)
);

create index orders_customer_idx on orders.orders (customer_id);
