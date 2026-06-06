# Task ID

TASK-MONO-187

# Title

Fix the **2 remaining broken `#anchor` links** in the repo — `account-service/retention.md` links to `data-model.md#anonymization`, but the target heading is `## Anonymization (삭제 유예 + PII 제거)` (GitHub slug `#anonymization-삭제-유예--pii-제거`). Surfaced by a **repo-wide anchor-existence checker** (the completion of the dead-ref audit dimension: MONO-181 spec-file / MONO-184 README-file checked file existence only; neither validated `#anchor` fragments). Link-only; brings anchor dead-refs to repo-wide **0** (82/82 links resolve).

# Status

ready

# Owner

claude (Opus 4.8) — monorepo-level dead-ref terminal fix (1 file, 2 links). One atomic PR (CLAUDE.md § Cross-Project Changes is moot — single project file; bundled as the dead-ref arc terminal, MONO-181/184/186 precedent).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **선행/맥락**: repo-wide anchor-existence sweep (2026-06-07) — 1800 tracked `.md`, 82 `](path#anchor)` links resolved against target-file heading slugs (GitHub-slugger algorithm: lowercase → strip markdown → drop non `[\p{L}\p{N} _-]` → space→hyphen, no-collapse/no-trim). **2 broken** (both this `#anonymization`); the other 21 initial candidates were false positives from a naive slugifier (`\s+`-collapse + trim) — corrected slugifier confirmed them GitHub-valid (`--` from removed punctuation between spaces; leading `-` from leading emoji removal).
- **gap→iam 무관** — pre-existing anchor typo (link omitted the heading's Korean qualifier). Completes the dead-ref dimension started by MONO-181/184/186.

# Goal

After this task, the repo-wide anchor-existence sweep returns **0 broken** (82/82 `#anchor` links resolve to an existing heading slug). No prose/requirement/contract change.

# Scope

## In Scope (2 link fixes, 1 file)

`projects/iam-platform/specs/services/account-service/retention.md`:
1. L119 — `[data-model.md](./data-model.md#anonymization)` → `(./data-model.md#anonymization-삭제-유예--pii-제거)`.
2. L161 — same fix.

Target heading (unchanged): `## Anonymization (삭제 유예 + PII 제거)` in `data-model.md` → GitHub slug `anonymization-삭제-유예--pii-제거` (the `(`/`)`/`+` removed, ` + ` → `--`, Korean kept).

## Out of Scope

- Renaming the `data-model.md` heading (the heading is correct/informative; the **link** was the error — link-only fix per dead-ref discipline).
- The 21 false-positive candidates — GitHub-valid (verified); not touched.
- Any other GAP residue / project-internal prose (MONO-180/186 boundary).

# Acceptance Criteria

- AC-1: Repo-wide anchor-existence sweep (all `git ls-files '*.md'`, GitHub-slugger) returns **0 broken** (was 2).
- AC-2: `git diff` = `retention.md` only, exactly 2 lines, anchor-token change only (`#anonymization` → `#anonymization-삭제-유예--pii-제거`).
- AC-3: The 2 links resolve to the existing `## Anonymization (삭제 유예 + PII 제거)` heading.

# Related Specs

- `projects/iam-platform/specs/services/account-service/{retention.md, data-model.md}` — link target corrected; both bodies unchanged.

# Related Contracts

- None.

# Edge Cases

- **Korean-qualified heading slug** — GitHub keeps Korean letters; ` + ` (with surrounding spaces) → `--` (the `+` removed leaves two spaces → two hyphens). The anchor must include the full qualifier, not just the English word.
- **Slugifier correctness** — the checker's GitHub-slugger was validated by 80/82 links resolving (only these 2 broken); the naive `\s+`-collapse/trim variant produced 21 false positives (now excluded).

# Failure Scenarios

- **Mistyping the Korean slug** → still broken. The slug was computed programmatically from the actual heading (not hand-typed) and verified by the repo-wide sweep returning 0.
- **Changing the heading instead** → out of scope (link-only).
