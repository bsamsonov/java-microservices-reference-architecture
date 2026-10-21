# SpringErrorLogLines

**Meaning.** The service writes ERROR log lines. Severity `info`: no notification, the alert is only visible in the alert list.
**Impact.** None by itself; it is a hint while investigating other alerts.
**Check.** "Spring services" dashboard, Logs row: error text and the link to the trace.
**Action.** Expected error: lower the log level. Unexpected one: open a ticket.
