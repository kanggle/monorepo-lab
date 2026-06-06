# Task ID

TASK-MONO-186

# Title

Fix **3 broken cross-file `#anchor` links** caused by the gap→iam rename's heading-rename lag — the 6 `projects/*/PROJECT.md` `## GAP IdP Integration` headings (slug `#gap-idp-integration`) were never renamed, but MONO-179 changed the inbound README links to `PROJECT.md#iam-idp-integration`, leaving the anchors dead. Rename the 6 headings → `## IAM IdP Integration`. This is the **anchor** dead-ref class that the file-existence checkers (MONO-181 spec / MONO-184 README) do **not** detect. Heading-only (structural anchor target); section body prose stays MONO-180 residue.

# Status

ready

# Owner

claude (Opus 4.8) — monorepo-level cross-project anchor fix (6 `projects/*/PROJECT.md`). One atomic PR (CLAUDE.md § Cross-Project Changes; MONO-181/184 dead-ref precedent).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **선행/맥락**: gap→iam rename 179. 179 renamed the inbound README anchor links to `PROJECT.md#iam-idp-integration` (scm/finance/erp READMEs) but left the **target headings** `## GAP IdP Integration` (slug `#gap-idp-integration`) → broken anchors. Surfaced during MONO-185 (TEMPLATE.md heading rename incidentally fixed a sibling anchor; revealed file-existence checkers miss anchors).
- **no requirement/contract change** — heading text token only (`GAP`→`IAM`); section body byte-unchanged.

# Goal

After this task, all `]( ...PROJECT.md#iam-idp-integration)` anchor links resolve (the 6 `projects/*/PROJECT.md` IdP-integration headings read `## IAM IdP Integration` → slug `iam-idp-integration`). No inbound link points to the old `#gap-idp-integration` (verified 0). The cross-project PROJECT.md IdP-section heading is uniform.

# Scope

## In Scope (6 heading renames)

`## GAP IdP Integration` → `## IAM IdP Integration` in:
1. `projects/erp-platform/PROJECT.md` L77 — **fixes broken anchor** (erp README L79 → `PROJECT.md#iam-idp-integration`).
2. `projects/finance-platform/PROJECT.md` L71 — **fixes broken anchor** (finance README L79).
3. `projects/scm-platform/PROJECT.md` L67 — **fixes broken anchor** (scm README L86).
4. `projects/fan-platform/PROJECT.md` L70 — consistency (no current inbound anchor link; uniform heading + future-proof).
5. `projects/platform-console/PROJECT.md` L52 — consistency.
6. `projects/wms-platform/PROJECT.md` L49 — consistency.

## Out of Scope

- **The section BODY prose under these headings** (and the 896 `GAP` refs across 119 project-internal spec/contract files) — MONO-180's deliberately-deferred residue, much of it **historical** (iam-platform ADR-001 "GAP as OIDC AS" decision record, `tasks/INDEX.md` dated entries, `migration-notes.md`, `admin-web/overview.md` tombstone). A blind sweep would corrupt audit trails. This task touches only the **heading** (the structural anchor target); body prose remains residue (consistent with the 113 untouched files).
- Inbound `#gap-idp-integration` links — none exist (verified); nothing breaks.
- Any code/contract/migration.

# Acceptance Criteria

- AC-1: `git grep -nE '^#+ .*GAP IdP Integration' -- '**/PROJECT.md'` returns **0** (was 6); `^## IAM IdP Integration` present in all 6.
- AC-2: The 3 inbound `PROJECT.md#iam-idp-integration` README links resolve to an existing `## IAM IdP Integration` heading (slug `iam-idp-integration`).
- AC-3: `git diff` shows only the 6 heading lines changed (`GAP`→`IAM`); section bodies + everything else byte-unchanged.

# Related Specs

- None changed. The PROJECT.md IdP-integration section semantics are unchanged; only the heading token aligns.

# Related Contracts

- None.

# Edge Cases

- **Heading vs body GAP mismatch** — after the rename, the section heading reads `IAM` while body prose may still say `GAP` (MONO-180 residue). Acceptable: the heading is the structural anchor target; the body GAP is the same residue as the 113 other project-internal files (out of scope by the MONO-180/185 boundary).
- **`## GAP IdP Integration` is the markdown heading form** — the `## ` prefix disambiguates from inline `§ GAP IdP Integration` body references (which stay residue). Replace targets the heading only.
- **No inbound `#gap-idp-integration`** — verified 0; the rename breaks no existing anchor.

# Failure Scenarios

- **Renaming a body inline `GAP IdP Integration` ref** → would touch residue. Only the `## `-prefixed heading line is changed.
- **Missing a PROJECT.md** → re-run AC-1 grep (0 GAP IdP headings).
