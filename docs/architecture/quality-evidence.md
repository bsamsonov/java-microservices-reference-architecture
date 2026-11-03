# Quality evidence: experiments on a running platform

Tests prove the behaviour on Testcontainers; this page records what was seen on the k3d platform (`make -C platform up`, `tilt ci`).
Each entry gives the method, the numbers and what changed because of it. Raw data (CSV, timelines, thread dumps) is not kept in the repository.

## Broker outage

**Question (QD3, QD5, risk R1).** Kafka is unreachable for 9 minutes while orders keep coming. Does the API stay up,
does the alert `OutboxBacklogStale` fire, and does every event reach `notifications-service` once Kafka is back, without a restart?

**Method.** One `POST /orders` every 2 s through the ingress with a token of `alice`. The outage is a network cut, not a stopped broker:
Kafka storage in k3d is ephemeral, so deleting the broker pod would also delete the topic contents and spoil the count.
The NetworkPolicies of `orders-service` and `notifications-service` are patched to allow only ports 5432 and 8080 in `infra`,
and the broker container is restarted once (`kill -TERM 1`) to close the connections already open.
k3s enforces NetworkPolicies with kube-router, which rejects the packets: clients see a refused connection within 50 ms, not a silent timeout.
At minute 6.5 `orders-service` is restarted (`kubectl rollout restart`), as a rolling update or an eviction during an outage would do.
The outbox gauges and Alertmanager are read every 15 s; at the end orders, Kafka offsets of `orders.events`, notifications and audit rows are counted.

**Run 1, staleness monitor off (as in ADR-0003).**

| Time (min) | What happened |
|---|---|
| 0:00 | cut; orders keep returning 201 in 12–20 ms, `outbox_publications_pending` grows by ~50 a minute |
| 5:27 | `OutboxBacklogStale` pending in Prometheus (oldest publication > 300 s) |
| 6:27 | firing; in Alertmanager 7 s later; `SpringErrorLogLines` is inhibited by it |
| 6:45 | `orders-service` restarted; the new pod starts without Kafka and serves orders |
| 8:44 | connectivity restored; ~380 publications delivered within a minute |
| 8:44 → | **44 publications stay in the table** (42 to Kafka, 2 to the audit module), the alert keeps firing |

All 644 requests returned 201; the slowest took 0.26 s. After the restore 652 orders, 610 notifications: the 42 missing notifications
are exactly the 42 stuck Kafka publications. 618 records in `orders.events` for 610 events: 8 duplicates, absorbed by the consumer (ADR-0004).
The stuck rows: 40 `RESUBMITTED` (the old pod was sending them again when it was stopped; the restart does not republish them and the scheduled
resubmission takes only `FAILED` rows) and 4 `PUBLISHED` (created by the old pod during the rolling update, after the new pod had republished).
Turning the staleness monitor on (`SPRING_MODULITH_EVENTS_STALENESS_*=5m` on the deployment) marked them `FAILED`, and all 44 were delivered
within a minute: 652 orders, 652 notifications, 652 audit rows. This is [ADR-0037](../adr/0037-outbox-staleness-monitor.md).

**Run 2, staleness monitor on (ADR-0037), same scenario.** 316 requests, one `502` at the moment of the rolling update (see below), 315 orders.
The alert was active in Alertmanager by 7:54 (Prometheus itself had restarted at minute 2 under memory pressure on the laptop) and resolved after the backlog was gone. The outbox was empty 4.5 minutes after the restore with no restart and no manual step:
the last 4 rows were the `PUBLISHED` ones left by the stopped pod, picked up when they turned 5 minutes old.
315 orders, 315 notifications, 315 audit rows; 415 records in `orders.events`, so **100 duplicates** (32 %), all absorbed by the consumer.
The duplicates are the cost named in ADR-0037: a row older than 5 minutes is marked `FAILED` again while its resubmission is still waiting for the broker.

**Run 3 and run 4, planned restarts and a crash with Kafka up.** Run 3 used the configuration of ADR-0037 before republish on restart was dropped,
run 4 the final one (no republish, the task executor awaits its tasks on shutdown). Orders every 0.4–0.6 s, three `kubectl rollout restart` each.
After every restart no publication older than 15 s was left; run 3: 300 orders, 300 records in `orders.events`, 300 notifications;
run 4: 673 orders, 673 records, 673 notifications, so no loss and no duplicates. A `kill -9` of the JVM in run 4 lost nothing either,
but nothing was in flight at that moment (the network cut did not break the open producer connection), so the crash path is shown only by
`OutboxStalePublicationIntegrationTest` and by the 4 `PUBLISHED` rows of run 2. HTTP is the weak spot: each planned restart cost one or two requests
(`502`, or `504` after the 30 s ingress timeout), and the crash 40 `503` until the new pod was ready, because there is one replica.

**Other findings.**
- A pod started while Kafka is down has no topic metadata, so every send blocks for `max.block.ms` (60 s) in the shared task executor.
  The thread dump showed all 8 `task-*` threads in `KafkaProducer.waitOnMetadata`; republished and new events queue behind them.
  Delivery catches up once Kafka is back; the outbox gauges kept working because scheduling has its own pool (`spring.task.scheduling.pool.size: 2`).
- The `Admin.close()` hang from the Testcontainers test (a silently unreachable broker on the first send after start) did not show here,
  because kube-router rejects instead of dropping. It stays covered only by the cluster id warm-up in `OutboxRedeliveryIntegrationTest`.
- The `502` came from the ingress sending a request to the old pod while it stopped: no graceful shutdown or `preStop` delay in the chart yet.
  Fixed by ADR-0038, see [rollout and eviction](#rollout-and-eviction).

**Not checked yet.** A Grafana screenshot of the run; a silent network drop; an outage with two replicas; a crash with sends in flight on the platform.

## Rollout and eviction
**Method.** The chart of ADR-0038 (`preStop` sleep 10 s, grace period 75 s, `maxSurge: 1` / `maxUnavailable: 0`), `orders-service` with 2 replicas
and a PodDisruptionBudget `minAvailable: 1`; Kafka and the database up. One `POST /orders` every ~0.2 s through the ingress for 4 minutes.
Three `kubectl rollout restart` of `orders-service`, 30 s apart; then what `kubectl drain` does on one node: an eviction of each `orders-service` pod
through the Eviction API, repeated while the PDB refuses. k3d has one node, so a real drain would also evict Postgres and Kafka.

**Run 5 (2026-10-28).** 1023 requests, **all `201`**, the slowest 0.36 s. Each rolling restart took 22–23 s.
The first eviction was accepted at once; the second was refused by the PDB three times, for 16 s, until the replacement pod was Ready.
1023 new orders, 1023 new records in `orders.events`, 1023 new notifications, and the outbox empty at the end: no loss and **no duplicates**,
also with two replicas running the outbox jobs. Before the chart change the same restarts cost one or two `502`/`504` each (runs 2–4 above).

**Not checked.** A crash with two replicas (requests to the dead pod fail until readiness drops it); a drain of a real node with several nodes;
a redeploy of `notifications-service`, which has one replica and so no PDB.
