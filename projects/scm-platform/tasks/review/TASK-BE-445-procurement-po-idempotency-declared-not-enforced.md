---
id: TASK-BE-445
type: TASK-BE
title: procurement-service PO mutation idempotency is declared in the contract but never enforced
status: review
service: procurement-service
domain: scm
traits: [transactional]
created: 2026-07-17
---

# TASK-BE-445 — procurement PO idempotency declared-but-not-enforced

## Goal

Make `procurement-service` actually enforce the `Idempotency-Key` semantics its
contract already promises on the JWT-authed PO mutation endpoints. Today the
header is *required* (missing → 400 `IDEMPOTENCY_KEY_REQUIRED`) but its **value is
discarded** — `draft`/`confirm`/`cancel` never dedupe on it, the declared
`idempotency_keys` table is dead (no entity/repository/consumer), and the mapped
`422 IDEMPOTENCY_KEY_MISMATCH` path is thrown nowhere. A retried `POST /po`
produces **two distinct purchase orders** (→ duplicate downstream supplier orders).

## AC-0 — Finding confirmation (re-measured against `main` @ 234698c12)

1. **Declaration** — `specs/contracts/http/procurement-api.md:7-10`: "Mutating
   endpoints require `Idempotency-Key` … Same key + different payload → 422
   `IDEMPOTENCY_KEY_MISMATCH`." Reaffirmed per-endpoint (`:65-68`, `:185-188`,
   `:206`, `:222-225`). `db/migration/procurement/V1__init.sql:197-209` declares a
   dedicated `idempotency_keys` table (PK `(idempotency_key, endpoint, tenant_id)`
   + `payload_hash` + `response_status` + `response_body`), described as the S2
   dedupe cache with **Redis primary / DB fallback (fail-CLOSED, Failure Scenario D)**.
2. **Enforcement — absent** — `PurchaseOrderController.draft` (`:51-68`),
   `confirm` (`:125-132`), `cancel` (`:134-143`) take `@RequestHeader("Idempotency-Key")`
   but never pass the value downstream: `DraftPurchaseOrderCommand`/`ConfirmPurchaseOrderCommand`/
   `CancelPurchaseOrderCommand` have **no key field** (only `submit` forwards it, and
   only to the OUTBOUND supplier call). `PurchaseOrderApplicationService.draft`
   performs no key lookup/insert and deliberately randomises `po_number` from the
   UUIDv7 tail so two rapid identical drafts do **not** collide — duplicates are
   engineered to *succeed*.
3. **Dead table + dead exception** — no `@Table("idempotency_keys")` entity, no
   repository, no filter/interceptor consumes it (only `WebhookSignatureFilter`,
   HMAC, unrelated, exists). `IdempotencyKeyMismatchException` is mapped to 422 in
   `GlobalExceptionHandler` but **thrown nowhere in `src/main`** (constructed only
   in a handler unit test) — the declared 422 path is unreachable.
4. **Cross-service parity** — the ASN webhook path IS correctly deduped
   (`uq_asn_tenant_supplier_ref` UNIQUE + `findBySupplierAsnRef` guard), and
   `from-suggestion` is idempotent on `sourceSuggestionId`. So the team knows the
   pattern; the JWT-authed PO mutation endpoints are the **outlier** that declares
   idempotency without wiring it (mirrors wms inventory TASK-BE-505 and the
   cross-service-parity heuristic).
5. **Vacuous test (green-CI-hides-it)** — the only idempotency IT,
   `SupplierIdempotencyIntegrationTest`, verifies the **outbound supplier** dedup,
   drives `service.submit(...)` at the **application-service layer** (bypassing the
   HTTP controller), and leans on the state machine
   (`PoStatusTransitionInvalidException`) — it never touches `idempotency_keys` and
   never covers duplicate `POST /po` drafts. `PurchaseOrderControllerSliceTest`
   only asserts the missing-header 400.

**Concrete leak (draft):** `POST /api/procurement/po` with `Idempotency-Key: k1`;
the response is lost to a timeout; the client retries with the same `k1` → **two
distinct POs** (distinct `id` + `po_number`), two audit rows, two
`scm.procurement.po.*` outbox events → duplicate downstream supplier orders.
confirm/cancel are incidentally shielded by the state machine (a second transition
throws), so `draft` is the concrete duplication leak.

## Direction (fork — decide before implementing)

- **Option A (RECOMMENDED) — DB-fallback-table-backed idempotency filter,
  fail-closed.** Wire the declared `idempotency_keys` table (entity + repository)
  behind a servlet filter over `POST /api/procurement/po`, `/{poId}/submit`,
  `/{poId}/confirm`, `/{poId}/cancel`: canonical `payload_hash` + tenant (from the
  verified JWT) + endpoint + key. First request → execute, cache
  `(response_status, response_body)` on 2xx keyed by the PK; replay → same
  payload_hash returns the cached response (mutation not re-run); different
  payload_hash → **422 `IDEMPOTENCY_KEY_MISMATCH`**. The PK `(idempotency_key,
  endpoint, tenant_id)` gives race-safety (concurrent first-requests → one wins the
  insert, the loser replays/serialises). **Fail-CLOSED** (contract Failure Scenario
  D): a store error rejects rather than proceeds — unlike the wms shared filter's
  fail-open posture, which is why the shared `IdempotencyKeyFilter` is NOT reused
  here. This is the declared persistence layer and gives full correctness on its
  own.
- **Redis-primary fast path** — the contract names Redis as the primary and the DB
  table as the fallback. Option A implements the **fail-closed correctness layer**
  (DB); the Redis fast path is a pure performance optimisation with identical
  observable semantics and is a deliberate **follow-up** (note it in the DONE record
  — do not silently claim it done).
- **Option B (rejected) — reuse the shared `com.example.web.idempotency.IdempotencyKeyFilter`.**
  It fail-**opens** on store errors (409 semantics), contradicting scm's declared
  fail-closed 422 posture; adopting it would silently change the contract's failure
  behaviour. Rejected.

## Acceptance Criteria

1. `POST /api/procurement/po` retried with the same `Idempotency-Key` + same
   payload → **one** PO; the second call replays the cached 201 (no second PO, no
   second outbox event).
2. Same key + **different** payload (same endpoint+tenant) → **422
   `IDEMPOTENCY_KEY_MISMATCH`**.
3. Missing key → **400 `IDEMPOTENCY_KEY_REQUIRED`** (behaviour preserved).
4. Two concurrent identical first-requests → exactly one execution (PK race).
5. Store/DB failure → fail-CLOSED (reject), not silent proceed.
6. Regression tests exercise the HTTP layer (not the app-service shortcut) and
   fail against pre-change `main`; mutation-checked. CI Linux
   `Integration (scm-platform)` is the authority (local Docker is flaky here).
7. The dead `IdempotencyKeyMismatchException` is now thrown on the mismatch path;
   the `idempotency_keys` table is consumed.

## Related Specs / Contracts

- `specs/contracts/http/procurement-api.md` § Idempotency
- `rules/traits/transactional.md` — T1 (REST idempotency)
- `db/migration/procurement/V1__init.sql` (`idempotency_keys` table)

## Edge Cases

- Tenant must come from the **verified JWT** (ActorContext), never a header — the PK
  includes tenant_id, so a cross-tenant key reuse must not collide.
- `submit`/`confirm`/`cancel` already forward or state-guard; the filter still
  covers them for replay-cache correctness (and to make the declared 422 reachable
  uniformly), but must compose cleanly with the state machine (a cached 2xx replay
  short-circuits before the domain transition).
- Payload canonicalisation must treat semantically-equal JSON as equal so a
  legitimate re-serialised retry is not a false 422.
- `cancel` has an optional body (`required=false`) — an absent body hashes to a
  stable canonical empty value.

## Failure Scenarios

- **Duplicate PO (primary):** retried `POST /po` → two POs + duplicate supplier
  orders (today). After: one PO, cached replay.
- **Silent semantic drift:** same key + different payload → today a *second* PO
  (draft) or a state-machine 422 with the wrong code elsewhere; after: uniform 422
  `IDEMPOTENCY_KEY_MISMATCH`.

## Notes

- Recommend annotation: `(분석=Opus 4.8 / 구현 권장=Opus — fail-closed idempotency
  with response-replay, PK race-safety, HTTP-layer mutation-checked tests)`.
- Larger build than the wms/ecommerce siblings (fail-closed response-cache +
  DB entity + filter). Redis-primary fast path deferred as a documented follow-up.
