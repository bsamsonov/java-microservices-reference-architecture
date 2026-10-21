# SpringHeapHigh

**Meaning.** The service heap has been above 90 % of the old-generation maximum for 10 minutes.
**Impact.** Frequent GC, growing latency, then `OutOfMemoryError` and a pod restart.
**Check.**
1. "Spring services" dashboard, JVM row: heap and GC time.
2. Does the heap grow after every GC (leak) or stay flat (limit too low)?
**Action.** Leak: take a heap dump. Limit too low: raise `resources.limits.memory` in `deploy/values.yaml`.
