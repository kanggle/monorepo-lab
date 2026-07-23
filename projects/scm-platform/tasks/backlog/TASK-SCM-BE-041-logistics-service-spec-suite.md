# TASK-SCM-BE-041 — logistics-service spec suite (Phase 1: architecture + EasyPost dispatch + shipping.confirmed subscription)

**Status:** backlog
**Type:** TASK-SCM-BE
**Depends on / 전제:** [ADR-MONO-053](../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) **ACCEPTED** (this task may not move `backlog/ → ready/` while 053 is PROPOSED). Reads: [ADR-MONO-052](../../../../docs/adr/ADR-MONO-052-transport-context-map.md) §D2/§D5/§D7, [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §D4.
**후속 / blocks:** TASK-SCM-BE-042 (logistics-service bootstrap impl), which reads the specs this task writes.

> **Promotion gate.** `backlog → ready` is allowed only once ADR-MONO-053 is ACCEPTED (its architecture decisions are this task's source of truth) — see `projects/scm-platform/tasks/INDEX.md` § Move Rules. Until then this task is a placeholder capturing the agreed Phase-1 spec scope; do not implement.

---

## Goal

Author the **spec suite** for a new scm service, `logistics-service`, realising ADR-MONO-053 Phase 1: the **carrier-dispatch** half of the hybrid carrier + 3PL multi-node fulfillment design. This is the spec task that precedes the bootstrap impl (BE-042), mirroring the demand-planning precedent (TASK-SCM-BE-023 spec → TASK-SCM-BE-024 bootstrap).

Scope of this task is **spec-only** — no application code, no Flyway, no gateway wiring. It produces the documents BE-042 will build against, and the additive contract that lets `logistics-service` consume the **existing** wms `outbound.shipping.confirmed` event with **no new event contract** (ADR-052 §D5).

## Scope

**In scope** (all under `projects/scm-platform/`):

1. `specs/services/logistics-service/architecture.md` — canonical `architecture.md` form (ADR-MONO-012): identity table + Service Type composition (`event-consumer` + `rest-api`), hexagonal layout, dependencies. Records:
   - the `outbound.shipping.confirmed` **subscription** as the wms↔transport seam (ADR-052 §D5);
   - `ShipmentDispatchPort` (application port) with `EasyPostDispatchAdapter` / `GoodsflowDispatchAdapter` (`@Profile("!standalone")`) + `StandaloneDispatchAdapter` (`@Profile("standalone")`) (ADR-053 §D2);
   - `CarrierRouter` selection (region → vendor) (ADR-053 §D3);
   - the `FulfillmentRouter` **seam with only the self-fulfillment branch** wired, 3PL branch documented as Phase 2 (ADR-053 §D4);
   - the relocation target for wms's `SHIPPED_NOT_NOTIFIED` state + `:retry` endpoint + `tms_status` + `carrierCode` (ADR-053 §D8), documented as Phase-1 inbound from wms;
   - Service Type test-requirement sections per `platform/service-types/{event-consumer,rest-api}.md`.
2. `specs/services/logistics-service/external-integrations.md` — the `integration-heavy` required artifact (I1–I4, I7–I9) for **EasyPost** and **굿스플로**: direction, auth, per-vendor timeout / circuit-breaker / retry+jitter / idempotency-key / bulkhead (no pool shared across vendors), observability hooks, failure-mode matrix. Mirrors `outbound-service/external-integrations.md` §2 (TMS) structure.
3. `specs/contracts/events/` — the additive **subscription** doc recording `logistics-service` as a consumer of wms `outbound.shipping.confirmed.v1` (consumed subset, group id, dedup via `eventId`, retry→DLT, degradation). **No wms-side schema/payload change** (additive consumer bullet only, mirroring the `replenishment-subscriptions.md` precedent).
4. `PROJECT.md` Service Map — move `logistics-service` from **v2 (deferred)** to **v1-active** with the Phase-1 responsibility (carrier dispatch), noting Phase 2 (3PL) still deferred. Traits frontmatter **byte-unchanged**.
5. `projects/scm-platform/tasks/INDEX.md` — reflect this task's lifecycle position.

**Out of scope** (explicitly deferred):
- Any application code / Flyway / gateway route / CI filter (→ BE-042).
- 3PL specs (`ThirdPartyFulfillmentPort`, `THIRD_PARTY_LOGISTICS` node factory, demand-planning routing) — Phase 2, gated on D8-3 (ADR-053 §D5/§D7).
- 스윗트래커 tracking (`ShipmentTrackingPort`) — Phase 3 optional (ADR-053 §D6).
- The wms retirement **implementation** (ADR-053 §D8) — cross-project impl task, later in Phase 1.

## Acceptance Criteria

- [ ] `specs/services/logistics-service/architecture.md` exists, passes the ADR-MONO-012 canonical-form shape (identity table + Service Type composition present), declares Service Types `event-consumer` + `rest-api`, and each declared Service Type resolves to an existing `platform/service-types/<type>.md`.
- [ ] `architecture.md` documents the seam as a **subscription** to `outbound.shipping.confirmed` (not a synchronous call), citing ADR-052 §D5; no synchronous wms→logistics REST is proposed.
- [ ] `architecture.md` documents `ShipmentDispatchPort` + the three adapters (EasyPost / 굿스플로 / standalone) and the `CarrierRouter`, and shows the `FulfillmentRouter` seam with the 3PL branch explicitly marked Phase 2.
- [ ] `external-integrations.md` exists and, for **each** of EasyPost and 굿스플로, declares all of: direction, auth, timeout, circuit-breaker, retry+jitter, idempotency-key, bulkhead, failure-mode matrix — with **no pool shared across the two vendors** stated explicitly (integration-heavy I9).
- [ ] The subscription contract doc is **additive**: `git diff` shows no change to any wms event **schema/payload**; only a consumer registration is added (verify the wms `outbound-events.md` producer surface is byte-unchanged except an optional additive consumer bullet).
- [ ] `PROJECT.md` Service Map lists `logistics-service` as v1-active (Phase-1 carrier dispatch), Phase 2 (3PL) noted deferred; the YAML frontmatter (`domain`/`traits`/`service_types`) is **byte-unchanged** (`git diff --stat` shows frontmatter lines untouched).
- [ ] No application code, Flyway migration, gateway route, or CI workflow file is modified by this task (spec-only; `git diff --name-only` limited to `.md` under `projects/scm-platform/` + this task file move).
- [ ] All internal doc links resolve (dead-ref check clean); ADR-053 / ADR-052 references use correct anchors.

## Related Specs

- `projects/scm-platform/specs/services/logistics-service/architecture.md` (**new**)
- `projects/scm-platform/specs/services/logistics-service/external-integrations.md` (**new**)
- `projects/scm-platform/specs/services/demand-planning-service/architecture.md` (precedent: 3-facet Service Type composition; do not modify)
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` §2 (TMS) — structural template for the EasyPost/굿스플로 integration doc
- `projects/wms-platform/specs/services/outbound-service/architecture.md` §2.10 — the `SHIPPED_NOT_NOTIFIED` / post-commit shape being relocated (read-only reference)

## Related Contracts

- `projects/scm-platform/specs/contracts/events/<new>-subscriptions.md` (**new** — additive subscription to wms `outbound.shipping.confirmed.v1`), precedent: `replenishment-subscriptions.md`
- `projects/wms-platform/specs/contracts/events/outbound-events.md` — the producer surface (read-only; at most an additive consumer bullet, no schema change)
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` — the operator `:retry` route is **reserved** here (documented, not wired; wiring is BE-042)

## Edge Cases

- **Seam already emits.** `outbound.shipping.confirmed` exists and is emitted today; the spec must consume it as-is (no request for a new field). If a needed field is genuinely absent, that is a **separate** wms contract task, not a silent widening here.
- **Standalone profile.** The spec must define `StandaloneDispatchAdapter` behaviour (in-memory ack) so local/standalone bring-up and CI without vendor credentials work, matching the wms `@Profile("standalone")` TMS fallback.
- **Two vendors, one shipment.** `CarrierRouter` must yield exactly one vendor per shipment; the spec must state the default/override rule (region-based) and the fallback when a region is unmapped (fail to a documented default vendor, not silent drop).
- **Idempotency continuity.** The relocated dispatch must preserve `Idempotency-Key={shipment.id}` semantics so a retried/relocated call is deduped identically to the wms interim (ADR-052 §D7).

## Failure Scenarios

- **A — ADR not accepted.** If ADR-MONO-053 is still PROPOSED, this task must not be promoted to `ready/` or implemented (HARDSTOP-09; the architecture is not yet ratified). STOP and report.
- **B — Spec proposes a synchronous seam.** If any draft introduces a synchronous wms→logistics REST call, it violates ADR-052 §D5/§A3 (couples wms's shipping confirmation to scm availability). Reject and keep the fact-event seam.
- **C — Non-additive contract drift.** If the subscription doc forces any change to a wms event schema/payload, the "no new contract" premise (ADR-052 §D5) is broken — escalate to a wms contract task instead of editing the producer here.
- **D — Frontmatter drift.** If the `PROJECT.md` Service Map edit touches the YAML `traits`/`domain`/`service_types` lines, classification changes silently — revert the frontmatter and keep only the Service Map table edit.
- **E — Scope creep into 3PL/tracking.** If 3PL (`THIRD_PARTY_LOGISTICS` factory, demand-planning routing) or 스윗트래커 tracking appears in this task's diff, it has absorbed Phase 2/3 — split it back out (ADR-053 §D7 phasing).

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): spec authoring for a new service with cross-project seam reasoning → **Opus** (contract/architecture judgment, not a mechanical edit).
