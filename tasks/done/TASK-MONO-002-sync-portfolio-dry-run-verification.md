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

## Outcome (2026-04-26)

Both dry-runs completed cleanly. No bash error, no validation rejection.

### Inspection results

| Inspection point | Status | Notes |
|---|---|---|
| Correct `PROJECT_TYPES` | ✅ | Both report `Type: direct-include` (post-PR #61) |
| Correct `PROJECT_REMOTES` | ✅ | wms → `kanggle/wms-platform.git`, ecommerce → `kanggle/ecommerce-microservices-platform.git` |
| `SHARED_PATHS` complete vs current repo | ✅ with note | All 19 listed paths still exist at repo root |
| Removed paths still listed | ✅ | No false positives — every listed path actually exists |
| Drift: paths exist but not enumerated | ⚠️ minor | See "New repo-root paths since SHARED_PATHS was last updated" below |

### Captured dry-run output

`./scripts/sync-portfolio.sh --dry-run wms-platform` and the equivalent for
ecommerce both print:

```
[sync] Project:  <project-name>
[sync] Remote:   https://github.com/kanggle/<project-name>.git
[sync] Type:     direct-include
[sync] Source:   /c/Users/kangdow/dev/project/ai-project/monorepo-lab
[sync] Workdir:  /tmp/portfolio-sync/<project-name>
[sync] [dry-run] Would clone monorepo, run filter-repo, force-push to https://github.com/kanggle/<project-name>.git
[sync] [dry-run] Kept paths:
             libs/
             platform/
             rules/
             .claude/
             tasks/templates/
             docs/guides/
             build.gradle
             settings.gradle
             gradle/
             gradlew
             gradlew.bat
             gradle.properties
             .gitignore
             .gitattributes
             .dockerignore
             .editorconfig
             .github/
             CLAUDE.md
             TEMPLATE.md
             projects/<project-name>/
```

### New repo-root paths since `SHARED_PATHS` was last updated

PR #65 introduced `tasks/INDEX.md` and `tasks/{ready,in-progress,review,done}/`
at the monorepo root. None of these are in `SHARED_PATHS`. Other root
paths NOT in `SHARED_PATHS`: `README.md`, `package.json`, `scripts/`,
`build/` (gradle output, intentionally excluded).

### Why current `SHARED_PATHS` is correct without modification

For each non-enumerated root path, the extraction outcome is what we
actually want:

| Path | Why exclusion is correct |
|---|---|
| `tasks/INDEX.md` (root) | Project's own `projects/<name>/tasks/INDEX.md` gets path-renamed to `<extracted>/tasks/INDEX.md` and survives. Root one would only leak monorepo-specific MONO task vocabulary into the standalone. |
| `tasks/ready/`, `tasks/in-progress/`, `tasks/review/`, `tasks/done/` (root) | Same reasoning — project's own lifecycle dirs hoist to `<extracted>/tasks/{...}/` and the standalone gets its TASK-BE/INT/FE/DOC history. The root MONO history stays in the monorepo. |
| `README.md` (root) | Portfolio-hub README referencing all projects. Project's own `projects/<name>/README.md` hoists to `<extracted>/README.md`. Filter-repo's path-rename overwrites the root one. |
| `package.json` (root) | Monorepo convenience scripts (`wms:up`, `ecommerce:up` etc.) tied to `projects/<name>/` paths that disappear after hoisting. ecommerce has its own `projects/ecommerce-.../package.json` (turbo/pnpm workspace) which hoists to root in its standalone. wms has no project-level package.json (backend only) — that is correct for the standalone. |
| `scripts/` (root) | Contains `sync-portfolio.sh` itself plus `README.md` for monorepo tooling. Each project has its own `projects/<name>/scripts/` for project-specific helpers, which hoists to `<extracted>/scripts/`. |

### Recommendation

**Policy (a) — strip root `tasks/` lifecycle from extraction (current
behavior, no script change needed).** The script as-is implements this
implicitly by only listing `tasks/templates/` in `SHARED_PATHS`. The
templates survive (they're universal); the lifecycle dirs and INDEX
do not. The standalone gets its own project-level lifecycle dirs and
INDEX through the path-rename of `projects/<name>/tasks/`.

No change to `scripts/sync-portfolio.sh`. No follow-up task is needed
to update `SHARED_PATHS`. Future repo-root additions should be
evaluated against the same "should this leak into a standalone repo?"
question — and added to `SHARED_PATHS` only if the answer is yes.

### What this task does NOT validate

- Whether the post-process `direct_include` step still produces a
  buildable standalone after the hoisting. That requires a real
  filter-repo run (live or in a throwaway local repo), which is the
  job of a future TASK-MONO-003 (live extraction validation).
- Whether `kanggle/wms-platform` and `kanggle/ecommerce-microservices-platform`
  in their current state (last force-push date unknown) can be
  overwritten by a fresh extraction without losing in-flight work. The
  user should confirm there is no unmerged content on those remotes
  before any live run.
