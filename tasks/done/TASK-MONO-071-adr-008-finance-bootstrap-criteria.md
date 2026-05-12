# Task ID

TASK-MONO-071

# Title

Publish ADR-MONO-008 (PROPOSED) — finance-platform bootstrap criteria, integration mode, Template-first procedure

# Status

review

# Owner

monorepo

# Task Tags

- spec
- adr
- bootstrap
- finance

---

# Goal

Author `ADR-MONO-008` in **PROPOSED** status to define the concrete criteria, integration mode (standalone vs monorepo-`direct-include`), and Template-first procedure for the **6th project (finance-platform)** bootstrap.

The 6th project is the **first downstream consumer of `kanggle/project-template`** (Phase 5 LAUNCHED 2026-05-13, ADR-MONO-003b ACCEPTED). Per [ADR-MONO-002 § D4](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) + memory `project_portfolio_7axis_architecture.md`, the recommended order is `scm → finance → erp → mes`. scm-platform shipped 2026-05-04~07; finance is next.

Per [ADR-MONO-003a § D2.1](../../docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md):

> Adding finance / erp / mes / any new project skeleton under `projects/<name>/` is NOT an OVERRIDE-class change... Bootstrap inherently resets the churn clock for `libs/` (skeleton-driven `settings.gradle` change) and shifts portfolio narrative scope — both are decision points the OVERRIDE was not designed to cover.

So finance bootstrap **requires a new ADR** with user-explicit authorisation. ADR-MONO-008 is that ADR. PROPOSED status = criteria + procedure documented, the actual bootstrap awaits user-explicit ACCEPTED transition (same staged pattern as ADR-MONO-003b).

---

# Scope

## In Scope

### A. New ADR file

`docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` — Status: **PROPOSED** 2026-05-13.

Required sections:

- **Context** — Phase 5 LAUNCHED, Template repo exists, finance is next per ADR-MONO-002 § D4, ADR-MONO-003a § D2.1 mandates a fresh ADR for new domain bootstrap.
- **Decision** — six sub-decisions:
  - **D1** — Integration mode (standalone via Template `Use this template`, OR monorepo `direct-include`, OR both)
  - **D2** — Project classification (domain, traits, service_types per `rules/taxonomy.md` and `platform/service-types/INDEX.md`)
  - **D3** — Initial service skeleton scope (first service name, responsibility, architecture style)
  - **D4** — Procedure (Template fork → bootstrap → monorepo register if direct-include)
  - **D5** — Readiness criteria (must-pass before ACCEPTED transition)
  - **D6** — ACCEPTED transition mechanics (user-explicit intent forms, commit pattern, audit-trail row format)
- **Consequences** — what changes at PROPOSED merge vs ACCEPTED transition vs post-bootstrap.
- **Alternatives Considered** — at minimum: (i) skip finance, jump to erp/mes (rejected: ordering rationale per ADR-MONO-002 § D4), (ii) bootstrap without ADR (rejected: ADR-MONO-003a § D2.1 forbids), (iii) bootstrap into monorepo only (no Template flow demo).
- **Relationship to ADR-MONO-002 + 003b** — ADR-MONO-002 § D4 ordering is the parent; ADR-MONO-003b § D3.4 sync mechanism is downstream context.
- **Status transition history** — empty placeholder for ACCEPTED row.

### B. Cross-reference updates

- Memory `project_portfolio_7axis_architecture.md` — note ADR-008 PROPOSED status + finance as next.
- ADR-MONO-002 § D4 — append a forward-pointer line at the end (running-addendum convention) pointing to ADR-MONO-008 for the finance bootstrap decision.

### C. No other changes

- No project skeleton creation (deferred to ACCEPTED transition).
- No `libs/`, `platform/`, `rules/`, `.claude/` change (D2.1 scope).
- No update to TEMPLATE.md (Phase 6+ procedure already enumerated; ADR-008 layers decisions over it).

## Out of Scope

- ACCEPTED transition of ADR-MONO-008. This task only lands PROPOSED.
- Actual finance-platform bootstrap (creating `projects/finance-platform/` skeleton, files, first service). Gated on ACCEPTED.
- The finance domain rule library (`rules/domains/finance.md`) authoring. Done at ACCEPTED transition only if missing (`rules/README.md` on-demand policy).
- The finance trait declarations beyond `transactional` (default trait stack TBD at ACCEPTED).
- erp / mes bootstrap (separate future ADR-MONO-009 / 010 candidates).
- Migrating existing standalone repos (if any pre-existing finance prototype exists, handled per memory `project_ecommerce_import_readiness.md` trigger logic; out of this ADR's scope).

---

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` exists with `Status: PROPOSED`, dated 2026-05-13.
- [ ] ADR-008 contains: Context / Decision (D1–D6) / Consequences / Alternatives Considered (≥3) / Relationship to ADR-MONO-002+003b / Status transition history section (empty placeholder).
- [ ] **D1 (Integration mode)** explicitly weighs standalone-only / monorepo-only / both options with portfolio narrative rationale.
- [ ] **D2 (Classification)** declares: domain (proposed: `finance` — must verify `rules/taxonomy.md` first), traits (proposed: `transactional` + 1-2 others TBD), service_types.
- [ ] **D3 (Initial service)** picks one service for v1 skeleton (e.g. `account-service` / `ledger-service` / `payment-service`).
- [ ] **D4 (Procedure)** walks: `gh repo create kanggle/finance-platform --template kanggle/project-template --public --clone` → flat layout init → optional monorepo `direct-include` registration.
- [ ] **D5 (Readiness criteria)** = checklist before ACCEPTED transition (minimum 4 items: domain in taxonomy / trait stack decided / service skeleton scope decided / user-explicit intent recorded).
- [ ] **D6 (ACCEPTED transition)** specifies: intent forms, commit pattern, audit-trail row format.
- [ ] Memory `project_portfolio_7axis_architecture.md` updated to reference ADR-008 PROPOSED.
- [ ] ADR-MONO-002 § D4 forward-pointer line appended.
- [ ] No service code touched. No `libs/` / `apps/` / `projects/` diff.
- [ ] CI green (path-filter `docs(adr)` flag only).

# Related Specs

- `docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md` § D4 (ordering parent)
- `docs/adr/ADR-MONO-003b-phase-5-launch-criteria.md` § 1.4 + § D3 (no obligation to bootstrap; Template ↔ monorepo sync context)
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § D2.1 (new domain bootstrap requires fresh ADR — this ADR satisfies that requirement)
- `TEMPLATE.md` § Starting a New Project from the Extracted Template (Phase 6+)
- `rules/taxonomy.md` (must verify `finance` domain entry exists or add as part of D2)
- `platform/service-types/INDEX.md` (service_types validation)
- Memory `project_portfolio_7axis_architecture.md` (7-axis finance/erp positioning)
- Memory `project_scm_platform_bootstrap.md` (5번째 프로젝트 bootstrap reference pattern)

# Related Contracts

None — meta-policy ADR. No HTTP / event contract change.

# Edge Cases

- **`finance` not in `rules/taxonomy.md`** — if missing, D2 should propose adding it; the actual add is a separate small PR before ACCEPTED transition (since taxonomy edit IS OVERRIDE-class per ADR-003a § D1 or D2, depending on whether viewed as cleanup or domain bootstrap component — leaning D2.1 component, needs ADR-008 nod).
- **Integration mode choice** — `direct-include` enables monorepo CI/refactor benefits; `standalone-only` cleanly demonstrates Template `Use this template` flow without re-triggering monorepo churn. Both options have portfolio narrative value. D1 weighs both; ACCEPTED transition picks one or both.
- **No pre-existing prototype** — unlike ecommerce/GAP, no external prototype to import. Pure greenfield. D4 procedure is simpler (no composite-build hassle).
- **Trait stack guess** — finance domain typically `transactional` + `compliance-heavy` + `event-driven`. Final stack decided at ACCEPTED via D5 readiness checklist.

# Failure Scenarios

- **Reviewer asks "why not jump to erp?"** — answer: ADR-MONO-002 § D4 recommends scm → finance → erp → mes order based on stress-axis progression (transactional + integration-heavy + batch-heavy → adds compliance + event-driven → adds workflow → adds plant-floor real-time). Reversing order skips intermediate validation; documented in D1.
- **Reviewer asks "is this OVERRIDE-class?"** — no. Per ADR-MONO-003a § D2.1, new domain bootstrap requires fresh ADR. ADR-008 PROPOSED IS that fresh ADR. The PROPOSED PR itself does not touch `libs/` / `platform/` / `rules/` / `.claude/`, so it satisfies D2.1 narrowly (the doc-only meta-policy edit is structurally PR #395 / #410 class). The ACCEPTED transition + actual bootstrap is when D2.1 fully fires.
- **Reviewer asks "what about the Template repo first-use demonstration?"** — covered by D4 procedure. The `gh repo create ... --template ...` step IS the first downstream Template usage. ACCEPTED moment records this artifact.

---

# Implementation Plan

1. Author `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` per the structure above (Status: PROPOSED).
2. Append ADR-MONO-002 § D4 forward-pointer.
3. Update memory `project_portfolio_7axis_architecture.md`.
4. Single bundled commit.
5. Lifecycle: ready → review on PR creation.
6. Push branch + open PR.
7. After merge: close chore (review → done).

# Estimated Cost

- Files: ADR-008 new (~250 LOC) + ADR-002 forward-pointer (~3 LOC) + memory (~3 LOC) + this task file. Total ≈ 280 LOC.
- CI: path-filter `docs(adr)` flag only → ~20s baseline.
- Time: ~1.5 hour authoring + commit/push.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (meta-policy authoring — D1 integration mode + D2 classification require interpretive judgement; structurally identical to TASK-MONO-069 ADR-003b PROPOSED authoring path).
