# Task ID

TASK-MONO-105

# Title

Platform doc consistency bundle — service-type broken skill links (P13) + api-gateway error-code duplication (P14) + jwt-standard-claims entrypoint index (P23)

# Status

done

# Task Tags

- api
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

# Goal

Close three GENUINE shared-`platform/` documentation-consistency findings
surfaced by a 2026-05-15 portfolio-wide spec-drift audit and confirmed by
reconciliation against the current tree (stale audit items were dropped — only
verified-remaining drift is ticketed here).

Monorepo-level because every target path is under shared `platform/` /
`rules/` (CLAUDE.md "When to Use Root vs Project Tasks" → Root `tasks/`). No
`projects/<name>/` impact (verified — none of the three findings reference
project content).

After this task: (WI-1) `platform/service-types/frontend-app.md` skill links
resolve; (WI-2) the api-gateway error-code set has a single canonical home
with the other file pointing to it instead of duplicating; (WI-3)
`platform/contracts/jwt-standard-claims.md` is reachable from
`platform/entrypoint.md` and uses a heading style consistent with sibling
platform contract docs.

---

# Scope

## In Scope

**WI-1 — P13 (mechanical, 2 links).** `platform/service-types/frontend-app.md:21`
references `frontend/architecture/feature-sliced-design.md` and
`frontend/architecture/layered-by-feature.md`. Neither exists under
`.claude/skills/frontend/architecture/`; the skill-dir convention is
`<category>/<skill>/SKILL.md` (e.g. `.claude/skills/service-types/frontend-app-setup/SKILL.md`,
`.claude/skills/cross-cutting/api-versioning/SKILL.md` — both verified to use
the `/SKILL.md` form). Locate the real target skill path(s) for the
feature-sliced-design / layered-by-feature guidance and repoint both links to
the correct `<category>/<skill>/SKILL.md`. If no such skill file exists, repoint
to the nearest correct existing skill and note the gap in the task PR
description (do **not** invent a skill stub — on-demand policy, `rules/README.md`).

**WI-2 — P14 (dedup, 6 codes).** `platform/api-gateway-policy.md:132-137`
defines a 6-code gateway error table (`UNAUTHORIZED` 401, `FORBIDDEN` 403,
`RATE_LIMIT_EXCEEDED` 429, `SERVICE_UNAVAILABLE` 503, `CIRCUIT_OPEN` 503,
`DOWNSTREAM_ERROR` 502). All 6 are also defined in `platform/error-handling.md`
(lines ~73/84/98/129/130/131). No dedup pointer exists between the two files.
Make `platform/error-handling.md` the single canonical catalog (it already is
the canonical error catalog per prior TASK-MONO-051/052 waves) and replace the
duplicated table body in `api-gateway-policy.md` with a reference/pointer to the
canonical codes (keep only gateway-specific *behavioral* notes that are not
error-code definitions). Exact dedup shape is a small editorial decision —
record the chosen convention in the PR description.

**WI-3 — P23 (low / cosmetic).** `platform/contracts/jwt-standard-claims.md` is
linked from `platform/README.md:35` and `rules/common.md:61` (so the audit's
"double-orphan" claim is **false** and is not in scope), but it is **absent
from `platform/entrypoint.md`** (the canonical spec-reading entry agents
follow). Add an index entry for it in `platform/entrypoint.md` at the
appropriate step, and normalize its heading style to match sibling
`platform/contracts/*.md` docs if a divergence is confirmed on read.

## Out of Scope

- Any `projects/<name>/` change (none of the three findings touch project paths).
- Renaming or restructuring `.claude/skills/` (WI-1 only repoints links to the
  *existing* convention; it does not move skill files).
- Changing any error-code value, HTTP status, or gateway runtime behavior
  (WI-2 is doc dedup only — the codes themselves are unchanged).
- The 9 STALE audit findings already closed by prior cycles (BE-144/151/156,
  SCM-BE-010, ADR-001, ADR-MONO-012 D3) — explicitly not re-litigated here.
- P12 (service-type catalog drift) — reconciled STALE: `platform/service-types/INDEX.md`
  and `.claude/config/domains.md` both already list all 8 types incl.
  `identity-platform`; `platform/glossary.md` intentionally points to INDEX
  rather than enumerating. No drift.

---

# Acceptance Criteria

- [ ] WI-1: `platform/service-types/frontend-app.md` contains 0 links of the
      bare `<category>/<skill>.md` form; both former links resolve to an
      existing `.claude/skills/.../SKILL.md` path (verified by following the
      relative path from the file).
- [ ] WI-2: the 6 gateway error codes are defined in exactly one file
      (`platform/error-handling.md`); `platform/api-gateway-policy.md` references
      them via a pointer and no longer re-declares the code/status table.
      `grep` for each of the 6 code names shows the definition site is unique.
- [ ] WI-3: `platform/entrypoint.md` contains a reference to
      `contracts/jwt-standard-claims.md`; heading style in
      `jwt-standard-claims.md` is consistent with sibling `platform/contracts/`
      docs (or a no-change note recorded if already consistent).
- [ ] `./gradlew check` is not required (docs-only); verification = markdown
      link resolution + grep uniqueness for WI-1/WI-2, manual read for WI-3.
- [ ] No shared-library-policy (HARDSTOP-03) regression introduced — no
      project-specific names added to any shared file.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> this is a monorepo-level task touching shared `platform/`; read
> `rules/common.md` and `platform/shared-library-policy.md`. No `PROJECT.md`
> applies (repo-root scope).

- `platform/service-types/frontend-app.md` — WI-1 edit target (L21).
- `platform/service-types/INDEX.md` — service-type catalog (P12 STALE evidence;
  read for the `/SKILL.md` link convention reference).
- `platform/api-gateway-policy.md` — WI-2 edit target (L132-137).
- `platform/error-handling.md` — WI-2 canonical error catalog (keep as SoT).
- `platform/contracts/jwt-standard-claims.md` — WI-3 edit target (heading style).
- `platform/entrypoint.md` — WI-3 edit target (add index entry).
- `platform/shared-library-policy.md` — HARDSTOP-03 guard for all WIs.

# Related Skills

- `.claude/skills/refactor-spec/SKILL.md` — primary (spec/doc consistency, no
  meaning change).
- `.claude/skills/validate-rules/SKILL.md` — re-run after WI-1/WI-3 to confirm
  no new broken link / orphan.

---

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` (WI-3) — heading/index only, no
  claim-shape change.
- No HTTP/event contract under any `projects/<name>/specs/contracts/` is
  touched.

---

# Target Service

- N/A (shared `platform/` documentation — monorepo-level)

---

# Architecture

No service architecture changes. `platform/error-handling.md` remains the
canonical error catalog (TASK-MONO-051/052 precedent); WI-2 only removes the
duplicate from `api-gateway-policy.md`.

---

# Implementation Notes

1. WI-1/WI-2 are mechanical; WI-3 is cosmetic. May ship as one spec PR
   (`feedback_pr_bundling` — bundling allowed; all three are shared-doc
   consistency with no behavioral effect).
2. WI-1: prefer repointing to an existing skill over creating one
   (`rules/README.md` on-demand policy — missing skill = "no additional
   constraint", do not auto-stub).
3. WI-2: the dedup direction is fixed (error-handling.md = SoT); only the
   pointer wording is editorial.
4. This is the "(writing) → ready" stage — the spec PR adds this file to
   `ready/` + the INDEX ready list only. Implementation lands in a separate
   impl PR per the root INDEX PR Separation Rule.

---

# Edge Cases

- WI-1: if BOTH feature-sliced-design and layered-by-feature map to a single
  combined skill file, collapse to one link rather than duplicating.
- WI-2: a code present in `api-gateway-policy.md` but with a *gateway-specific
  nuance* (e.g. `CIRCUIT_OPEN` retry semantics) — keep the prose nuance, remove
  only the duplicated code/status definition.
- WI-3: if `jwt-standard-claims.md` heading style is already consistent on
  read, record "no change needed" rather than forcing edits.

# Failure Scenarios

- WI-1 repointed to a non-existent `/SKILL.md` → still broken; must verify the
  target resolves before marking done.
- WI-2 removes the table but adds no pointer → gateway error semantics become
  undiscoverable from `api-gateway-policy.md`; the pointer is mandatory.
- WI-3 adds an `entrypoint.md` entry at the wrong step → mis-sequences the
  spec-reading order; place it where other `platform/contracts/` refs sit.

---

# Test Requirements

- Docs-only; no unit/integration test. Verification:
  - WI-1/WI-2: relative-link resolution + `grep` definition-uniqueness.
  - WI-3: manual read + `platform/entrypoint.md` reference present.
  - Re-run `.claude/skills/validate-rules` to confirm 0 new broken-link/orphan.

---

# Definition of Done

- [ ] WI-1 links resolve; WI-2 codes single-sourced + pointer added; WI-3
      entrypoint index entry added + heading style reconciled (or no-change
      recorded)
- [ ] `validate-rules` shows no new broken-link/orphan regression
- [ ] No HARDSTOP-03 regression (no project-specific content in shared files)
- [ ] Branch: `task/mono-105-platform-doc-consistency` (substring `master` 금지)
- [ ] Spec PR adds this file to `ready/` + INDEX ready list only (no impl)
- [ ] Ready for review
