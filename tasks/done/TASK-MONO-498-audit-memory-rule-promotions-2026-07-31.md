# Task ID

TASK-MONO-498

# Title

Land 4 memory-audit rule promotions into CLAUDE.md / platform/ (git-workflow, refactoring, event-driven)

# Status

done

# Owner

monorepo

# Task Tags

- code
- docs

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

A 2026-07-31 `/audit-memory` session surfaced 7 personal-memory rules the user explicitly approved for promotion to shared, repo-wide guidance (via `AskUserQuestion`, all 7 confirmed). 5 of the 7 were formalized as CLAUDE.md/platform/ edits directly in the main checkout during that session, outside the normal task workflow — this task exists purely to land those already-written, already-approved edits through the proper root-task + worktree + PR path, per `CLAUDE.md § Task Rules` ("Monorepo-level work... → task in repo-root `tasks/ready/`"). No new content is designed here; the diff already exists and is copied into this task's worktree verbatim.

The 4 changed files carry 5 distinct additions:

1. **`CLAUDE.md` § Task Rules** — two new bullets: (a) an ADR-scoped exception to the shared-path→root-task rule (when an ACCEPTED ADR already authorizes touching specific shared paths as part of one project's scope, filing under that project's `tasks/ready/` is acceptable — verify via the ADR's scope section + a `HARDSTOP-03` diff review, not by reflexive re-filing); (b) an "Agent-delegated plan-exact implementation" rule (diff the actual changed files against an approved plan's literal field/interface names before merging, when a single agent implemented that plan — the agent's own completion report may omit deviations it didn't flag).
2. **`CLAUDE.md` § Cross-Project Changes catalog** — two new one-liner bullets pointing to `platform/git-workflow-policy.md` detail: local-only "DO NOT COMMIT" header markers (grep before a broad `git add`, confirm include/exclude with the user explicitly), and `Agent(isolation:"worktree")` scaffold-branch cleanup (delete both the task branch and the harness's own `worktree-agent-<id>` branch after merge).
3. **`platform/git-workflow-policy.md`** — the full procedures backing the two CLAUDE.md one-liners above: a new bullet in § Post-Merge Branch Hygiene for the scaffold-branch cleanup, and a new § "Local-Only Override File Markers" section.
4. **`platform/refactoring-policy.md`** — a new Mandatory rule 7: prefer additive-shim decoupling over forcing a cross-project change atomic (decompose "A's removal breaks B" into an additive change on B, B's repoint, then A's removal — each independently mergeable), pairing with the existing consumer-grep rule 6.
5. **`platform/event-driven-policy.md` § Producer Rules** — a new bullet requiring deterministic `event_id` derivation (not random per-publish) from the event's natural transition key, with the outbox-PK-collision-as-409 consequence spelled out when `event_id` doubles as the outbox row's primary key.

---

# Scope

## In Scope

- Landing the existing, already-reviewed diff to exactly these 4 files: `CLAUDE.md`, `platform/git-workflow-policy.md`, `platform/refactoring-policy.md`, `platform/event-driven-policy.md`.
- Standard task lifecycle (worktree → commit → push → PR) for this diff.
- Fixing the one `ADR-053` → `ADR-MONO-053` citation typo in `platform/refactoring-policy.md` rule 7, found by the 2026-07-31 `/validate-rules` run (Info-severity, one-word fix, safe to bundle here since it's inside the same diff this task is landing).

## Out of Scope

- Any other `/validate-rules` finding from the same 2026-07-31 run (1 Critical — `code-reviewer` vs `qa-engineer` test-coverage gap; 3 other Warnings — `rules/common.md`/`entrypoint.md` baseline mismatch, 27 skills missing `specs/` references, `testcontainers` skill's symmetric-JWT example contradicting `jwt-auth`; 4 other Info items) — tracked separately, not bundled into this doc-promotion task.
- The two remaining memory-audit promotion candidates that were *not* approved for CLAUDE.md/platform placement in this session (`env_schedule_cron_ephemeral_use_backlog_task` → landed in the user's personal `~/.claude/CLAUDE.md`, outside this repo, already done; nothing further needed here).
- Any application code — this is a docs/rules-only task, `git diff --stat` must show only the 4 files above.

---

# Acceptance Criteria

- [ ] `CLAUDE.md`, `platform/git-workflow-policy.md`, `platform/refactoring-policy.md`, `platform/event-driven-policy.md` in the worktree match the content already verified in the main checkout (diff applied verbatim), with the one `ADR-053`→`ADR-MONO-053` fix included.
- [ ] `git diff --stat` against `origin/main` touches exactly those 4 files plus this task's own lifecycle files (INDEX.md, task file move).
- [ ] No application/service code touched.
- [ ] CI green (doc-lint / dead-ref guards; no test suite applies to a docs-only change).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `CLAUDE.md` (the file being amended)
- `platform/git-workflow-policy.md` (the file being amended)
- `platform/refactoring-policy.md` (the file being amended)
- `platform/event-driven-policy.md` (the file being amended)

---

# Related Contracts

None — this task changes only shared governance/policy docs, not any HTTP/event contract.

---

# Target Service

- Monorepo-level (`CLAUDE.md` + 3 `platform/` files) — no single service.

---

# Architecture

N/A — documentation-only task.

---

# Implementation Notes

The diff already exists in the main checkout's working tree (uncommitted as of 2026-07-31, produced during a `/audit-memory` session where the user explicitly approved all 7 promotion candidates via `AskUserQuestion`, 5 of which land here — the other 2 were an `env_schedule_cron_ephemeral_use_backlog_task` memory landing in the user's personal `~/.claude/CLAUDE.md` outside this repo, and 2 reusable-pattern memories, `project_additive_shim_decouples_atomic_crossproject` and `project_deterministic_event_id_outbox_pk_collapses_dupes`, whose content is items 4 and 5 above). Implementation is: create the worktree from `origin/main`, copy the 4 files' content over verbatim (fixing the one citation typo), verify no other drift, commit, push, open PR.

---

# Edge Cases

- The worktree's `origin/main` may have moved since the diff was drafted against a slightly older `main` — re-diff against fresh `origin/main` before copying to confirm no conflicting concurrent edit to the same 4 files landed in the meantime.

---

# Failure Scenarios

- Silently expanding scope to also fix the other `/validate-rules` findings (the Critical `code-reviewer`/`qa-engineer` gap, the 3 other Warnings) would mix an unrelated, larger governance decision into a small doc-promotion PR — explicitly out of scope, file those separately if picked up.

---

# Test Requirements

- None (docs-only). Run doc-lint / dead-ref CI guards.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Doc-lint / dead-ref checks passing
- [ ] Contracts unchanged (verified — none applicable)
- [ ] Ready for review
