-- Feature: product. Owns schema "products". code is the business key: unique and immutable.
create schema if not exists products;

create table products.products (
    id          uuid           primary key,
    code        varchar(32)    not null unique,
    name        varchar(200)   not null,
    price       numeric(12, 2) not null check (price >= 0),
    active      boolean        not null,
    created_at  timestamptz    not null,
    updated_at  timestamptz    not null,
    version     bigint         not null
);

create index products_active_code_idx on products.products (active, code);
