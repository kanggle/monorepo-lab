# Task ID

TASK-MONO-111

# Title

ADR-MONO-015 — platform-console dashboards model (composed operator overview) PROPOSED

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

Author **ADR-MONO-015** (PROPOSED) recording the resolution of a HARDSTOP-06/09 surfaced during the ADR-MONO-013 Phase 2 slice-4 (`TASK-PC-FE-005` dashboards) investigation: the `console-integration-contract.md` § 3 / ADR-MONO-013 D7.4 parity checklist `dashboards` line is bare (no implementing task, no § 2.4.x binding), there is **no GAP producer endpoint** for it, and the `admin-web` parity source is a **Grafana iframe** (`admin-web/architecture.md:75`).

After this task: the decision (Model **B — composed operator overview from existing read endpoints**, user-chosen) + the explicit § 3 parity-line redefinition (HARDSTOP-09 mitigation) + contract-reconciliation mandate + downstream task plan are recorded; `TASK-PC-FE-005` / FE-006 is explicitly PAUSED until ADR-MONO-015 ACCEPTED.

# Scope

## In Scope

- `docs/adr/ADR-MONO-015-platform-console-dashboards-model.md` (PROPOSED).
- Minimal additive forward-pointer in `ADR-MONO-013` (Status/History "AMENDED-BY ADR-MONO-015" + a § 3 / D7.4 note that the `dashboards` parity line is refined by ADR-MONO-015) — ADR-013 is ACCEPTED; additive cross-reference only, no decision change.
- This task file + memory update.

## Out of Scope

- The composed-overview implementation (`TASK-PC-FE-005`, post-ACCEPTED).
- `console-integration-contract.md` § 2.4.4 / § 3 binding + `console-web/architecture.md` `features/dashboards` module (post-ACCEPTED, spec-first, per ADR § D4).
- FE-006 parity-verify (depends on the dashboards line being resolved).
- Flipping ADR-015 to ACCEPTED.
- GAP-side change (the decision is compose-from-existing — no producer change).

# Acceptance Criteria

- [ ] `ADR-MONO-015` exists, Status PROPOSED, house ADR structure (Status/History/Decision-driver/Supersedes-Amends/Related → §1 Context → §2 Decision D1–D6 → §3 Consequences → §4 Alternatives → §5 Relationship → §6 Transition History → §7 Provenance + model annotation).
- [ ] D1 selects Model B; A (Grafana iframe) / C (defer) explicitly rejected with reasons.
- [ ] D2 explicitly redefines the ADR-MONO-013 § 3 / D7.4 `dashboards` parity line as a composed operator overview (not Grafana) — the HARDSTOP-09 mitigation, documented + defensible (incl. how admin-web retirement stays defensible w.r.t. the dropped Grafana view).
- [ ] D4 mandates spec-first reconciliation of `console-integration-contract.md` § 3 → new § 2.4.4 + `console-web/architecture.md`; GAP specs unchanged.
- [ ] D5 enumerates the post-ACCEPTED sequence (TASK-PC-FE-005 → FE-006 = Phase 3 gate) + the additive ADR-013 note.
- [ ] ADR-MONO-013 carries an additive forward-pointer to ADR-MONO-015 (no decision change).
- [ ] ADR number unique; internal links resolve; `validate-rules` clean (doc-only).

# Related Specs

> Monorepo-level governance doc (shared `docs/adr/`). No project `PROJECT.md` classification applies to this task.

- `platform/hardstop-rules.md` (HARDSTOP-06 #3, HARDSTOP-09 #2 — the mandate)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § 3 / § D7.4 (parity checklist — amended additively)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (staged-ADR precedent; operator token the overview reads use)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 3 (bare `dashboards` line)
- `projects/global-account-platform/specs/services/admin-web/architecture.md` (`dashboards/page.tsx` = Grafana iframe — the parity source)
- `projects/global-account-platform/specs/contracts/http/admin-api.md` (no dashboard endpoint — the gap; the read endpoints the overview composes)

# Related Skills

- `.claude/skills/` — ADR authoring (ADR-MONO-008/013/014 house pattern).

---

# Related Contracts

- None changed by this task (doc-only). The contract reconciliation (§ 2.4.4 binding) is mandated by the ADR for the post-ACCEPTED execution phase (TASK-PC-FE-005).

---

# Target Service

- None (monorepo-level governance doc).

---

# Architecture

ADR-MONO house structure per `docs/adr/ADR-MONO-008` / `ADR-MONO-013` / `ADR-MONO-014`.

---

# Implementation Notes

- PROPOSED — decision direction (B) chosen but ACCEPTED gates the spec reconciliation + FE-005 (Hard Stop "PAUSE until ACCEPTED" discipline; ACCEPTED requires user-explicit intent, not self-declared).
- This ADR amends ADR-MONO-013 § 3 / D7.4 (parity checklist) — additive forward-pointer + a refinement note only; do not alter ADR-013 decisions (ADR-013 ACCEPTED; HARDSTOP-04 discipline).
- Doc-only: no churn-clock effect.

---

# Edge Cases

- Reader treats the user's "B" answer as ACCEPTED authorization → § 1.3 + § D6 guard (PROPOSED; explicit ACCEPTED required).
- Reader tries to build FE-005 / resume Phase 2 slice 4 without ACCEPTED → § D5/§ D6 + ADR-013 cross-ref re-enter the Hard Stop.
- Reader reads the dashboards parity-drop as a silent scope cut → D2 explicitly documents the redefinition + the admin-web-retirement defensibility (Grafana → operator/SRE tooling, not a console blocker).

---

# Failure Scenarios

- ADR lands ACCEPTED prematurely → FE-005 built + parity redefined before the decision is finalized (mitigated: § 1.3/§ D6, user-explicit ACCEPTED).
- `dashboards` parity hole left untracked → mitigated: ADR § 1.1 + D2 + D5 own the resolution + sequencing.
- FE-006 run against an unresolved dashboards line → false parity-pass / undefendable admin-web retirement (the HARDSTOP-09 failure mode this ADR prevents).

---

# Test Requirements

- ADR internal link / reference lint clean; `validate-rules` no new inconsistency (doc-only).

---

# Definition of Done

- [ ] ADR-MONO-015 authored (PROPOSED)
- [ ] ADR-MONO-013 forward-pointer + § 3/D7.4 refinement note added (additive)
- [ ] Acceptance Criteria satisfied
- [ ] Specs untouched (reconciliation deferred to post-ACCEPTED per ADR)
- [ ] Ready for review
