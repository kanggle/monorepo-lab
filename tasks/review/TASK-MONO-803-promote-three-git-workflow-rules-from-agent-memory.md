# TASK-MONO-803 — promote three git-workflow rules from agent memory into the policy docs

- **Status**: review
- **Type**: docs (monorepo-level, doc-only)
- **Created**: 2026-10-21 UTC
- **Scope class**: monorepo-level (`platform/`, `CLAUDE.md`)

# Origin

2026-10-21 `/audit-memory`, owner-approved promotion of three rules that lived only in an agent's private memory, so every other session met them unguarded. Also repairs a dangling pointer: `platform/git-workflow-policy.md` § "A PR whose base is not `main`…" says a conflicting PR "still matches the workflow; it just cannot run — see the distinction below", but no such distinction existed below.

# Goal

Write the three rules down where every session reads them, project-agnostic (HARDSTOP-03):

1. Zero check runs on a PR has two distinct causes (merge conflict vs non-matching base); how to tell them apart; how a CI poller should report it.
2. A PR title is a snapshot from when it was opened; update it before the squash merge because the squash subject is permanent.
3. Before starting a ready task, look for in-flight work on the same ticket (worktrees, branches, open PRs).

# Scope

## In Scope

- `platform/git-workflow-policy.md`: new subsection "Zero checks has two distinct causes", new section "A PR Title Is a Snapshot", new subsection "Before Starting a Ready Task".
- `CLAUDE.md` § Git / branch / worktree discipline: one catalog line for rule 3 pointing at the policy.

## Out of Scope

- Any other rule; any script, guard, workflow, or project file. Nothing added or removed under `scripts/`.
- Impact on `projects/<name>/`: none.

# Acceptance Criteria

- [x] **AC-1** — the "see the distinction below" reference in the policy resolves to a real subsection that states both causes, the one-line diagnosis (`gh pr view <n> --json mergeable,mergeStateStatus,baseRefName`), and the poller reporting rule. It describes what is observable (empty check list and empty runs list) without ruling on whether a conflicting PR "matches" the workflow.
- [x] **AC-2** — the policy states the PR-title rule with both remedies (`gh pr edit --title`, `gh pr merge --subject`).
- [x] **AC-3** — the policy states the in-flight-work check (`git worktree list`, `git branch -a | grep -i`, `gh pr list --search`) in the concurrent-session section, and `CLAUDE.md` carries a one-line catalog entry.
- [x] **AC-4** — all text project-agnostic; every `scripts/check-*.sh` guard run after `git add` shows no failure caused by this change (record in PR body).

# Related Specs

- `platform/git-workflow-policy.md`
- `CLAUDE.md`
- `tasks/INDEX.md`

# Related Contracts

None.

# Edge Cases

- `mergeable` is `UNKNOWN` right after `gh pr create` — the text tells the reader to re-query.
- A closed-and-reopened conflicting PR still has zero runs — stated.
- `CLAUDE.md` is a prose home for several guards — guards are run in full.

# Failure Scenarios

- A guard that counts or quotes `CLAUDE.md`/policy text goes red — fix the prose home it names, not the guard.
- Wording that names a service or project path violates HARDSTOP-03 — none used.
