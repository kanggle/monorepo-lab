# Task ID

TASK-MONO-056

# Title

`rules/` library 4-way sync audit — drift fix (B common-rule refactor closure)

# Status

done

# Owner

monorepo

# Task Tags

- spec
- rules
- audit

---

# Goal

Close minor 4-way sync drift in the `rules/` library surfaced by a follow-up audit after TASK-MONO-029 (2026-05-04) and Wave 2 (TASK-MONO-051 / PR #352, partial update). Memory `project_b_common_rule_refactor_pending.md` candidate #3 "rules/ audit 재진입" — this is the closure task.

The full audit (Sections A–F via backend-engineer agent) surfaced exactly **2 actionable drift items** + 1 already-known item (`platform/error-handling.md` Wave 3 — already covered by TASK-MONO-052 / `chore/mono-052-...` branch). All other sections (B trait cross-refs, D taxonomy.md ↔ domain rule narrative, E TEMPLATE.md, F README.md self-consistency) = PASS.

---

# Scope

## In Scope

### Fix 1: `.claude/config/activation-rules.md` — trait link drift

Two trait entries currently say `→ Detailed rules: *(file to be created when a project declares this trait)*` but the actual files exist:

- L49 `regulated` → file `rules/traits/regulated.md` **exists** since at least 2026-05-01 (GAP IdP series)
- L134 `audit-heavy` → file `rules/traits/audit-heavy.md` **exists** since at least 2026-05-04 (cleanup series 029~033)

PR #328 (TASK-MONO-051 Wave 1) updated `multi-tenant` + `batch-heavy` in this same location but missed these two. Mechanical 2-line fix.

### Fix 2: `rules/common.md` — "NOT in Common" table missing 2 entries

`platform/` has 18 markdown files (excluding `README.md` + `service-types/` + `contracts/`). `common.md` registers 14 canonical files; the remaining 4 are in the "Files Explicitly NOT in Common" table — except **2 files** which are present in `platform/` but cataloged nowhere:

- `platform/glossary.md` — referenced informally by no rule/domain/trait file. Status: reference glossary, agents lookup on-demand.
- `platform/object-storage-policy.md` — referenced by `rules/traits/content-heavy.md` L41 and `rules/domains/ecommerce.md` L139, but never registered in `common.md`. Activation = `content-heavy` trait.

Add both rows to the "Files Explicitly NOT in Common" table with proper activation conditions. This restores the agent-discoverable-source-of-truth invariant per `rules/README.md` (every platform/ rule file must be either common or explicitly excluded).

## Out of Scope (explicitly deferred or non-actionable)

- **Section A.* on `glossary.md` activation policy** — leave as "reference glossary, on-demand lookup" rather than promoting to common. glossary is not enforcement; it's terminology. Spec-author judgment.
- **Section B saas.md L143 / wms.md L149 "broken relative path"** — the audit flagged `platform/service-boundaries.md` (plain inline backticks, not a markdown link) as a broken relative path. Re-reading the context shows these are **plain dual-reference text** ("위치: `specs/services/` 전반 또는 `platform/service-boundaries.md`") — not links, no broken-link issue. Skip.
- **Section F `/validate-rules` skill linkage** — declared "future v0.2 scope" in `rules/README.md` L133; not a current drift.
- **Wave 3 catalog backfill** (`platform/error-handling.md`) — already covered by TASK-MONO-052 (`chore/mono-052-error-handling-catalog-wave-3` branch).

---

# Acceptance Criteria

- [ ] `.claude/config/activation-rules.md` L49 (regulated) updated: `→ Detailed rules: [\`rules/traits/regulated.md\`](../../rules/traits/regulated.md)`
- [ ] `.claude/config/activation-rules.md` L134 (audit-heavy) updated: `→ Detailed rules: [\`rules/traits/audit-heavy.md\`](../../rules/traits/audit-heavy.md)`
- [ ] `rules/common.md` "Files Explicitly NOT in Common" table includes 2 new rows (glossary.md, object-storage-policy.md) with activation conditions
- [ ] No code changes, no service spec changes
- [ ] B refactor closure recorded in memory `project_b_common_rule_refactor_pending.md` (candidate #3 status)

---

# Related Specs

- `.claude/config/activation-rules.md` (target)
- `rules/common.md` (target)
- `rules/README.md` § Three Layers, § Resolution Order (context)
- `rules/taxonomy.md` § Traits (cross-ref)
- `tasks/done/TASK-MONO-029-rules-claude-config-sync.md` (predecessor — established 4-way sync rule)
- `tasks/done/TASK-MONO-051-error-handling-catalog-audit.md` (Wave 2 — partial activation-rules.md update)

# Related Skills

- `.claude/skills/refactor-spec` (spec drift cleanup pattern)

---

# Related Contracts

None — internal monorepo rules library only.

---

# Target Service

N/A — shared paths only:
- `.claude/config/activation-rules.md`
- `rules/common.md`

---

# Architecture

N/A — registry/dispatch consistency restoration. No service architecture impact.

---

# Implementation Notes

Both fixes are small mechanical edits:

```
# Fix 1 (activation-rules.md)
- regulated:    L49 "*(file to be created ...)*" → markdown link to rules/traits/regulated.md
- audit-heavy:  L134 "*(file to be created ...)*" → markdown link to rules/traits/audit-heavy.md

# Fix 2 (common.md)
Add 2 rows to "Files Explicitly NOT in Common" table:
- platform/glossary.md         | (reference glossary — on-demand lookup, no activation condition)
- platform/object-storage-policy.md | content-heavy trait
```

For future audits, the discoverable-source-of-truth invariant is:

```
For every f in platform/*.md (except README.md / service-types/ / contracts/):
  either f appears in rules/common.md "Index" table (always-load)
  or     f appears in rules/common.md "Files Explicitly NOT in Common" table (conditional-load)
```

A CI grep gate enforcing this is a follow-up candidate (low priority — would file as TASK-MONO-057 or fold into a future `/validate-rules` skill expansion).

---

# Edge Cases

- **glossary.md classification**: this file is a terminology reference, not enforcement-bearing. Putting it in "NOT in Common" with a "no activation condition" annotation is the cleanest classification (vs promoting to common Index alongside 14 canonical rule files, which would conflate "reference" with "rule").
- **object-storage-policy.md activation**: `content-heavy` trait is the natural condition since both content-heavy.md and (via content-heavy declaration) ecommerce.md reference it. fan-platform also declares `content-heavy` so it inherits naturally.

---

# Failure Scenarios

- **None expected** — pure registry consistency fix. Existing tests (none — these are docs) are unaffected.

---

# Test Requirements

N/A — docs-only fix. Verification = manual:
- `grep -n 'regulated\.md\|audit-heavy\.md' .claude/config/activation-rules.md` shows live markdown links
- `grep -n 'glossary\.md\|object-storage' rules/common.md` shows both files in "NOT in Common" table

---

# Definition of Done

- [ ] activation-rules.md 2 entries updated
- [ ] common.md 2 entries added
- [ ] B refactor closure note in memory updated
- [ ] Impl PR description references this task + the audit method (4-way sync grep)

---

# Provenance

Audit performed by backend-engineer agent (Sonnet) in chat — 6 sections (A common.md vs platform/ / B domain rule cross-refs / C trait rule cross-refs / D taxonomy.md narrative vs detail / E TEMPLATE.md / F README.md). Sections B (broken-relative-path false positive), C, D, E, F = PASS. Section A = 2 missing rows. Already-known activation-rules.md drift surfaced before the audit started.

D4 OVERRIDE applies per ADR-MONO-003 § 3.4 risk 2 (B common-rule refactor scope). last_churn marker reset is minor (docs-only, no libs/ touch).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (4-line mechanical edit + 1 audit method note).
