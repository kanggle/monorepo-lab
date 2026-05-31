# Task ID

TASK-MONO-163

# Title

Promote two HIGH-priority operational rules from project memory to `CLAUDE.md` (audit-memory 2026-06-01 § 5 outcome): (1) the auto-mode classifier hard-blocks `.claude/` self-modification (hooks/agents/commands edit+commit) even with explicit approval → hand the patch to the user; (2) two branch-hygiene rules — `git branch -r` is a stale local cache (prune before concluding remote state) + stacked-PR base-ref-deletion auto-close hazard.

# Status

done

# Owner

backend

# Task Tags

- docs
- monorepo
- governance

---

# Dependency Markers

- **source**: `/audit-memory` run 2026-06-01 § 5 (promote candidates). User approved promoting the **HIGH 2 groups** only (classifier `.claude/` self-mod block + branch_hygiene 2 new rules). MED candidates (pr_on_request / pr_bundling / gradle_rerun / worktree / concurrent-branch) deferred by user.
- **memory sources (worked-example companions, NOT deleted — catalog/detail split)**: `env_classifier_claude_self_mod_block` (group 1), `project_branch_hygiene_policy` (group 2, the 2026-06-01 `git branch -r` lesson + the 2026-05-22 stacked-PR auto-close hazard).
- **model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (docs-only catalog edit, single shared file).

---

# Goal

Add the two HIGH operational rules to `CLAUDE.md` so any new session/agent inherits them, keeping `CLAUDE.md` as the **catalog** that points to the existing memory files for worked-example detail (no memory deletion — these memories add specificity beyond the catalog line, per the established catalog/detail convention).

# Scope

## In scope (`CLAUDE.md` only — repo-root shared file; NOT under `.claude/`, so editable)

1. **§ Cross-Project Changes → "Post-merge branch hygiene"** — add two bullets:
   - **`git branch -r` is a stale local cache**: before concluding remote-branch state or recommending a mass remote deletion, run `git fetch --prune` (or `git remote prune origin`). `git fetch origin main` updates only `main` and does NOT prune — so refs already deleted on `origin` (P3 applied) still appear locally as dozens of stale tracking refs, falsely reading as "needs cleanup". Prune first, then confirm the real residue. (Avoids handing the user an unnecessary mass `push --delete`.)
   - **Stacked-PR base-ref-deletion auto-close hazard**: deleting a PR's base ref auto-closes that PR on GitHub, and `gh pr reopen` is then rejected (base gone). So `gh pr merge <base> --squash --delete-branch` is destructive-in-disguise for any child PR stacked on it. Prevention: retarget the child to main first (`gh pr edit <child> --base main`), or merge the base WITHOUT `--delete-branch`. Recovery: `git rebase --onto origin/main <base-squash-sha>` the child + force-with-lease + open a fresh PR.
   - Update the existing classifier bullet (currently push-`--delete`-only) to also note the **`.claude/` self-mod block** (group 2 below), OR add a dedicated bullet — implementer's choice for cleanest placement.

2. **Classifier `.claude/` self-mod block** — document that the auto-mode classifier **hard-blocks editing or committing files under `.claude/hooks/`, `.claude/agents/`, `.claude/commands/`** even with explicit user approval (same higher-safety-layer as mass `push --delete` / non-agent process kill). The agent must **hand the exact patch to the user** to apply + commit; do not attempt shell-write bypass. Note `platform/` is NOT subject to this (only `.claude/`). Best home: extend the existing classifier note in "Post-merge branch hygiene", or add to "Hard Stop Rules" preamble — implementer picks the placement that reads cleanest as a catalog entry.

3. **Update the "Worked examples + procedure" pointer line** (currently lists `project_branch_hygiene_policy` + `project_ci_path_filter_074_075_quirk`) to also reference `env_classifier_claude_self_mod_block` for the classifier `.claude/` block detail.

## Out of scope
- The MED promote candidates (deferred by user).
- Any memory-file deletion (the source memories stay as detail companions).
- Any `.claude/` file edit (that is exactly what's classifier-blocked; this task only touches root `CLAUDE.md`).

# Acceptance Criteria

- **AC-1**: `CLAUDE.md` § Cross-Project Changes "Post-merge branch hygiene" contains the `git branch -r` stale-cache rule (prune before concluding/mass-delete) and the stacked-PR base-ref auto-close hazard rule, each concise (catalog-level), pointing to `project_branch_hygiene_policy` for detail.
- **AC-2**: `CLAUDE.md` documents the classifier `.claude/` self-mod hard-block (hand-patch-to-user; `platform/` exempt), pointing to `env_classifier_claude_self_mod_block`.
- **AC-3**: Edits are additive — no existing CLAUDE.md rule is removed or weakened; the catalog/detail convention is preserved (memories not deleted).
- **AC-4**: `CLAUDE.md` remains internally consistent (no contradiction with the existing push-`--delete` classifier bullet); markdown well-formed.

# Related Specs / Code

- `CLAUDE.md` § Cross-Project Changes "Post-merge branch hygiene" (the two new branch-hygiene bullets) + the classifier note.
- Memory: `env_classifier_claude_self_mod_block`, `project_branch_hygiene_policy` (constraints #5 + Stacked-PR auto-close hazard sections).

# Edge Cases / Failure Scenarios

- **Do not edit any `.claude/` file** — classifier-blocked; this task is CLAUDE.md (repo root) only.
- **Keep catalog-level brevity** — CLAUDE.md is the catalog; detail stays in the memory files (do not paste the full worked examples).
- **HARDSTOP-03 N/A** — CLAUDE.md is shared/project-agnostic; these are repo-wide governance rules (correct for a shared file), no project-specific content introduced.

# Notes

- audit-memory 2026-06-01 § 5 HIGH promotions. MED candidates deferred. Docs-only; merge via PR per shared-file lifecycle.
