# TASK-BE-385 — wms spec refactor pass (meaning-preserving consistency/naming/clarity + drift findings)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (3-agent discovery + careful meaning-preservation triage)

---

## Goal

Run a full `/refactor-spec` pass over all wms-platform specs (7 services + contracts + integration), applying **only meaning-preserving** structural/consistency/naming/clarity fixes, and producing a prioritized **findings report** of the content **drift** (value / contract / behavior divergences) that the refactor constraint forbids auto-fixing. Sibling of the concurrent finance/ecommerce/scm/erp spec-refactor efforts.

**Method**: 3 parallel read-only discovery agents (dead-ref+orphan / structure+consistency+missing-section / duplication+naming+clarity) scanned ~65 wms spec files. Each finding was then **independently re-verified** before any edit (several agent findings were inexact — e.g. claimed `## Role`/`## Testing Strategy` gateway headings did not exact-match; a flagged "no-op hides the throw" clarity issue was already stated correctly). Only verified, meaning-preserving fixes were applied; everything touching a contract value, an event/topic name, a state-machine behavior, or requiring new content was **flagged, not fixed** (per `/refactor-spec` constraints).

**Headline result**: wms specs are **structurally healthy** — **0 dead references** (521 links verified), terminology largely consistent, no orphan-delete candidates. The real issues are **content drift**: service docs that restate a canonical contract with a stale/wrong value. Those are listed under § Findings for a separate **verified** drift-fix task.

## Scope (Applied — meaning-preserving, verified)

All under `projects/wms-platform/specs`:

1. **naming/typo** — `services/admin-service/database-design.md` (seed table): built-in role `WMS_SUPERVISOR` → `WMS_SUPERADMIN`. Verified against the **actual** seed migration `apps/admin-service/.../V99__seed_dev_data.sql` (which seeds `WMS_SUPERADMIN`, 2×) + admin `domain-model.md` + `architecture.md` + `rules/domains/wms.md` — `WMS_SUPERVISOR` was a doc-only typo contradicting the seed it documents.
2. **naming/term** — `services/outbound-service/overview.md` (×3: persistent-stores row, invariant 6, Owned Data): aggregate `ShippingRecord` → `Shipment`, matching every other outbound doc (`domain-model.md` / `architecture.md` / `sagas/outbound-saga.md`).
3. **consistency/heading** — `services/gateway-service/overview.md`: `## Public surface (routes)` → `## Public surface` (sibling convention across all 6 other services; verified no cross-ref anchors point to the old slug).
4. **orphan** — `services/gateway-service/architecture.md` § References: added a cross-ref link to the service's own `external-integrations.md` (the only wms spec not reachable from the spec link graph; file is valid, so cross-ref > delete).

## Out of Scope — Findings (drift; require a **verified** follow-up task, NOT pure refactor)

These are real quality issues but each touches a **contract value / event name / state-machine behavior** or needs **new content**, so `/refactor-spec` forbids silently applying them. Each must be confirmed against the owning contract/ADR/code before editing. Recommended as a follow-up **drift-fix** task (TASK-BE-386, `分析=Opus / 구현=Opus`).

**Tier A — service doc contradicts its canonical contract (highest value; fix the service doc to match, after confirming canonical):**

- `inventory-service/{state-machines/reservation-status.md, sagas/reservation-saga.md}` say an `INSUFFICIENT_STOCK` reserve failure emits **`inventory.adjusted`**; canonical `contracts/events/inventory-events.md` (§4a) + the TASK-MONO-196 reconciliation say it emits the dedicated **`inventory.reserve.failed`** (`wms.inventory.reserve.failed.v1`). Same residue inside the canonical `contracts/events/outbound-events.md` itself (§3 callout L250-251/263 stale vs §4a/§C1) — a **contract-file** self-contradiction → confirm against MONO-196 + code, then fix.
- `inventory-service/{architecture.md, domain-model.md, overview.md}` event tables omit `inventory.reserve.failed` and list only 4 of 8 topics vs `inventory-events.md`.
- `notification-service/overview.md` Kafka-consume list names topics not actually subscribed in v1 (vs `contracts/events/notification-subscriptions.md` — real set = `inventory.alert`, `inventory.adjusted`, `inbound.inspection.completed`, `inbound.asn.cancelled`, `outbound.order.cancelled`, `outbound.shipping.confirmed`).
- `notification-service/idempotency.md`: inline `notification_event_dedupe` CHECK lists 2 outcomes vs canonical 4 in `database-design.md` (`+'NO_RULE','ERROR'`); references non-existent topic `wms.notification.delivery.failed.v1` (only `…delivered.v1` exists).
- `notification-service/external-integrations.md` uses delivery status `SENT` on Slack 2xx; canonical enum is **`SUCCEEDED`**.
- notification alert-matcher (`notification-subscriptions.md`, `notification-service/{domain-model.md, database-design.md}`) matches `outbound.order.cancelled` on field **`priorStatus`**; the authoritative outbound event field is **`previousStatus`** — a field-name mismatch that breaks the matcher (and one path also references the forbidden post-`SHIPPED` cancel).

**Tier B — event/aggregate name drift in service overviews (contract values):**

- `inbound-service/overview.md`: event `inbound.asn.created.v1` (canonical `…received.v1`); state summary drops `INSPECTED`/`IN_PUTAWAY`/`PUTAWAY_DONE`.
- `outbound-service/overview.md`: event `outbound.picking.confirmed.v1` (canonical `…completed.v1`, per `outbound-events.md`); invariant 2 references a non-existent order state `CONFIRMED`.
- `master-service/overview.md`: location-code uniqueness stated "within a warehouse"; rule **W3** = global uniqueness.
- inventory partition-key prose oversimplified to `location_id`/`sku_id` vs the per-topic keys in `inventory-events.md`.

**Tier C — config / state-machine semantics (confirm intent; may be intentional):**

- `outbound-service/sagas/outbound-saga.md` §9.2 sweeper cap "starts at 10" vs wired default **5** (`outbound.saga.sweeper.max-attempts=5`).
- outbound saga ASCII omits `SHIPPED_NOT_NOTIFIED`/`STUCK_RECOVERY_FAILED`; `domain-model.md` Open Items stale (architecture marks done).
- `saga-status.md` "or operator force-completes" implies a REST endpoint not in v1 (only DB-only `:force-fail`).
- inventory MANUAL release reuses reason `ADJUSTMENT_RECLASSIFY` (adjustment-catalog code) — confirm intentional reuse.
- master lot diagram `EXPIRED → INACTIVE` while labeling `EXPIRED` "(terminal)".

**Tier D — duplication (identical recaps; single-source-of-truth tidy, low urgency):**

- Inline event/topic tables restated in `*/architecture.md` for inbound/outbound/inventory/master/admin (canonical = the `contracts/events/*.md`); admin error-code table duplicates `rules/domains/wms.md`; `admin/idempotency.md` + `notification/domain-model.md` restate DDL from their `database-design.md`. All currently identical → convert to cross-refs (deferred; verify identity per file first).

**Tier E — structure / missing-section (require authoring or larger restructure; flag only):**

- `notification-service/domain-model.md` lacks the sibling-standard `## Scope` / `## Common Aggregate Shape` / `## Entity Relationship Diagram` / `## Aggregate Boundaries` / `## Forbidden Patterns` / `## Open Items` sections + `---` dividers, and uses non-standard idempotency headings. Normalizing requires authoring content → a dedicated notification-service spec-completion task, not a refactor.
- `notification/master idempotency.md` use non-numbered H2s vs the numbered-H2 convention in admin/inbound/inventory/outbound; admin & inventory `idempotency.md` lack a `## Test Requirements` section.

## Acceptance Criteria

- **AC-1 (meaning-preserving)** — every applied edit is a verified consistency/naming/clarity normalization; **0** requirement / contract / event-payload / state-machine semantics changed. `git diff` touches only `specs/**` (no `apps/**`, no Flyway, no contract `payload`/topic-name).
- **AC-2 (verified direction)** — each naming fix was confirmed against a higher-priority source (the seed migration for `WMS_SUPERADMIN`; the outbound docs for `Shipment`).
- **AC-3 (no new dead refs)** — the added gateway cross-ref resolves; the heading rename broke no anchor (grep-verified 0 inbound anchors).
- **AC-4 (findings recorded)** — all drift the pass declined to auto-fix is captured under § Findings with file, canonical source, and tier, for a follow-up verified task.

## Related Specs

- All `projects/wms-platform/specs/**` (scanned). Edited: `services/admin-service/database-design.md`, `services/outbound-service/overview.md`, `services/gateway-service/{overview.md, architecture.md}`.

## Related Contracts

- `contracts/events/{inventory-events.md, outbound-events.md, notification-events.md, notification-subscriptions.md}` (canonical sources named in § Findings — NOT edited in this task).

## Edge Cases

- **Agent findings are not ground truth** — 3 discovery agents surfaced candidates; several were inexact (wrong heading text, already-clear prose). Every applied edit was independently re-verified against the file + canonical source. Unverifiable/contract-touching candidates were flagged, not applied.
- **Drift fix direction** — fixing a service doc to match a canonical contract is meaning-preserving ONLY if the canonical is truly authoritative; where the canonical itself is internally contradictory (outbound-events.md §3 vs §4a) the follow-up must resolve the contract first.

## Failure Scenarios

- **F1 — silent contract drift "fix" in wrong direction** — auto-applying a Tier A/B value change without confirming the canonical (or against live code) could codify a wrong value. Guarded by deferring all Tier A–C to a verified follow-up task.
- **F2 — anchor break from heading rename** — guarded by AC-3 (grep-verified no inbound anchors before renaming `## Public surface`).
