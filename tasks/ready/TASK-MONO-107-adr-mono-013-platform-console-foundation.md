# Task ID

TASK-MONO-107

# Title

ADR-MONO-013 — platform-console foundation criteria (PROPOSED)

# Status

ready

# Owner

architecture

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- adr

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Author **ADR-MONO-013** (PROPOSED) recording the converged foundation decisions for a unified, AWS/GCP-console-style operations surface (`platform-console`) over the portfolio's enterprise suite (gap · scm · wms + future erp · finance).

After this task, the following are true:

- The integration model, project placement, GAP `admin-web` retirement path, cross-project BFF integration-contract skeleton, and phased roadmap with dependency gates are recorded as a PROPOSED ADR.
- ADR-MONO-003a § D2.1's "new project bootstrap requires a fresh ADR" mandate is satisfied for `platform-console` (analogous to ADR-MONO-008 for finance).
- A future session has a concrete readiness checklist (§ D7) and ACCEPTED-transition mechanics (§ D8) to evaluate before any project-creation churn.

# Scope

## In Scope

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` (PROPOSED).
- Decisions: D1 model (B — console *is* the UI), D2 placement (new `projects/platform-console/`), D3 classification (recommended, finalised at ACCEPTED), D4 `admin-web` parity-gated retirement, D5 BFF integration-contract skeleton, D6 phased roadmap + dependency invariants, D7 readiness criteria, D8 ACCEPTED mechanics.
- Cross-references to ADR-MONO-003a/003b/008/002, GAP PROJECT.md, project-overview § 2.6, TEMPLATE.md, taxonomy.

## Out of Scope

- Creating `projects/platform-console/` (deferred to ADR-013 ACCEPTED — D8).
- GAP OIDC client / tenant-product registry implementation (Phase 1).
- finance/erp taxonomy or `rules/domains/<d>.md` (JIT at Phase 5/6; governed by ADR-MONO-008 / future erp ADR — explicitly NOT this task).
- The full BFF integration-contract spec (Phase 0 deliverable, separate task/spec — only the skeleton is fixed in the ADR).
- Any flip to ACCEPTED.

# Acceptance Criteria

- [ ] `ADR-MONO-013` exists with Status PROPOSED and the house ADR structure (Status/Date/History/Decision driver/Supersedes/Related → §1 Context → §2 Decision D1–D8 → §3 Consequences → §4 Alternatives → §5 Relationship → §6 Transition History → §7 Provenance + model annotation footer).
- [ ] D1 selects Model B with launcher/hybrid/micro-frontend explicitly rejected.
- [ ] D2 selects new `projects/platform-console/` with GAP-internal and `platform/` placements rejected.
- [ ] D4 makes `admin-web` removal parity-gated and spec-first (GAP PROJECT.md change).
- [ ] D6 records the Phase 0–8 sequence and the dependency invariants (ADR ACCEPTED gate; Phase 2 parity gates Phase 3; contract validated by MVP before finance/erp; BFF after 5 domains).
- [ ] ADR explicitly defers finance/erp domain governance to ADR-MONO-008 / future erp ADR and forbids pre-authoring their taxonomy here.
- [ ] ADR-MONO-013 slot is unique (no pre-existing ADR-MONO-013).
- [ ] `validate-rules` / link-lint clean for the new ADR's internal references.

# Related Specs

> Follow `platform/entrypoint.md` Step 0 — monorepo-level doc task; no project `PROJECT.md` classification applies. This is shared `docs/adr/` work per CLAUDE.md § Task Rules (monorepo-level).

- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § D2.1
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` (structural template + governs Phase 5)
- `docs/adr/ADR-MONO-003b-phase-5-launch-criteria.md`
- `docs/project-overview.md` § 2.6
- `projects/global-account-platform/PROJECT.md` (GAP service map — `admin-web`)
- `TEMPLATE.md` § Local Network Convention
- `rules/taxonomy.md` (D3 domain/trait candidates)

# Related Skills

- `.claude/skills/` — ADR authoring follows the existing ADR-MONO house pattern (ADR-MONO-008 reference).

---

# Related Contracts

- None (doc-only). The BFF integration contract is skeleton-only in the ADR; its full spec is a separate Phase 0 deliverable.

---

# Target Service

- None (monorepo-level governance doc).

---

# Architecture

Follow the ADR-MONO house structure as exemplified by `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md`.

---

# Implementation Notes

- PROPOSED, not ACCEPTED — design decisions are converged from the dialogue, but project-creation churn waits for an explicit ACCEPTED moment (ADR-MONO-003a § D2.1 churn-clock; same staged pattern as ADR-MONO-008).
- This ADR does NOT reset the shared-library churn clock (doc-only under `docs/adr/`); the reset occurs at ADR-013 ACCEPTED (Phase 1 skeleton).
- Do not extend scope into finance/erp taxonomy — that is JIT at Phase 5/6 by design.

---

# Edge Cases

- ADR-MONO-013 number collision — verified free (existing: …012, 012a; next sequential = 013).
- Reader conflates this ADR with finance/erp governance — § 5 table + § D6 explicitly scope it out.
- Reader treats PROPOSED as build authorisation — § 1.4 + § D8.1 intent forms guard against premature bootstrap.

---

# Failure Scenarios

- ADR lands ACCEPTED prematurely → unintended project-creation churn + churn-clock reset (mitigated: § 1.4, § 4.7, D8.1).
- `admin-web` retirement read as "delete now" → live operator capability loss (mitigated: D4 parity-gated, Phase 2 → gate → Phase 3 in D6).
- finance/erp taxonomy speculatively pre-authored → violates `rules/README.md` on-demand policy (mitigated: § 1.2.4, § 4.6, D6 invariants).

---

# Test Requirements

- Link/reference lint clean for the new ADR (internal relative links resolve).
- `validate-rules` shows no new inconsistency from the added ADR.

---

# Definition of Done

- [ ] ADR-MONO-013 authored (PROPOSED)
- [ ] This task's Acceptance Criteria all satisfied
- [ ] Internal links resolve
- [ ] Specs updated first if required (n/a — doc-only, no spec/contract change)
- [ ] Ready for review
