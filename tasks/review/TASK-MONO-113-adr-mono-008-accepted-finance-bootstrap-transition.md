# Task ID

TASK-MONO-113

# Title

ADR-MONO-008 PROPOSED → ACCEPTED transition — finance-platform bootstrap authorization + governance recordings (PR-A, doc-only)

# Status

review

# Owner

architect

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

# Dependency Markers

- **depends on**: ADR-MONO-008 (PROPOSED 2026-05-13, TASK-MONO-071) — this task is its prescribed § D6 ACCEPTED transition. ADR-MONO-003a § D2.1 (new-domain bootstrap requires a fresh ADR — ADR-008 is that ADR; this task flips it). ADR-MONO-002 § D4 (ordering parent — `scm → finance`).
- **prerequisite for**: TASK-MONO-114 (PR-B — finance-platform bootstrap artifact: monorepo direct-include + `rules/domains/fintech.md` + account-service skeleton + GAP V-slot seed + TASK-FIN-BE-001). PR-B may not land before this ADR is ACCEPTED.
- **origin**: user-explicit affirmative bootstrap direction this session (deliberate AskUserQuestion selections: "finance-platform bootstrap" → domain "fintech" → integration mode "C. Both"), satisfying ADR-MONO-008 § D6.1 (NOT the ambiguous "finance는 언제?" form).
- **spec-first / spec-only / doc-only**: this PR is **doc-only** — ADR status flip + governance recordings + memory. Zero production code / contract / schema / event / skeleton (those are PR-B / TASK-MONO-114). Per ADR-MONO-008 § D6.2 PR-A definition.

---

# Goal

ADR-MONO-008 ("finance-platform Bootstrap Criteria, Integration Mode, Template-First Procedure") was authored PROPOSED 2026-05-13 as a pre-authored criteria document — explicitly designed (§ 1.2, § 1.3, § 4.5) to transition to ACCEPTED when (a) the D5 readiness criteria are evaluated and (b) user-explicit bootstrap intent satisfies § D6.1. Both now hold:

- **D5.1** `fintech` in `rules/taxonomy.md` — confirmed present (L201, `#### fintech`).
- **D5.2** trait stack finalised — `[transactional, regulated, audit-heavy]`. Validated against `rules/taxonomy.md` § Traits (11 traits): all three present. ADR § D2's "optional `event-driven`" is **excluded** — `event-driven` is NOT one of the 11 taxonomy traits; including it would be a HARDSTOP-02 (unknown trait). Final stack = exactly the 3 valid compliance-stress traits.
- **D5.3** initial service — `account-service` (ADR § D3 default; `ledger-service` deferred to v2).
- **D5.4** integration mode — **Option C (Both)** — Template fork (external `kanggle/finance-platform`) + monorepo `projects/finance-platform/` direct-include (ADR § D1 default recommendation; user-chosen).
- **D5.5** user-explicit intent — recorded: deliberate multi-part affirmative authorization via AskUserQuestion (bootstrap finance-platform + `fintech` + Option C). Satisfies § D6.1 ("finance 부트스트랩 시작 / affirmative direction context"); not the excluded ambiguous form.
- **D5.6** Template repo state — informational only (§ D5.6 / § D4 step 2 non-gating); recorded in the ADR § 6 row.

This is the **ADR's own § D6-prescribed governed transition**, driven by satisfied user intent — structurally identical to the ADR-MONO-003b PROPOSED→ACCEPTED precedent (a legitimate, recorded transition, not a unilateral self-declaration). Per ADR-MONO-008 § D6.2, the ACCEPTED transition + recordings is **PR-A (doc-only)**; the bootstrap artifact is the separate **PR-B (TASK-MONO-114)**.

# Scope

## In Scope (doc-only, all shared paths → monorepo-level task)

1. **`docs/adr/ADR-MONO-008-finance-platform-bootstrap.md`**:
   - `**Status:** PROPOSED` → `**Status:** ACCEPTED`.
   - `**History:**` line: append `· ACCEPTED <date> (TASK-MONO-113 — D5.1–D5.6 evaluated, § D6.1 user intent satisfied; Option C; PR-A).`
   - § 6 Status Transition History: append the ACCEPTED row in the exact § D6.3 format (`| <date> | ACCEPTED | C (Both) | fintech / [transactional, regulated, audit-heavy] / [rest-api, event-consumer] | Both (kanggle/finance-platform standalone + projects/finance-platform monorepo) | "<user intent quote>" | PR-A #<this> / PR-B #<MONO-114> |`). PR-B # backfilled when PR-B opens (placeholder `#TBD-PR-B` until then).
2. **`docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md`** § D4 — append a progression line after the existing 2026-05-13 Forward pointer (L159): the `scm → finance` step is now ACCEPTED/executing per ADR-MONO-008; § D4 ordering advances one slot; erp/mes remain deferred to a future ADR.
3. **`docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md`** § 3 Audit trail — append row #15: category **"New domain bootstrap"** (Phase 6 first downstream Template usage), per ADR-MONO-008 § D4 step 19. One-off category — does NOT add "New domain bootstrap" to § D1 enumeration (analogous to row #14's "Phase 5 launch" one-off note); the new-domain bootstrap is ADR-MONO-008-governed, not a recurring D4 OVERRIDE category.
4. **Memory** (`project_portfolio_7axis_architecture`, `project_monorepo_template_strategy` + their `MEMORY.md` index lines): finance = ACCEPTED/bootstrapping, Option C, Template first downstream usage confirmed.
5. **This task** lifecycle ready → review (PR-A), → done (close chore).

## Out of Scope

- Any bootstrap artifact: `rules/domains/fintech.md`, `projects/finance-platform/` tree, account-service skeleton, GAP V-slot Flyway seed, `settings.gradle`, root `package.json`, `docs/project-overview.md` roster, root `README.md` hub, `scripts/sync-portfolio.sh` PROJECT_REMOTES, the external `kanggle/finance-platform` repo — **all PR-B / TASK-MONO-114** (ADR-MONO-008 § D6.2).
- Changing ADR-MONO-008's decisions (D1–D6 are fixed by the PROPOSED ADR; this task only flips status + records — it does not re-decide).
- erp / mes ordering (ADR-MONO-009/010 future, per ADR-008 § 3.2 + ADR-002 § D4).

# Acceptance Criteria

- **AC-1**: `grep -n "^\*\*Status:\*\*" docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` → `ACCEPTED` (was `PROPOSED`). No other ADR-008 § 1–5 decision text altered (status/history/§6 only).
- **AC-2**: ADR-008 § 6 has a new append-only ACCEPTED row matching the § D6.3 column format exactly: `| <date> | ACCEPTED | C (Both) | fintech / [transactional, regulated, audit-heavy] / [rest-api, event-consumer] | Both | "<verbatim user intent>" | PR-A #<n> / #TBD-PR-B |`.
- **AC-3**: ADR-MONO-002 § D4 has one appended progression line (after L159) recording scm→finance ACCEPTED via ADR-008; existing Forward-pointer text byte-unchanged.
- **AC-4**: ADR-MONO-003a § 3 table has appended row `| 15 | #<this PR> | <date> | <commit> | New domain bootstrap | … finance-platform (ADR-MONO-008 ACCEPTED, Option C) — Phase 6 first downstream Template usage … one-off, not added to § D1 |`. Rows 1–14 byte-unchanged.
- **AC-5**: memory `project_portfolio_7axis_architecture` + `project_monorepo_template_strategy` reflect finance ACCEPTED/Option C + MEMORY.md index lines updated. (Memory is outside git; verified by inspection, not CI.)
- **AC-6 (doc-only)**: `git diff --stat` = 3 ADR files + 1 task file (ready→review). Zero code/contract/schema/event/skeleton. No `projects/finance-platform/` path created in this PR.
- **AC-7 (CI)**: doc-only → CI `changes` markdown fast-lane; no build/test job. HARDSTOP hooks PASS (no project path-token into shared README in this PR). Self-review APPROVED.
- **AC-8 (governance integrity)**: the ACCEPTED flip cites ADR-008 § D6 + the verbatim user-intent quote in PR-A description; it is the ADR-prescribed transition (not a self-declaration) — ADR-MONO-003b precedent explicitly invoked.

# Related Specs

- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` — the ADR being transitioned (its § D4/D5/D6 govern this task exactly).
- `docs/adr/ADR-MONO-002` § D4, `docs/adr/ADR-MONO-003a` § D2.1 + § 3, `docs/adr/ADR-MONO-003b` (ACCEPTED-transition precedent).
- `rules/taxonomy.md` § Financial Services (L201 `fintech`) + § Traits (11) — D5.1/D5.2 evidence.
- memory `project_portfolio_7axis_architecture`, `project_monorepo_template_strategy`, `project_scm_platform_bootstrap` (5번째 부트스트랩 reference).

# Related Contracts

- None. No HTTP/event contract. ADR governance + audit-trail documentation only; invisible across every service boundary. (PR-B's GAP V-slot seed will touch GAP auth/account contracts — out of scope here.)

# Edge Cases

- **"never self-declare ADR ACCEPTED" standing rule**: honored — this is ADR-MONO-008's own § D6-prescribed transition, gated on satisfied user-explicit § D6.1 intent (deliberate AskUserQuestion authorization), recorded with the verbatim quote. Identical governance shape to ADR-MONO-003b's recorded ACCEPTED transition. The standing rule guards against *unilateral* acceptance without process/authorization — not against executing an explicitly user-authorized, ADR-prescribed transition.
- **`event-driven` trait trap**: ADR-008 § D2 lists "optional `event-driven`" but the 11 taxonomy traits contain no `event-driven`. Final D5.2 stack must exclude it (else PR-B's PROJECT.md → HARDSTOP-02). Recorded here so PR-B inherits the correct 3-trait stack.
- **PR-B # not yet known**: ADR-008 § 6 row + ADR-003a row #15 use `#TBD-PR-B` placeholder; backfilled in the PR-B close chore (TASK-MONO-114) — append-only, no rewrite of PR-A's rows.
- **ADR-002 forward-pointer already mentions ADR-008**: do not rewrite L159; append a NEW progression line below it (the L157-159 block stays byte-identical — append-only audit discipline, BE-302/INDEX seam lesson).

# Failure Scenarios

- **ADR-008 § 1–5 decision text edited** → AC-1 rejects (only Status/History/§6 may change; D1–D6 are fixed by the PROPOSED ADR).
- **`event-driven` carried into the recorded trait stack** → AC-2 + Edge Case; PR-B would then HARDSTOP-02. Mitigation: stack pinned to `[transactional, regulated, audit-heavy]` here.
- **PR-B artifact leaks into this PR** (a `projects/finance-platform/` file, `rules/domains/fintech.md`, settings.gradle) → AC-6 rejects (doc-only diffstat = 3 ADR + 1 task).
- **Treated as not needing user authorization (over-reach)** → § D6.1 + AC-8: the transition is gated on the recorded verbatim user intent; absent that it would not proceed.
- **Existing ADR-003a rows 1–14 / ADR-002 forward-pointer mutated** → AC-3/AC-4 append-only check rejects.

# Verification

1. `grep -n "Status:\*\* ACCEPTED" docs/adr/ADR-MONO-008*.md` → 1; `grep -c PROPOSED` on its Status line → 0 (AC-1).
2. ADR-008 § 6 tail row matches § D6.3 format incl. verbatim user-intent quote + `C (Both)` + `fintech / [transactional, regulated, audit-heavy] / [rest-api, event-consumer]` (AC-2).
3. `git diff docs/adr/ADR-MONO-002*.md` → exactly one appended line after the Forward-pointer block; L157-159 unchanged (AC-3).
4. `git diff docs/adr/ADR-MONO-003a*.md` → exactly one appended row #15; rows 1–14 + prose unchanged (AC-4).
5. `git diff --stat` → 3 ADR + 1 task md only; no `projects/finance-platform/`, no `rules/domains/fintech.md`, no `settings.gradle` (AC-6).
6. memory files + MEMORY.md index inspected (AC-5). CI `changes` fast-lane only (AC-7).

분석=Opus 4.7 / 구현=Opus 4.7 (governance transition — D5 evaluation + D6.1 intent adjudication + append-only audit-trail discipline require interpretive judgement; doc-only, structurally identical to the ADR-MONO-003b ACCEPTED-transition path) — executed directly this session / 리뷰=Opus 4.7 (inline self-review; status-flip surgical + append-only + governance-citation discipline).
