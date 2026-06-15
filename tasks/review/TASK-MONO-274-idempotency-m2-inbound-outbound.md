# TASK-MONO-274 — ADR-MONO-038 M2: migrate inbound + outbound onto the shared Idempotency-Key filter

**Status:** review

**Type:** TASK-MONO (monorepo-level — migrates two `projects/wms-platform/` services onto the `libs/java-web-servlet` shared filter; one atomic PR)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (behavior-preserving migration of Family-A services onto the shared abstraction)

---

## Goal

Implement **M2** of the ADR-MONO-038 roadmap: migrate the two Family-A WMS services — `inbound-service` and `outbound-service` — off their own per-service idempotency filters/ports/DTOs onto the shared `IdempotencyKeyFilter` (M1, TASK-MONO-273). **Net-zero** behavior: both already used the lib `BodyHashUtil` (Family-A canonical hash) since MONO-271, so the canonical hash is unchanged; only the filter/port/DTO plumbing collapses.

## Scope

**In scope** — one atomic PR, two services:

For each of `inbound-service` and `outbound-service`:

1. The Redis + in-memory store adapters now `implement com.example.web.idempotency.IdempotencyStore` (the per-service port had the identical 4-method contract) and return the lib `StoredResponse` record.
2. Delete the per-service `IdempotencyStore(Port)` interface and `StoredResponse` record (use the lib's).
3. Delete the per-service filter (`InboundIdempotencyFilter` / `OutboundIdempotencyFilter`) and its unit test (the control flow is now tested in the lib `IdempotencyKeyFilterTest`).
4. Add a service `IdempotencyErrorWriter` implementation writing the service's own `ApiErrorEnvelope` (byte-compatible with the former filter's 409 / 503 / — for outbound — 400 responses).
5. Rewrite the filter-registration `@Configuration` to register `IdempotencyKeyFilter` with a service `IdempotencyFilterConfig` (methods, path predicate, key-length, TTLs), the `JsonValueBodyCanonicalizer`, the store, the error writer, and (outbound only) an `OutboundIdempotencyMetrics` wrapping the optional `MeterRegistry`.
6. Re-point the store tests + context smoke tests at the lib `StoredResponse` / `IdempotencyStore`; rewire the outbound `IdempotencyFilterRedisIT` to instantiate the shared filter with the outbound config.

**Per-service config preserved:** inbound = POST only, `/api/v1/inbound/**` (webhook-skip), no key-length guard, no metrics. outbound = POST/PATCH/PUT/DELETE, `/api/v1/outbound/**` (webhook-skip), 255-char key guard, Micrometer metrics (`outbound.idempotency.*`).

**Out of scope:** `master` + `admin` (M3, Family B); final duplication sweep + spec cross-references (M4); single-algorithm canonicalizer unification (deferred).

## Acceptance Criteria

- **AC-1** — Neither service contains its own idempotency filter / `IdempotencyStore(Port)` / `StoredResponse`; both register `IdempotencyKeyFilter` via their config.
- **AC-2 (behavior equivalence)** — The Idempotency-Key behavior is byte-identical per service: replay, 409 `DUPLICATE_REQUEST`, 503 `PROCESSING` (+ `Retry-After`), and (outbound) 400 over-length-key; the canonical hash is unchanged (both still use `BodyHashUtil`); the Redis key shapes (`inbound:idempotency:` / `outbound:idempotency:`) and TTLs are unchanged; outbound's metric names are preserved.
- **AC-3** — `:inbound-service:test` + `:outbound-service:test` GREEN locally; both context smoke tests boot. The outbound `IdempotencyFilterRedisIT` (Testcontainers) passes on CI. Full required CI checks GREEN (0 failing) before merge.
- **AC-4** — No change to any service other than inbound/outbound (and no lib change — M1 already shipped the abstraction).

## Related Specs

- `docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md` (§ 2 I1–I6, § 3.3 M2).
- `projects/wms-platform/specs/services/{inbound,outbound}-service/idempotency.md` (behavior **unchanged**).

## Related Contracts

None. The Idempotency-Key HTTP behavior is preserved byte-for-byte.

## Edge Cases

- **`StoredResponse` field rename** (`createdAt` → lib `storedAt`) — the timestamp is not used in replay logic; old 24h-TTL entries serialized with `createdAt` deserialize with `storedAt=null` (Spring Boot's `fail-on-unknown-properties=false`), harmless and self-healing within the deploy window.
- **outbound metrics when no `MeterRegistry`** — standalone profile → `IdempotencyMetrics.NO_OP`.
- **inbound over-length key** — inbound configures no `maxKeyLength`, so `writeKeyTooLong` is never invoked (its default throw is unreachable).

## Failure Scenarios

- **Idempotency IT regression** — if the migration altered behavior, the outbound `IdempotencyFilterRedisIT` (real-Redis Testcontainers) fails on CI; do not merge on any failing required check (CLAUDE.md 3-dim rule).
- **Reactive-classpath** — both services are servlet apps already depending (transitively/now) on `libs:java-web-servlet`; no reactive gateway is affected.
