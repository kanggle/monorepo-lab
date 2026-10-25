# Task ID

TASK-MONO-818

# Title

Demo seed: the two shipped ecommerce orders are shipped by hand in ecommerce while their wms outbound stays `PICKING` — ship them through wms so both sides agree

# Status

done

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

---

# Implementation — 2026-10-25 UTC

- `infra/demo/seed/lib-wms-outbound.sh` (new) — `wms_ship_outbound <gw> <orderNo> [lotId] [cartonNo] [waitSeconds]`: find by `?orderNo=` (bounded wait) → already `SHIPPED` = no-op → picking request (reservation reply) → confirm → packing unit → seal → shipment. `skuId` comes from the picking request; `lotId` is `null` when not given; one-line orders only (stops with a warning otherwise). The file name is not `seed-*.sh`, so `seed.sh` never runs it as a domain.
- `infra/demo/seed/seed-wms.sh` — `push_outbound_to_shipped` now calls it with `$ORDER_NO` · `$LOT_ID` · `BOX-DEMO-1` (same token, same calls, same order as before; the moved comment block keeps the «volume history, not a code defect» text).
- `infra/demo/seed/seed-ecommerce.sh` — before the hand-shipping, `ship_via_wms` for slots 4 · 5: skip if the shipping is no longer `PREPARING` (re-run); skip with a warning if `wms-gateway-service` is not running; otherwise push through wms with the `ecommerce` operator token (the event-created wms row's tenant is the envelope's `ecommerce` — `FulfillmentRequestedConsumer` → `ReceiveOrderService`), then wait up to 90 s for the shipping to leave `PREPARING` (consumer token, buyer-only endpoint). On any failure: today's hand path + a warning naming the mismatch (401/403/404 add «tenant or role»). `ship_progress` no longer re-sends a stage the shipping has already passed (an event-shipped `SHIPPED` would otherwise risk the demo tracking number overwriting the wms `shipmentNo`).
- `infra/demo/seed/README.md` — one paragraph under «도메인 규칙을 우회하지 마라».

# Verification

- AC-2 — `bash -n` rc=0 on all three scripts. `infra/demo/verify-demo-wrapper.sh` (static, local): cell `(y)` «시드 배선 유지 (도메인 시드 6개 · 전부 dbexec 경유 …)» ok, 84 cells ok and 0 failed before the run hit my 900 s limit at cell `(z25)` (rc=124 — not a verdict; the CI `verify-demo-wrapper --live` job is the full run, see the PR).
- Not measurable here: AC-1 · AC-3 · AC-4 need a stack — next bake (the seed runs at boot from the AMI's clone) / window. What to read in the boot log: «주문 4: wms 출하 확정 → 이벤트로 배송 SHIPPED» and «주문 5: …», wms «생성 출하 <orderId> → SHIPPED», and **no** «wms 경로로 출고하지 못했습니다». Then the slot-4 shipping's tracking number should be the wms `shipmentNo`, not `DEMO-1234567890`.
- 🔴 Unverified assumption that AC-3 will decide: the `ecommerce` assume token carries the wms outbound write roles (`OUTBOUND_WRITE`). The ecommerce tenant subscribes wms (`seed-ecommerce.sh` operator-block comment), but I did not read the derived role set for it. If it does not, the fallback fires and the log says «테넌트·역할을 의심하라».

Open: AC-1 · AC-3 · AC-4 (bake / window).

---

## CORRECTION — 35차 창 라이브 결과 (2026-10-25 UTC)

35th window: bake `bake-54b79233c` (AMI `ami-068ae07b2b3ac7797`, pin PR #4558), fresh volume, all nine bundles started. Evidence is the boot journal and host reads from that window; the raw outputs are in the session scratchpad, not in the repo.

- **Boot gate.** Journal `[seed:` lines «주문 4: wms 출하 확정 → 이벤트로 배송 SHIPPED (wms created)» and «주문 5: … (wms created)». «wms 경로로 출고하지 못했습니다» count = 0. Seed ✗ lines = 0 (the only ⚠ is the known minio image-seed warning). 100 containers, none unhealthy or exited except `ecommerce-minio-init` Exited (0), which is normal.
- **AC-1 ✅** — the shared pick → pack → ship path ran: the slot 4 and slot 5 lines above, and on the second `seed-wms` run «존재 출고 SO-DEMO-0001 이미 SHIPPED» (one of the two log lines the AC names).
- **AC-2** — not re-measured in this window. Its record stays § Verification (static `bash -n` and the `verify-demo-wrapper.sh` seed cell). The live boot adds only that the three scripts ran with 0 ✗ lines.
- **AC-3 ✅** — fresh volume.
  - Slot 4, order `c76625ca…`: ecommerce `SHIPPED`; wms outbound `SHIPPED`, saga `COMPLETED`; shipping `trackingNumber` `SHP-20261025-5153` = the wms `shipment_no`; `wmsRouted: true`.
  - Slot 5, order `9d05ca68…`: ecommerce `DELIVERED`; wms `SHIPPED` / `COMPLETED`; tracking `SHP-20261025-2800` = the wms `shipment_no`; `statusHistory` PREPARING → SHIPPED → IN_TRANSIT → DELIVERED.
  - The seed log names the path each slot took (event path, not the hand path).
- **AC-4 ⚪** — needs a boot with ecommerce started and wms not started. This window started every bundle, so the fallback was not reachable. The AC text says «⚪ with the reason if not reached», so this ⚪ with its reason is the closure the AC asks for.
- 🔵 A side effect of the window, not part of this ticket: with ≥1 wms alert row the console `/wms` overview degrades. Recorded in `TASK-MONO-672` § 35th window — results.

⇒ 판정: 모든 AC 닫힘 — 닫기 chore 대상 (AC-4 is closed by its own «⚪ with the reason» clause; AC-2 rests on § Verification, which the close chore's dimension (d) must open and read)
