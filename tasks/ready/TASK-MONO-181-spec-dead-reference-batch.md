# Task ID

TASK-MONO-181

# Title

Fix **16 broken cross-references in spec markdown** surfaced by a `/refactor-spec all` dead-reference audit — link-only corrections (stale task locations, path-depth miscounts, wrong ADR basename, a rename-introduced over-edit, retired-service relink, deliberately-uncreated-file link). No requirement/contract/decision change.

# Status

ready

# Owner

claude (Opus 4.8) — monorepo-level dead-ref batch across 5 projects' specs (erp/finance/scm/iam-platform/platform-console). One atomic PR (CLAUDE.md § Cross-Project Changes; precedent TASK-MONO-085 dead-ref batch).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **선행/맥락**: `/refactor-spec all` dead-reference scan (2026-06-07) — 9 spec dirs, 1730 relative links, **16 broken**. 1건은 TASK-MONO-180 broad sweep가 도입한 회귀(F16), 나머지 15건은 사전존재 backlog(rename 무관).
- **no requirement/contract change** — pure markdown link corrections; spec bodies/semantics byte-unchanged except the link target tokens.

# Goal

After this task, the spec dead-reference checker (relative markdown link existence sweep across `platform/` + all `projects/*/specs/`) returns **0 broken**, with every cross-reference resolving to an existing file. No spec requirement, contract, API/event schema, or architecture decision changes.

# Scope

## In Scope (16 link fixes)

**Stale task location (ready→done moves not reflected):**
1. `projects/erp-platform/specs/integration/iam-integration.md` L50 — `tasks/ready/TASK-MONO-119-...` → `tasks/done/` (MONO-119 is done).
2. `projects/erp-platform/specs/services/masterdata-service/architecture.md` L9 — `../../../tasks/ready/TASK-ERP-BE-001-...` → `tasks/done/` (project task done).
3. `projects/finance-platform/specs/integration/iam-integration.md` L50 — `tasks/ready/TASK-MONO-114-...` → `tasks/done/`.

**Path-depth miscount (off-by-one `../`):**
4. `projects/finance-platform/specs/integration/iam-integration.md` L116 — `../../contracts/http/account-api.md` → `../contracts/http/account-api.md` (overshoot; contracts is under specs/, one level up from integration/).
5–11. `projects/iam-platform/specs/services/admin-web/overview.md` (retirement tombstone, 7 links) — `](../../../../docs/adr/` → `](../../../../../docs/adr/` (ADR-013/014/015, +1 `../`); `](../../../platform-console/` → `](../../../../platform-console/` (PROJECT.md + console-integration-contract, +1 `../`). (L61 `docs/migration-notes.md` + L63 `PROJECT.md` already resolve — NOT touched.)

**Wrong ADR basename (observability-stack is 007, not 006):**
12. `projects/platform-console/specs/contracts/console-integration-contract.md` L1159 — `ADR-MONO-006-observability-stack.md` → `ADR-MONO-007-worktree-ephemeral-observability-stack.md` (006 = lint-remediation-as-agent-context; link text `ADR-MONO-006`→`ADR-MONO-007`).
13. `projects/platform-console/specs/services/console-bff/architecture.md` L294 — same ADR-006→007 fix (depth `../../../../../` already correct).

**Retired-service relink (admin-web architecture.md removed; only overview.md tombstone remains):**
14. `projects/platform-console/specs/services/console-web/architecture.md` L300 — `iam-platform/specs/services/admin-web/architecture.md` → `admin-web/overview.md` (the tombstone).

**Deliberately-uncreated-file link (drop markup, keep prose):**
15–16. `projects/erp-platform/specs/services/read-model-service/architecture.md` L306, L490 — `[`data-model.md`](data-model.md)` → plain `` `data-model.md` `` (the prose itself says it is a "low-priority follow-up if the projection grows" — the file is intentionally not yet created; the link to a non-existent intended file is the dead-ref).

**Rename-introduced regression (TASK-MONO-180 broad sweep over-edited a frozen tasks/done filename):**
17. `projects/scm-platform/specs/integration/iam-integration.md` L53 — `tasks/done/TASK-MONO-042-**iam**-v0013-scm-oidc-clients.md` → `...-**gap**-v0013-...` (tasks/done filenames are historical/frozen; MONO-179 excluded them, but the gap→iam sweep changed `gap`→`iam` inside this link target. Actual file = `...gap-v0013...`).

> (16 broken links span items 1–17 above; items 5–11 = the 7 admin-web links, items 15–16 = the 2 read-model occurrences.)

## Out of Scope

- Any `global-account-platform`/`gap`/`GAP` reference that is **historical narrative** (dated PR/SHA snapshots, past-tense event logs) — not a current pointer; leave intact (CLAUDE.md residue policy).
- Spec requirement/contract/decision content — link tokens only.
- Flyway migration files, tasks/done bodies.

# Acceptance Criteria

- AC-1: Dead-reference checker (relative markdown link existence across `platform/` + all `projects/*/specs/`) returns **0 broken** (was 16).
- AC-2: `git diff` shows ONLY link-target token changes + the two `data-model.md` link-markup removals — no prose/requirement/contract/schema/decision change.
- AC-3: Each corrected link resolves to an existing file (verified per-link).

# Related Specs

- `projects/{erp,finance,scm}-platform/specs/integration/iam-integration.md`
- `projects/iam-platform/specs/services/admin-web/overview.md` (retirement tombstone)
- `projects/platform-console/specs/{contracts/console-integration-contract.md, services/console-bff/architecture.md, services/console-web/architecture.md}`
- `projects/erp-platform/specs/services/{masterdata-service,read-model-service}/architecture.md`

# Related Contracts

- None changed. `account-api.md` / `console-integration-contract.md` link targets are corrected to resolve; their content is untouched.

# Edge Cases

- admin-web/overview.md is a **deliberate tombstone** whose stated purpose is "historical references resolve to a meaningful pointer rather than a 404" — its own broken links defeat that purpose; the depth fix restores it.
- F16 (scm) is the only rename-introduced break; the rest predate MONO-180 (surfaced, not caused, by this audit).
- `data-model.md` (read-model) is intentionally uncreated — drop the link markup, do NOT create a stub (no new spec authoring).

# Failure Scenarios

- **Over-correcting historical narrative** → noise + loss of audit trail. Only current-pointer links in the 16-item list are touched.
- **Creating data-model.md stub** → spec authoring, out of scope. Drop link only.
- **Wrong `../` count reintroduced** → re-run the dead-ref checker (AC-1) to confirm 0.
