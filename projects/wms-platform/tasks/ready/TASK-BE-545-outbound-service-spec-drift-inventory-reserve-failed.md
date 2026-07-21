# TASK-BE-545 — outbound-service spec drift: stale `inventory.adjusted` / `InventoryAdjustedConsumer` reserve-failure residue

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (mechanical, meaning-preserving drift correction — fix direction already code-verified by TASK-MONO-196 / TASK-BE-431 / TASK-BE-387; each edit must still be re-grepped against live code before landing)

---

## Goal

Finish the reserve-failure event rename that **TASK-BE-387** started. TASK-MONO-196 replaced the pre-existing `inventory.adjusted{reason=INSUFFICIENT_STOCK}` overload (used as the reserve-failure saga reply) with a **dedicated** `inventory.reserve.failed` event (topic `wms.inventory.reserve.failed.v1`, consumer `InventoryReserveFailedConsumer`). TASK-BE-387 corrected the **inventory-service** specs and the `outbound-events.md` **contract** callout — but it did **not** touch the **outbound-service's own service specs**, which still restate the retired convention (`inventory.adjusted{INSUFFICIENT_STOCK}` / `InventoryAdjustedConsumer`).

This is the classic "deletion leaves survivors — grep the consumers" gap: the retired convention's restatements in the *consuming* service's docs were never swept. This task brings those docs into conformance with the already-shipped code. **Doc-only, meaning-preserving, no code / schema / behavior change.**

## Code verification (the authority for the fix direction)

Confirmed against live code on `origin/main` (2026-07-21):

- **outbound-service has NO `InventoryAdjustedConsumer`.** The reserve-failure consumer is `InventoryReserveFailedConsumer` — `projects/wms-platform/apps/outbound-service/src/main/java/com/wms/outbound/adapter/in/messaging/consumer/InventoryReserveFailedConsumer.java` (`@KafkaListener` on `wms.inventory.reserve.failed.v1`). The full outbound consumer set is `InventoryReserved` / `InventoryReleased` / `InventoryConfirmed` / `InventoryReserveFailed` / `MasterEvent` / `FulfillmentRequested` / `ManualShipConfirm`Consumer — no adjusted consumer.
- **inventory-service emits** `InventoryReserveFailedEvent.eventType() == "inventory.reserve.failed"` on an `INSUFFICIENT_STOCK` reserve shortfall (`ReserveStockService` / `PickingRequestedConsumer`, "TASK-MONO-196: event path emits inventory.reserve.failed"). Genuine stock-adjustment still emits `inventory.adjusted` (`InventoryAdjustedEvent`) — a **different, live** event.
- **Canonical contract** `specs/contracts/events/inventory-events.md` § 4a already defines `inventory.reserve.failed` (topic `wms.inventory.reserve.failed.v1`); `outbound-events.md` § 3 callout + § C1 were already corrected by TASK-BE-387. Only outbound-service's **service-level** docs remain stale.
- **outbound-service/architecture.md is internally contradictory today**: line 227 carries the stale `inventory.adjusted` event row, while line 238 already documents the correct `wms.inventory.reserve.failed.v1 → InventoryReserveFailedConsumer` (TASK-BE-431). The stale row must go.

## Scope

Correct the stale reserve-failure restatements in the **6 outbound-service spec files** below. All paths under `projects/wms-platform/specs/services/outbound-service/`. **Line numbers are a snapshot hypothesis — re-grep at implementation time (see AC-1); the surrounding text, not the line number, identifies the site.**

1. **`architecture.md`** — stale event row `inventory.adjusted (with reason INSUFFICIENT_STOCK) | wms.inventory.adjusted.v1 | Out-of-stock signal during reserve → trigger compensation` (~:227) → the dedicated `inventory.reserve.failed` / `wms.inventory.reserve.failed.v1` reserve-failure reply. (The already-correct note at ~:238 stays; remove the duplication/contradiction.)
2. **`idempotency.md`** — consumer table row `InventoryAdjustedConsumer (filtered: reason=INSUFFICIENT_STOCK) | wms.inventory.adjusted.v1` (~:311) and the consumer list mention (~:391) → `InventoryReserveFailedConsumer` / `wms.inventory.reserve.failed.v1`.
3. **`workflows/outbound-flow.md`** — emit line `inventory.adjusted{reason=INSUFFICIENT_STOCK}` (~:173) and the ASCII step `emit inventory.adjusted.v1 (reason=INSUFFICIENT_STOCK, sagaId)` + `InventoryAdjustedConsumer (filtered: …)` (~:197–198) → the dedicated event / consumer.
4. **`sagas/outbound-saga.md`** — failure-matrix row "Inventory reports INSUFFICIENT_STOCK (via `inventory.adjusted`)" (~:275) and `InventoryAdjustedConsumer (filtered: INSUFFICIENT_STOCK)` (~:628) → the dedicated event / consumer.
5. **`state-machines/order-status.md`** — backorder-trigger note `inventory.adjusted{reason=INSUFFICIENT_STOCK}` (~:117) and the two transition-table rows citing `InventoryAdjustedConsumer` (~:135, ~:163) → the dedicated event / consumer.
6. **`state-machines/saga-status.md`** — `RESERVE_FAILED` entry-trigger `InventoryAdjustedConsumer filtered: INSUFFICIENT_STOCK` (~:32), the ASCII `inventory.adjusted{INSUFFICIENT_STOCK} (Kafka)` (~:54), the mermaid edge `REQUESTED --> RESERVE_FAILED : inventory.adjusted<br/>{INSUFFICIENT_STOCK}` (~:129), and the transition-table row (~:158) → the dedicated event / consumer.

**Secondary — investigate, fix only if confirmed drift (do NOT assume):**

7. **`specs/contracts/events/outbound-events.md` reply payload examples** — the inventory→outbound reply *examples* (~:565–591) may show a `sagaId` field, while the live reply records (`InventoryReservedEvent` / `InventoryReleasedEvent` / `InventoryConfirmedEvent` / `InventoryReserveFailedEvent`) carry only `pickingRequestId` (partition key `locationId`, resp. `pickingRequestId` for reserve-failed), and outbound resolves the saga via `SagaIdResolver` (`findByPickingRequestId`). § C1 already documents this. **Verify the example blocks against the actual `.java` event records before touching**; if the examples already match code, leave them. This is lower-confidence than items 1–6 — record the finding either way in the review note.

**Explicitly out of scope (do NOT touch — genuine live references):**
- The **genuine stock-adjustment** `inventory.adjusted` / `wms.inventory.adjusted.v1` flow (admin projection `InventoryProjectionConsumer`, notification `#wms-alerts`, `InventoryAdjustedEvent`, adjustment-audit) — a real, current event. Only the **reserve-failure** restatements change.
- Any `apps/**` code, Flyway migration, or contract *behavior*.
- The `outbound-events.md` supersession/history note (if present) that intentionally records the pre-MONO-196 name.

## Acceptance Criteria

- **AC-1 (recount, code wins)** — implementation begins by re-grepping `inventory\.adjusted|InventoryAdjustedConsumer|reserve\.failed` under `specs/services/outbound-service/` and re-confirming each site against live code (`InventoryReserveFailedConsumer.java`, `InventoryReserveFailedEvent.java`, `inventory-events.md` § 4a). No edit relies on this task's snapshot line numbers alone; the code is the authority, the line list is a pointer.
- **AC-2 (doc-only)** — `git diff origin/main -- 'projects/wms-platform/apps/**'` is empty; no Flyway, no contract behavior, no ADR. Only files under `specs/services/outbound-service/` (and, iff item 7 is confirmed, `specs/contracts/events/outbound-events.md`) change.
- **AC-3 (no over-reach)** — the genuine adjust-stock `inventory.adjusted` flow (admin/notification/`InventoryAdjustedEvent`) is untouched; only reserve-failure restatements changed. Post-edit, `grep -rn 'InventoryAdjustedConsumer' specs/services/outbound-service/` returns **zero** hits, and every remaining `inventory.adjusted` under that path refers to genuine stock adjustment.
- **AC-4 (self-consistent)** — `architecture.md` no longer contains both the stale row and the correct note; the reserve-failure reply is named consistently across all 6 files (event `inventory.reserve.failed`, topic `wms.inventory.reserve.failed.v1`, consumer `InventoryReserveFailedConsumer`).
- **AC-5 (canonical unchanged)** — `inventory-events.md` (canonical, already defines the event) is unedited.

## Related Specs

- `projects/wms-platform/specs/services/outbound-service/architecture.md`, `idempotency.md`, `workflows/outbound-flow.md`, `sagas/outbound-saga.md`, `state-machines/order-status.md`, `state-machines/saga-status.md`

## Related Contracts

- `specs/contracts/events/inventory-events.md` § 4a — canonical for `inventory.reserve.failed` (referenced, **unedited**).
- `specs/contracts/events/outbound-events.md` — § 3 / § C1 already corrected by TASK-BE-387; only the reply-payload **examples** (item 7) are in-scope, and only if verified as drift.
- ADR-MONO-022 / TASK-MONO-196 — introduced the dedicated `inventory.reserve.failed` event.

## Edge Cases

- **adjust-stock vs reserve-failure collision** — both flows legitimately involve the word "adjusted" in different senses. The discriminator is *purpose*: stock-adjustment audit (`inventory.adjusted`, keep) vs reserve shortfall reply (`inventory.reserve.failed`, the rename target). Every site must be classified before editing.
- **mermaid/ASCII diagram edges** — `saga-status.md` and `outbound-flow.md` carry the stale name inside diagram source, not just prose tables; the diagram edges must be updated too (easy to miss with a prose-only pass).
- **`architecture.md` internal contradiction** — fixing only the note and leaving the row (or vice-versa) leaves the file self-inconsistent; both must reconcile to the dedicated event.

## Failure Scenarios

- **F1 — wrong-direction fix** — guarded by § Code verification + AC-1 (each value pinned to a live `.java` symbol; recount before edit).
- **F2 — over-reach onto genuine `inventory.adjusted`** — guarded by AC-3 (zero `InventoryAdjustedConsumer` hits, residual `inventory.adjusted` must all be genuine adjustment).
- **F3 — false item-7 edit** — guarded by the "verify, fix only if confirmed" framing + AC-2; if the examples already match code, no edit and the finding is recorded.
- **F4 — silent line-number rot** — guarded by AC-1 (text identifies the site, not the line number).

## Provenance

Discovered during a design-review of wms outbound saga event flow (2026-07-21). Sibling of TASK-BE-387 (which fixed the inventory-service side + `outbound-events.md` contract callout of the same TASK-MONO-196 rename but left the outbound-service service specs stale).
