# Git Workflow Policy

Normative git / branch / worktree procedures for AI agents and developers working in this monorepo. This file is **read on-demand** (Source-of-Truth Priority layer 5, `platform/` remaining files) — `CLAUDE.md` carries only the one-line catalog form of each rule and points here for the full procedure.

This file is **self-contained**: a fresh clone needs nothing but this file to follow the rules. Worked-incident forensics belong **here** (or in agent personal memory), never re-inlined into `CLAUDE.md` — that is what caused the catalog drift this file corrects. Project-agnostic: no service names, API paths, or domain entities.

---

## Branch Naming

**Never include the substring `master` in branch names.** The sandbox `--force` regex matches `master` as a substring and blocks `git push` even on feature branches.

- Rename around the noun: `task/be-161-database-design-...` (not `...-master-service-...`).
- Or use the abbreviation: `ms-`, `mst-`.
- Workaround if you hit it: `git push -u origin HEAD` (renaming the branch is cleaner).
- Encountered repeatedly across multiple tasks — treat as a standing constraint, not a one-off.

---

## Concurrent-Session Worktree Isolation

Multiple interactive agent sessions (or routines) running at the same time must **never share the main checkout**. Each concurrent task gets its own `git worktree add` directory; the main checkout stays parked on a stable branch (ideally `main`) and is not used for task work.

- A `git checkout` / `checkout -b` in a directory another live session is using moves the **single shared HEAD + index** of that directory — the other session's uncommitted WIP gets stranded on the wrong branch, and its next `git commit` lands on *your* branch. ("branch per task" alone is insufficient; the isolation unit is the **worktree (directory)**, not the branch.)
- The `protect-main-branch.ps1` hook only blocks commits/pushes **on `main`** — it does NOT catch two sessions sharing the main checkout across two *feature* branches. There is no automated guard for this case; it is a discipline rule. (`docs/guides/monorepo-workflow.md` documents the worktree convention but is human-reference-only and assumes the harness-managed `.claude/worktrees/agent-<id>/` dispatch model, not manual multi-session worktrees.)
- **Symptom**: `git worktree list` shows the main checkout on an unexpected feature branch, or `git status` surfaces files from a task you are not working on.
- **Recovery**: commit only your own files by **explicit path** (leave the other session's WIP untouched), then `git checkout <their-branch>` to restore the shared HEAD — uncommitted WIP travels along and re-lands on the correct branch (0 path overlap = no conflict). Afterward move your work to its own `git worktree add` directory.
- Worktree-add Windows pitfalls (DWIM remote-branch resolution, failed-remove, prune side effects) — always pass **absolute** worktree-add paths; a relative `../x` from a drifted shell cwd can nest a stray directory inside the main checkout and lose files. (Agent personal-memory detail, this host: `env_git_worktree_verify_windows`, `env_concurrent_git_branch_switch_hazard`.)
- Worktree teardown on Windows with junctioned `node_modules` — when a frontend worktree shares the main checkout's `node_modules` via a directory junction (reparse point), tearing the worktree down naively (`Remove-Item -Recurse` / `git worktree remove`) follows the junction and **corrupts the main checkout's `node_modules`** (e.g. a missing `.pnpm` store then breaks the main tree's `tsc`/`vitest`). Remove the junction reparse points **first** (`cmd /c rmdir <junction>`, which unlinks without following), then delete the worktree directory. Recovery if corrupted: re-run the affected app's install (`pnpm install --force`). (Agent personal-memory detail, this host: `env_worktree_node_modules_junction_cleanup_hazard`.)

### Dispatching a subagent into a worktree

When delegating implementation to a subagent (the Agent tool) that must edit *inside* a worktree, pass **absolute worktree paths** in the prompt and instruct it to use them. A relative-looking path resolves against the **session cwd** (the parked main checkout), so the subagent's edits silently land in the protected main checkout instead of the worktree — the same contamination this section guards against, via a different route.

- **Guard**: immediately after dispatch, run `git status --porcelain -- <path>` in the main checkout; any modification there is a leak.
- **Recover**: `git restore <path>` (tracked) + remove only the **named** untracked stray files (leave the working tree otherwise untouched).
- The subagent may self-correct by re-applying with absolute paths, but the orphaned copy in the main checkout persists until cleaned, and the classifier blocks the *subagent* from cleaning the main checkout — the **orchestrator** must. (Worked incident: TASK-MONO-241, 2026-06-13.)

---

## Post-Merge Branch Hygiene

The repo squash-merges PRs; feature/chore refs are not auto-pruned and accumulate.

- After a PR squash-merges, delete its feature + close-chore refs immediately. Stacked work uses a single tip-only PR (the tip contains its base; the base ref becomes squash-residue → delete it too).
- A ref is **squash-merge-stale** (safe to delete) when its task is in `origin/main`'s `tasks/done/` (or its squash commit is in `git log origin/main`).
- The auto-mode classifier is a context-sensitive higher safety layer over the permission allowlist — it **may** gate mass `git push origin --delete`, but not as a hard rule (it allowed a confirmed-merge-stale batch on 2026-06-15). **Attempt the deletion first** when every target ref is confirmed merge-stale (per the bullet above) and not worktree-occupied / OPEN; `gh pr create` / `gh pr merge --squash` pass; local `git branch -D` is fine for the agent. Only on an **actual** block: STOP and hand the user the exact command — do not reformulate to bypass.
- **`git branch -r` is a stale local cache, not `origin` truth.** Before concluding remote-branch state or recommending a mass remote deletion, run `git fetch --prune` (or `git remote prune origin`). `git fetch origin main` updates only `main` and does NOT prune — so refs already deleted on `origin` linger locally as stale tracking refs that falsely read as "needs cleanup". Prune first, confirm the real residue, then hand over only what genuinely remains (often nothing — avoid an unnecessary user `push --delete`).
- **Stacked-PR base-ref-deletion auto-close hazard.** Deleting a PR's base ref auto-closes that PR on GitHub, and `gh pr reopen` is then rejected — so `gh pr merge <base> --squash --delete-branch` is destructive-in-disguise for any child PR stacked on it. Prevention: retarget the child first (`gh pr edit <child> --base main`), or merge the base without `--delete-branch`. Recovery: `git rebase --onto origin/main <base-squash-sha>` the child, `--force-with-lease`, open a fresh PR.

(Agent personal-memory detail, this host: `project_branch_hygiene_policy`.)

---

## A PR whose base is not `main` receives **zero** checks — and merges unblocked

`.github/workflows/ci.yml` is triggered by `pull_request: branches: [main]`. That filter matches on the PR's
**base**. A PR opened against any other base therefore **matches no workflow at all** — GitHub does not run,
skip, or queue anything. It reports **0 checks**.

This is the dangerous case precisely because it looks like the safe one:

- "0 failing checks" and "CI ran and approved this" are **indistinguishable at the merge button**.
- There is no red X, no pending spinner, and nothing to block the merge — branch protection has no required
  check to wait for when no workflow ever matched.
- The code reaches `main` through the **normal, green-looking path**, having never been compiled or tested.

**0 checks is not a flake, and it is not a merge conflict** (a conflicting PR still *matches* the workflow;
it just cannot run — see the distinction below). It means the workflow never applied.

**Therefore**: open every PR — spec and impl alike — with **`base=main`**, and merge them sequentially.
Where the work genuinely stacks, follow § Post-Merge Branch Hygiene: land it as a single tip-only PR whose
base is `main`, not as a chain of PRs pointed at each other.

> The invariant is about the **PR base**, not about local branch topology. Stacked *branches* are fine.
> A PR whose `base != main` is not.

Before trusting a PR's check state, confirm the count is non-zero — an empty check list is a signal, not an
absence of problems.

---

## `gh pr create` / `gh pr merge` Body Hook False-Match

The `protect-main-branch` hook inspects the command string for direct-to-`main` pushes. A `gh pr create` / `gh pr merge` whose **inline body text** (`--body "…"`) contains literal tokens such as `push origin --delete`, `push --delete`, or `reset … to main` can trip a false-match and be **blocked** even though the command only opens/merges a PR. Workaround: pass the body via a file — `gh pr create --body-file <path>` (the hook matches the inline command string, not file contents) — or reword the body to avoid those literal tokens. (Agent personal-memory detail, this host: `project_branch_hygiene_policy`.)

---

## `git commit && git push` Chained in One Bash Call Is Blocked Whole

Chaining `git commit … && git push …` in a **single** Bash-tool invocation lets the `protect-main-branch` hook match the `push` half and block the **entire** call — so the commit never lands either. Run them as **two separate Bash calls**: commit first, confirm it succeeded, then push. (Agent personal-memory detail, this host: `project_console_web_ecommerce_ops_bug_class`.)

---

## `.claude/` Self-Modification Is Classifier-Blocked

The auto-mode classifier hard-blocks editing or committing files under `.claude/hooks/`, `.claude/agents/`, `.claude/commands/` even with explicit user approval (the same higher-safety layer as mass `push --delete`). Hand the exact patch to the user to apply + commit; do not attempt a shell-write bypass. `platform/` is **not** subject to this — only `.claude/`. (Agent personal-memory detail, this host: `env_classifier_claude_self_mod_block`.)

---

## CI Path-Filter Constraint

When editing `.github/workflows/` `dorny/paths-filter` configuration: never use negation patterns (the `predicate-quantifier: 'some'` negation misclassifies a file as "in"); use a pure-positive `code-changed` filter composed with the original via an outputs-layer AND; backfill new code extensions into the positive filter; add an entry per new project. (Agent personal-memory detail, this host: `project_ci_path_filter_074_075_quirk`.)

---

## Merge-Verification Worked Incident

The three-dimension objective merge verification before any close chore (defined in `CLAUDE.md` § Task Rules) exists because a "merged it" statement is not proof. Worked incident: a PR was squash-merged while a required integration check was still failing → `main` went RED four times in a row → recovery required a separate top-priority fix-task to restore `main` GREEN. CI-RED-at-merge time creates a main regression; the `statusCheckRollup` of the merged PR is the authoritative record. If any of the three dimensions fails, STOP and open a fix-task before the close chore.
