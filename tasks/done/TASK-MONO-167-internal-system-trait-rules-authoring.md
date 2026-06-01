# Task ID

TASK-MONO-167

# Title

Author the missing `rules/traits/internal-system.md` trait rules file (on-demand-policy backfill — erp-platform declared `internal-system` as primary at ADR-MONO-016 bootstrap but the detailed rules file was never added) + reconcile the `.claude/config/activation-rules.md` stale "file to be created" pointer — spec authoring only, no new decisions

# Status

done

# Owner

architect / spec authoring (rules library — no production code)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

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

- **surfaced by**: `project_refactor_sweep_status` memory (RULES-001 candidate, 2026-05-26 `/refactor-spec rules` dry-run) + audit-memory 2026-06-02 전수 본문 정독 (re-confirmed the gap is real, not stale memory).
- **on-demand policy basis**: [`rules/README.md`](../../rules/README.md) § On-Demand Generation Policy — "새 프로젝트가 특정 domain/trait을 선언할 때, 해당 프로젝트 PR에서 규칙 파일을 같은 변경으로 함께 추가한다." erp-platform bootstrap (ADR-MONO-016 ACCEPTED 2026-05-19, PR #619~#621) declared `traits: [internal-system, transactional, audit-heavy]` with `internal-system` as the **first primary use** but did NOT add `rules/traits/internal-system.md` — this task backfills it.
- **catalog already registered (no new tag)**: `internal-system` is already in [`rules/taxonomy.md`](../../rules/taxonomy.md) (§ internal-system def + Incompatibilities `internal-system + multi-tenant = 허용` + decision-tree #12) and [`.claude/config/traits.md`](../../.claude/config/traits.md) (line 25) and [`.claude/config/activation-rules.md`](../../.claude/config/activation-rules.md) (§ internal-system, 5 categories). Only the detailed `rules/traits/` file + the activation-rules link pointer are missing — NO taxonomy/catalog membership change.
- **sibling pattern**: [`rules/traits/audit-heavy.md`](../../rules/traits/audit-heavy.md) — the structural template (Scope / Mandatory Rules / Forbidden Patterns / Required Artifacts / Interaction with Common Rules / Checklist). `internal-system.md` mirrors it.
- **`.claude/` classifier hazard**: editing `.claude/config/activation-rules.md` may be blocked by the auto-mode classifier (`env_classifier_claude_self_mod_block`). If blocked, hand the one-line pointer patch to the user; the `rules/traits/internal-system.md` authoring (the substantive deliverable) is NOT under `.claude/` and is unaffected.

---

# Goal

Backfill the one missing trait rules file in the rule library so the resolution order (rules/README.md § Resolution Order step 4) has a concrete `rules/traits/internal-system.md` to load for the erp-platform (and any future `internal-system` project), and remove the stale "(file to be created when a project declares this trait)" pointer in `activation-rules.md` now that a project HAS declared it.

This is **spec authoring with NO new decisions** (`/refactor-spec` out-of-scope constraint is why it needs its own task): the trait's 5 rule categories are already fixed by `activation-rules.md` (RBAC / admin·operator workflow / scheduled jobs / operational traceability / SSO integration) and `taxonomy.md` (SSO 연동 / 권한 매트릭스 / 감사 로그 / 외부 노출 금지 / 내부 네트워크 제약). This task expresses those already-decided categories as Mandatory Rules + Forbidden Patterns + Required Artifacts + Interaction + Checklist in the sibling format — it does not invent new constraints.

# Scope

## In Scope

1. **`rules/traits/internal-system.md`** (new) — author in the audit-heavy.md sibling structure:
   - **Scope** — internal employee/operator-only system; no external public traffic; SSO-based internal auth; admin/operator-centric; commonly co-declared with `audit-heavy` + `transactional`; `multi-tenant` co-declaration allowed (taxonomy Incompatibilities).
   - **Mandatory Rules** covering exactly the 5 activation-rules categories (no more, no fewer new constraints): SSO-only authentication (no self-auth, OAuth2 Resource Server, single tenant-claim pass) / no external exposure + internal network boundary / RBAC permission matrix (fail-closed, least-privilege, undefined=deny) / admin·operator workflow (operation catalog + approval/delegation state machine + separation-of-duties) / scheduled-jobs governance (idempotent, re-run safe, dup-execution guard, manual-trigger audited) / operational traceability (which operational actions are traced — recording mechanism cross-refs audit-heavy).
   - **Forbidden Patterns / Required Artifacts / Interaction with Common Rules / Checklist** — mirror sibling depth (~200-350 lines total).
   - Keep it **domain-agnostic** (rules library discipline — no `erp`/service names/API paths; cross-ref `rules/domains/erp.md` for erp-specific RBAC/approval rather than duplicating).
2. **`.claude/config/activation-rules.md`** line 110 — replace `→ Detailed rules: *(file to be created when a project declares this trait)*` with `→ Detailed rules: [`rules/traits/internal-system.md`](../../rules/traits/internal-system.md)` (drift reconcile per rules/README.md § Routing Layer). **If the classifier blocks this `.claude/` edit, hand the one-line patch to the user.**
3. **Task md + root `tasks/INDEX.md`** ready entry.

## Out of Scope

- **taxonomy.md / traits.md / domains catalog membership** — `internal-system` already registered; NO catalog change.
- **New rule decisions** — only express the already-fixed 5 categories; if a genuinely new constraint seems needed, that is a separate decision (STOP).
- **erp-platform spec/code changes** — erp already works (on-demand "파일 부재 = 추가 제약 없음"); this only adds the now-loadable rules file. No erp service edit.
- **Overrides of common rules** — internal-system adds constraints; it does not loosen common (no `## Overrides` block unless a genuine conflict exists → then HARDSTOP-04).

# Acceptance Criteria

- [ ] **AC-1** `rules/traits/internal-system.md` exists, follows the audit-heavy.md section structure (Scope / Mandatory Rules / Forbidden Patterns / Required Artifacts / Interaction with Common Rules / Checklist).
- [ ] **AC-2** Mandatory Rules cover all 5 activation-rules categories (RBAC / admin·operator workflow / scheduled jobs / operational traceability / SSO integration) + taxonomy's "외부 노출 금지 / 내부 네트워크 제약" — each as a numbered rule.
- [ ] **AC-3** File is domain-agnostic (no service names / API paths / `erp` specifics; grep clean) — cross-references `rules/domains/erp.md` and sibling traits instead of duplicating.
- [ ] **AC-4** Interaction section cross-references `audit-heavy` (recording vs what-to-trace split), `transactional` (approval state machine), `multi-tenant` (allowed co-decl), and the platform SSO/auth canonical source.
- [ ] **AC-5** `activation-rules.md` § internal-system pointer updated to link the new file (or, if classifier-blocked, the exact patch handed to the user + noted in the PR).
- [ ] **AC-6** `/validate-rules` (or manual: taxonomy ↔ .claude/config ↔ rules/traits disk file set membership) passes — no drift introduced.
- [ ] **AC-7** No production code, no erp spec/code, no taxonomy/catalog membership change in the diff (rules/traits + activation-rules pointer + task lifecycle only).

# Related Specs

- [`rules/README.md`](../../rules/README.md) § On-Demand Generation Policy + § Resolution Order + § Routing Layer (drift rule).
- [`rules/taxonomy.md`](../../rules/taxonomy.md) § internal-system (def + Incompatibilities + decision tree).
- [`.claude/config/activation-rules.md`](../../.claude/config/activation-rules.md) § internal-system (the 5 categories this file expresses).
- [`rules/traits/audit-heavy.md`](../../rules/traits/audit-heavy.md) (sibling structural template).
- [`projects/erp-platform/PROJECT.md`](../../projects/erp-platform/PROJECT.md) (the declaring project; internal-system primary).

# Related Contracts

- None (rule library documentation; no API/event contract).

# Edge Cases

- **internal-system × audit-heavy overlap** — operational traceability (I-rule) vs audit-heavy A1/A2 recording. Resolve by SPLIT: internal-system defines *which operational actions must be traceable*, audit-heavy defines *how to record immutably*. Cross-ref, do not duplicate.
- **internal-system × multi-tenant** — taxonomy marks this 허용 (internal but org-isolated). The SSO rule must accommodate a tenant claim without contradicting multi-tenant.md.
- **scheduled-jobs vs batch-heavy** — batch-heavy (if co-declared) owns batch *processing* semantics; internal-system's scheduled-jobs rule owns *operational governance* (idempotency/dup-guard/manual-trigger audit). Cross-ref to avoid double-ownership.
- **classifier blocks the activation-rules.md edit** — the substantive deliverable (rules/traits file) still lands; the pointer reconcile is handed to the user (do not shell-bypass).

# Failure Scenarios

- **Inventing new constraints** — `/refactor-spec` was out-of-scope precisely because authoring can drift into new decisions. Mitigation: AC-2 ties rules to the already-fixed 5 categories; a genuinely new constraint → STOP + separate decision.
- **Domain leakage** — copying erp RBAC/approval specifics into the trait file breaks rules-library agnosticism (HARDSTOP-03 risk on shared path). Mitigation: AC-3 grep-clean + cross-ref erp.md.
- **Drift (one-sided edit)** — adding the rules file without the activation-rules pointer (or vice versa) leaves the routing catalog inconsistent. Mitigation: AC-5 + AC-6 (`/validate-rules`).
- **CI** — docs-only change; markdown fast-lane. No code job impact expected.

---

분석=Opus 4.8 / 구현 권장=Opus (rules-library spec authoring under the "no new decisions" + domain-agnostic + drift-reconcile discipline; sibling-format fidelity). 단순 transcription 이 아니라 5 카테고리를 common 과 충돌 없이 trait 규칙으로 표현 + audit-heavy/transactional/multi-tenant 교차 경계 판정이 필요해 Opus.
