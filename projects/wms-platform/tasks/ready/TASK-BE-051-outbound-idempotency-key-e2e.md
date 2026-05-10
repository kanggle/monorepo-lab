# Task ID

TASK-BE-051

# Title

outbound-service Idempotency-Key end-to-end — store/lookup wiring complete (T1)

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- test

---

# Goal

Complete the **Idempotency-Key handling** end-to-end across all outbound mutation endpoints. Today, controllers only call `RequestContext.requireIdempotencyKey(...)` (header presence check) but the actual store/lookup via Redis is **not wired** into the use-case path. Honoring the same key twice currently allows duplicate side-effects despite the spec mandate.

After this task, a repeat POST/PATCH/DELETE with the same `Idempotency-Key` returns the SAME response shape (status code, body, headers) without producing a second side-effect.

---

# Scope

## In Scope

- Implement `IdempotencyStorePort` (already declared in `application/port/out/`) with Redis adapter
- Wire idempotency check into all mutation endpoints:
  - `POST /api/v1/outbound/orders` (createOrder)
  - `POST /api/v1/outbound/orders/{id}:cancel` (cancelOrder)
  - `POST /api/v1/outbound/picking-requests/{id}/confirmations` (confirmPicking)
  - `POST /api/v1/outbound/orders/{id}/packing-units` (createPackingUnit)
  - `PATCH /api/v1/outbound/packing-units/{id}:seal` (sealPackingUnit)
  - `POST /api/v1/outbound/orders/{id}/packing-units:confirm` (confirmPacking)
  - `POST /api/v1/outbound/orders/{id}/shipments` (confirmShipping)
  - `POST /api/v1/outbound/shipments/{id}:retry-tms-notify` (retryTmsNotify, post-TASK-BE-049)
- Per `transactional` T1 contract:
  - Key format: opaque string, max 255 chars
  - TTL: 24 hours (Redis EXPIRE)
  - Lookup-or-Insert atomicity (Lua script or Redis SET NX EX)
  - On hit: return cached `(status_code, body_json, content_type)` snapshot — NO side-effect
  - On miss: execute use-case + store result snapshot
- Snapshot must include the response ETag (versioned) so concurrent retries from the same caller don't see stale state
- Metrics:
  - `outbound.idempotency.lookup.count{result=hit|miss}`
  - `outbound.idempotency.lookup.duration.seconds`
  - `outbound.idempotency.store.failure.total`
- Standalone profile fallback: in-memory store (`StandaloneIdempotencyStore`) with `@Profile("standalone")`
- Unit + IT (Redis Testcontainers) for hit / miss / TTL expiry / different key same body / same key different body (conflict 409)

## Out of Scope

- ERP webhook idempotency (separate, already wired via dual-layer dedupe in `webhook_inbox`)
- Async event idempotency (separate, via `outbound_event_dedupe`)
- Cross-service idempotency (each service maintains its own key namespace)
- Garbage collection beyond Redis TTL

---

# Acceptance Criteria

- [ ] All 8 mutation endpoints honor `Idempotency-Key` end-to-end.
- [ ] Repeat with same key + same body → byte-identical response, no side-effect.
- [ ] Repeat with same key + different body → 409 CONFLICT (`IDEMPOTENCY_KEY_REUSE_INVALID`).
- [ ] Different keys → independent execution.
- [ ] TTL = 24h verified in IT (use Testcontainers Redis with TTL fastforward, or short-TTL config in `application-test.yml`).
- [ ] All 3 metrics present + tagged.
- [ ] Standalone profile uses in-memory store (no Redis required for local dev).
- [ ] CI Linux Integration job covers all scenarios.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `rules/traits/transactional.md` (T1 idempotency contract — primary)
- `specs/services/outbound-service/architecture.md` §Idempotency
- `specs/contracts/http/outbound-service-api.md` — confirm `Idempotency-Key` header documented for all mutation endpoints
- `specs/services/outbound-service/idempotency.md` (Open Item — author or update)

# Related Skills

- `.claude/skills/backend/redis-session/SKILL.md` (Redis Lua atomicity pattern)
- `.claude/skills/backend/standalone-profile/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/outbound-service-api.md` (no schema change; behavior contract — same key returns same response)
- `IDEMPOTENCY_KEY_REUSE_INVALID` error code added to `platform/error-handling.md` (if not present)

---

# Target Service

- `outbound-service`

---

# Architecture

Follow:

- `specs/services/outbound-service/architecture.md` (Hexagonal — `IdempotencyStorePort` is an outbound port; Redis is an adapter)

---

# Implementation Notes

- Redis Lua script for atomic SET-NX-EX + GET (avoid race between two concurrent same-key requests)
- Hash function for body comparison: SHA-256 of canonical-JSON, stored in Redis alongside response snapshot
- Apply at controller-aspect or interceptor level to avoid threading through every use-case method
- Per spec, controllers call `RequestContext.requireIdempotencyKey(...)` for presence; this task adds a separate `IdempotencyAspect` (or `IdempotencyHandlerInterceptor`) wrapping the controller method
- Cross-bean delegation if aspect-based — avoid Spring AOP self-invocation per memory `feedback_refactor_code_baseline_it.md`
- Standalone fallback: simple `ConcurrentHashMap` with `ScheduledExecutorService` evicting expired entries

---

# Edge Cases

- Caller sends same key + body but body has irrelevant whitespace differences → canonical-JSON comparison handles this.
- Caller's first request times out before storing snapshot → Redis SET NX prevents partial state; second attempt sees miss and retries (correct — first never committed).
- Server crashes between use-case completion and snapshot store → second attempt re-executes use-case → idempotent if downstream handlers are idempotent (they are, per T1).
- Caller uses an `Idempotency-Key` value > 255 chars → 400 BAD REQUEST (validation error, not 409).
- Same key reused across endpoints → currently scoped per endpoint (key namespace = endpoint URI + method). Document this contract.

---

# Failure Scenarios

- Redis unavailable → fail-open: log + metric, allow request through (NOT fail-closed; otherwise availability < idempotency-correctness, which violates T1 trade-off in WMS context).
- Redis returns stale snapshot due to replication lag → caller sees old response; harmless because response is identity-equivalent for the same input.
- Snapshot serialization fails → log, treat as miss, allow request through.
- TTL expires mid-request → no impact (atomic Lua SET-NX-EX runs at request start).

---

# Test Requirements

- Unit: `IdempotencyAspect` / `IdempotencyHandlerInterceptor` logic against mocked `IdempotencyStorePort`.
- Integration (Testcontainers Redis): all 5 scenarios in Acceptance Criteria.
- Failure-mode (per `transactional` Required Artifact 5): same key POSTed concurrently → exactly one side-effect (Lua atomicity).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (unit + IT)
- [ ] Tests passing locally and in CI Linux Integration job
- [ ] `IDEMPOTENCY_KEY_REUSE_INVALID` error code in `platform/error-handling.md`
- [ ] `outbound-service-api.md` documents `Idempotency-Key` for all 8 mutation endpoints
- [ ] `idempotency.md` spec updated (Open Item closure)
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-code wms outbound-service` dry-run (Manual Finding #4). The presence-only check (`requireIdempotencyKey`) was a partial implementation; this task completes the T1 contract.

분석=Opus 4.7 / 구현 권장=Opus (Lua atomicity + canonical-JSON + aspect-based + 8-endpoint surface).
