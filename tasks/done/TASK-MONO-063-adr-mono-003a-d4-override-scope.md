# Task ID

TASK-MONO-063

# Title

Publish ADR-MONO-003a — canonicalize D4 OVERRIDE scope across cleanup-class series

# Status

done

# Owner

monorepo

# Task Tags

- spec
- adr
- policy

---

# Goal

Canonicalize the **D4 OVERRIDE scope** that has accumulated across three documents (ADR-MONO-003 § 2026-05-11 update, ADR-MONO-006 § 3.4 / § 4.2 / § 7, memory `project_monorepo_template_strategy.md` L28) into a single policy ADR (`ADR-MONO-003a`).

The original D4 OVERRIDE (2026-05-11, ADR-MONO-003 § 2026-05-11 update) was scoped to "B common-rule cleanup 한정". One day later (2026-05-12) the scope silently extended to "B common-rule refactor ∪ OpenAI Harness gap series" via ADR-MONO-006 § 4.2 — a multi-doc consensus rather than a recorded decision. Memory was updated to match. **The scope expansion is correct but the recording is fragile** — future scope creep (e.g. "this new cleanup is also fine, right?") has no single authority to point to, and a reader of just ADR-MONO-003 would not know the OpenAI Harness gap series falls under the override.

This ADR makes the canonicalization explicit:

1. Single canonical scope definition (this ADR is the authority; ADR-MONO-003 § 2026-05-11 / ADR-MONO-006 § 4.2 / memory all become historical references).
2. Explicit IN-scope / OUT-of-scope criteria so future "is this allowed under OVERRIDE?" questions have a checkable answer.
3. Audit trail of every PR landed under OVERRIDE to date (provenance).
4. Re-defined Phase 5 trigger now that the original "30-day churn-quiet window" gate is effectively bypassed.

---

# Scope

## In Scope

### A. New ADR file

`docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` — Status: ACCEPTED 2026-05-12 (meta-policy ADR; no implementation gate).

Required sections:

- **Context** — why canonicalization is needed (multi-doc fragmentation, scope-creep risk, audit-trail need).
- **Decision** — IN-scope categories (with exhaustive PR list to date), OUT-of-scope categories (with explicit examples), and the meta-rule for adding future categories.
- **Audit trail** — table of every PR landed under D4 OVERRIDE from 2026-05-11 onwards.
- **Phase 5 trigger** — new gate definition replacing the "30-day churn-quiet window" that this OVERRIDE effectively bypasses. Records that Phase 5 launch now requires user-explicit decision + separate ADR-MONO-003b (the launch ADR).
- **Consequences** — what changes for future authors / reviewers / future-self reading old refs.
- **Relationship to ADR-MONO-003** — does not supersede (D1 still holds), does canonicalize § 2026-05-11 update's scope definition (the scope block becomes a historical pointer to this ADR).

### B. Cross-reference updates

Single source of truth pattern — every external mention of "D4 OVERRIDE scope" should resolve to this ADR after canonicalization:

- `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` § Related → add `ADR-MONO-003a` link as the canonical scope source.
- Memory `project_monorepo_template_strategy.md` L28 → update D4 OVERRIDE bullet to point to ADR-MONO-003a as canonical (the inline scope text stays as a one-line summary; the authority is the ADR).

ADR-MONO-003 § 2026-05-11 update **stays as-is** — historical record of the original decision. Reader reaches ADR-MONO-003a via this task's PR link or the new ADR-MONO-006 § Related entry.

## Out of Scope

- Modifying ADR-MONO-003 body text (immutable historical record).
- Re-evaluating Phase 5 launch readiness (separate ADR-MONO-003b when triggered).
- Filing gap #3 (worktree ephemeral observability) — the canonicalization records gap #3 as IN-scope but its actual filing is a separate task whose timing depends on e2e scenario accumulation.
- Backfilling OVERRIDE-applies notes into past PRs (immutable).
- Defining "what counts as B common-rule refactor" or "what counts as Harness gap" beyond the audit trail — the audit trail is enumerative, the meta-rule covers future cases.

---

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` exists with Status: ACCEPTED, dated 2026-05-12.
- [ ] ADR-003a contains: Context / Decision (with IN/OUT criteria) / Audit trail (≥9 PRs from 2026-05-11 onwards) / Phase 5 trigger redefinition / Consequences / Relationship to ADR-MONO-003.
- [ ] IN-scope criteria explicitly enumerate: (i) B common-rule refactor (closed 5/5), (ii) OpenAI Harness gap series (A closed, #2 closed, #3 OPEN), (iii) the ADR-MONO-003a publication itself.
- [ ] OUT-of-scope criteria explicitly enumerate: (i) new domain bootstrap, (ii) arbitrary cross-project breaking changes, (iii) library major-version bumps with breaking changes, (iv) Phase 5 actual launch.
- [ ] Audit trail table lists PR # / date / commit SHA / category (B / gap-A / gap-#2) / one-line description for each OVERRIDE-applies PR landed to date.
- [ ] Phase 5 trigger section replaces the "verify-template-readiness exit 0" auto-trigger with "user-explicit decision + ADR-MONO-003b", consistent with ADR-MONO-003 § 2026-05-11 update D2/D3 weakening.
- [ ] `ADR-MONO-006` § Related links to ADR-MONO-003a.
- [ ] Memory `project_monorepo_template_strategy.md` L28 D4 OVERRIDE bullet ends with "canonical source = ADR-MONO-003a" (and any ad-hoc inline scope text is removed or marked summary-only).
- [ ] No service code touched. No `libs/` / `apps/` / `projects/` diff.
- [ ] CI green (path-filter `rules` + path-filter `docs`(adr) flags only — no service integration / E2E / boot-jars jobs triggered).

# Related Specs

- `docs/adr/ADR-MONO-003-phase-5-template-extraction-deferred.md` § 2026-05-11 update (original scope statement, becomes historical after this ADR)
- `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` § 3.4 + § 4.2 + § 7 (provenance of scope extension)
- Memory `project_monorepo_template_strategy.md` L28 (D4 OVERRIDE inline scope)
- Memory `reference_openai_harness_engineering.md` (gap series source)

# Related Contracts

None — meta-policy ADR. No HTTP / event contract change.

# Edge Cases

- **Future "is this cleanup?" question** — if a future PR proposes a cleanup-class change that doesn't fit B-refactor or Harness-gap categories, the meta-rule in ADR-MONO-003a § Decision applies: requires user-explicit acknowledgement before any commit. The ADR enumerates "what counts" as snapshot, but explicitly does NOT pre-approve unbounded "cleanup" — every new category needs an explicit nod in writing.
- **Reader of ADR-MONO-003 alone** — ADR-MONO-003 § 2026-05-11 says "B common rule cleanup 한정 의도". Without this canonicalization, that reader is misinformed about current scope. After this ADR, ADR-MONO-003 should ideally carry a one-line forward-pointer to ADR-MONO-003a — but ADR immutability convention says append-only. The "Related" line at the top of ADR-MONO-003 is also already populated. **Resolution**: a single forward-pointer line is added at the very end of ADR-MONO-003 (after § 2026-05-11 update) titled "### Forward pointer (2026-05-12)" pointing to ADR-MONO-003a. This is append-only, consistent with the running-addendum convention ADR-MONO-003 already uses.
- **Phase 5 launch via ADR-MONO-003b** — when triggered, ADR-MONO-003a's "Phase 5 trigger" section becomes the authority on what user-explicit decision satisfies the trigger. ADR-MONO-003b authoring will reference ADR-MONO-003a § Phase 5 trigger.
- **Gap #3 filing** — when filed, the gap-#3 task spec references ADR-MONO-003a § Audit trail "OPEN" status row. No re-canonicalization needed (the audit trail is updated by appending, not rewriting).

# Failure Scenarios

- **Reviewer asks "why is this not an ADR-MONO-003 update?"** — answer: the running-addendum pattern works for clarifying decisions; canonicalization of scope across multiple sources is a new decision (the *choice* to consolidate authority is itself a recorded judgement). Inline updates would not surface the scope-creep prevention rationale.
- **Reviewer asks "is gap #3 in scope or not?"** — the ADR records gap #3 as IN-scope (consistent with ADR-MONO-006 § 7 enumerating the gap series). The audit trail row for gap #3 carries status `OPEN — not yet filed`.
- **Reviewer asks "what about gap-A semantic 5 (HARDSTOP-02/04/06/07/08)?"** — already marked Phase 3b deferred in ADR-MONO-006 § 6. If filed, falls under "OpenAI Harness gap series" IN-scope automatically. No new authorisation needed.

---

# Implementation Plan

1. Author `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` per the structure above.
2. Append forward-pointer line to end of `docs/adr/ADR-MONO-003-phase-5-template-extraction-deferred.md` (the only mutation to ADR-MONO-003; consistent with its existing running-addendum pattern).
3. Update `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` § Related to include `ADR-MONO-003a` link.
4. Update memory `project_monorepo_template_strategy.md` L28 → point to canonical ADR-MONO-003a.
5. Single bundled commit (spec + impl + chore — feedback_pr_bundling precedent).
6. Push to branch; await user decision on PR open per feedback_pr_on_request.
7. After merge: lifecycle move ready → review → done (per close chore protocol).

# Estimated Cost

- Files: ADR-003a new (~150 LOC) + ADR-003 forward-pointer (~3 LOC) + ADR-006 Related update (~1 LOC) + memory L28 (~3 LOC) + this task file. Total ≈ 200 LOC additions.
- CI: path-filter `rules` flag matches → `Build & Test` + `changes` only. ~20s baseline.
- Time: ~1 hour authoring + commit/push.

분석=Opus 4.7 / 구현=Opus (meta-policy authoring, requires judgement on scope criteria phrasing).
