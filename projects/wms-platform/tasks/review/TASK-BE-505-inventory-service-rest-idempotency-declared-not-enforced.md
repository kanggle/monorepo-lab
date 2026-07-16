---
id: TASK-BE-505
type: TASK-BE
title: inventory-service REST idempotency is declared in the contract but never enforced (no filter consumes the store)
status: review
service: inventory-service
domain: wms
traits: [transactional]
created: 2026-07-16
---

# TASK-BE-505 — inventory-service REST idempotency declared-but-not-enforced

## Goal

Make `inventory-service` actually enforce the REST idempotency semantics its
contract already promises. Today the service builds the `IdempotencyStore` bean
and **nothing consumes it** — the mutating HTTP endpoints (`/adjustments`,
`/{id}/mark-damaged`, `/{id}/write-off-damaged`, `/transfers`, reservation POSTs)
accept an `Idempotency-Key` header, validate only its *presence*, and then run
the mutation unconditionally. A retried POST double-writes stock.

`inventory-service` is the **sole outlier** among the five mutating rest-api
services: `inbound` and `outbound` wire the shared
`com.example.web.idempotency.IdempotencyKeyFilter`; `master` and `admin` wire a
service-local `IdempotencyFilter`. inventory wires neither.

## Scope

**In scope**
- Wire an idempotency consumer for `inventory-service` mutating REST endpoints so
  that same-key+same-body replays return the cached response without re-executing
  the mutation, and same-key+different-body returns `409 DUPLICATE_REQUEST`.
- Add the HTTP-layer regression tests mandated by
  `specs/services/inventory-service/idempotency.md` §6 (#2 first-request-cached,
  #3 replay-not-re-executed, #4 key-reuse-different-body → 409) — the tests that
  the current suite structurally cannot exercise.
- Reconcile any spec/Javadoc claim that references a filter which does not exist
  in this service (see Edge Cases).

**Out of scope**
- The Kafka event-dedupe mechanism (§2) — already correctly enforced via
  `inventory_event_dedupe` PK + `EventDedupePort`. Do not touch.
- Reservation domain-level idempotency (`picking_request_id` unique constraint,
  §1.7) — already enforced; the new HTTP filter is an additive outer guard, not a
  replacement.
- Any change to the idempotency *contract* — the contract is already correct;
  this task closes the enforcement gap, it does not renegotiate semantics.

## AC-0 — Finding confirmation (re-measured; do not inherit)

Confirmed by direct multi-layer read on `main` @ `d18886dfb` (grep-absence was
corroborated by reading each enforcement site, not assumed):

1. **Filter absent** — `apps/inventory-service/.../config/IdempotencyConfig.java`
   builds only the `IdempotencyStore` bean; its own Javadoc admits *"The
   TASK-BE-022 work that consumes the store (`IdempotencyFilter`) lives
   downstream — this bootstrap task only stands the infrastructure up."* No
   `FilterRegistrationBean`, no filter class anywhere in `inventory-service/src/main`.
2. **Service does not dedupe** — `AdjustStockService` passes
   `command.idempotencyKey()` straight into `StockAdjustment.create(...)` as a
   persisted column (lines 118/136/161) and **never queries it**. No
   `findByIdempotencyKey`/`existsBy...` exists. Same for `TransferStockService`.
3. **No DB fallback** — `db/migration/V3__init_adjustment_transfer_tables.sql`
   declares `idempotency_key VARCHAR(100)` **nullable, no UNIQUE constraint/index**
   on either `stock_adjustment` or `stock_transfer`.
4. **No edge masking** — `gateway-service` only lists `Idempotency-Key` under CORS
   `allowedHeaders` (application.yml:45); it forwards, it does not dedupe.
5. **No lib auto-registration** — `libs/java-web-servlet` ships no
   `META-INF/spring/...AutoConfiguration.imports` / `spring.factories` for the
   filter; it must be wired explicitly (as inbound does).
6. **Existing IT is vacuous (green-CI-hides-broken-path)** —
   `integration/AdjustmentTransferIntegrationTest` drives via
   `adjustStock.adjust(...)` at the **application-service layer** (bypassing the
   HTTP/filter path) and passes a **fresh `UUID.randomUUID()` key on every call**
   (lines 79/108/133/137) — even the two-call test at 130+134 uses two *different*
   random keys. The guarded condition (same key twice, through the web layer) is
   never constructed, so the missing filter cannot be caught.

## AC-1 — Direction (choose before implementing)

- **Option A (RECOMMENDED) — wire the shared `IdempotencyKeyFilter`.** Register
  `com.example.web.idempotency.IdempotencyKeyFilter` (ADR-MONO-038, already on
  the classpath — inventory already imports `com.example.web.idempotency.IdempotencyStore`)
  in `IdempotencyConfig` on `POST /api/v1/inventory/*`, consuming the existing
  store, with a `JsonValueBodyCanonicalizer` and an inventory error-writer that
  emits the contract's `VALIDATION_ERROR` / `DUPLICATE_REQUEST` bodies. This is
  the exact mechanism `inbound`/`outbound` already run, satisfies the contract's
  *replay-cached-response* semantics (which a DB constraint cannot give), and
  removes the now-obsolete "consumed downstream" Javadoc. Least surprise, matches
  fleet precedent. **Recommend proceeding with A.**
- **Option B — DB unique constraint + service-layer existence check.** Add a
  UNIQUE index on `idempotency_key` and a pre-write lookup. Rejected as primary:
  it cannot replay the original `2xx` body (contract line 111 requires cached
  replay, not a 409/500 on the second call), diverges from the fleet's filter
  pattern, and leaves the `mark-damaged`/`write-off` non-`/adjustments` paths
  needing per-table constraints. Only revisit if A is blocked.

If Option A is confirmed, the implementer wires the filter + writes the §6
regression tests. If new information forces B, **STOP and report** — do not
silently switch direction.

## Acceptance Criteria

1. A single mutating endpoint POSTed twice with the **same `Idempotency-Key` and
   same body** produces **exactly one** `stock_adjustment` (or `stock_transfer`)
   row, one `inventory_movement` pair, one `available` decrement, one
   `inventory.adjusted` outbox event; the second call returns the **cached**
   response (not a fresh mutation).
2. Same key + **different body** → `409 DUPLICATE_REQUEST` (contract line 112);
   `inventory.idempotency.mismatch.count` increments.
3. Absent key on a mutating endpoint → `400 VALIDATION_ERROR` (behavior preserved).
4. HTTP-layer regression tests covering §6 #2/#3/#4 exist and **fail against
   pre-change `main`** (run through MockMvc/WebTestClient or the real filter, not
   the app-service shortcut), and pass after the fix. CI Linux
   `Integration (wms-platform)` is the authority (local redis:7-alpine startup is
   a known flake on this host).
5. No regression to reservation idempotency (`picking_request_id`) or Kafka
   event-dedupe.
6. `IdempotencyConfig` Javadoc + `ReserveStockService` comment no longer reference
   a filter that does not exist (Edge Cases).

## Related Specs

- `specs/services/inventory-service/idempotency.md` §1.4 (Redis PENDING/COMPLETE/
  body-hash), §1.5 (409), §6 (mandated tests)
- `specs/services/inventory-service/architecture.md` § Idempotency
- `rules/traits/transactional.md` — T1 (REST idempotency)

## Related Contracts

- `specs/contracts/http/inventory-service-api.md` § Idempotency Semantics
  (lines 108–114) + per-endpoint "Requires `Idempotency-Key`"

## Edge Cases

- `ReserveStockService` line ~98 comment claims body-hash mismatches are "handled
  upstream" by the "REST `IdempotencyFilter`" — a filter that does not exist in
  this service. After Option A the claim becomes true; ensure the comment matches
  the wired reality.
- The filter must NOT double-guard reservations into a broken state: reservation
  POSTs carry both an `Idempotency-Key` (filter) and a `pickingRequestId` (domain
  unique). A same-key replay short-circuits at the filter (cached response) before
  reaching the domain check — verify this composes cleanly (no 409-from-domain
  after a 200-from-cache mismatch).
- inventory has **no webhooks** (those live in inbound), so a plain POST-prefix
  filter on `/api/v1/inventory/*` needs no webhook exclusion.
- Body canonicalizer choice (`JsonValueBodyCanonicalizer` Family-A, as inbound
  uses) must treat semantically-equal bodies as equal so a legitimate retry with
  re-serialized JSON is not mis-flagged as `DUPLICATE_REQUEST`.

## Failure Scenarios

- **Double stock write (primary):** `POST /api/v1/inventory/{id}/mark-damaged`
  `{quantity:3}` with `Idempotency-Key: K`. A network retry / ops double-click
  resends the identical request. Expected: cached `200`, stock moved once. Actual
  (today): mutation runs again → two `stock_adjustment` rows, two
  `inventory_movement` pairs, `available` decremented by 6 not 3, two
  `inventory.adjusted` events. Same shape for `/adjustments` (double adjust) and
  `/transfers` (double transfer).
- **Silent semantic drift:** same `K` sent with a *different* body returns `201`
  instead of the contractual `409 DUPLICATE_REQUEST`, so a client bug that reuses
  a key across distinct operations is masked instead of rejected.
- **Regression guard blind spot:** because the current IT bypasses HTTP and uses
  fresh keys, a future refactor that *removes* even the reservation guard would
  still pass CI — the missing HTTP-layer test is itself a latent risk.

## Notes

- Recommend annotation: `(분석=Opus 4.8 / 구현 권장=Opus — transactional
  idempotency filter wiring + Redis replay semantics + §6 test matrix)`.
- Lifecycle: 3 PRs, all base=main, no stacking (spec writing→ready / impl
  ready→review / chore review→done), per `tasks/INDEX.md`.
