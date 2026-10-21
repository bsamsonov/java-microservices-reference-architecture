# SpringDbPoolExhausted

**Meaning.** Service threads have been waiting for a free Hikari connection for 5 minutes in a row.
**Impact.** Requests wait for the database; once users notice, `HttpLatencyBudgetBurn` fires.
**Check.**
1. "Spring services" dashboard, Hikari row: active, pending, connection acquire time.
2. Long transactions and queries in Postgres (`pg_stat_activity`).
**Action.** Fix the long transaction or the slow query; increase the pool size only as a last resort.
