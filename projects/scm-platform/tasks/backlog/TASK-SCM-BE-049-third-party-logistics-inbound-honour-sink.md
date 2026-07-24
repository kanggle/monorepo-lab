# TASK-SCM-BE-049 — 3PL inbound-expected honour + scm-internal sink (route away from wms, record against the 3PL node)

**Status:** backlog
**Type:** TASK-SCM-BE
**Depends on / 전제:** [TASK-SCM-BE-048](TASK-SCM-BE-048-third-party-logistics-inbound-allocation.md) **done** (a replenishment PO can now be *addressed* to a `THIRD_PARTY_LOGISTICS` node — the allocation this task honours) · [ADR-MONO-055](../../../../docs/adr/ADR-MONO-055-order-to-node-allocation-ownership.md) **ACCEPTED** §D4 (the honour sink is an scm-internal expectation record; the external 3PL-WMS notification stays deferred) · [ADR-MONO-054](../../../../docs/adr/ADR-MONO-054-third-party-logistics-node-activation.md) §D3 (honour = route **away from** wms, do not widen the wms DLT gate) · [TASK-SCM-BE-047](../done/TASK-SCM-BE-047-third-party-logistics-observed-stock.md) **done** (observation reconciles the expectation).
**후속 / blocks:** none required. The **external** 3PL-WMS ASN (`ThirdPartyFulfillmentPort`, ADR-054 §D7) and all of Surface B (ADR-055 §D5) stay deferred — **do not** build them here.

> **The honour/sink half.** BE-048 lets demand-planning *decide* a 3PL node is a replenishment target (draft a PO with `destinationNodeType = THIRD_PARTY_LOGISTICS`). This task decides *what happens to that PO's inbound expectation*: it must be **honoured** (ADR-054 §D3 / 052 §D8-3) — routed to an scm sink and recorded against the 3PL node — **not** DLT'd, and **not** sent to wms (wms does not operate the 3PL's WMS — ADR-050 §D4).

---

## Why (the gap this closes)

Today a 3PL-destined PO's inbound expectation is **silently dropped at the scm producer** — [`PurchaseOrderApplicationService.maybePublishInboundExpected`](../../apps/procurement-service/src/main/java/com/example/scmplatform/procurement/application/PurchaseOrderApplicationService.java) only emits `scm.procurement.inbound-expected.v1` when `isWmsWarehouseDestination()` is true; a non-wms destination `log.debug`s and returns (ADR-055 §5). That is *safe* (nothing is DLT'd) but not *honoured*: after BE-048, a legitimately 3PL-addressed replenishment PO would produce **no record anywhere** that we expect stock to arrive at the 3PL. ADR-055 §1.3 named "no 3PL inbound-expectation **sink** exists in scm" as the Surface-A gap this task fills.

The sink is **scm-internal and lightweight** (ADR-055 §D4): an expected-inbound record against the `THIRD_PARTY_LOGISTICS` node in `inventory-visibility-service` — the context that already owns the node and its staleness. The **physical** receiving is the 3PL's own WMS (ADR-050 §D4); we **record** the expectation and let BE-047's observation **reconcile** it when the stock lands. The **external** notification of the 3PL's WMS (an ASN to 품고/ShipBob) is the deferred `ThirdPartyFulfillmentPort` (ADR-054 §D7) and is **explicitly not built here** — the internal record does not depend on it.

## Scope

**In scope (contract-first):**

1. **Producer-side routing** — on PO confirm, a `THIRD_PARTY_LOGISTICS`-destined PO is routed to the **3PL inbound path** (the scm sink) instead of the wms `inbound-expected` publish. The existing wms publish-gate (`isWmsWarehouseDestination()`) is the fork point; the 3PL branch is now a *record*, not a *drop*. The wms consumer gate is **untouched** (ADR-054 §D3).
2. **The sink** — a minimal **expected-inbound** record against the 3PL node in `inventory-visibility-service` (the exact model — a new small aggregate/table vs. an annotation on the node — is a design call the task makes; keep it read-model-shaped and observation-reconcilable). It records: node id, sku(s), expected quantity, source PO reference, expectedAt. Contract row precedes code.
3. **Reconciliation with observation (BE-047)** — when a later 3PL observation (BE-047) shows the expected stock, the expectation is marked satisfied / aged out. Reuse the staleness/observation machinery rather than a bespoke scheduler where possible; a never-satisfied expectation is a visible operational signal, not a silent leak.
4. **Tests** — unit: a 3PL-destined PO records an expectation (does not publish to wms); a wms-destined PO still publishes `inbound-expected.v1` unchanged. Slice/IT: the expectation appears against the 3PL node; observation reconciles it; the wms DLT gate never sees a 3PL destination. Testcontainers IT is CI-authority on Windows.

**Out of scope:**

- **External 3PL-WMS notification** (`ThirdPartyFulfillmentPort` / an ASN to the vendor) — ADR-054 §D7, stays deferred. The sink is scm-internal only.
- **Widening the wms DLT gate** — ADR-054 §D3. wms keeps refusing non-`WMS_WAREHOUSE` destinations; we route so it never receives one.
- **Surface B / outbound** (ADR-055 §D5) — untouched.
- **The allocation decision** (BE-048) — this task assumes the PO is already correctly 3PL-addressed; it does not decide the node.
- **Mutating 3PL stock** — we record an *expectation* and *observe* arrival (BE-047); we never pick/pack/adjust at a 3PL.

## Acceptance Criteria

- [ ] A confirmed PO with `destinationNodeType = THIRD_PARTY_LOGISTICS` records an expected-inbound against the 3PL node in inventory-visibility; contract row lands **before/with** the code.
- [ ] The same PO does **not** publish `scm.procurement.inbound-expected.v1` toward wms, and the wms consumer/DLT gate never receives a 3PL destination (verified — no DLT).
- [ ] A `WMS_WAREHOUSE`-destined PO still publishes `inbound-expected.v1` exactly as before — no regression to ADR-050.
- [ ] A later BE-047 observation of the expected stock reconciles (satisfies/ages) the expectation; an unmet expectation remains visible (not silently dropped).
- [ ] **No** external 3PL-WMS notification, **no** `ThirdPartyFulfillmentPort`, **no** wms gate widening, **no** Surface-B/outbound change, **no** 3PL stock mutation.
- [ ] Build & Test + scm Integration CI lanes **GREEN**.

## Related Specs

- `projects/scm-platform/specs/services/procurement-service/architecture.md` — the PO-confirm publish path forks: wms → `inbound-expected.v1`; 3PL → scm sink.
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` / `data-model.md` — the expected-inbound record against a 3PL node + observation reconciliation.
- `docs/adr/ADR-MONO-055-order-to-node-allocation-ownership.md` §D4; `docs/adr/ADR-MONO-054-...md` §D3/§D7; `docs/adr/ADR-MONO-050-...md` §D4.

## Related Contracts

- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` (**canonical**) — the expected-inbound record surface (if operator/read-visible).
- `projects/scm-platform/specs/contracts/events/*inbound-expected*` — document that a 3PL destination routes to the scm sink, not the wms event (the event itself is unchanged; the producer's routing is).
- No **wms** contract change (wms is deliberately not involved — ADR-050 §D4 / ADR-054 §D3).

## Edge Cases

- **Expectation never satisfied** — a 3PL that never reports the stock (observation stays STALE) must leave the expectation visible/aging as an ops signal, not silently purge it.
- **Duplicate PO / re-confirm** — recording must be idempotent on the PO reference (a re-confirmed or replayed PO does not double-record the expectation).
- **Partial arrival** — an observation showing *some* of the expected quantity: decide partial-satisfy vs binary; document. (v1 may be binary with a note.)
- **3PL node deregistered/absent** — a PO addressed to a node that no longer exists must fail closed with a clear error, not create an orphan expectation.
- **wms-destined PO unaffected** — the fork must not perturb the existing wms publish (timing, payload, outbox) — assert byte-equivalence of the wms branch.

## Failure Scenarios

- **A — DLT leak.** If routing sends the 3PL destination toward the wms consumer, it DLTs (ADR-055 §5: `IllegalArgumentException` → non-retryable → DLT). The fork must keep 3PL entirely producer-side.
- **B — External integration smuggled in.** Building any real 3PL-WMS notification (`ThirdPartyFulfillmentPort`, a vendor ASN adapter) means the task absorbed ADR-054 §D7 deferred work. The sink is scm-internal; stop.
- **C — wms gate widened.** Editing `CreateScmInboundExpectationService`'s whitelist to accept `THIRD_PARTY_LOGISTICS` violates ADR-054 §D3/§A1 (a wms ASN the 3PL's flow can never satisfy). Route away instead.
- **D — Silent leak restored.** If the 3PL branch still just `log.debug`+returns (the pre-055 drop) with no record, the honour is unbuilt — the gap ADR-055 §D4 named is still open.
- **E — Contract after code.** The expected-inbound contract row precedes/accompanies the code.

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): a new small expected-inbound model + producer fork + observation reconciliation — the model-shape and reconciliation design are genuine calls (**Opus**); the fork wiring is mechanical once shaped. 구현은 BE-048 done 이후 backlog → ready 승격 시.
