# TASK-MONO-441 — audit which agent-memory rules are genuinely absent from canon (promote only those)

- **Type**: TASK-MONO (canon hygiene — audit-first, mostly-negative expected)
- **Status**: done
- **Scope**: `platform/testing-strategy.md`, `platform/git-workflow-policy.md`, `CLAUDE.md`, `rules/`
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (judgement per candidate; the work is verification, not writing)

## Goal

The 2026-07-20 `/audit-memory` pass produced 14 "promote to canon" candidates — rules that
apply to **any** developer or AI session, not personal preferences, and that a subagent
reported as missing from the repo's canonical docs.

**That list is a hypothesis, and spot-checking already falsified part of it.** This task's
job is to find the small subset that is genuinely absent, not to promote 14 things.

## AC-0 — Re-measure before touching anything (mandatory; the code wins)

**Do not inherit the candidate list or its suggested destinations.** Both were produced by a
subagent and by an orchestrator whose own verification grep was wrong twice during the audit:

1. A `^#\{2,4\}` heading pattern reported `platform/testing-strategy.md` § *CI Guards /
   Drift Detectors — Authoring Rules* as **non-existent**. It exists (level-1 heading) and
   carries **G1–G9**.
2. Reading G5/G6/G7/G9 then showed several candidates are **already canon**:
   - **G5** *"Reachability is not only about CI triggers…"* ≈ `project_guard_reachability_not_just_bite`
   - **G6** *"Ask the question the failure mode asks…"* ≈ `feedback_guard_predicate_wrong_verify_the_artifact`
   - **G7** *"Don't re-enumerate the source of truth. Derive from it."* ≈ adjacent to `feedback_recount_population_dont_inherit_scope`
   - **G9** *"Merging is half. Ask how the fix reaches the place it runs."* ≈ the deployment-layer lesson

⇒ **AC-0 = for each candidate, read the actual canonical section and decide COVERED /
PARTIAL / ABSENT, quoting the canonical text.** Expect most to be COVERED. A mostly-negative
result is the correct outcome, not a failed task (`TASK-MONO-398` precedent: *"zero is a
result"*).

## Candidate list (hypothesis — verify each, do not assume)

| # | memory | rule (one line) | likely destination |
|---|---|---|---|
| 1 | `project_enforcement_straggler_sibling_parity` | Auditing "is X enforced?" means lining up sibling services; N-1-of-N wiring is the defect signature, and infra existing ≠ mechanism consumed | testing-strategy G-rules **or** security-rules |
| 2 | `project_ci_path_filter_074_075_quirk` | A job `if:` combining a push fallback with a paths-filter output must use `!= 'false'`, not `== 'true'` — an empty filter output otherwise lets an unverified change land | git-workflow-policy § CI Path-Filter Constraint |
| 3 | `project_branch_hygiene_policy` | Retargeting a stacked PR's base (`gh pr edit --base`) does not retrigger CI — `edited`/`ready_for_review` aren't default `pull_request` activity types; only close+reopen or a push does | git-workflow-policy § "A PR whose base is not `main`…" |
| 4 | `project_shared_file_task_series_single_worktree_serialize` | A task series repeatedly editing the same shared file uses ONE worktree serially, branching N+1 from `origin/main` only after N merges | git-workflow-policy, near § Concurrent-Session Worktree Isolation |
| 5 | `feedback_deletion_leaves_survivors_grep_the_consumers` | Before calling a decommission leftover "orphaned/harmless", grep its live consumers — immutability of history is not the reason | CLAUDE.md § Task Rules |
| 6 | `feedback_recount_population_dont_inherit_scope` | A count/scope inherited from a prior task, ADR, ticket, or code comment — including an audit report you just wrote — is a hypothesis; recount from source | CLAUDE.md § Recommending Tasks… (**check G7 overlap first**) |
| 7 | `feedback_local_proves_behaviour_not_performance` | A single measured value is not a distribution; state sample size/range before writing it into a durable surface, and put the hedge in the code comment too, not only the PR | testing-strategy (**check G4 overlap first**) |
| 8 | `feedback_repo_knows_what_it_does_not_say` | Before deduping copies of a rule, check whether they diverged — undiverged duplication is propagation; the real defect is usually that no canonical file declares it | `rules/README.md` or CLAUDE.md § Source of Truth Priority |
| 9 | `project_ci_wallclock_playbook` | CI wall-clock method: profile per job → check the slow lane isn't already-paid-for serialisation → shard across runners not within → stop when expected gain < observed variance | testing-strategy, new § (**low priority — brand new, and INDEX + `ci.yml` comments already own the detail**) |
| 10–14 | e2e/bootstrap-ordering candidates | (subagent-reported, unverified — several looked like restatements of existing `## Integration-test bootstrap pitfalls` / `## E2E Smoke vs Full`) | verify before writing |

## Scope / Acceptance Criteria

- **AC-1**: every candidate classified COVERED / PARTIAL / ABSENT with the canonical text
  quoted. COVERED items are closed with that quote — no edit.
- **AC-2**: only ABSENT (or PARTIAL with a concrete gap) items are written, each as a
  minimal addition to the section that already owns the topic. **No new top-level sections**
  unless nothing owns the topic.
- **AC-3 (lossless)**: after writing, diff the promoted text against the memory it came from
  and confirm nothing was dropped. Precedent for why: `proj_console_ui_conventions_canonical_home`
  records a promotion that **silently lost one rule**.
- **AC-4**: promoted memories get their body reduced to a pointer (`규칙 정경=<file> § <section>`)
  plus worked incidents/measurements only — the pattern several memories already follow.
  `MEMORY.md` hook updated to match.
- **AC-5**: personal-preference memories are explicitly **out of scope** and must not be
  promoted: `feedback_language`, `feedback_pr_on_request`, `feedback_pr_bundling`,
  `feedback_proceed_without_confirmation`, `feedback_auto_worktree_per_task`.

## Related

- Provenance: 2026-07-20 `/audit-memory` (also fixed 6 stale claims + 1 dangling path,
  archived 5 completion records, and compacted `MEMORY.md` 28.1KB → 23.6KB).
- `platform/testing-strategy.md` § CI Guards / Drift Detectors — Authoring Rules (G1–G9).
- `platform/git-workflow-policy.md` §§ Concurrent-Session Worktree Isolation / A PR whose
  base is not `main` / CI Path-Filter Constraint.
- Precedent for canon promotion: TASK-MONO-423, TASK-MONO-425, TASK-MONO-437.

## AC-0/AC-1 Result — classification (2026-07-20)

Method: **all four candidate destinations read in full** (`platform/testing-strategy.md` 324L,
`git-workflow-policy.md` 133L, `security-rules.md` 117L, `rules/README.md` 133L, `refactoring-policy.md`
111L, `CLAUDE.md` 228L = ~1,050L). No heading-pattern grep was used to decide presence — that is precisely
what produced the false negative recorded in AC-0.

| # | verdict | canonical text found (or the gap) | action |
|---|---|---|---|
| 1 | **PARTIAL** | `security-rules.md`: *"Enumerate the callers and verify each one; do not infer it from configuration"* + *"Fleet services re-created this exact defect on four separate occasions, each time by **mirroring a sibling**"*. The sibling-audit method is canon **as an instance**. Genuinely absent: *"a test that bypasses the enforcement layer proves nothing"* + *"infrastructure existing ≠ mechanism consumed"* | promoted the **absent half only** → `testing-strategy.md`; sibling-line-up method **not** promoted (duplication risk) |
| 2 | **ABSENT** | § CI Path-Filter Constraint has negation/outputs-AND/backfill but **not** the `!= 'false'` fail-safe, and not "the consuming `if:` can override a correct filter" | promoted → same section |
| 3 | **ABSENT** | § *A PR whose base is not `main`* covers 0-checks and base-ref-deletion auto-close, but **not** that `--base main` retargeting fails to retrigger | promoted → new `###` under that `##` |
| 4 | **NOT PROMOTED** | shared-file task-series serialisation is workflow ergonomics for concurrent agent sessions, not a repo invariant; § Concurrent-Session Worktree Isolation already owns the hazard it prevents | closed, no edit |
| 5 | **ABSENT** | `refactoring-policy.md` rates *Remove Dead Code* **"Low"** risk and § Verification never asks who consumes what a removal leaves behind | promoted → § Rules / Mandatory #6 (also corrects the "Low" framing) |
| 6 | **PARTIAL → no edit** | G7 (*"Don't re-enumerate the source of truth. Derive from it."*) owns the guard case; `CLAUDE.md` § Recommending Tasks owns the stale-local-state case (*"recommending against stale local state duplicates already-closed work"*). Residual generalisation overlaps #5 | closed, no edit |
| 7 | **PARTIAL** | G4 owns *host*-dependence (*"a threshold calibrated on your host is a proposition about your host"*, *printed not asserted*). It says nothing about **sample count** | one paragraph appended to G4 |
| 8 | **ABSENT — and canon actively prescribed the inverse** | `rules/README.md` § Index File Rule says *"중복 발견 시 이 파일에서 삭제"*. Correct for an index; read as a general dedup rule it inverts the prescription | promoted → scoping caveat in that section |
| 9 | **NOT PROMOTED** | as the ticket predicted: `tasks/INDEX.md` + `ci.yml` comments own the detail, and the method is one week old with a single application | closed, no edit |
| 10–14 | **UNVERIFIABLE** | the audit report never enumerated these; the list was not captured before context rolled. Judged from the memories themselves: the e2e/bootstrap topics map onto § E2E Smoke vs Full and § Integration-test bootstrap pitfalls, both of which exist | closed, no edit — **recorded as unverified rather than assumed covered** |

**Score: 4 ABSENT + 2 PARTIAL promoted, 8 closed with no edit.** The mostly-negative outcome AC-0 predicted
held: **9 of 14 candidates needed nothing.**

**Incidental fix (out of the candidate list, found while reading):** `testing-strategy.md` § Integration lane
serialisation ended with a copy-pasted fragment duplicated verbatim from § Integration-test bootstrap
pitfalls (a 2-constructor `@Autowired` explanation appended to an unrelated paragraph about `ci.yml`).
Removed. No rule content lost — the fragment's real copy remains at its own bullet.

## Edge Cases / Failure Scenarios

- **The expected outcome is "mostly already covered."** Do not manufacture additions to
  justify the ticket.
- **A duplicate is worse than a gap here**: `feedback_repo_knows_what_it_does_not_say` warns
  that reflexive dedup inverts the prescription — and the same logic applies to reflexive
  promotion. A rule stated twice in two canonical files is a future divergence.
- Two memories are cited **by name** from canon (`platform/git-workflow-policy.md` §
  `git commit && git push`… cites `project_console_web_ecommerce_ops_bug_class`). Deleting
  or gutting those files orphans the citation — reduce to a pointer, never delete.
