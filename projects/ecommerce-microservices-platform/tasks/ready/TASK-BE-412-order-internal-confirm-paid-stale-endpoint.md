---
id: TASK-BE-412
title: order-service internal endpoint — POST /api/internal/orders/confirm-paid-stale (stale paid-order forward-confirm)
status: ready
project: ecommerce-microservices-platform
service: order-service
type: feature
created: 2026-06-20
---

# TASK-BE-412 — order-service `POST /api/internal/orders/confirm-paid-stale`

> Recommended implementation model: **Opus** (resource-server security wiring + transactional
> per-order isolation + event-replay correctness).
> **PREREQUISITE for TASK-BE-413** (batch-worker caller) — the caller cannot be wired until this
> endpoint is live. Unblocked by TASK-BE-410 (spec-clarification, done).

## Goal

Implement the order-service-owned internal system-command endpoint that **forward-confirms**
paid-but-unconfirmed stale orders (`status='PENDING' AND payment_id IS NOT NULL` past a threshold)
by transitioning each `PENDING → CONFIRMED` through the same application path the normal saga uses,
emitting the standard `OrderConfirmed` event so downstream fulfillment fires. Recovery for a lost
`product.product.stock-changed`/`ORDER_RESERVED` confirmation event — **confirm, never cancel**
(the customer already paid).

## Scope

In:
- `POST /api/internal/orders/confirm-paid-stale` controller on a **gateway-excluded** internal
  route (`/api/internal/orders/**`; the gateway routes only `/api/orders/**` + `/api/admin/orders/**`,
  so no external route reaches it).
- A **resource-server** security filter chain for `/api/internal/**` validating the inbound
  `client_credentials` Bearer JWT (JWKS signature + `exp`/`nbf`/`iat` + issuer + audience).
  **Fail-closed**: missing/expired/malformed/wrong-issuer/wrong-audience → **401 `UNAUTHORIZED`**,
  sweep never runs. This chain is separate from the existing gateway-fronted user chain (which
  trusts `X-User-*` headers); `/api/internal/**` is never gateway-fronted and validates the JWT
  directly.
- Repository query for the predicate (server-side):
  `status='PENDING' AND payment_id IS NOT NULL AND created_at < (now - olderThanMinutes) ORDER BY created_at ASC LIMIT :limit`.
  (New repo method, e.g. `findStalePaidUnconfirmed(Instant cutoff, int limit)` — distinct from
  BE-138's `findStuckPaymentPending` which is `payment_id IS NULL`.)
- Per-order **`REQUIRES_NEW`** confirm that **reuses the normal confirm path**
  `OrderConfirmationService.confirmOrder(orderId)` (re-load + `Order.confirm(clock)` + save +
  `recordOrderConfirmed`/`recordStatusTransition` metrics + `publishOrderConfirmed` via the
  transactional outbox). Cross-bean delegation through a separate handler bean so the
  `REQUIRES_NEW` `@Transactional` goes through the Spring AOP proxy (same split as BE-138
  `OrderStuckDetector` → `OrderStuckRecoveryHandler`; the private-`@Transactional`-on-`this` trap).
- Idempotent skip: `Order.confirm()` already returns `false` (no event) for an already-`CONFIRMED`
  order → count as `skipped`, not errored. Re-load + status re-check inside the per-order TX so a
  row that raced out of `PENDING` (e.g. to `CANCELLED`) is skipped, never force-confirmed.
- Response `{ scanned, confirmed, skipped, confirmedOrderIds? }`; `scanned == confirmed + skipped`
  on success; per-order failures (optimistic-lock conflict, etc.) isolated, left for the next tick,
  excluded from `confirmed`, still `200` with partial tally.
- Metrics on the order-service side as appropriate (status-transition counter already exists;
  reuse `recordOrderConfirmed`). Structured logs per order outcome.
- Integration test (Testcontainers `@SpringBootTest`): seed a paid-unconfirmed `PENDING` order +
  a `PENDING payment_id IS NULL` order (BE-138 bucket) + an already-`CONFIRMED` order; assert only
  the paid-unconfirmed one is confirmed, the `payment_id IS NULL` one is untouched, the confirmed
  one is skipped, and an `OrderConfirmed` outbox row is written for the recovered order. Auth IT:
  no/invalid bearer → 401 (fail-closed); valid client_credentials bearer → 200.

Out:
- batch-worker scheduling / `OrderServiceClient` / caller-side token provider — that is **TASK-BE-413**.
- Any change to the user `cancel` or admin `status` endpoints (explicitly NOT reused).
- IAM seed of `ecommerce-internal-services-client` row (tracked in iam-integration.md; dev secret
  via `.env`; treat acquisition fail-closed at the resource-server, which only validates JWKS).

## Acceptance Criteria

- [ ] **AC-1**: `POST /api/internal/orders/confirm-paid-stale` exists on a gateway-excluded
  `/api/internal/orders/**` route; request `{ olderThanMinutes (default 30, ≥1), limit (default 200, 1..1000) }`,
  response `{ scanned, confirmed, skipped, confirmedOrderIds? }` per the contract.
- [ ] **AC-2**: A dedicated `/api/internal/**` resource-server security chain validates the
  `client_credentials` Bearer JWT and is **fail-closed** — no/expired/malformed/wrong-issuer/wrong-audience
  → 401, sweep does not execute. (IT proves both the 401 and the 200 path.)
- [ ] **AC-3**: Server-side predicate exactly
  `status='PENDING' AND payment_id IS NOT NULL AND created_at < (now - olderThanMinutes) ORDER BY created_at ASC LIMIT :limit`;
  a `payment_id IS NULL` PENDING order is **never** selected (disjoint from BE-138). (IT proves disjointness.)
- [ ] **AC-4**: Each selected order is confirmed via the **same** path as the normal saga
  (`OrderConfirmationService.confirmOrder` semantics) — `PENDING → CONFIRMED`, same `OrderConfirmed`
  event on `order.order.confirmed` co-committed through the outbox; an `OrderConfirmed` outbox row is
  written per confirmed order. (IT asserts the outbox row + payload shape per `order-events.md`.)
- [ ] **AC-5**: Idempotent — already-`CONFIRMED` orders are counted `skipped` (no event, no error);
  re-running the sweep is a no-op for already-recovered orders; per-order failure is isolated and the
  call still returns 200 with a partial tally; `scanned == confirmed + skipped` on a clean run.
- [ ] **AC-6**: Per-order work runs in `REQUIRES_NEW` via a separate handler bean (AOP-proxied),
  not a private `@Transactional` on `this` (BE-138 trap avoided).

## Related Specs

- `specs/contracts/http/internal/order-confirm-paid-stale.md` (the owning contract — full shape)
- `specs/contracts/http/order-api.md` (§ Internal Endpoints + BE-138 boundary table)
- `specs/services/order-service/{overview,dependencies}.md` (internal-route exception)
- `specs/services/order-service/architecture.md` (Layered/hexagonal rules — controller → application → domain)
- `specs/integration/iam-integration.md` (`ecommerce-internal-services-client` ACTIVE; token validation rules)
- `platform/service-types/rest-api.md` (order-service Service Type)

## Related Contracts

- `specs/contracts/http/internal/order-confirm-paid-stale.md` (owned by this endpoint)
- `specs/contracts/events/order-events.md` — `OrderConfirmed` (re-emitted unchanged)

## Edge Cases

- Already-`CONFIRMED` order matched by a stale read → `confirm()` returns false → `skipped`, no event.
- Order raced from `PENDING` to `CANCELLED` (user cancel) between select and re-load → re-check skips it.
- `olderThanMinutes` too small would catch orders the normal saga is about to confirm → default 30,
  validated ≥1; the normal saga still wins races (idempotent skip if it confirmed first).
- `payment_id IS NULL` PENDING order (BE-138 bucket) → never selected.
- Empty result set → `{ scanned:0, confirmed:0, skipped:0 }`, 200.
- Optimistic-lock conflict on save → isolated per-order, excluded from `confirmed`, next tick retries.

## Failure Scenarios

- Missing/invalid bearer → 401 fail-closed (NEVER run the sweep unauthenticated). A regression that
  opened this route un-authenticated would let any internal-network actor mass-confirm orders.
- Reusing the user/admin endpoint instead of a server-side predicate → ownership/role coupling +
  caller would have to enumerate ids it cannot compute. Rejected by design.
- Calling `Order.confirm()` outside the normal path (skipping the event publish) → downstream
  fulfillment would NOT fire and the order would silently stay unfulfilled. MUST reuse the saga path.
