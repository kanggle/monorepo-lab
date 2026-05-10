# Task ID

TASK-SCM-BE-006

# Title

procurement-service architecture spec authoring (retroactive)

# Status

ready

# Owner

backend

# Task Tags

- spec
- code

---

# Goal

Author the missing `projects/scm-platform/specs/services/procurement-service/architecture.md` spec file. TASK-SCM-BE-002 implementation (PR #239) shipped 89 files of production code without a corresponding architecture.md — `gateway-public-routes.md` references procurement as one of three core services but the canonical spec file is absent. This is **retroactive spec authoring** to document the shipped implementation.

After this task: any future implementation work on procurement-service has a canonical `architecture.md` to reference, and the `specs/services/` tree is consistent (gateway / procurement / inventory-visibility all have `architecture.md`).

---

# Scope

## In Scope

- Create `projects/scm-platform/specs/services/procurement-service/architecture.md` documenting the shipped state of `apps/procurement-service/`.
- Sections (mirror `inventory-visibility-service/architecture.md` structure):
  - Service Identity (name, type, architecture style, traits, etc.)
  - Responsibilities
  - Architecture Style Rationale (Hexagonal — declared in TASK-SCM-BE-002)
  - Layer Structure (domain / application / adapter packages)
  - PO 상태기계 8단계 (DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED → PARTIALLY_RECEIVED → RECEIVED → SETTLED → CLOSED + CANCELED)
  - Resilience4j integration patterns (CB + retry + idempotency-key — `integration-heavy` trait)
  - Outbox + audit_log pattern (S1, S7)
  - Security (AES-GCM column 암호화, JWT validation)
  - Dependencies (libs/java-messaging, libs/java-security, GAP, Postgres, Redis, Kafka)
  - References (rules/domains/scm.md, rules/traits/transactional.md / integration-heavy.md)
- (Optional but recommended) `specs/contracts/http/procurement-api.md` — author or extend if the existing v1 endpoint catalog is incomplete.

## Out of Scope

- Production code changes (procurement-service is already in production via PR #239).
- v2 features (supplier portal, demand forecasting).
- Test additions beyond what TASK-SCM-BE-002b/c/d already covered.

---

# Acceptance Criteria

- [ ] `procurement-service/architecture.md` exists and accurately documents the shipped implementation.
- [ ] All 8 PO state transitions are documented with allowed event sources (caller / supplier-ack / asn-receipt / etc.).
- [ ] Resilience4j configuration values (CB threshold, retry attempts, jitter) match `application.yml`.
- [ ] Outbox + audit_log invariants documented (T3 atomicity, append-only).
- [ ] Cross-references to `rules/domains/scm.md` Mandatory Rules S1-S8 (which apply, which don't).
- [ ] No production code changes (`apps/procurement-service/` untouched).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/scm-platform/PROJECT.md` (declared traits + service_types)
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` (mirror reference for structure/format)
- `projects/scm-platform/specs/services/gateway-service/architecture.md` (mirror reference)
- `projects/scm-platform/specs/services/gateway-public-routes.md` (current procurement endpoint references)
- `rules/domains/scm.md` (S1-S8 Mandatory Rules)
- `rules/traits/transactional.md` + `integration-heavy.md` + `batch-heavy.md`
- `platform/service-types/rest-api.md`

# Related Skills

- `.claude/skills/architecture/hexagonal/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`

---

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` (verify exists; extend if minimal)
- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` (verify exists)

---

# Target Service

- procurement-service (spec only — code already shipped)

---

# Architecture

Hexagonal (Ports & Adapters) — already declared in TASK-SCM-BE-002 implementation. This task documents that decision retroactively.

---

# Implementation Notes

- Read `apps/procurement-service/src/main/java/com/scm/procurement/` package layout to ground the spec in reality.
- Read `application.yml` for Resilience4j tuning values.
- Read existing `apps/procurement-service/src/main/resources/db/migration/` for schema invariants (PO table, outbox table, audit_log table, supplier-credentials table).
- Reuse table-of-contents and section ordering from `inventory-visibility-service/architecture.md` for consistency.

---

# Edge Cases

- If during writing the spec drift surfaces vs implementation (e.g., spec says X, code does Y) → record as a finding in the PR body, do NOT change implementation. File a separate `fix` task if needed.

---

# Failure Scenarios

- Implementation patterns disagree across files (e.g., audit_log writes from multiple use-cases use different conventions) → document the dominant pattern + flag the divergent ones.

---

# Test Requirements

- N/A (spec-only). Existing tests (TASK-SCM-BE-002b/c/d) already cover the implementation.

---

# Definition of Done

- [ ] `procurement-service/architecture.md` authored
- [ ] No production code changes
- [ ] PR description summarizes what was discovered during the audit (any spec-vs-implementation drift)
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-spec all` (2026-05-11) audit Finding [SCM 2]. Skipped from PR #326 because /refactor-spec scope is structural drift, not new spec authoring.

분석=Opus 4.7 / 구현 권장=Opus (retroactive spec authoring with code-evidence grounding; large surface).
