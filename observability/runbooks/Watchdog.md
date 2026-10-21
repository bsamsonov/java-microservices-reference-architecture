# Watchdog

**Meaning.** This alert always fires: it proves that the Prometheus → Alertmanager → receiver chain works.
**Impact.** Its absence is the problem. An external service (for example healthchecks.io) expects a ping every minute
from the `heartbeat` receiver and reports when the pings stop.
**Check.** Are Prometheus and Alertmanager alive; is Watchdog listed at http://localhost:9093 (compose) or http://alertmanager.localhost:8080 (k3d)?
**Action.** Bring the failed component back.
