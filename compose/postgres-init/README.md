# Service databases in compose

Each service has its own Postgres user and database (ADR-0007). Postgres runs the `*.sql` files from this folder only on first start,
while the `pgdata` volume is still empty. The admin user is `postgres` (local only); services never use it.
Every service adds a `<db>.sql` file here:

```sql
-- User and database of <svc>. Only this user can connect; it owns everything it creates through Flyway.
create role <db> login password '<db>';   -- local demo only
create database <db> owner <db>;
revoke connect, temporary on database <db> from public;
```

The service's `application.yaml` has the matching local defaults (`username: <db>`, `password: <db>`).

If the volume already exists, run the new file by hand:
`docker compose -f compose/compose.yaml exec -T postgres psql -U postgres < compose/postgres-init/<db>.sql`.
A volume created before the per-service users (everything owned by `app`) is easiest to recreate: `docker compose -f compose/compose.yaml down -v`
(local data is lost).
