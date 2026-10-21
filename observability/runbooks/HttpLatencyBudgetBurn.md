# HttpLatencyBudgetBurn

**Meaning.** Fewer than 99 % of the service's requests finish within 500 ms, and the 4-week SLO budget will run out early,
within `exhaustion` (alert label). `critical`: in 2–4 days; `warning`: in 2–4 weeks.
**Impact.** Users wait; under growing load slow requests turn into timeouts and 5xx.
**Check.**
1. "Spring services" dashboard, HTTP row: p95 and the per-endpoint breakdown.
2. Hikari row: are threads waiting for a database connection?
3. JVM row: GC and CPU.
**Action.** Slow endpoint: open the trace of the slowest request. Waiting for the pool: see `SpringDbPoolExhausted`.
