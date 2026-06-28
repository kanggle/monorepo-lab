# TASK-ERP-BE-027 — erp notification-service: conform the inbox response to the shared §1 shape (ADR-MONO-043 P2)

**Status:** done
**Type:** backend (conformance, net-zero additive)

## Goal

First **P2 conformance** increment of ADR-MONO-043 (the ADR is ACCEPTED; P1a contract + P1b `libs/java-notification` are merged). Conform the erp notification-service inbox response to the **§1 canonical envelope** of `platform/contracts/notification-inbox-contract.md` so the **P3 console-bff aggregator** can fan it in with per-domain attribution.

**Scope = the REST §1 shape only** (Axis 1). The deeper lib-internal adoption (consumer dedupe → `EventDedupePort`, delivery → the lib Category-C engine) was investigated and found to be **NOT net-zero for erp** — see the Deferral finding below. Forcing it would change externally-observable behavior the erp CI integration lane pins (HARDSTOP-06), so it is deferred, mirroring the ADR-MONO-038 M3 "share the leaf shape, keep the divergent engine service-side" outcome.

## Scope

`projects/erp-platform/apps/notification-service/`:

- `presentation/dto/NotificationResponse.java` — **additive** contract §1 fields:
  - `sourceDomain` = constant `"erp"` (always; the aggregator's attribution key).
  - `deepLink` = nullable (`null` for now — erp does not yet derive a console approval route; Jackson `NON_NULL` omits it). A future increment may derive `/erp/approvals/{sourceId}`.
  - Pre-existing `sourceType`/`sourceId` preserved unchanged (contract §1.2 non-normative domain extension).
- `presentation/controller/NotificationInboxControllerSliceTest.java` — assert the new `sourceDomain`/`deepLink` on list + getOne.

All three response paths (list / getOne / mark-read) carry `sourceDomain` (they share `NotificationResponse.from(...)`).

## Out of Scope (deferred — see finding)

- **Axis 2** consumer dedupe → lib `EventDedupePort` / `NotificationConsumerSupport`.
- **Axis 3** delivery → lib Category-C engine (`DeliveryDispatcher`/`DeliveryRecord`/`BackoffCalculator`/`DeliveryStore`/`DeliveryRecordEntity`) + channel SPI.
- No producer change (zero-retrofit); no other erp service.

## Deferral finding — why Axes 2/3 are not net-zero for erp (ADR-038 M3 analog)

`libs/java-notification` was generalized from the **wms** Category-C reference, so its concrete shapes are wms's. erp deliberately diverges on four axes, each pinned by the erp spec + the CI `Integration (erp-platform, Testcontainers)` lane:

| Axis | lib (wms-derived) | erp (spec + IT pinned) | Conflict |
|---|---|---|---|
| Terminal status | `DeliveryStatus.SUCCEEDED` | `DELIVERED` + DB `CHECK (status IN ('PENDING','DELIVERED','FAILED'))` (V1) + IT asserts `DELIVERED` | adopting lib enum → CHECK violation + IT fail |
| Backoff | list-indexed `[1,5,30,120,600]` | exponential `RetryBackoffPolicy` (`backoffFor(2)=2s`, `backoffFor(3)=4s`), unit-pinned | different `scheduledRetryAt` → not net-zero |
| Dedupe | `EventDedupePort.process(UUID,String,Runnable)` single-shot | `EventDedupeStore.isProcessed(String)/markProcessed(eventId,topic,aggregateId)` 2-phase + provenance, String ids, own `ApprovalEventEnvelope` | UUID coercion + provenance loss; breaks consumer tests |
| Due-query / DB | `DeliveryStore.findDuePending` rows under Postgres `FOR UPDATE SKIP LOCKED` | id-list + per-row `REQUIRES_NEW` reload, **MySQL 8** | DB-flavor + contract mismatch |

**Net-zero engine convergence is therefore impossible without either (a) per-domain behavior/schema change (not net-zero — needs an architecture.md + CHECK-constraint + IT change), or (b) the lib growing strategy seams** (a pluggable `BackoffStrategy`, a status-mapping seam, a provenance-carrying String-keyed dedupe variant, a DB-portable due-query) — a `libs/java-notification` (ADR-MONO-043 D4) **follow-up**. Until then, the contract (D3) §1 **shape** is the net-zero conformance surface; the delivery/dedupe engine stays erp-owned (ADR-038 M3 precedent: share the leaf, keep the divergent engine service-side).

## Acceptance Criteria

- [x] `NotificationResponse` carries `sourceDomain="erp"` (never null) + nullable `deepLink` (NON_NULL omit), extension fields preserved.
- [x] list / getOne / mark-read all emit `sourceDomain`.
- [x] Net-zero for the CI IT lane (the additive JSON fields are not asserted-absent by any IT; no schema/behavior change).
- [x] `:notification-service:test` BUILD SUCCESSFUL (Docker-free); CI `Integration (erp-platform, Testcontainers)` authoritative for net-zero.
- [x] Axes 2/3 deferral recorded with the four-axis finding.

## Related Specs

- [ADR-MONO-043](../../../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) — ACCEPTED; this is the P2/erp shape-conformance increment.
- [platform/contracts/notification-inbox-contract.md](../../../../platform/contracts/notification-inbox-contract.md) — §1 shape (P1a).
- [ADR-MONO-038](../../../../docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md) §5 M3 — the "share the leaf, keep the divergent engine service-side" precedent.

## Edge Cases / Failure Scenarios

- **Forcing lib enum/backoff/dedupe onto erp** → CHECK violation / IT failure / behavior drift (the deferral finding). Avoided.
- **Aggregator (P3) can't attribute erp items** → resolved: `sourceDomain="erp"` always present.

## Definition of Done

- [x] Additive §1 shape conformance + slice-test assertions.
- [x] `:test` GREEN; deferral finding documented.
- [ ] commit + push (branch `task/erp-be-027-notification-conformance-p2`) + PR + CI `Integration (erp-platform)` GREEN + merge (3-dim verify).
- [ ] Follow-up (separate, post-decision): either a `libs/java-notification` seam-addition task (enabling net-zero engine adoption across domains) or a per-domain decline — to be decided when P2 spans all four domains.
