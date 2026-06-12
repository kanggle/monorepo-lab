# Task ID

TASK-MONO-235

# Title

Promote the concurrent-session worktree-isolation rule from project memory to `CLAUDE.md` — multiple live interactive sessions must never share the main checkout; each concurrent task gets its own `git worktree add` directory and the main checkout stays parked on a stable branch.

# Status

ready

# Owner

backend

# Task Tags

- docs
- monorepo
- governance

---

# Dependency Markers

- **source**: 2026-06-13 live incident — three concurrent interactive sessions (be-357 increment B, be-361, fin-be-015) ran correctly in isolated worktrees, but a fourth (pc-fe-070) and an increment-C session both operated in the shared main `monorepo-lab` checkout. A `git checkout` in the shared dir moved the single HEAD, stranding the increment-C WIP on the wrong branch (recovered by explicit-path commit + HEAD restore). Root cause: the worktree convention is documented only in the human-reference guide and is not an enforced rule.
- **prior decision**: `TASK-MONO-163` § Dependency Markers recorded this "concurrent-branch" promotion candidate as **user-deferred** (MED) at the 2026-06-01 audit-memory run. This task un-defers it after the 2026-06-13 incident demonstrated the gap concretely.
- **memory sources (worked-example companions, NOT deleted — catalog/detail split)**: `env_concurrent_git_branch_switch_hazard` (the shared-HEAD switch hazard + reflog forensics + stash recovery), `env_git_worktree_verify_windows` (Windows `worktree add` DWIM/failed-remove/prune pitfalls).
- **model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (docs-only catalog edit, single shared file).

---

# Goal

Add the concurrent-session worktree-isolation rule to `CLAUDE.md` so any new session/agent inherits it, keeping `CLAUDE.md` as the **catalog** that points to the existing memory files for worked-example detail (no memory deletion — the catalog/detail convention from MONO-163 is preserved).

# Scope

## In scope (`CLAUDE.md` only — repo-root shared file; NOT under `.claude/`, so editable)

1. **§ Cross-Project Changes** — add a new bold subsection **"Concurrent-session worktree isolation"** as a peer to "Branch name constraint" / "Post-merge branch hygiene", stating:
   - Concurrent interactive sessions/routines must never share the main checkout; each task gets its own `git worktree add` directory; the main `monorepo-lab` checkout stays parked on a stable branch (ideally `main`).
   - The isolation unit is the **worktree (directory)**, not the branch — a shared directory has a single HEAD + index, so "branch per task" alone does not prevent collision.
   - `protect-main-branch.ps1` only blocks commits/pushes **on `main`**; it does NOT catch two sessions sharing the main checkout across two feature branches. No automated guard exists — discipline rule. Note that `docs/guides/monorepo-workflow.md` documents the worktree convention but is human-reference-only and assumes the harness-managed `.claude/worktrees/agent-<id>/` dispatch model.
   - Symptom + recovery: explicit-path commit of your own files → `git checkout <their-branch>` to restore the shared HEAD (uncommitted WIP travels along, 0 path overlap = no conflict) → move your work to its own worktree.
   - Pointer to memories `env_concurrent_git_branch_switch_hazard` and `env_git_worktree_verify_windows`.

## Out of scope
- Any `.claude/` file edit (classifier-blocked) — including extending `protect-main-branch.ps1` to detect shared-checkout feature-branch switches. That hook hardening is a **follow-up** requiring a hand-to-user patch; this task is `CLAUDE.md`-only.
- Any memory-file deletion (the source memories stay as detail companions).
- The other MED promote candidates deferred at MONO-163 (pr_on_request / pr_bundling / gradle_rerun).

# Acceptance Criteria

- **AC-1**: `CLAUDE.md` § Cross-Project Changes contains a "Concurrent-session worktree isolation" subsection requiring each concurrent session to use its own `git worktree add` directory with the main checkout parked on a stable branch.
- **AC-2**: The subsection states the isolation unit is the worktree/directory (not the branch) and explains the shared-HEAD/index collision mechanism.
- **AC-3**: The subsection notes the `protect-main-branch.ps1` enforcement gap (main-only) and points to `env_concurrent_git_branch_switch_hazard` + `env_git_worktree_verify_windows` for detail.
- **AC-4**: Edits are additive — no existing CLAUDE.md rule is removed or weakened; catalog/detail convention preserved (memories not deleted); markdown well-formed.

# Related Specs / Code

- `CLAUDE.md` § Cross-Project Changes (new "Concurrent-session worktree isolation" subsection).
- `docs/guides/monorepo-workflow.md` §§ "always in a worktree, never directly on main" / "Worktree isolation" (the human-reference convention being promoted to an enforced catalog rule).
- Memory: `env_concurrent_git_branch_switch_hazard`, `env_git_worktree_verify_windows`.

# Related Contracts

- None (governance/docs change — no API or event contract touched).

# Edge Cases / Failure Scenarios

- **Do not edit any `.claude/` file** — classifier-blocked; this task is `CLAUDE.md` (repo root) only. The hook-hardening idea is explicitly a follow-up, not in scope.
- **Keep catalog-level brevity** — CLAUDE.md is the catalog; full worked example stays in the memory files.
- **HARDSTOP-03 N/A** — CLAUDE.md is shared/project-agnostic; this is a repo-wide governance rule (correct for a shared file), no project-specific content introduced.
- **Self-dogfooding** — this very task was authored in a dedicated worktree (`mlab-mono235`) off `origin/main`, leaving the contested main checkout untouched, demonstrating the rule being added.

# Notes

- Un-defers the MONO-163 "concurrent-branch" MED candidate after the 2026-06-13 incident. Docs-only; merge via PR per shared-file lifecycle.
