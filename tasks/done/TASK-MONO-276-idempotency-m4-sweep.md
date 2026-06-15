# TASK-MONO-276 — ADR-MONO-038 M4: final idempotency duplication sweep + spec/README cross-references

**Status:** done

**Type:** TASK-MONO (monorepo-level — completes the ADR-038 roadmap; migrates `inventory-service` store/DTO + spec/ADR doc sync; one atomic PR)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (final sweep + a 5th-consumer discovery)

---

## Goal

Implement **M4** (final step) of the ADR-MONO-038 roadmap: sweep the repo for any residual idempotency duplication and bring the specs/ADR in sync with the now-shared implementation.

**Discovery:** a **fifth** idempotency consumer — `inventory-service` — was found. It uses the store **programmatically** (`ReserveStockService`, no REST filter), but carries its own byte-identical `IdempotencyStore` port + `StoredResponse` record (the I2 abstractions). M4 migrates those to the lib too, completing the store/DTO unification across all five wms idempotency consumers.

## Scope

**In scope** — one atomic PR:

1. **`inventory-service`** — add `libs:java-web-servlet`; Redis + in-memory stores `implement com.example.web.idempotency.IdempotencyStore` returning the lib `StoredResponse`; delete the per-service `IdempotencyStore` port + `StoredResponse`; re-point `IdempotencyConfig` + the store test + the context smoke test. Its programmatic usage (`ReserveStockService`) and Redis adapter behavior are unchanged.
2. **Spec cross-references** — fix the stale reference to the deleted `OutboundIdempotencyFilter` in `outbound-service/idempotency.md`; update `master-service/idempotency.md` "Implementation Notes" (the illustrative interface sketch → the realized lib abstraction; mark its own "promote to `libs/` once 3+ services adopt it" prediction fulfilled).
3. **ADR-MONO-038** — § 3.3 M4 marked done with the inventory-discovery + sweep result.
4. **Verification** — repo-wide sweep confirming zero remaining duplicate `StoredResponse` records / `IdempotencyStore` interfaces / `RequestBodyCanonicalizer` / `CachedBodyHttpServletRequest` classes; only the two Family-B `IdempotencyFilter` classes + per-service Redis/in-memory adapters + per-service config/error-writer/metrics legitimately remain.

**Out of scope:** changing `inventory-service`'s programmatic idempotency usage; single-algorithm canonicalizer unification (deferred); the Family-B filter unification (declined per ADR-038 § 5).

## Acceptance Criteria

- **AC-1** — `inventory-service` no longer has its own `IdempotencyStore` port or `StoredResponse`; its stores implement the lib `IdempotencyStore`; `:inventory-service:test` GREEN (incl. context smoke + Redis store test).
- **AC-2 (sweep)** — `git grep` shows **zero** `record StoredResponse`, `interface IdempotencyStore`, `class RequestBodyCanonicalizer`, or `class CachedBodyHttpServletRequest` under `projects/wms-platform/apps/`; the only `IdempotencyFilter` classes are master + admin (Family B).
- **AC-3 (spec sync)** — no spec references a deleted class; the outbound + master idempotency specs reflect the shared `libs/java-web-servlet` abstraction (behavior unchanged).
- **AC-4** — Full required CI checks GREEN (0 failing) before merge. No service other than inventory changed in code; the rest is docs.

## Related Specs

- `docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md` (§ 3.3 M4).
- `projects/wms-platform/specs/services/{outbound,master}-service/idempotency.md` (cross-references corrected; behavior unchanged).
- `platform/shared-library-policy.md` (the project-agnostic test the lib store/DTO satisfy).

## Related Contracts

None. No HTTP/event contract change.

## Edge Cases

- **inventory has no REST filter** — it uses the lib `IdempotencyStore` programmatically (application-level idempotency in `ReserveStockService`); only the store + DTO (I2) are shared, which is exactly the generic part. The ADR-038 *filter* abstraction does not apply to inventory.
- **`StoredResponse` field rename** (`createdAt` → lib `storedAt`) — unused in inventory's flow; benign.

## Failure Scenarios

- **Residual duplication missed** — the AC-2 sweep is the gate; any remaining duplicate is a failed sweep.
- **Reactive-classpath** — inventory is a servlet app; adding `libs:java-web-servlet` affects no reactive gateway.
