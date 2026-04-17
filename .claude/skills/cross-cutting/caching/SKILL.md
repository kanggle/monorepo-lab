---
name: caching
description: Cache tier selection, TTL, invalidation policy
category: cross-cutting
---

# Skill: Caching Strategy

Cross-cutting policy for choosing, applying, and invalidating caches.

Prerequisite: read `platform/architecture.md` and `platform/observability.md` before using this skill. This skill is the policy/checklist layer; concrete Redis usage patterns live in `backend/redis-session/SKILL.md` and `backend/rate-limiting/SKILL.md`.

---

## Cache Tier Selection

| Tier | Use When | Avoid When |
|---|---|---|
| Local in-process (Caffeine) | Read-mostly, small (< 10k entries), per-instance staleness acceptable | Multi-instance consistency required |
| Redis (shared) | Cross-instance consistency, session/token store, idempotency keys | Hot keys > 10k req/s without partitioning |
| HTTP (CDN/edge) | Public read paths, immutable assets, low personalization | Authenticated user data |
| Database materialized view | Aggregation reads, low write churn | Real-time freshness required |

Default: prefer Redis when in doubt — local caches hide consistency bugs across instances.

---

## Pattern Selection

| Pattern | When | Notes |
|---|---|---|
| Cache-aside (read-through on miss) | Default for read paths | App owns load + invalidation |
| Write-through | Strong consistency between cache and DB | Adds write latency |
| Write-behind | High write throughput, eventual consistency OK | Risk of loss on failure |
| Refresh-ahead | Predictable hot keys | Requires async refresh worker |

---

## Key Naming

- Format: `<service>:<entity>:<id>[:<variant>]`
- Always namespace by service to prevent cross-service collision
- Include version suffix when payload schema changes: `product:v2:{id}`

```
auth:session:{userId}
auth:refresh:{tokenId}
product:detail:v2:{productId}
order:summary:{userId}:{status}
```

---

## TTL Rules

| Data | Default TTL | Rationale |
|---|---|---|
| Session/auth | 30 min sliding | Match access token lifetime |
| Reference data (catalog) | 1 hour | Balance freshness vs DB load |
| Idempotency key | 24 hours | Cover client retry windows |
| Aggregation/analytics | 5 min | Acceptable staleness for dashboards |

Never use infinite TTL without an explicit invalidation channel.

---

## Invalidation

Trigger invalidation from **domain events**, not from controllers, to keep cache consistency in the same transaction boundary.

```
ProductUpdated → invalidate product:detail:v2:{id}, product:list:*
OrderStatusChanged → invalidate order:summary:{userId}:*
```

Use Redis SCAN (not KEYS) for prefix invalidation. Avoid wildcards in hot paths — prefer per-entity invalidation.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Caching authenticated responses on CDN | Add `Cache-Control: private` |
| Cache stampede on hot key expiry | Use mutex/lock or jitter on TTL |
| Unbounded local cache | Always set max size + eviction policy |
| No metrics | Emit hit/miss/evict counters per cache name (see `observability-setup.md`) |
| Caching sensitive PII without expiry | Define explicit TTL + key encryption if at-rest sensitive |

---

## Verification Checklist

- [ ] Cache tier choice documented in service spec
- [ ] Key namespace scoped to service
- [ ] TTL set explicitly, never infinite
- [ ] Invalidation triggered by domain event
- [ ] Hit/miss metrics emitted
- [ ] Load test confirms cache reduces DB QPS as expected
