# Task ID

TASK-MONO-057

# Title

`CLAUDE.md` split — catalog + safety-net pattern (B common-rule refactor candidate #1 closure)

# Status

done

# Owner

monorepo

# Task Tags

- spec
- rules
- refactor

---

# Goal

Reduce `CLAUDE.md` from 312 lines → 209 lines (-33%) by applying the **catalog + safety-net** pattern from OpenAI Harness Engineering's "AGENTS.md 100라인 목차" guidance (memory `reference_openai_harness_engineering.md`). The shipping content is reorganized so `CLAUDE.md` carries only:

1. The minimum safety surface every AI session must see (Hard Stop Rules, Source of Truth Priority, Repository Layout map).
2. Pointers to canonical detail files (rules/README.md / TEMPLATE.md / docs/guides/monorepo-workflow.md / tasks/INDEX.md).
3. Behavior rules that **cannot** live elsewhere (Recommending Tasks model annotation, Identify the Target Project entry).

This is **B (Common Rule Refactoring) wave 1 candidate** (highest cost / highest impact per memory `project_b_common_rule_refactor_pending.md`). Candidates #2 (error-handling catalog Wave 2/3) and #3 (rules/ 4-way sync audit) already closed (TASK-MONO-051 / -052 / -056).

---

# Scope

## In Scope

### A. Trim CLAUDE.md sections in place (no destination migration needed)

These sections already had short essential content — trim to the same shape without losing meaning:

| Section | Before | After | Change |
|---|---|---|---|
| Header | 4 | 3 | "catalog + safety net" framing line added |
| Repository Layout | 38 | 27 | Visual tree retained (essential first-session map); shared/project boundary list reformatted; verbose paragraph trimmed |
| Identify the Target Project | 18 | 13 | Combined sub-bullets; same 3 rules + path conventions |
| Project Classification | 16 | 9 | Numbered list compressed; rules/README.md / .claude/config/ links added |
| Core Principles | 8 | 7 | Same 5 principles, less filler |
| Source of Truth Priority | 28 | 23 | 14-priority list preserved verbatim; conflict resolution paragraph compressed |
| Task Rules | 21 | 11 | Required-sections list compressed to a single line; lifecycle redirect explicit |
| Required Workflow | 29 | 18 | 12-step numbered list compressed; monorepo-level steps inlined |
| Hard Stop Rules | 20 | 16 | Same 10 conditions, verbose phrasing trimmed |
| Architecture / Shared Library / Contract / Testing | 4 × 6 = 24 | 1 unified "Layer Rules" section, 7 lines | One-line per rule + canonical link |
| Cross-Project Changes | 18 | 14 | Commit scope list retained; verbose monorepo-advantage paragraph trimmed |
| Local Network Convention | 39 | 6 | **Body migrated to TEMPLATE.md (already master per L438)**; 1-paragraph summary + master link only |
| Recommending Tasks | 18 | 18 | Unchanged — agent-behavior rule that must persist across sessions |

### B. TEMPLATE.md cross-reference fix

`TEMPLATE.md` L255 stated `CLAUDE.md § Local Network Convention (authoritative)` but L438 declared TEMPLATE.md is master. Fix L255 to match L438 (TEMPLATE.md master, CLAUDE.md summary).

### C. No content lost

Every rule statement from the pre-split CLAUDE.md is preserved either:
- In CLAUDE.md (compressed phrasing).
- In a canonical file already referenced (no new files created): `rules/README.md` (Project Classification detail), `TEMPLATE.md` (Local Network Convention master), `docs/guides/monorepo-workflow.md` (full Conventional Commit convention), `tasks/INDEX.md` (lifecycle / decision table).

No external destination file was edited beyond TEMPLATE.md L255 — the receiving content was already in place.

## Out of Scope

- **Section A.* canonical file edits** — beyond TEMPLATE.md L255 stale fix. The destination files already cover the migrated content; no new prose needed.
- **Cross-ref stale-reference cleanup** — `docs/adr/ADR-MONO-001-port-prefix-scaling.md` L269 references `CLAUDE.md § Port Namespace Convention` (a section name that no longer exists after TASK-MONO-024 retired PORT_PREFIX). Historical link, leave as-is per ADR immutability.
- **Further compression to ~100 lines** — risk of losing first-session discoverability for new agents. Conservative cut to ~210 lines is the first-pass deliverable. Future iteration may shrink further if discoverability proves robust.

---

# Acceptance Criteria

- [ ] `CLAUDE.md` line count ≤ 220 (target 209 actual)
- [ ] All 13 section headers present (Repository Layout / Identify Target Project / Project Classification / Core Principles / Source of Truth Priority / Task Rules / Required Workflow / Hard Stop Rules / Layer Rules / Cross-Project Changes / Local Network Convention / Recommending Tasks)
- [ ] Source of Truth Priority 14-item list preserved verbatim
- [ ] Hard Stop Rules 10 conditions preserved verbatim
- [ ] Recommending Tasks model-annotation rule preserved verbatim (this is a behavior rule that must replay correctly in every new session)
- [ ] TEMPLATE.md L255 fixed: master = TEMPLATE.md, summary = CLAUDE.md
- [ ] No new files created
- [ ] No external destination file edited beyond TEMPLATE.md L255

---

# Related Specs

- `CLAUDE.md` (target — overwritten)
- `TEMPLATE.md` § Local Network Convention (already master, no body change; L255 cross-ref fix)
- `rules/README.md` (Project Classification detail — already covers)
- `docs/guides/monorepo-workflow.md` (Cross-Project Changes detail — already covers)
- `tasks/INDEX.md` (Task Rules detail — already covers)
- Memory `project_b_common_rule_refactor_pending.md` candidate #1 (provenance)
- Memory `reference_openai_harness_engineering.md` (pattern source — "AGENTS.md 100라인 목차")

# Related Skills

- N/A — meta-policy edit, no skill applies.

---

# Related Contracts

None — internal monorepo operating rules only.

---

# Target Service

N/A — shared paths only:
- `CLAUDE.md`
- `TEMPLATE.md` (1-line cross-ref fix)

---

# Architecture

N/A — documentation reorganization. No service architecture impact.

---

# Implementation Notes

The split preserves the **shape every AI session expects** while compressing verbose prose:

- **First-session essentials remain in CLAUDE.md**: Repository Layout visual map, Identify the Target Project entry, Hard Stop conditions, Source of Truth Priority list, Recommending Tasks model rule.
- **Detailed content with canonical home stays canonical**: Local Network Convention (TEMPLATE.md), 14 common rule files (rules/common.md + platform/), lifecycle (tasks/INDEX.md), Conventional Commit convention (docs/guides/monorepo-workflow.md).
- **Layer Rules consolidated**: 4 separate sections (Architecture / Shared Library / Contract / Testing) → 1 unified "Layer Rules" section with 4 one-liners + canonical links. Eliminates 17 lines without losing any rule.
- **The Required Workflow 12-step list is preserved** because it's the canonical execution order for AI sessions. Compressed sub-bullets, kept structure.

The previously-existing TEMPLATE.md L438 declared the master split:

> Source of truth: `TEMPLATE.md § Local Network Convention` is the master specification for hostname-based routing. `CLAUDE.md § Local Network Convention` is a concise summary that redirects here.

L255 contradicted this ("CLAUDE.md authoritative"). Fixed.

---

# Edge Cases

- **Re-reading the trimmed CLAUDE.md in a new session**: agents starting fresh see Repository Layout + Hard Stop + Recommending Tasks immediately. Other sections compress without removing essential rule statements. Risk = low, but discoverability of the cross-ref destinations matters — every redirect uses a markdown link, not plain text.
- **Cross-ref staleness**: ADR-MONO-001 L269 references `CLAUDE.md § Port Namespace Convention` which doesn't exist post-TASK-MONO-024 (PORT_PREFIX retired). Historical link, ADR-immutability principle applies — leave as-is.
- **D4 churn-clock impact**: this PR touches `CLAUDE.md` (shared) + `TEMPLATE.md` (shared 1-line). Per existing D4 OVERRIDE precedent (PR #328 / #352 / TASK-MONO-052 catalog series), B common-rule refactor scope applies — last_churn marker resets minimally.

---

# Failure Scenarios

- **Information loss**: each section deletion is matched by either (a) preservation in CLAUDE.md with compressed phrasing or (b) cross-ref to the canonical destination. The 312-line original is recoverable from git history.
- **Cross-ref breakage**: TEMPLATE.md L255 stale "CLAUDE.md authoritative" is fixed in this PR. Other cross-refs verified — `CLAUDE.md § Hard Stop Rules` / `§ Source of Truth Priority` / `§ Required Workflow` / `§ Cross-Project Changes` / `§ Local Network Convention` / `§ Recommending Tasks` / `§ Project Classification` all still resolve.

---

# Test Requirements

N/A — docs-only edit. Verification:

```bash
wc -l CLAUDE.md            # expect ~209
grep -c '^# ' CLAUDE.md    # expect 13 section headers (incl. title)
grep -n '§' CLAUDE.md      # confirm cross-refs survive
```

External grep — verify no broken section references:

```bash
grep -rn 'CLAUDE.md §' docs/ TEMPLATE.md .claude/ rules/ projects/ scripts/ tasks/ \
  | grep -v 'Port Namespace Convention'  # historical ADR L269, expected
```

All hits should reference live sections in the new CLAUDE.md (Hard Stop Rules / Source of Truth Priority / Required Workflow / Cross-Project Changes / Local Network Convention / Recommending Tasks / Project Classification).

---

# Definition of Done

- [ ] `CLAUDE.md` rewritten per the section table above
- [ ] `TEMPLATE.md` L255 cross-ref fix
- [ ] Section header count verified (13)
- [ ] No new files created
- [ ] Memory `project_b_common_rule_refactor_pending.md` candidate #1 marked closed
- [ ] PR description summarizes the split + line-count reduction + 0 content loss claim

---

# Provenance

Memory `project_b_common_rule_refactor_pending.md` candidate #1 — pending since 2026-05-11 with rationale memory `reference_openai_harness_engineering.md` (OpenAI Harness Engineering "AGENTS.md 100라인 목차" pattern).

D4 OVERRIDE applies per [ADR-MONO-003](../../docs/adr/ADR-MONO-003-phase-5-template-extraction-deferred.md) § 3.4 risk 2 (B common-rule refactor scope, user-acknowledged).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (catalog + safety-net rebalancing requires judgment on which rules stay in the always-loaded first-session surface vs which redirect — not a mechanical edit).
