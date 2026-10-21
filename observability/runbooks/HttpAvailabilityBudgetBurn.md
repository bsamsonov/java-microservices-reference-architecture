# HttpAvailabilityBudgetBurn

**Meaning.** The service (`application`) returns 5xx so often that the 4-week error budget of the 99.5 % SLO
will run out early, within `exhaustion` (alert label). `critical`: gone in 2–4 days; `warning`: in 2–4 weeks.
**Impact.** Users are getting errors right now.
**Check.**
1. "Spring services" dashboard, HTTP row: share of 5xx and the per-endpoint breakdown.
2. ERROR logs of the service in the same dashboard row, then jump to the trace via `trace_id`.
3. Other alerts of the same service: `SpringDbPoolExhausted` or `SpringHeapHigh` often explain the cause.
**Action.** Roll back the last release if the increase started with it; otherwise find the cause through the trace.
