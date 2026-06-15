# TASK-BE-387 — wms spec drift-fix (code-verified Tier A/B corrections from TASK-BE-385 § Findings)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (each fix verified against production code before edit)

---

## Goal

Close the **Tier A/B drift findings** that TASK-BE-385 (wms spec refactor pass) flagged but could not auto-fix under the `/refactor-spec` meaning-preserving constraint. Each finding is a wms spec **restating a contract with a stale/wrong value** (event name, topic, enum, field, uniqueness scope). This task fixes the docs to match the **implemented truth** — and **every fix direction was verified against the actual production code / Flyway migration / domain rule before editing** (not from the discovery agents' assertions alone).

This is a **doc-only, meaning-preserving** drift correction: the spec is brought into conformance with the already-shipped code. No code, no schema, no behavior change. (Distinct from `/refactor-spec`, which forbids touching contract values — here the contract *value in the doc* is provably wrong vs the code, so correcting it is reality-alignment, not a new decision. The "ADR-trigger = competing-convention" rule applies: there is no competing convention, only a stale doc vs authoritative code → no ADR needed.)

## Code verification (the authority for each fix)

- **`inventory.reserve.failed`** (A1/A2): `InventoryReserveFailedEvent.eventType() == "inventory.reserve.failed"`, written by `ReserveStockService` on an `INSUFFICIENT_STOCK` shortfall (`PickingRequestedConsumer` "TASK-MONO-196: event path emits inventory.reserve.failed"). The old `inventory.adjusted` overload was retired. Canonical `inventory-events.md` § 4a + § C1 already define it; the service docs (and the `outbound-events.md` § 3 callout) carried pre-MONO-196 residue.
- **`previousStatus`** (A6): `OrderCancelledEvent` field + `EventEnvelopeSerializer` key are `previousStatus` (not `priorStatus`).
- **6 notification subscriptions** (A3): `AlertConsumer` has exactly 6 `@KafkaListener` topics (inventory.alert / inventory.adjusted / inbound.inspection.completed / inbound.asn.cancelled / outbound.order.cancelled / outbound.shipping.confirmed).
- **dedupe CHECK 4 values + delivered topic** (A4): `V1__init.sql` `CHECK (outcome IN ('QUEUED','FILTERED','NO_RULE','ERROR'))`; the only delivery topic is `wms.notification.delivered.v1` (failure rides `outcome=FAILED_RETRY_EXHAUSTED`).
- **`SUCCEEDED`** (A5): `DeliveryStatus.SUCCEEDED`; `writeDeliveryCompleted(delivery, "SUCCEEDED")` (the value `SENT` does not exist in the enum).
- **`inbound.asn.received`** (B1): `AsnReceivedEvent.eventType() == "inbound.asn.received"` (not `inbound.asn.created`).
- **`outbound.picking.completed`** (B2): `PickingCompletedEvent.eventType() == "outbound.picking.completed"` (not `outbound.picking.confirmed`).
- **location code globally unique** (B3): `rules/domains/wms.md` W3 + `V4__init_location.sql` ("location_code is GLOBALLY unique — not scoped to warehouse or zone" + `UNIQUE (location_code)`).

## Scope (Applied — 15 spec files, all under `projects/wms-platform/specs`)

1. **inventory** — `state-machines/reservation-status.md` + `sagas/reservation-saga.md`: reserve-failure event `inventory.adjusted` → `inventory.reserve.failed` (only the INSUFFICIENT_STOCK / stale-master reply path; genuine adjust-stock `inventory.adjusted` untouched). `architecture.md` + `domain-model.md` + `overview.md`: add the missing `inventory.reserve.failed` (topic `wms.inventory.reserve.failed.v1`) to the event tables/lists; `overview.md` "4 events" → "5 events".
2. **notification** — `overview.md`: wrong Kafka-consume list → the real 6 subscribed topics. `idempotency.md`: dedupe CHECK 2→4 values + `wms.notification.delivery.failed.v1` → `wms.notification.delivered.v1`. `external-integrations.md`: delivery status `SENT` → `SUCCEEDED` (4 sites). `domain-model.md` + `database-design.md`: `priorStatus` → `previousStatus`.
3. **contracts** — `events/notification-subscriptions.md`: `priorStatus` → `previousStatus` (2 sites). `events/outbound-events.md`: § 3 callout + consumer-expectation residue `inventory.adjusted` → `inventory.reserve.failed` (aligning the file to its own § C1 + § 4a reconciliation note; the explanatory note at the bottom that documents the supersession is intentionally retained).
4. **inbound** — `overview.md`: `inbound.asn.created.v1` → `inbound.asn.received.v1` (2 sites; `inbound.asn.cancelled` untouched).
5. **outbound** — `overview.md`: `outbound.picking.confirmed.v1` → `outbound.picking.completed.v1` (2 sites; `outbound.shipping.confirmed` untouched).
6. **master** — `overview.md`: location-code uniqueness "within a warehouse" → globally across the entire system (W3) (2 sites).

**Out of scope (deferred — TASK-BE-385 § Findings Tier C–E):**
- Tier C saga config/state semantics (sweeper cap 10 vs 5, missing saga ASCII states, force-completes phrasing, reason-code reuse, lot terminal-state) — need per-item config/code confirmation; not value-name drift.
- Tier D identical-recap dedup — verify-identity-per-file refactor, low urgency.
- Tier E notification domain-model missing sibling-standard sections — requires authoring (not drift).
- **No code / Flyway / contract *behavior*** — only the doc text that restated a contract value was corrected. `inventory-events.md` was already correct (canonical) and is unedited.

## Acceptance Criteria

- **AC-1 (code-verified)** — every applied value matches the authoritative production code / migration / rule cited in § Code verification. No fix relies on a discovery agent's claim alone.
- **AC-2 (meaning-preserving / doc-only)** — `git diff origin/main -- 'projects/wms-platform/apps/**'` is empty. No Flyway, no contract behavior, no ADR change. 15 files, all under `specs/`.
- **AC-3 (no over-reach)** — genuine adjust-stock `inventory.adjusted`, `inbound.asn.cancelled`, `outbound.shipping.confirmed`, and the intentional `outbound-events.md` supersession note are untouched (only the stale restatements changed).
- **AC-4 (canonical unchanged)** — `inventory-events.md` (which already defined `inventory.reserve.failed`) is unedited; service docs were updated to match it (lower-priority source → canonical).

## Related Specs

- inventory: `architecture.md`, `domain-model.md`, `overview.md`, `state-machines/reservation-status.md`, `sagas/reservation-saga.md`
- notification: `overview.md`, `idempotency.md`, `external-integrations.md`, `domain-model.md`, `database-design.md`
- inbound/outbound/master: `overview.md`

## Related Contracts

- `specs/contracts/events/notification-subscriptions.md`, `specs/contracts/events/outbound-events.md` (drift residue corrected to match the implemented event field/name; behavior unchanged).
- `specs/contracts/events/inventory-events.md` (canonical for `inventory.reserve.failed` — referenced, unedited).
- `docs/adr/ADR-MONO-022` / TASK-MONO-196 (the reconciliation that introduced the dedicated `inventory.reserve.failed` event).

## Edge Cases

- **adjust-stock vs reserve-failure** — `inventory.adjusted` is a real event for the stock-adjustment flow; only the reservation-failure restatements were changed. Both flows now read correctly.
- **`outbound-events.md` supersession note** — the bottom note documents that § 3 *used to* say `inventory.adjusted`; it is preserved verbatim (explanatory migration history), while the live § 3 callout + consumer expectation now name the dedicated event.

## Failure Scenarios

- **F1 — wrong-direction fix** — guarded by § Code verification (each value pinned to a `.java`/`.sql`/rule citation).
- **F2 — over-reach onto a correct token** — guarded by AC-3 (genuine adjust/cancel/shipping references and the supersession note left intact).
