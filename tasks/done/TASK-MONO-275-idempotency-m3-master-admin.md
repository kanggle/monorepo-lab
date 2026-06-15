# TASK-MONO-275 — ADR-MONO-038 M3: migrate master + admin (Family B) onto the shared idempotency leaf abstractions

**Status:** done

**Type:** TASK-MONO (monorepo-level — migrates two `projects/wms-platform/` services onto `libs/java-web-servlet`; one atomic PR; ADR-038 doc note)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (behavior-preserving Family-B migration + an ADR scope-refinement finding)

---

## Goal

Implement **M3** of the ADR-MONO-038 roadmap for the two Family-B WMS services — `master-service` and `admin-service`. **Implementation finding:** the Family-B *filters* diverge from the Family-A `IdempotencyKeyFilter` on five spec'd, tested behavioral axes (UUID key validation, fail-closed store posture, bounded lock-wait-poll, 4xx caching, different storage-key shape). Force-fitting them onto the Family-A filter is **not** behavior-preserving. Per the ADR-038 § 5 "M3 implementation finding", M3 therefore unifies only the **leaf** abstractions and keeps master/admin's own `IdempotencyFilter`.

## Scope

**In scope** — one atomic PR, two services:

For each of `master-service` and `admin-service`:

1. Add `implementation project(':libs:java-web-servlet')`.
2. The Redis + in-memory store adapters `implement com.example.web.idempotency.IdempotencyStore` (identical 4-method contract) and return the lib `StoredResponse`.
3. Delete the per-service `IdempotencyStore` port + `StoredResponse` + `RequestBodyCanonicalizer` (readTree) + `CachedBodyHttpServletRequest` (wrapper) + master's `RequestBodyCanonicalizerTest` (the readTree canonicalizer is now lib-tested by `JsonTreeBodyCanonicalizerTest`).
4. The service `IdempotencyFilter` **stays** (distinct control flow preserved) but now uses the lib `JsonTreeBodyCanonicalizer` (`BodyCanonicalizer.hash`), the lib `CachedBodyHttpServletRequestWrapper` (`getCachedBody`), the lib `IdempotencyStore`, and the lib `StoredResponse`. Its own storage-key SHA-256 + UUID validation + lock-wait-poll + 4xx-caching + fail-closed posture are unchanged.
5. The filter-registration `@Configuration` now produces a `BodyCanonicalizer` bean (`new JsonTreeBodyCanonicalizer(objectMapper)`).
6. Re-point tests/context smoke tests at the lib `IdempotencyStore` / `StoredResponse`; master's `IdempotencyFilterTest` keeps asserting the full Family-B behavior (UUID 400, 503 fail-closed, 4xx cache, replay/conflict).
7. ADR-MONO-038 § 3.3 M3/M4 + § 5 amended with the "M3 implementation finding".

**Out of scope:** changing master/admin's filter control flow (it's spec'd + tested — Alternative B was explicitly not chosen); generalizing the Family-A filter to absorb Family B (Alternative A — declined in the finding); single-algorithm canonicalizer unification (deferred follow-up).

## Acceptance Criteria

- **AC-1** — Neither service contains its own `RequestBodyCanonicalizer`, `CachedBodyHttpServletRequest`, `IdempotencyStore` port, or `StoredResponse`; each uses the lib leaf abstractions. The Family-B `IdempotencyFilter` classes remain (distinct control flow).
- **AC-2 (behavior equivalence)** — The Family-B Idempotency-Key behavior is byte-identical: UUID validation (400 `VALIDATION_ERROR`), fail-closed store errors (503 `SERVICE_UNAVAILABLE`), bounded lock-wait → 409 `CONFLICT`, 4xx caching, replay / 409 `DUPLICATE_REQUEST`, the `sha256({key}:{method}:{uri})` storage key, and the canonical body hash (the lib `JsonTreeBodyCanonicalizer` is byte-identical to master's former `RequestBodyCanonicalizer`, adopting its lenient `JsonProcessingException|RuntimeException|IOException` fallback — a safe superset for admin).
- **AC-3** — `:master-service:test` + `:admin-service:test` GREEN locally (incl. master's `IdempotencyFilterTest` pinning the Family-B behaviors + both context smoke tests). Full required CI checks GREEN (0 failing) before merge.
- **AC-4** — No service other than master/admin changed; no lib change (M1 already shipped the abstraction). ADR-038 doc note added.

## Related Specs

- `docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md` (§ 3.3 M3, § 5 "M3 implementation finding").
- `projects/wms-platform/specs/services/{master,admin}-service/idempotency.md` (behavior **unchanged** — incl. UUID keys, fail-closed, lock-wait, 4xx caching).

## Related Contracts

None. The Family-B Idempotency-Key HTTP behavior is preserved byte-for-byte.

## Edge Cases

- **admin's narrower catch → lenient superset** — admin's former `RequestBodyCanonicalizer` caught only `JsonProcessingException`; the lib `JsonTreeBodyCanonicalizer` adds `RuntimeException`/`IOException` → raw-byte fallback (master's behavior). A malformed/non-JSON admin body now hashes to its raw bytes rather than propagating — a safe, intended improvement (ADR-038 I3), not a regression.
- **`StoredResponse` field rename** (`createdAt` → lib `storedAt`) — timestamp unused in replay; old 24h-TTL entries deserialize with `storedAt=null`, harmless.
- **Family-B filter unchanged** — UUID validation, fail-closed, lock-wait-poll, 4xx caching all preserved by keeping the service filter.

## Failure Scenarios

- **Behavior regression** — master's `IdempotencyFilterTest` pins the Family-B behaviors; any drift fails the build. Do not merge on a failing required check.
- **Reactive-classpath** — master/admin are servlet apps; adding `libs:java-web-servlet` affects no reactive gateway.
