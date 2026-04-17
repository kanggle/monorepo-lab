---
name: performance-tuning
description: Performance targets, profiling, load testing
category: cross-cutting
---

# Skill: Performance Tuning

Cross-cutting policy for measuring and improving service performance.

Prerequisite: read `platform/testing-strategy.md` and `platform/observability.md` before using this skill. Use `cross-cutting/observability-setup/SKILL.md` for instrumentation and `cross-cutting/caching/SKILL.md` for cache strategy.

---

## Performance Targets (Default SLOs)

| Endpoint Class | p50 | p95 | p99 |
|---|---|---|---|
| Read (cached) | < 50 ms | < 150 ms | < 300 ms |
| Read (DB) | < 100 ms | < 300 ms | < 600 ms |
| Write (single entity) | < 150 ms | < 400 ms | < 800 ms |
| Write (transactional with events) | < 250 ms | < 600 ms | < 1200 ms |
| Search | < 200 ms | < 500 ms | < 1000 ms |

Override per service in `specs/services/<service>/slo.md`. Treat targets as commitments, not aspirations.

---

## Measurement Order

Always measure before optimizing. Workflow:

1. **Reproduce** under realistic load (`testing/e2e-test/SKILL.md`)
2. **Profile** to find the actual hot spot
3. **Hypothesize** one change at a time
4. **Verify** with the same load test
5. **Commit** the change with before/after numbers in PR description

---

## Backend Profiling (JVM)

| Tool | When |
|---|---|
| Async-profiler | CPU hot spots, lock contention |
| JFR (Java Flight Recorder) | Production-safe sampling |
| `jstack` / `jcmd Thread.print` | Deadlock or blocked threads |
| VisualVM | Local dev memory leaks |

```bash
# Async profiler in K8s pod
kubectl exec <pod> -- java -jar async-profiler.jar -d 60 -f /tmp/cpu.html <pid>
```

---

## Frontend Profiling (Node / Browser)

| Tool | When |
|---|---|
| Chrome DevTools Performance | Browser render bottlenecks |
| Lighthouse / web-vitals | Core Web Vitals (LCP, INP, CLS) |
| `next build` analyze | Bundle size regression |
| React Profiler | Excessive re-renders |

See `frontend/bundling-perf/SKILL.md` for bundle and asset optimization.

---

## Common Hot Spots

### N+1 Queries
- Detect: enable `spring.jpa.show-sql` in test, count queries per request
- Fix: `JOIN FETCH`, `@EntityGraph`, batch fetching, projection DTO

### Unbounded Result Sets
- Always paginate (`backend/pagination/SKILL.md`)
- Reject queries without `limit` at the controller layer

### Synchronous External Calls in Loops
- Batch into a single call when possible
- Use parallel streams or `CompletableFuture.allOf` only when API supports concurrency

### Lock Contention
- Reduce transaction scope
- Switch to optimistic locking (`@Version`)
- Use Redis distributed lock with TTL for cross-instance critical sections

### Cold Cache
- Pre-warm on deploy for known hot keys
- Use refresh-ahead pattern (`cross-cutting/caching/SKILL.md`)

---

## Load Testing

| Type | Tool | When |
|---|---|---|
| Smoke | k6 / Gatling | Every PR touching hot path |
| Stress | k6 / Gatling | Before release |
| Soak (4-24h) | k6 / Gatling | Major architecture change |
| Spike | k6 / Gatling | Before promo events |

Load test scripts live under `tests/load/<service>/`. CI runs smoke automatically.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Optimizing without profiling | Always profile first |
| Premature caching of cold data | Measure hit rate after deploy |
| Ignoring p99 because p50 looks fine | Track p95 and p99 separately |
| Tuning JVM heap to 90% of pod limit | Leave headroom for native memory |
| Missing connection pool tuning | Set HikariCP `maximumPoolSize` per CPU count |
| Garbage allocation in hot loop | Reuse buffers, avoid boxing |

---

## Verification Checklist

- [ ] SLO defined in service spec
- [ ] Latency metrics emitted (`observability-setup.md`)
- [ ] Load test script committed under `tests/load/`
- [ ] PR description includes before/after numbers for any perf change
- [ ] No regression in p95/p99 vs previous release
