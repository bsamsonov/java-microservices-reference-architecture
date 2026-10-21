# Service databases in compose

Postgres runs the `*.sql` files from this folder only on first start, while the `pgdata` volume is still empty.
The `orders` database is created by `POSTGRES_DB`. Every further service adds a `<db>.sql` file here:

```sql
create database <db> owner app;
```

If the volume already exists, create the database by hand: `docker compose -f compose/compose.yaml exec postgres createdb -U app <db>`.
