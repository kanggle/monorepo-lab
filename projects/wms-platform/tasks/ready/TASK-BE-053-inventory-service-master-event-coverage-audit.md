# Task ID

TASK-BE-053

# Title

inventory-service master event coverage audit — warehouse / zone / partner consumer absence decision

# Status

ready

# Owner

backend

# Task Tags

- code
- spec

---

# Goal

`inventory-service/architecture.md` § Identity (line 73-74) lists "Warehouse / Zone / Location / SKU / Partner / Lot identity" as **referenced master identities owned by master-service**, but the actual consumer set in production code is narrower:

- **Present**: `MasterLocationConsumer`, `MasterSkuConsumer`, `MasterLotConsumer` (the latter added retroactively to spec by TASK-BE-052 / PR #336)
- **Absent**: no `MasterWarehouseConsumer`, `MasterZoneConsumer`, `MasterPartnerConsumer` classes

§ Event Consumption catalogue (line 232-241) only enumerates `master.location.*` / `master.sku.*` / `master.lot.*` (after BE-052 fix). The 3 missing aggregates' subscription state is unclear: is this **intentional** (inventory-service does not need warehouse/zone/partner reads for v1 invariants) or **drift** (consumers should exist)?

After this task: the decision is documented + spec aligned.

---

# Scope

## In Scope

- Code-evidence audit: search inventory-service `domain/`, `application/`, `adapter/out/persistence/` for any reference to `warehouse_id`, `zone_id`, `partner_id` (FK columns, JPA fields, service logic).
- Determine whether inventory-service v1 invariants require any of warehouse/zone/partner identity:
  - Warehouse: does adjustment / transfer logic need warehouse-level rules? (e.g., cross-warehouse transfer rejection)
  - Zone: does picking eligibility consider zone (e.g., DRY/COLD zone restrictions)?
  - Partner: does inventory-service ever filter by partner / supplier (likely no — partner is outbound/inbound concern)?
- **Decision tree**:
  - **Scenario A (intentional, inventory does NOT need them)**: add explicit "no Warehouse / Zone / Partner consumer in v1 — these identities are not referenced in inventory invariants" note to `inventory-service/architecture.md` § Event Consumption (mirror the BE-052 negative-note pattern). No code change.
  - **Scenario B (drift, should consume)**: file follow-up impl task to add the missing consumer(s). Document the gap in PR body with the specific invariant requiring the consumer.
  - **Scenario C (mixed — some yes, some no)**: A for some, B for others, file impl follow-up only for the missing ones.

## Out of Scope

- Implementing the missing consumer (if Scenario B/C, file as separate task).
- inbound-service master event coverage (already correct per BE-052).
- outbound-service master event coverage (already correct per memory `project_outbound_sweep_complete.md`).
- master-service publication side (no change).

---

# Acceptance Criteria

- [ ] Code-evidence audit completed (warehouse_id / zone_id / partner_id grep results in PR body).
- [ ] Decision documented per aggregate (warehouse / zone / partner): A / B / C with rationale.
- [ ] If Scenario A (any): inventory-service/architecture.md § Event Consumption gains an explicit "no <Aggregate> consumer in v1" note for each.
- [ ] If Scenario B (any): follow-up impl task filed (one per missing consumer) with the invariant motivating it.
- [ ] No production code changes in this task (impl filed separately).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/wms-platform/specs/services/inventory-service/architecture.md` (target)
- `projects/wms-platform/specs/services/master-service/architecture.md` (publishes the master.*)
- `projects/wms-platform/specs/services/inbound-service/architecture.md` § Event Consumption (reference — has all 6 master consumers via single-dispatcher pattern after BE-052)
- `projects/wms-platform/specs/services/outbound-service/architecture.md` § Event Consumption (reference — has all 6 master consumers)
- `rules/domains/wms.md` (Mandatory Rules for inventory invariants)

# Related Skills

- `.claude/skills/messaging/event-consumer-coverage/SKILL.md` (if exists)

---

# Related Contracts

- `projects/wms-platform/specs/contracts/events/master-events.md` (read-only — confirm published event surface)

---

# Target Service

- inventory-service (audit + spec only)

---

# Implementation Notes

- Grep commands (PowerShell-friendly):
  - `grep -r "warehouse_id\|warehouseId\|MasterWarehouse" projects/wms-platform/apps/inventory-service/`
  - `grep -r "zone_id\|zoneId\|MasterZone" projects/wms-platform/apps/inventory-service/`
  - `grep -r "partner_id\|partnerId\|MasterPartner" projects/wms-platform/apps/inventory-service/`
- Cross-check inventory-service migrations (`db/migration/V*.sql`) for any FK column referencing those aggregates.
- The `MasterReadModelPort` may have unused method signatures referencing absent aggregates — note them as findings (cleanup candidate).
- Pattern reference: outbound-service consumer set has all 6 master aggregates because outbound-service needs Partner (customer) for shipping decisions and Warehouse/Zone for picking heuristics. inventory-service may genuinely not need them.

---

# Edge Cases

- inventory-service references warehouse_id in test fixtures only (not production domain) → still Scenario A (test fixture allowed without consumer; document in note).
- A v2-deferred feature (e.g., FEFO picking by zone) hints at future Zone consumer — note as v2 trigger but keep v1 Scenario A.
- `MasterReadModelPort` interface declares `findWarehouseById()` etc. but no implementation populates the read-model — that's API surface drift; cleanup candidate.

---

# Failure Scenarios

- Audit reveals the v1 invariants DO depend on a master aggregate the consumer is absent for (e.g., picking validates zone but no MasterZoneConsumer hydrates the cache → operator sees stale zone data) — Scenario B + impl follow-up MANDATORY, not optional.

---

# Test Requirements

- N/A (audit + spec only). If Scenario B triggers an impl follow-up, that task carries its own test requirements.

---

# Definition of Done

- [ ] Audit evidence in PR body
- [ ] Per-aggregate decision documented
- [ ] inventory-service/architecture.md updated (Scenario A notes)
- [ ] Follow-up impl tasks filed (Scenario B/C)
- [ ] Ready for review

---

# Provenance

Surfaced from TASK-BE-052 side-finding (2026-05-11 master.lot consumer audit, PR #336). Filed as separate task because each missing-aggregate decision needs code-evidence audit beyond BE-052's named single-aggregate scope.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (3-aggregate audit + scenario decision + spec note; small-medium effort).
