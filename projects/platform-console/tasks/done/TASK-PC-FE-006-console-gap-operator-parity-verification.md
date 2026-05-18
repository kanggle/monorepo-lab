# Task ID

TASK-PC-FE-006

# Title

console-web Phase 2 slice 5 (FINAL) — GAP admin-web operator-parity verification (ADR-MONO-013 Phase 3 gate)

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- test
- code

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

# Dependency Markers

- **depends on**: `TASK-PC-FE-002` (accounts, #575), `TASK-PC-FE-003` (audit/security, #576), `TASK-PC-FE-004` (operators, #577), `TASK-PC-FE-005` (composed overview, #580 → main `7f8d6be0`) — **all merged**. ADR-MONO-013/014/015 ACCEPTED.
- **part of**: ADR-MONO-013 § D6 **Phase 2** — **slice 5 of 5 (FINAL)**: FE-002 ✅ → FE-003 ✅ → FE-004 ✅ → FE-005 ✅ → **FE-006 parity-verify** (this).
- **gates**: ADR-MONO-013 § 6 Phase 3 (`admin-web` retirement) — its prerequisite is literally "**Phase 2 parity verified**" (ADR-MONO-013 § 6 row 3, line 111). This task **produces** that verification. (The retirement itself is a **GAP project-internal spec-first** change — explicitly out of scope here.)
- **spec-first**: the § 3 parity-checklist finalization (verified matrix) lands **before/with** the verification test (HARDSTOP-06). No producer/feature change.
- **task-file**: this FE-006 task file is `git add`-ed in this PR's first commit (FE-003 gap-prevention rule). Do **not** touch the still-untracked FE-002/FE-002a task files (separate lifecycle chore).

# Goal

Produce the **formal parity verification** that the console's GAP operator surface (FE-002..005) has reached functional parity with `admin-web`'s operator surface across the **ADR-MONO-015-refined** `console-integration-contract.md` § 3 checklist, satisfying the ADR-MONO-013 § 6 Phase 3 prerequisite ("Phase 2 parity verified"). This is a **verification + finalization** task — it does **NOT** re-implement or modify any feature; it attests, with an executable parity matrix, that every checklist capability is present, operator-token-bounded, tenant-scoped, and covered.

After this task: § 3 is finalized (every line marked verified-by-FE-006 with a capability→module→§2.4.x→producer-endpoint→test-evidence matrix); a consolidated parity-verification test suite asserts the attestation programmatically; ADR-MONO-013 Phase 3 admin-web-retirement gate is **satisfiable** (the retirement is a separate GAP-internal task).

# Scope

## In Scope

### Spec-first (lands before/with the verification test, same PR commit 1)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 3 — finalize the parity checklist into a **verified parity matrix**: for every admin-web operator capability (accounts: search/detail/lock/unlock/bulk-lock/revoke-session/gdpr-delete/export · audit: query · security: login-history/suspicious · operators: create/edit-roles/change-status/change-password · dashboards: **ADR-MONO-015-refined → composed operator overview, not Grafana**) record → console feature module → § 2.4.x binding → GAP producer endpoint (`admin-api.md` §) → verification-test evidence. Mark each line "**verified by TASK-PC-FE-006**" and add an explicit closing statement: *"Phase 2 parity COMPLETE; the ADR-MONO-013 § 6 Phase 3 `admin-web`-retirement gate ('Phase 2 parity verified') is satisfied. Retirement itself is a separate GAP project-internal spec-first task (GAP `PROJECT.md` service map), out of scope here."*
- `projects/platform-console/specs/services/console-web/architecture.md` — a brief "Phase 2 operator-parity complete (FE-002..006)" note in the appropriate section; canonical Identity table + `### Service Type Composition` H3 untouched.
- ADR-MONO-013 § 6 D7.4 / Phase 3 row — an **additive** "Phase 2 parity verified by TASK-PC-FE-006 (PR #…)" note only; **no ADR-013 decision change** (ADR-013 ACCEPTED; HARDSTOP-04 discipline). Keep minimal/additive.
- GAP specs **unchanged**.

### Code/test (`apps/console-web`, follows the matrix)
- A consolidated **parity-verification test suite** (`tests/unit/parity-verification.test.ts` or similar) that programmatically attests, for each § 3 matrix row, that the capability is present and correctly bounded — composed over the **existing** FE-002..005 surface (do NOT re-test their internals; assert the parity-relevant invariants):
  - the feature module + route exist and resolve (accounts `/accounts`, audit `/audit`, operators `/operators`, overview `/dashboards`);
  - each capability's server client targets the **correct GAP producer path** (the `admin-api.md` § in the matrix) with the **operator token** (`getOperatorToken()`, never the GAP access token — the #569 boundary) and `X-Tenant-Id`;
  - mutation capabilities (accounts lock/unlock/bulk-lock/revoke-session/gdpr-delete; operators create/edit-roles/change-status) carry the contract-correct headers (per the § 2.4.1/§ 2.4.3 matrices — incl. the FE-004 per-endpoint Idempotency-Key non-uniformity) and are reason+confirm-gated; read capabilities (audit, overview) carry **no** mutation artifacts;
  - the refined `dashboards` line maps to the composed overview (not Grafana), per-source-isolated.
  - a single machine-readable parity matrix (a typed table/fixture the test iterates) so the matrix and the test cannot drift.
- No feature/route/producer changes. No new GAP client.

## Out of Scope

- **`admin-web` retirement itself** — ADR-MONO-013 Phase 3, a **GAP project-internal spec-first** change (GAP `PROJECT.md` service map → app removal). FE-006 only *satisfies the gate*; it does not retire anything and touches no GAP code/spec.
- Re-implementing / modifying any FE-002..005 feature, route, or producer binding (verification only — if a real parity gap is found, STOP and raise a fix task referencing the original slice, per the project Review Rules; do not silently patch here).
- New functional capability beyond the refined § 3 checklist.
- The FE-002/FE-002a untracked task-file lifecycle gap (separate chore).
- `console-bff` (Phase 7); wms/scm/finance/erp (Phase 4–6).

# Acceptance Criteria

- [ ] § 3 finalized into a verified parity matrix (capability → feature module → § 2.4.x → `admin-api.md` § → test evidence) covering accounts(8)/audit/security/operators(4)/dashboards-overview; the `dashboards` row reflects the **ADR-MONO-015-refined** composed-overview definition (explicitly not Grafana); each line marked "verified by TASK-PC-FE-006".
- [ ] § 3 carries the explicit closing statement that Phase 2 parity is COMPLETE and the ADR-MONO-013 Phase 3 `admin-web`-retirement gate is satisfied, with retirement called out as a separate GAP-internal task (out of scope).
- [ ] A consolidated parity-verification test asserts every matrix row programmatically (route resolves; correct producer path; operator-token-not-GAP bearer; tenant scope; mutation header/confirm vs read no-mutation per the § 2.4.x matrices); the matrix is a single machine-readable fixture the test iterates (no matrix↔test drift).
- [ ] ADR-MONO-013 § 6 / D7.4 carries an **additive** "Phase 2 parity verified by TASK-PC-FE-006" note only — ADR-013 decisions byte-unchanged.
- [ ] **No feature/route/producer/GAP change**: `git diff` touches only `projects/platform-console/` specs + the verification test + (additive) ADR-013 note; no FE-002..005 source modified; no GAP spec/code modified.
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (the new suite + all existing FE-001..005 suites; **zero regression**); spec internal-link lint clean; `validate-rules` no new inconsistency.
- [ ] Scope = `projects/platform-console/` (+ additive ADR-013 note) only; no churn-clock effect. ADR-MONO-013 Phase 2 = COMPLETE (5/5); Phase 3 gate satisfiable.
- [ ] If any real parity gap is found during verification → it is **reported as a new fix task** referencing the gapped slice (not silently patched here); FE-006 does not green-wash.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § 3 / § 6 (Phase 3 gate = "Phase 2 parity verified") / § D7.4 (the enumerated checklist)
- `docs/adr/ADR-MONO-014-...` (operator-token boundary the matrix attests) / `docs/adr/ADR-MONO-015-...` (the refined `dashboards` definition)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.1/2.4.2/2.4.3/2.4.4 + § 3 (the checklist being finalized)
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; FE-001..005)
- `projects/global-account-platform/specs/contracts/http/admin-api.md` (the producer endpoints the matrix maps to — read-reference only, unchanged)
- `projects/platform-console/tasks/review/TASK-PC-FE-002..005-...` (the merged slices being verified — task-shape parity)
- `projects/platform-console/tasks/INDEX.md` § Review Rules (a found gap → new fix task, not in-place patch)

# Related Skills

- `.claude/skills/` — qa/verification (parity matrix + executable attestation), frontend-engineer (console structure / § 2.4.x bindings), contract finalization, security review (operator-token boundary attestation).

---

# Related Contracts

- **Changed (this task, spec-first)**: `console-integration-contract.md` § 3 (finalized verified matrix) + `console-web/architecture.md` (Phase-2-complete note) + additive ADR-MONO-013 § 6/D7.4 note.
- **Consumed (unchanged)**: GAP `admin-api.md` (the producer endpoints the matrix references); the FE-002..005 feature modules (attested, not modified).

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — § 3 finalization + a consolidated parity-verification test. No GAP target (the retirement is a separate GAP-internal task).

---

# Architecture

- Verification artifact, not a feature: it composes/attests over the existing FE-002..005 surface. The single source of truth is the machine-readable parity matrix (spec § 3 ↔ the test fixture) — they must not be able to drift.
- ADR-MONO-013 Model B parity gate (D4): the console must reach **and verify** functional parity with `admin-web`'s operator surface before retirement; FE-006 is that verification step. ADR-MONO-015 D2: the `dashboards` parity line is the refined composed-overview (not Grafana) — the matrix records this explicitly so the retirement decision stays defensible.

---

# Implementation Notes

- Spec-first hard gate (HARDSTOP-06): finalize § 3 (verified matrix) + the additive ADR-013 note before/with the verification test, same PR.
- This is the **capstone** — verification + finalization only. Do NOT modify FE-002..005 features/routes/producers. Reuse the existing test utilities; the new suite is an *attestation layer*, not a re-test of internals.
- Honesty over green-wash: if verification surfaces a real parity gap (a checklist capability missing/incorrect), STOP and raise a **new fix task** referencing the gapped slice (project Review Rules) — do not silently patch it under FE-006. The matrix must reflect reality.
- ADR-013 is ACCEPTED — only an additive "parity verified" note; no decision change (HARDSTOP-04).
- Recommend implementation model: **Opus** (cross-slice parity attestation + § 3 contract finalization + the Phase-3-gate defensibility statement — interpretive, governance-adjacent). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`.
- Branch name must not contain the `master` substring.
- Local Docker unavailable → vitest jsdom is the local gate (FE-001..005 precedent); CI owns the rest.

---

# Edge Cases

- A checklist capability's route/module is present but its client targets the wrong producer path or uses the GAP token instead of the operator token → that is a **real parity gap** → new fix task, matrix records it as NOT verified (no green-wash).
- The refined `dashboards` line: the matrix must map it to the composed overview (`features/dashboards`, § 2.4.4) and explicitly note "not Grafana — ADR-MONO-015 D2", so the Phase 3 retirement defensibility (Grafana view re-scoped to operator/SRE tooling) is recorded.
- accounts "detail" (capability #2) is composed (no dedicated producer GET-by-id) — the matrix records its composition basis (search/list item + per-account ops), consistent with FE-002, not a fabricated endpoint.
- Matrix ↔ test drift: the test MUST iterate the same machine-readable matrix the spec documents (one fixture) so a future feature change that breaks parity fails the suite.
- Someone treats FE-006 merge as "admin-web is retired" → § 3 closing statement + this task's Out-of-Scope explicitly state retirement is a separate GAP-internal task; FE-006 only satisfies the gate.

# Failure Scenarios

- Green-washing a missing capability to "verified" → violates the honesty AC; the matrix must reflect reality and a gap → new fix task.
- Re-implementing/patching a feature under FE-006 → scope violation; verification only (a gap is reported, not fixed here).
- Matrix and test drift apart → one fixture iterated by both prevents it; AC requires it.
- ADR-013 edited beyond an additive note → HARDSTOP-04; constrained to the additive "parity verified" line.
- FE-006 merged but read as authorizing admin-web removal → § 3 closing statement + Out of Scope make retirement a distinct GAP-internal task.
- GAP/shared path touched → scope violation; FE-006 is platform-console + additive ADR-013 note only.

---

# Test Requirements

- vitest (jsdom): the consolidated parity-verification suite — iterates the single machine-readable matrix; per row asserts route resolves + correct `admin-api.md` producer path + operator-token (not GAP) bearer + `X-Tenant-Id` + (mutation rows) contract-correct headers/confirm per § 2.4.1/§ 2.4.3 incl. FE-004 Idempotency-Key non-uniformity + (read rows audit/overview) no mutation artifacts + (dashboards) composed-overview/per-source-isolation/not-Grafana.
- All existing FE-001..005 suites green (zero regression); `pnpm build` + `pnpm lint` (0) green; axe unaffected (no new screen).
- Spec internal-link lint clean; `validate-rules` no new inconsistency.

---

# Definition of Done

- [ ] § 3 finalized as a verified parity matrix (single machine-readable fixture) + Phase-2-COMPLETE / Phase-3-gate-satisfied closing statement (retirement = separate GAP-internal task)
- [ ] Consolidated parity-verification test green, iterating the matrix (no matrix↔test drift), zero regression
- [ ] Additive ADR-MONO-013 § 6/D7.4 "parity verified by FE-006" note (no decision change)
- [ ] No feature/route/producer/GAP change; scope = platform-console only
- [ ] Any real gap found → reported as a new fix task (no green-wash)
- [ ] Acceptance Criteria all satisfied; ADR-MONO-013 Phase 2 = COMPLETE (5/5); Phase 3 admin-web-retirement gate satisfiable
- [ ] Ready for review
