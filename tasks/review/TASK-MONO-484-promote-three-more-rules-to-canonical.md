# TASK-MONO-484 — Promote three more durable rules from agent memory into canonical platform docs

**Status:** review

**Type:** TASK-MONO
**Analysis model:** Sonnet 5 / **Recommended impl model:** Sonnet 5 (three doc insertions into existing platform files; no code)

> Surfaced by the 2026-07-29 `/audit-memory` sweep (same pattern as `TASK-MONO-464`). Three more rules currently
> live **only** in the agent's private memory but are repo-wide and host-agnostic — they would apply identically
> to any developer or AI session working in this repo. Per `rules/README.md` § Index File Rule, a rule that
> lives in only one place (here: nowhere a human or a fresh session reads) is effectively a rule that does not
> exist for anyone but this one agent. This task moves them to where they are authoritative.
>
> **Root-level task** because every destination is a shared path (`platform/`, `CLAUDE.md`), which forces root
> per `CLAUDE.md` § Task Rules.

---

## Goal

Three rules become part of the canonical, human-and-agent-visible instruction set in `platform/git-workflow-policy.md` (each with a one-line catalog pointer added to `CLAUDE.md`'s existing "Git / branch / worktree discipline" bullet list):

1. **A task series that repeatedly edits the same shared file** (nav config, barrel `index.ts`, shared `types.ts`) **should reuse a single worktree and merge serially**, not run in parallel worktrees, to avoid a merge conflict on that shared file.
2. **Self-merging a PR the agent itself authored, and `git push --force-with-lease`, each require explicit user authorization** from the auto-mode classifier — independent of, and in addition to, the existing `.claude/` self-modification blocking documented in this file.
3. **After merging a PR that changes a console/web-store route, nav entry, testid, or screen heading, check the next nightly e2e run on `main` once** — the two full-stack e2e suites that would catch a regression run only in `nightly-e2e.yml`, not in `ci.yml` (an intentional cost trade-off, not a bug), so a PR can merge green having never exercised the screens it changed.

After this task, none of the three depends on the agent's memory to be enforced.

---

## Scope

### In Scope

- Insert rule (1) into `platform/git-workflow-policy.md` § Concurrent-Session Worktree Isolation, as a new
  subsection. Must state: reuse one worktree across the series (implement → verify → merge task N, then in the
  *same* worktree branch task N+1 off the just-merged `origin/main` so N's shared-file edit is already in the
  base) rather than parallelizing across separate worktrees, which produces a same-file merge conflict at the
  end of the series. Note this does not relax the concurrent-session isolation rule itself — it only describes
  how one session should sequence a single series.
- Insert rule (2) into `platform/git-workflow-policy.md` as a new top-level section adjacent to
  § `.claude/` Self-Modification. Must state both actions separately: (a) self-merging the agent's own
  just-authored PR needs an explicit, specific authorization (a general "proceed to completion" instruction is
  read as generic autonomy, not as naming merge-without-review); (b) `--force-with-lease` needs an explicit,
  specific authorization, with the workaround of pushing to a new ref (`git push origin HEAD:<branch>-v2`)
  instead of force-pushing when the user hasn't named a force push.
- Insert rule (3) into `platform/git-workflow-policy.md` as a new top-level section. Must state: name the two
  nightly-only suites, name the reason (`TASK-MONO-045` moved them for cost, not a bug — this gap is a
  trade-off shadow, not a defect, and will persist), and give the two mitigations (grep the e2e spec dirs for
  affected testids/URLs before merging a route/nav/testid change; fire `gh workflow run nightly-e2e.yml --ref
  <branch>` directly when unsure) plus the post-merge habit (check the next nightly run once).
- Add one short catalog bullet per rule to `CLAUDE.md`'s existing "Git / branch / worktree discipline" catalog
  list, matching the existing one-liner style of that list (safety-net summary + pointer to the new
  `platform/git-workflow-policy.md` section — no procedure detail in `CLAUDE.md` itself).

### Out of Scope

- Any code, test, or CI change. This is documentation only.
- Rewriting or restructuring existing sections beyond the inserted rule + its cross-reference.
- The other audit findings from the same 2026-07-29 sweep that are stale-content fixes, dangling-reference
  fixes, or memory-file consolidation — those are memory-maintenance actions in `~/.claude/.../memory/`, not
  repo changes, and are handled outside this task (same split `TASK-MONO-464` used).
- Trimming the three source memory files to canonical pointers — memory-maintenance, not part of this task.

---

## Acceptance Criteria

- **AC-0 (gate — re-measure each destination; the doc wins)** — Before inserting anything, read the current
  text of `platform/git-workflow-policy.md` § Concurrent-Session Worktree Isolation and § `.claude/`
  Self-Modification, and `CLAUDE.md`'s worktree-discipline catalog list, and confirm none of the three rules is
  already stated there. If any turns out to already be present, drop that one and say so rather than
  duplicating it.
- **AC-1** — Rule (1) is present under § Concurrent-Session Worktree Isolation, describing the reuse-one-worktree
  + serialize-the-series mechanic and why parallelizing produces the shared-file conflict.
- **AC-2** — Rule (2) is present as its own section, stating both the self-merge and force-push authorization
  requirements and the force-push workaround (new ref instead of force).
- **AC-3** — Rule (3) is present as its own section, naming both nightly-only suites, the `TASK-MONO-045` cost
  trade-off, the pre-merge grep/direct-nightly-run mitigations, and the post-merge check-once habit.
- **AC-4 (shared-file agnosticism)** — Every insertion stays project-agnostic per `CLAUDE.md` § Shared vs
  project boundary and HARDSTOP-03: no service/project name is load-bearing to the rule itself (a worked-incident
  citation like "as in `TASK-PC-FE-240`" is fine).
- **AC-5** — `CLAUDE.md`'s three new catalog bullets are added inside the existing "Git / branch / worktree
  discipline" list in the same one-liner style as the surrounding bullets, each ending with a pointer into the
  corresponding new `platform/git-workflow-policy.md` section.
- **AC-6** — No broken anchors/links introduced by the new cross-references.

---

## Related Specs

- `platform/git-workflow-policy.md` — destination for all three rules
- `CLAUDE.md` § Cross-Project Changes → "Git / branch / worktree discipline" catalog list — destination for the
  three one-line pointers
- `rules/README.md` § Index File Rule — the promotion rationale
- `CLAUDE.md` § Source of Truth Priority (memory is absent) and § Shared vs project boundary (HARDSTOP-03)
- Prior art: `TASK-MONO-464` (same pattern, same sweep family, three prior rules)

## Related Contracts

- None. No API or event contract is touched.

## Edge Cases

- **`platform/` and `CLAUDE.md` are not classifier-blocked** (unlike `.claude/hooks/` and `.claude/settings.json`),
  so these edits + commit + push should proceed normally; do not pre-emptively hand off.
- Rule (2) sits next to the existing `.claude/` Self-Modification section, which is about a *different* classifier
  trigger (self-modifying safety/config files) — word it so a reader does not conflate the two triggers.
- Rule (3) is a process/testing rule expressed as a git-workflow habit (post-merge check); it belongs here rather
  than `platform/testing-strategy.md` because the actionable moment is "after you merge," not "when you write a
  test."

## Failure Scenarios

- **F1 — inserting a rule that is already present**, producing a duplicate that then drifts from the original.
  Guarded by AC-0.
- **F2 — stating any rule with a project name as a load-bearing part of the rule**, tripping HARDSTOP-03 on
  shared-file project-specificity. Guarded by AC-4.
- **F3 — conflating rule (2) with the existing `.claude/` self-modification section**, making a reader think
  they're the same classifier trigger. Guarded by the Edge Cases note above.
- **F4 — a broken cross-reference anchor** from the new section links. Guarded by AC-6.
