# TASK-SCM-BE-044 — shipping.confirmed consumer + FulfillmentRouter (the live seam trigger)

**Status:** in-progress
**Type:** TASK-SCM-BE
**Depends on / 전제:** [TASK-SCM-BE-043](../done/TASK-SCM-BE-043-goodsflow-adapter-carrier-router.md) **done** (`CarrierRouter` + the stored `requested_carrier_code` routing signal this consumer populates) · [TASK-SCM-BE-042](../done/TASK-SCM-BE-042-logistics-service-bootstrap.md) **done** (Kafka consumer **scaffold** `KafkaConsumerConfig` — group `scm-logistics-v1`, manual ack, earliest, read_committed; `processed_events`; `Dispatch` + `DispatchShipmentUseCase`) · [ADR-MONO-053](../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) **ACCEPTED** §D4. Reads: [ADR-MONO-052](../../../../docs/adr/ADR-MONO-052-transport-context-map.md) §D5 (fact-event seam).
**후속 / blocks:** Phase 2 3PL (`FulfillmentRouter` 3PL branch — gated on ADR-052 §D8-3) · wms TMS-adapter retirement (D8, separate cross-project TASK-MONO-XXX) · Phase 3 스윗트래커 tracking (optional).

> **Phase-1 slice (3 of 3) — closes the loop.** BE-042 built the dispatch machinery (one vendor) and BE-043 added the second vendor + `CarrierRouter`, but nothing yet **feeds** them from the live seam: dispatch is only reachable via the operator `:retry` on a seeded row. This task wires the **`ShippingConfirmedConsumer`** (`@KafkaListener` on `wms.outbound.shipping.confirmed.v1`) — the real trigger — and introduces the **`FulfillmentRouter`** with **only the self-fulfillment branch wired** (every Phase-1 input arrives already wms-fulfilled → carrier dispatch; the 3PL branch is a documented Phase-2 extension point, not active logic). After this task the full Phase-1 loop runs end-to-end: *wms ships → event → logistics routes a carrier → vendor dispatched → `DISPATCHED`/`DISPATCH_FAILED`*.

---

## Goal

Wire `logistics-service`'s **live inbound seam** exactly as declared in
`specs/services/logistics-service/architecture.md` (§ Service Type Compliance →
event-consumer, § Multimodal seam → `FulfillmentRouter`) and
`specs/contracts/events/logistics-dispatch-subscriptions.md`: a `@KafkaListener`
consumes `wms.outbound.shipping.confirmed.v1` (group `scm-logistics-v1`), dedups on
`eventId` (T8) + `shipment_id`, creates a `Dispatch(PENDING)` carrying the event's
`carrierCode` as the stored routing signal, routes through `FulfillmentRouter`
(self-branch → carrier dispatch via the BE-043 `CarrierRouter`), and records
`DISPATCHED` / `DISPATCH_FAILED` — a **vendor failure is never a consume failure**.
Realises **ADR-MONO-053 §D4** and completes **Phase 1**.

## Scope

**In scope** (under `projects/scm-platform/apps/logistics-service/` unless noted):

1. **`ShippingConfirmedConsumer`** (`adapter/inbound/messaging/`) — `@KafkaListener(topics="wms.outbound.shipping.confirmed.v1", groupId="scm-logistics-v1", containerFactory=…)` on the **existing BE-042 scaffold** `kafkaListenerContainerFactory` (manual ack). Mirror the sibling `demand-planning-service` `WmsLowStockAlertConsumer` pattern (same wms→scm cross-project consumer shape).
2. **Envelope + payload mapping** — a consumer-side DTO for the **wms camelCase envelope** (`eventId`/`eventType`/`eventVersion`/`occurredAt`/`producer`/`aggregateType`/`aggregateId`/`tenantId`/`payload`) + the consumed payload subset (`shipmentId`, `shipmentNo`, `orderId`, `orderNo`, `warehouseId`, `carrierCode` **nullable**, `shippedAt`, `lines[]`) — **ignore unknown fields** (forward-compat, `FAIL_ON_UNKNOWN_PROPERTIES=false`). Authoritative shape: wms `outbound-events.md` §7. Reuse the envelope-mapping approach proven in `inventory-visibility-subscriptions.md` / `WmsLowStockAlertConsumer`.
3. **`FulfillmentRouter`** (`application/routing/`, framework-free) — introduced with **only the self-fulfillment branch wired**: `route(shipment) → SELF → carrier dispatch`. The **3PL branch is a documented extension point** (a `switch`/strategy seam that in Phase 1 always resolves `SELF`, with the 3PL arm either absent or a guarded `UnsupportedOperationException`/no-op clearly marked *Phase 2, ADR-052 §D8-3*). It must attach the Phase-2 3PL arm **without** modifying the dispatch path (ADR-053 §D4). Unit-tested (self-branch resolves; the extension point is documented, not active routing).
4. **Consume flow (the use case wiring)** — a `ConsumeShippingConfirmedUseCase` (or fold into the existing `DispatchShipmentUseCase` orchestration) that, in **one transaction**:
   a. dedup on envelope `eventId` via `processed_events` (T8) — duplicate → skip, no mutation;
   b. business guard: `dispatch.shipment_id` unique — a redelivered event finds the existing dispatch and **no-ops** (idempotent upsert);
   c. `Dispatch.create(...)` PENDING with `requestedCarrierCode = payload.carrierCode` (the BE-043 stored routing signal), `tenantId` echoed from the envelope;
   d. `FulfillmentRouter.route(...)` → SELF → `DispatchShipmentUseCase.dispatch(...)` (which routes via `CarrierRouter` → `ShipmentDispatchPort`);
   e. **manual ack after the dispatch TX commits.**
5. **Error handling + DLT** — wire a `DefaultErrorHandler` (backoff `[1s,2s,4s]` = 3 attempts) → DLT `wms.outbound.shipping.confirmed.v1.DLT` into the container factory (BE-042 left this to BE-044). **Malformed envelope** (null `eventId` / null `payload`) → **non-retryable** → immediate DLT + ops alert (mirror `demand-planning`'s `NonRetryableConsumerException`). **A vendor dispatch failure is NOT a consume failure** — the event is **ack'd**, the dispatch persisted `DISPATCH_FAILED`, recovery is the operator `:retry` (a failed carrier never DLTs or blocks the seam, S5). Transient DB fault → exhaust the 3 attempts → DLT.
6. **Observability** — the `logistics_event_dedup_hits_total` + dispatch metrics already declared; ensure the consumer path emits dedup hits and dispatch outcomes with per-vendor labels (via the existing adapter metrics). Kafka consumer trace propagation from the wms envelope; dispatch is a child span.

**Out of scope** (named follow-ups):
- **3PL active routing** (the `FulfillmentRouter` 3PL arm actually sending to a `ThirdPartyFulfillmentPort`, `THIRD_PARTY_LOGISTICS` node) → **Phase 2**, gated on ADR-052 §D8-3. Here it is only a documented extension point.
- **Destination-address enrichment / full label buy** → separate Phase-1 follow-up (the documented "known input gap" — the seam carries no address; Phase 1 = carrier handover / tracking-registration).
- **wms TMS-adapter retirement (D8)** — once this consumer is live, wms stops calling the vendor; that wms simplification is a **separate cross-project TASK-MONO-XXX** (do NOT touch `projects/wms-platform/` here).
- **Tracking (Phase 3 스윗트래커)** — a different axis, optional.
- **Any wms-side change** — the wms producer is unchanged (additive consumer only, ADR-052 §D5 "no new contract").

## Acceptance Criteria

- [ ] `./gradlew :projects:scm-platform:apps:logistics-service:build` succeeds; `ShippingConfirmedConsumer` + `FulfillmentRouter` present; the wms producer is untouched (no `projects/wms-platform/` diff).
- [ ] **Testcontainers Kafka IT** (CI-authoritative): publish `outbound.shipping.confirmed` with a domestic `carrierCode` (e.g. `CJ-LOGISTICS`) → a `Dispatch` row is created and routed to **굿스플로** (via `CarrierRouter`); an international `carrierCode` → EasyPost; a **null** `carrierCode` → the default vendor + `CARRIER_UNROUTABLE` degrade.
- [ ] **Dedup IT**: a duplicate `eventId` → no second dispatch (processed_events T8); a redelivered event with a **new** eventId but the **same** `shipmentId` → finds the existing dispatch and no-ops (no double-dispatch).
- [ ] **Malformed → DLT IT**: null `eventId` / null `payload` → **non-retryable**, lands on `wms.outbound.shipping.confirmed.v1.DLT` (not silently dropped), ops alert path exercised.
- [ ] **Vendor-failure ≠ consume-failure IT**: a WireMock vendor 5xx-exhaustion during consume → the event is **ack'd** (offset advances, NOT sent to DLT), the dispatch is `DISPATCH_FAILED`, and the operator `:retry` then recovers it to `DISPATCHED`.
- [ ] `FulfillmentRouter` unit test: Phase-1 input resolves to the **self / carrier-dispatch** branch; the 3PL extension point is present but not active (documented, guarded).
- [ ] `standalone` profile: no wms present → the topic never arrives → empty dispatch list, no hard dependency (the `StandaloneDispatchAdapter` still serves `:retry` rehearsal).
- [ ] scm Integration + E2E CI lanes **GREEN** (CI is authority — Windows local cannot run Testcontainers; read the junit XML). The scm **E2E cross-service smoke** should now be able to exercise the real wms→logistics loop (extend it if the existing smoke covers the seam — otherwise a follow-up INT task).
- [ ] No new error code needed; no wms-side change; no 3PL active path.

## Related Specs

- `projects/scm-platform/specs/services/logistics-service/architecture.md` § Service Type Compliance (event-consumer: group, dedup, manual ack, retry→DLT, malformed→DLT, vendor-failure≠consume-failure) + § Multimodal seam (`FulfillmentRouter` self-branch, 3PL extension point) + § Saga (Cat C post-commit)
- `projects/scm-platform/specs/services/logistics-service/external-integrations.md` §3 (dispatch failure states & saga coupling — the consume→dispatch sequence) + §4 (Kafka consumer policy: earliest, manual ack, `DefaultErrorHandler [1s,2s,4s]` → DLT)
- `projects/scm-platform/apps/demand-planning-service/.../WmsLowStockAlertConsumer.java` + `NonRetryableConsumerException.java` — the **sibling wms→scm consumer** to mirror (envelope mapping, retry/DLT, non-retryable classification)

## Related Contracts

- `projects/scm-platform/specs/contracts/events/logistics-dispatch-subscriptions.md` — **the authoritative subscription contract** (consumed subset, envelope convention, `CarrierRouter` selection, idempotency T8, retry+DLT, malformed handling, standalone degradation)
- `projects/wms-platform/specs/contracts/events/outbound-events.md` **§7** `outbound.shipping.confirmed` — **authoritative producing-side schema** (payload fields incl. nullable `carrierCode`; **read-only** — do not modify)

## Edge Cases

- **`carrierCode` is nullable at source.** wms `outbound-events.md` §7 marks `carrierCode` "may be null if carrier was not specified at shipping time". A null → the configured **default vendor** + `CARRIER_UNROUTABLE` degrade (BE-043 behaviour) — never a drop. The router already handles this; the consumer must pass the raw nullable through as `requestedCarrierCode`, not coerce it.
- **Envelope is wms camelCase, NOT the scm `BaseEventPublisher` shape.** Map the wms envelope explicitly (the subscription contract § Envelope is explicit about this) — do not assume the scm publisher shape.
- **Dedup is two-layered.** `eventId` (processed_events) AND `shipment_id` uniqueness. The same `eventId` may be redelivered by Kafka *or* by the wms outbox retry on the publisher side; a *different* eventId for the same shipment (wms republish) must also no-op via the `shipment_id` guard. Both layers are required.
- **Vendor failure must ack, not DLT.** The single most important behaviour: a carrier 5xx-exhaustion during consume is a recorded `DISPATCH_FAILED` + ack, NOT a consume retry/DLT (S5, Cat C). Only DB/infra faults and malformed envelopes exhaust-then-DLT. A test that lets a vendor failure DLT the event is asserting the wrong contract.
- **FulfillmentRouter must not become active 3PL routing.** Wire ONLY the self-branch. If the 3PL arm does anything but resolve-`SELF` / documented-guard, this task absorbed Phase 2 — pull it back.
- **Do not touch wms.** The producer is unchanged. A `projects/wms-platform/` edit means the "no new contract" seam invariant (ADR-052 §D5) was violated — the D8 wms simplification is a separate task.
- **Windows local = not authority.** Testcontainers Kafka IT SKIPs locally on Windows; CI Linux is the gate. (Host may also be memory-constrained — rely on CI, read the junit XML.)

## Failure Scenarios

- **A — Scope creep into Phase 2.** If a `ThirdPartyFulfillmentPort`, `THIRD_PARTY_LOGISTICS` node, or active 3PL routing appears, this task absorbed Phase 2 (gated on ADR-052 §D8-3) — split out. The `FulfillmentRouter` 3PL arm is a documented extension point only.
- **B — Vendor failure DLTs the seam.** If a carrier 5xx during consume sends the event to DLT (instead of ack + `DISPATCH_FAILED`), the eventual-consistency contract (S5 / Cat C) is broken — fix. A failed carrier is recovered by `:retry`, never by re-consuming.
- **C — Missing dedup layer.** If only `eventId` OR only `shipment_id` is guarded (not both), a redelivery double-dispatches — restore both layers.
- **D — wms-side edit.** Any `projects/wms-platform/` change violates the additive-consumer seam invariant — revert; the D8 wms retirement is a separate cross-project task.
- **E — Malformed silently dropped.** A null-envelope/null-payload event that is ack'd-and-dropped (instead of routed to DLT + alert) loses data silently — it must DLT non-retryably.
- **F — CI-RED at merge.** The 3-dim merge-verification rule applies; do not merge on a red scm Integration/E2E lane. The vendor-failure-≠-consume-failure and dedup ITs are the highest-signal guards — expect them to red first if the ack/DLT branching is wrong.

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): an event-consumer with two-layer idempotency, a retry/DLT + non-retryable-classification error handler, a routing seam, and Testcontainers-Kafka IT asserting the ack-vs-DLT branching → **Opus** (backend-engineer dispatch, `model=opus`). Not a mechanical edit.
