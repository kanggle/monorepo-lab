# Task ID

TASK-MONO-109

# Title

ADR-MONO-014 — platform-console operator auth (token exchange) PROPOSED

# Status

done

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

Author **ADR-MONO-014** (PROPOSED) recording the resolution of a HARDSTOP-06/09 surfaced during the ADR-MONO-013 Phase 2 investigation: the console authenticates operators via GAP OIDC (`platform-console-web`) but every `/api/admin/**` operator endpoint (incl. the BE-296 registry) requires an admin-service operator token (`token_type=admin`, `iss=admin-service`), with no bridge defined — and `console-integration-contract.md` § 2.1↔§ 2.2 is self-contradictory (shipped #569).

After this task: the decision (Model **B — RFC 8693-style token exchange**, user-chosen) + design + contract-reconciliation mandate + downstream task plan are recorded; Phase 2 / `TASK-PC-FE-002` is explicitly PAUSED until ADR-MONO-014 ACCEPTED.

# Scope

## In Scope

- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (PROPOSED).
- Minimal forward-pointer in `ADR-MONO-013` (Status/History "AMENDED-BY ADR-MONO-014" + a § D5 note) — ADR-013 is ACCEPTED; only an additive cross-reference, no decision change.
- This task file + memory update.

## Out of Scope

- The GAP exchange endpoint implementation (`TASK-BE-298`, post-ACCEPTED).
- console-integration-contract.md / console-registry-api.md / admin-api.md reconciliation edits (post-ACCEPTED, spec-first, per ADR § D4/D5).
- Phase 2 operator-parity (`TASK-PC-FE-002`+) — PAUSED until ADR-014 ACCEPTED + `TASK-BE-298` merged.
- Flipping ADR-014 to ACCEPTED.

# Acceptance Criteria

- [ ] `ADR-MONO-014` exists, Status PROPOSED, house ADR structure (Status/History/Decision driver/Supersedes-Amends/Related → §1 Context → §2 Decision D1–D6 → §3 Consequences → §4 Alternatives → §5 Relationship → §6 Transition History → §7 Provenance + model annotation).
- [ ] D1 selects Model B; A/C/D explicitly rejected with reasons.
- [ ] D4 mandates spec-first reconciliation of console-integration-contract.md §2.1/§2.2 + console-registry-api.md §Authentication + GAP admin-api.md.
- [ ] D5 enumerates the post-ACCEPTED sequence (GAP TASK-BE-298 → console exchange wiring + #569 contract fix → Phase 2 parity slices).
- [ ] ADR-MONO-013 carries an additive forward-pointer to ADR-MONO-014 (no decision change).
- [ ] ADR number unique; internal links resolve; `validate-rules` clean.

# Related Specs

> Monorepo-level governance doc (shared `docs/adr/`). No project `PROJECT.md` classification applies.

- `platform/hardstop-rules.md` (HARDSTOP-06 #3, HARDSTOP-09 #2 — the mandate)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D5
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.1/§ 2.2
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` § Authentication
- `projects/global-account-platform/specs/services/admin-service/security.md` (operator JWT boundary)

# Related Skills

- `.claude/skills/` — ADR authoring (ADR-MONO-008/013 house pattern).

---

# Related Contracts

- None changed by this task (doc-only). The contract reconciliation is mandated by the ADR for the post-ACCEPTED execution phase.

---

# Target Service

- None (monorepo-level governance doc).

---

# Architecture

ADR-MONO house structure per `docs/adr/ADR-MONO-008` / `ADR-MONO-013`.

---

# Implementation Notes

- PROPOSED — decision direction (B) is chosen but ACCEPTED gates the exchange-endpoint churn + Phase 2 resume (Hard Stop "PAUSE until ACCEPTED" discipline).
- This ADR amends ADR-MONO-013 §D5 (deferred bridge) — additive forward-pointer only; do not alter ADR-013 decisions.
- Doc-only: no churn-clock effect.

---

# Edge Cases

- Reader treats the user's "B" answer as ACCEPTED authorisation → §1.3 + §D6 guard (PROPOSED; explicit ACCEPTED required).
- Reader tries to resume Phase 2 without `TASK-BE-298` → §D5 + ADR-013 cross-ref re-enter the Hard Stop.

---

# Failure Scenarios

- ADR lands ACCEPTED prematurely → unbudgeted GAP auth churn before the exchange design is finalised (mitigated: §1.3/§D6).
- #569 contract self-contradiction left untracked → mitigated: ADR §1.1 + D5 step 2 explicitly own the fix.
- Phase 2 implemented against the contradictory contract → 401 in live env + undefendable implicit operator-trust decision (the HARDSTOP-09 failure mode this ADR prevents).

---

# Test Requirements

- ADR internal link / reference lint clean; `validate-rules` no new inconsistency.

---

# Definition of Done

- [ ] ADR-MONO-014 authored (PROPOSED)
- [ ] ADR-MONO-013 forward-pointer added (additive)
- [ ] Acceptance Criteria satisfied
- [ ] Specs untouched (reconciliation deferred to post-ACCEPTED per ADR)
- [ ] Ready for review
