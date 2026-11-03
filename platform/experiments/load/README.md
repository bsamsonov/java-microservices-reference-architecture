# Load test: the order flow with k6

`orders.js` places an order, reads it back and cancels one order in ten. It uses an open model (`ramping-arrival-rate`):
k6 keeps the request rate when the service slows down, so a slow service does not hide its latency by receiving fewer requests (coordinated omission).
The thresholds are the service SLOs from the client side: < 0.1 % failed requests, p95 < 500 ms. A crossed threshold makes k6 exit non-zero.

```bash
make -C platform up && tilt ci                      # the k3d stand
platform/experiments/load/run.sh smoke              # 30 s at 1 order/s
platform/experiments/load/run.sh plateau            # 2 min ramp, 8 min at RATE (default 20 orders/s)
RATE=20 platform/experiments/load/run.sh stress     # grows to 10 × RATE in 12 min
```

`run.sh` runs the pinned `grafana/k6` image against `orders.localhost:8080`, takes a token of `alice` once (it lives 15 minutes,
so every profile is shorter), and writes k6 metrics into the cluster Prometheus through a port-forward (`k6_*`, label `testid`).
The end-of-test summary goes to `summary-<profile>.json` in `$OUT_DIR`.

The load generator shares the laptop with the cluster. In production it runs on separate nodes or outside the cluster,
for example as a k6-operator `TestRun` (see the Roadmap in the root README). Results: [quality evidence](../../../docs/architecture/quality-evidence.md#load).
