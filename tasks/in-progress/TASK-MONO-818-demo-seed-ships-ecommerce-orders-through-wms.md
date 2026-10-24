# Task ID

TASK-MONO-818

# Title

Demo seed: the two shipped ecommerce orders are shipped by hand in ecommerce while their wms outbound stays `PICKING` — ship them through wms so both sides agree

# Status

in-progress

# Owner

devops

# Task Tags

- infra
- demo
- seed

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (two seed scripts across a cross-project event path; verifiable only at a bake)
> Drafted 2026-10-25 UTC. Owner decision 2026-10-25 UTC (AskUserQuestion): «wms 가 출고를 밀고 이벤트로 따라오게 (Recommended)». Facts read on origin/main `7ccea0903`.

# Dependency Markers

- 출처: `TASK-MONO-672` § 33rd window (small findings) · § 34th window (Findings) — «two seed orders with ecommerce shipping `SHIPPED` / `DELIVERED` while their wms outbound is `PICKING`».
- 전제: seeds run from the AMI's clone at boot (`infra/demo/demo-up.sh:422` → `infra/demo/seed/seed.sh`), so this must merge **before the next bake** to be measured.
- 관련: `ADR-MONO-066` (B) — picking requests exist only from the reservation reply. `ADR-MONO-022` D4 v2(c) — `deductWmsInventory` / `ManualShipConfirmConsumer`.

# Goal

After a fresh seed, the ecommerce order that sits at `SHIPPED` and the one that reaches `DELIVERED` have a wms outbound order that is `SHIPPED`, reached by the real path (wms pick → pack → ship → `wms.outbound.shipping.confirmed.v1` → ecommerce shipping `SHIPPED`), not two independent hand edits.

# Scope

## In Scope

- **AC-0 facts (measured while drafting, code only):**
  1. ecommerce slots 4 · 5 are paid → `CONFIRMED`, then `ship_progress` drives `PUT /api/shippings/{id}/status` by hand to `SHIPPED` (slot 4) and on to `DELIVERED` (slot 5) (`infra/demo/seed/seed-ecommerce.sh:347-376`, `:510-552`). The body never sets `deductWmsInventory`, so wms hears nothing (`ShippingCommandService.java:171-179`).
  2. Their wms outbound orders come from `ecommerce.fulfillment.requested.v1` (`FulfillmentRequestedConsumer`), with **`orderNo` = the ecommerce order id** (`WmsShippingConfirmedConsumer.java:20-21,60` keys the reverse event on it). Reservation → picking request → `PICKING`, and nothing moves them on: `seed-wms.sh` pushes only its own `$ORDER_NO` (`seed-wms.sh:478-549`).
  3. The minimal alternative does **not** work: with `deductWmsInventory=true`, `ManualShipConfirmConsumer` confirms shipment only from `PACKING_CONFIRMED`; from `PICKING` it is a WARN no-op (physical invariant) (`ManualShipConfirmConsumer.java` class javadoc «any earlier state ⇒ WARN no-op»).
  4. What a general push needs is available: the picking-request read returns `skuId` · `orderLineId` · `locationId` · `qtyToPick` per line (`outbound-service-api.md:548-575`); ecommerce SKUs are `tracking_type NONE`, so confirmation takes `lotId: null` (`seed-wms.sh:340`); the slot orders have exactly one line, quantity 1 (`seed-ecommerce.sh:134`); `GET /api/v1/outbound/orders` filters by `orderNo` (`OrderQueryController.java:76`).
  5. Seed order is not fixed: `seed.sh` runs the started domains in the order it is given and the window starts bundles separately, so the wms bundle may be up before, with, or after the ecommerce seed.
- **Fix:**
  - Factor the pick → pack → seal → ship sequence out of `seed-wms.sh` `push_outbound_to_shipped` into one function both seeds call, taking the order number and an optional lot (no copy — the next contract change must not land in one copy only). It reads `skuId` from the picking request instead of assuming the apple SKU.
  - `seed-ecommerce.sh`, slots 4 · 5: when the wms gateway answers, push the slot's wms outbound to `SHIPPED` (wms operator token), wait for ecommerce shipping to become `SHIPPED` by the event, then continue slot 5 to `DELIVERED` by hand (no wms event exists for delivery). When wms is not up or the push fails, fall back to today's hand path and say so (`seed_warn` naming the mismatch) — the seed never blocks the demo.

## Out of Scope

- The other seed orders (slot 3 `CONFIRMED` / wms `PICKING` is a consistent pair, and the cancel tests depend on it).
- Volumes seeded before this change (the two existing mismatched orders stay until a fresh volume).

# Acceptance Criteria

- [ ] **AC-1** — one shared pick → pack → ship function; `seed-wms.sh`'s own order still reaches `SHIPPED` through it (bake log: «생성 출하 … → SHIPPED» or «이미 SHIPPED»).
- [ ] **AC-2** — static: `bash -n` on every touched script; the repo's demo guards that read these files (`infra/demo/verify-demo-wrapper.sh` cells naming the seed) stay green.
- [ ] **AC-3** — at the next bake / window, fresh volume: the slot-4 and slot-5 orders' wms outbound is `SHIPPED`; ecommerce shipping reached `SHIPPED` with tracking = the wms `shipmentNo` (event path, not `DEMO-1234567890`); slot 5 is `DELIVERED`. The seed log names which path each slot took.
- [ ] **AC-4** — fallback: with wms not started, the ecommerce seed still produces `SHIPPED` / `DELIVERED` by hand and logs the mismatch warning (bake or a window that starts ecommerce alone; ⚪ with the reason if not reached).

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/shipping-service/architecture.md:206-216` (fulfillment out, shipping-confirmed in)
- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` § picking-requests · confirmations · packing-units · shipments
- `infra/demo/seed/README.md`

# Related Contracts

- None changed (seed uses existing APIs and events).

# Edge Cases

- The fulfillment event arrives seconds after the ecommerce order confirms (and later still if wms started after ecommerce) — poll for the wms order by `orderNo` with a bounded wait.
- Re-run on a seeded volume: the wms order is already `SHIPPED` → no-op; ecommerce shipping already `SHIPPED`/`DELIVERED` → today's idempotent handling.
- A picking request missing (reservation consumed before ADR-MONO-066 on an old volume) → the existing warning text, then fall back.

# Failure Scenarios

1. The push is copied instead of shared, and the two copies drift — AC-1.
2. The ecommerce seed ships by hand **and** wms ships → ecommerce shipping already `SHIPPED` when the event arrives; the seed must not hand-ship first.
3. The new path silently falls back every time (e.g. a token or tenant mismatch on the wms gateway) and the bake log still reads green — AC-3 checks the path taken, not only the final status.
