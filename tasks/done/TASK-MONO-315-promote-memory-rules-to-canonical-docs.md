# TASK-MONO-315 — Promote three repo-wide operating rules from agent memory to canonical docs

**Status:** done

**Type:** TASK-MONO (monorepo-level — shared paths `CLAUDE.md` + `platform/`)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (doc-only catalog promotion; no code)

---

## Goal

A memory audit (2026-06-28) surfaced three repo-wide operating rules that lived only in the agent's private/session memory. They apply to any developer or AI session, so they belong in the checked-in canonical docs (catalog form), with the worked-detail retained in memory. Promote them.

## Scope

Three additive doc edits (no code, no behaviour change):

1. **`platform/testing-strategy.md` § Rules** — add the Gradle test-cache caveat: after changing the constructor dependencies of a class a test mocks (e.g. an `@InjectMocks` target), run that module's `:test` once with `--rerun-tasks` to re-establish the baseline; a stale compiled-test cache can fail the first incremental run even though the source is correct. (from memory `feedback_gradle_rerun_tasks_after_mockito_dep_change`)
2. **`platform/git-workflow-policy.md` § Concurrent-Session Worktree Isolation** — add the Windows junctioned-`node_modules` teardown hazard: removing a worktree naively follows the junction and corrupts the main checkout's `node_modules`; remove the junction reparse points first (`cmd /c rmdir`), then delete the worktree. (from memory `env_worktree_node_modules_junction_cleanup_hazard`)
3. **`CLAUDE.md` § Task Rules (merge-verification bullet)** — add the CI-polling caveat: never use the exit code of `gh pr checks <n>` to decide GREEN (a pending check exits non-zero); parse its text / `--json` output. Placed inline with the existing 3-dimension merge-verification rule that already references `gh pr checks`. (from memory `env_bash_jq_absent_gh_checks_exit`, gh-checks-exit half)

## Out of Scope

- The `jq`-absent half of `env_bash_jq_absent_gh_checks_exit` — that is **host-specific** (this Windows machine's Bash tool lacks `jq`); a Linux/Mac developer has `jq`. It is NOT repo-universal, so it does not belong in the checked-in repo docs. It goes to the user-level `~/.claude/CLAUDE.md` § Windows Shell Environment instead (handled outside this repo PR).
- The other audit-surfaced candidates (memory-internal consolidation into the diagnostic-patterns catalog; adr030 dual read-path; etc.) — deferred, lower value.

## Acceptance Criteria

- [x] The three rules added to their target canonical files in catalog/one-paragraph form (worked-detail stays in memory).
- [x] Additive only — no existing rule altered; no service names / API paths / domain entities introduced into `platform/` (project-agnostic boundary preserved). The Windows-host specifics are environment notes flagged as agent-personal-memory detail, not project content.
- [x] `platform/testing-strategy.md` § Change Rule satisfied (the test-standard change is recorded in this canonical file).
- [ ] commit + push (branch `task/mono-315-promote-memory-rules-to-canonical-docs`) + PR + CI GREEN + merge (3-dim verify).
- [ ] Post-merge: annotate the three source memories with "catalog 승격됨 (MONO-315)" pointers; add the `jq`-absent rule to user-level `~/.claude/CLAUDE.md`.

## Related Specs

- The memory audit report (2026-06-28 session) that surfaced these.
- Precedent promotions: MONO-309 (Playwright URL assertions → testing-strategy; prune → TEMPLATE.md), 3-dim merge verification → CLAUDE.md, heredoc → user-level CLAUDE.md.

## Related Contracts

- None (doc-only).

## Edge Cases / Failure Scenarios

- **platform/ project-agnostic boundary** — the junction + gradle rules are env/tooling notes, not project-specific content; they carry no service/API/entity names, so HARDSTOP-03 does not trigger. Verified.

## Definition of Done

- [x] 3 canonical doc edits applied.
- [ ] PR merged (3-dim verify) + source-memory pointers + user-level `jq` rule.
