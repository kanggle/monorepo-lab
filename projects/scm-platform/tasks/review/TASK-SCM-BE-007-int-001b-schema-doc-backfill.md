# Task ID

TASK-SCM-BE-007

# Title

INT-001b cycle 2 schema fixes — data-model.md backfill

# Status

ready

# Owner

backend

# Task Tags

- spec

---

# Goal

Backfill the schema-level fixes that landed during TASK-SCM-INT-001b cycle 2 (PR #262) into the relevant `data-model.md` spec files. Currently the implementation reflects two fixes (`poNumber` UUID v7 substring(28) for collision avoidance, `InventoryNode.contactInfo` `@JdbcTypeCode(SqlTypes.JSON)` for Hibernate-PostgreSQL JSONB cast) but the `data-model.md` files do not document either.

After this task: the data-model spec files are accurate snapshots of the production schema + Hibernate type mappings.

---

# Scope

## In Scope

- Update `projects/scm-platform/specs/services/procurement-service/data-model.md` (or create if absent — coordinate with TASK-SCM-BE-006):
  - Document `poNumber` format: UUID v7 with `substring(28)` (rand_b tail) suffix for tight-loop collision avoidance.
- Update `projects/scm-platform/specs/services/inventory-visibility-service/data-model.md`:
  - Add `@JdbcTypeCode(SqlTypes.JSON)` annotation note for `InventoryNode.contactInfo` JSONB column.
  - Explain the Hibernate 6 + PostgreSQL JSONB 42804 datatype_mismatch root cause (this is a recurring monorepo pattern — same fix in BE-002d Supplier.contactInfoJson).
- Cross-reference both fixes to the project_scm_be_series_in_progress memory entry's "INT-001b 진단·fix reference" pattern.

## Out of Scope

- Production code changes (fixes already in main).
- Other JSONB columns beyond `contactInfo` (audit if needed but file separately).
- Migration scripts.

---

# Acceptance Criteria

- [ ] procurement-service `data-model.md` documents `poNumber` substring(28) format with rationale.
- [ ] inventory-visibility-service `data-model.md` documents `@JdbcTypeCode(SqlTypes.JSON)` on `InventoryNode.contactInfo` with Hibernate-PostgreSQL JSONB rationale.
- [ ] Cross-reference to RFC 9562 UUID v7 spec (rand_b field).
- [ ] No production code changes.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/scm-platform/specs/services/procurement-service/data-model.md` (target — coordinate with SCM-BE-006 if it doesn't exist yet)
- `projects/scm-platform/specs/services/inventory-visibility-service/data-model.md` (target)

---

# Related Contracts

- N/A (data-model.md is internal architecture, not contract).

---

# Target Service

- procurement-service (spec only)
- inventory-visibility-service (spec only)

---

# Implementation Notes

- The poNumber substring fix is in `apps/procurement-service/.../application/PurchaseOrderApplicationService.draft` — read for exact substring index (28).
- The `@JdbcTypeCode(SqlTypes.JSON)` annotation is in `apps/inventory-visibility-service/.../entity/InventoryNodeJpaEntity.java`.
- Same Hibernate-JSONB pattern applies to: `apps/procurement-service/.../entity/SupplierJpaEntity.contactInfoJson` (BE-002d fix). If procurement-service/data-model.md exists, document there too.

---

# Edge Cases

- If `data-model.md` files don't yet exist (likely for procurement-service per SCM-BE-006), create them as part of that task — this task only adds the schema annotations.

---

# Failure Scenarios

- N/A.

---

# Test Requirements

- N/A (spec-only). Production-level JSONB regression guards already exist in TASK-SCM-BE-005 IT cycle 1 (memory `project_scm_be_series_in_progress.md`).

---

# Definition of Done

- [ ] Both data-model.md files updated (or coordinated with SCM-BE-006)
- [ ] No production code changes
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-spec all` (2026-05-11) audit Finding [SCM 6]. Skipped from PR #326 because /refactor-spec scope does not author new sections.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (small spec backfill).
