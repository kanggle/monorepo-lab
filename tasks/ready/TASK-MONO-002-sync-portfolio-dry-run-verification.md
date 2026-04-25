# TASK-MONO-002 — sync-portfolio dry-run verification (post-Phase 7 + Phase 8)

## Goal

Verify that `scripts/sync-portfolio.sh` still produces a coherent extraction
plan for both `wms-platform` and `ecommerce-microservices-platform` after
Phase 7 (composite-build → direct-include consolidation, PR #58/#61) and
Phase 8 (CI scope changes, gradle wrapper removal, root task lifecycle).
Catch any drift between what the script's `SHARED_PATHS` / `PROJECT_TYPES`
declare and what actually exists in the monorepo today, before any future
live extraction force-pushes a broken layout to a standalone repo.

## Background

`scripts/sync-portfolio.sh` declares:

- `PROJECT_REMOTES`: wms-platform → kanggle/wms-platform, ecommerce-...
  → kanggle/ecommerce-microservices-platform.
- `PROJECT_TYPES`: both `direct-include` after PR #61 (was `composite-build`
  for ecommerce until then).
- `SHARED_PATHS`: 19 entries enumerating which repo-root paths survive
  extraction (libs, platform, rules, .claude, tasks/templates, docs/guides,
  build/settings/gradle*, .gitignore, .dockerignore, .editorconfig,
  .github, CLAUDE.md, TEMPLATE.md).

Two concrete drift risks:

1. PR #58 deleted ecommerce's project-internal `libs/` and `settings.gradle`,
   PR #61 flipped its `PROJECT_TYPES` to `direct-include`, PR #64 removed
   ecommerce's gradle wrapper. The post-process step `post_process_direct_include`
   restores root `build.gradle` and rewrites `projects:<name>:` paths in
   `settings.gradle` and subproject `*.gradle`. We have not rerun the
   script since these changes — even dry-run — to confirm the path
   rewrite still produces a runnable standalone layout.
2. PR #65 introduced repo-root `tasks/{ready,in-progress,review,done}/`
   and `tasks/INDEX.md`. `SHARED_PATHS` only carries `tasks/templates/` —
   the new lifecycle dirs and INDEX would NOT survive extraction today.
   Whether they SHOULD survive is a design call (a standalone repo
   probably wants its own task lifecycle, but inheriting MONO task
   history is questionable). Task should surface this and recommend.

## Scope

**In scope:**

1. Run `./scripts/sync-portfolio.sh --dry-run wms-platform` and capture
   the full output.
2. Run `./scripts/sync-portfolio.sh --dry-run ecommerce-microservices-platform`
   and capture the full output.
3. Inspect the dry-run reports for:
   - Correct PROJECT_REMOTES / PROJECT_TYPES values (post-PR #61).
   - Complete and current `SHARED_PATHS` enumeration vs actual repo state.
   - Any path that no longer exists (e.g., would the script try to keep
     a removed directory).
   - Any path that now exists but is not enumerated (drift).
4. Recommend updates to `SHARED_PATHS` for the new repo-root `tasks/`
   structure. Three plausible policies — pick one and document the
   reasoning:
   - **(a) Strip entirely** — exclude root `tasks/` from the extracted
     repo so the standalone starts with a clean lifecycle. Means the
     standalone needs its own bootstrap.
   - **(b) Carry templates only** — keep `tasks/templates/` as before;
     drop INDEX.md and lifecycle dirs. Standalone synthesises its own
     INDEX.md.
   - **(c) Carry the full root tasks/** — INDEX.md, templates, and any
     content in lifecycle dirs survive. Risk: monorepo task history
     leaks into a standalone repo that has no use for it.
5. Capture the dry-run output and the recommendation in this task's
   "Outcome" section.

**Out of scope:**

- Live extraction (force-push to either standalone remote). That is a
  separate task once the dry-run report is reviewed.
- Implementing the chosen `SHARED_PATHS` policy. Track as a follow-up
  task (TASK-MONO-003 or similar) after the recommendation is approved.
- Any change to `scripts/sync-portfolio.sh` source code. This task is
  read-only on the script; only its output is captured.

## Acceptance Criteria

1. Both dry-runs complete without bash errors.
2. Output for each dry-run shows the expected `Type: direct-include`
   line and the full `Kept paths` enumeration.
3. The four inspection points (correct types, complete shared paths,
   removed paths, drift) are reported as ✅ / ⚠️ / 🚨 in the Outcome
   section.
4. A recommendation between policies (a) / (b) / (c) for root `tasks/`
   handling is given with reasoning.
5. No script source change occurs in this task's PR.

## Related Specs

- `TEMPLATE.md` § "Starting a New Project from the Extracted Template"
  describes the extracted repo's expected layout — the dry-run report
  should be readable against this.
- `tasks/INDEX.md` § "When to Use Root vs Project Tasks" — informs
  policy (a)/(b)/(c) for root `tasks/`.

## Related Contracts

None.

## Edge Cases

- The dry-run does not actually invoke Docker, so it should run without
  the `docker info` precheck succeeding. (The script's `main` runs
  `docker --version` precheck; dry-run still passes because `--version`
  works without daemon.)
- `sync_project()` validates `PROJECT_TYPES[$project]` is in the allowed
  set — the dry-run still hits this validation. Both should pass after
  PR #61.
- Output formatting uses ANSI colour codes. When piping to a file or
  the task doc, strip them or note them in the report.

## Failure Scenarios

- **Bash error** (unset variable, missing path, etc.) — fail the task,
  open a fix follow-up before any live extraction is attempted.
- **PROJECT_REMOTES / PROJECT_TYPES mismatch** — same.
- **SHARED_PATHS drift** that materially changes the extracted repo
  shape — task surfaces it and recommends a follow-up; not a blocker
  for closing this task itself.

## Outcome

(To be filled in once the dry-runs run.)
