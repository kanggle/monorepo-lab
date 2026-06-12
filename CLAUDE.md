# CLAUDE.md

Minimum operating rules for AI agents and developers in this monorepo. **Catalog + safety net** — full detail lives in the canonical files linked below.

---

# Repository Layout

```
<repo-root>/
├── CLAUDE.md              ← this file
├── TEMPLATE.md            ← template extraction guide + Local Network Convention master
├── README.md              ← portfolio hub
├── platform/              ← platform regulations (shared)
├── rules/                 ← rule library: common.md + domains/ + traits/ + taxonomy.md + README.md (shared)
├── .claude/               ← agent config: skills/, agents/, commands/, config/ (shared)
├── libs/                  ← shared Java libraries
├── tasks/                 ← monorepo-level task lifecycle (shared)
├── docs/{adr,guides,project-overview.md}
├── build.gradle, settings.gradle ...
└── projects/<project>/    ← one directory per project (5 active)
    ├── PROJECT.md         ← project classification (domain, traits)
    ├── apps/ specs/ tasks/ knowledge/ docs/ infra/
```

**Shared vs project boundary** (strict, Hard-Stop-enforced):

- **Shared (repo root)**: `platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `tasks/INDEX.md` + monorepo-level `tasks/{ready,…}/`, `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md` — **must remain project-agnostic** (no service names, API paths, domain entities).
- **Project-specific (`projects/<name>/`)**: `PROJECT.md`, `apps/`, `specs/`, project `tasks/`, `knowledge/`, `docs/` (except `guides/`), `infra/`.

See [`TEMPLATE.md`](TEMPLATE.md) for the Discovery → Distribution strategy.

---

# Identify the Target Project (Read First)

Before reading any spec or starting implementation:

1. **Identify the target project** — walk up from the working location to the nearest ancestor with a `PROJECT.md` (typically `projects/<name>/PROJECT.md`).
2. **If ambiguous** (multiple projects touched in one request, or no project mentioned) — **ask the user**. Do not guess.
3. **If no `PROJECT.md` is locatable** — STOP and report (request is outside any defined project).

Path conventions in this document:

- **Repo-root-relative** (start with `platform/`, `rules/`, `.claude/`, `libs/`, `tasks/`, `docs/guides/`) — unambiguous.
- **Project-relative** (`PROJECT.md`, `apps/`, `specs/`, `tasks/ready/`, etc.) — **inside the target project directory**. Prefix with the resolved project path when interpreting.

---

# Project Classification

After the target project is identified, resolve rule layers:

1. Read `PROJECT.md` → obtain `domain` and `traits`.
2. Missing or unparseable frontmatter → **STOP**.
3. Verify each declared `domain`/`trait` against [`rules/taxonomy.md`](rules/taxonomy.md) (authoritative narrative) and the dispatch catalogs at [`.claude/config/`](.claude/config/) (`activation-rules.md` + `domains.md` + `traits.md`).
4. Undeclared/unknown tags → **Hard Stop**.
5. Load detail files per [`rules/README.md`](rules/README.md) resolution order: `rules/common.md` → `rules/domains/<domain>.md` → `rules/traits/<trait>.md` (each declared trait).
6. Missing trait/domain file = "no additional constraint beyond common" (on-demand policy in `rules/README.md`). Do not auto-generate stubs.

Agents/skills split: `common/` (always) vs `domain/<domain>/` (matched domain only). See [`.claude/agents/domain/README.md`](.claude/agents/domain/README.md) and [`.claude/skills/domain/README.md`](.claude/skills/domain/README.md).

---

# Core Principles

- Specifications are the source of truth.
- Work must be executed through tasks.
- Only tasks in the target project's `tasks/ready/` may be implemented.
- Follow the standard workflow: plan → implement → test → review.
- If specifications are missing, unclear, or conflicting, **stop and report**.

---

# Source of Truth Priority

When documents conflict, higher number = lower priority:

1. `<project>/PROJECT.md` (project classification — domain, traits)
2. `rules/common.md` and the canonical files it indexes (shared)
3. `rules/domains/<declared-domain>.md` (if present)
4. `rules/traits/<declared-trait>.md` for each trait (if present)
5. `platform/` remaining files (incl. `entrypoint.md`, auxiliary specs; within `platform/service-types/`, only the file matching the target service's declared `Service Type` is read — other service-type files skipped)
6. `<project>/specs/contracts/`
7. `<project>/specs/services/`
8. `<project>/specs/features/`
9. `<project>/specs/use-cases/`
10. `<project>/tasks/ready/`
11. `.claude/skills/` (shared)
12. `<project>/knowledge/`
13. `<project>/docs/` (excluding root `docs/guides/`)
14. existing code

> `docs/guides/` at repo root is **human reference only** — AI agents must NOT read it as a source of truth.

If a source is empty or absent, skip to the next. Layers 2–4 conflict resolution: common wins unless a domain/trait file contains an explicit `## Overrides` block referencing the specific common rule being relaxed. Otherwise → **Hard Stop**.

---

# Task Rules

- Do not implement work without a task.
- **Project-internal work** (changes inside a single `projects/<name>/`) → task in that project's `tasks/ready/`, follow `projects/<name>/tasks/INDEX.md`.
- **Monorepo-level work** (shared paths: `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/templates/`, `docs/guides/`, root `build.gradle`/`settings.gradle`/`.github/workflows/`/`scripts/`/`package.json`, `CLAUDE.md`, `TEMPLATE.md`, or cross-project structural changes) → task in repo-root `tasks/ready/`, follow [`tasks/INDEX.md`](tasks/INDEX.md) § "When to Use Root vs Project Tasks".
- Specs win over tasks. If implementation requires spec or contract changes, update them first.
- Tasks must contain all required sections: **Goal / Scope / Acceptance Criteria / Related Specs / Related Contracts / Edge Cases / Failure Scenarios**.
- **Objective merge verification before any close chore** — a "merged it" statement is not proof. Verify **three dimensions** before moving `review/ → done/`: (a) `gh pr view <n> --json state,mergedAt,mergeCommit,statusCheckRollup` returns `state=MERGED`; (b) `git log origin/main` tip matches the squash commit; (c) the impl PR's pre-merge `gh pr checks <n>` snapshot had **0 failing required checks** (CI-RED-at-merge time creates a main regression — `statusCheckRollup` of the merged PR is the authoritative record). If any of the three fails: STOP. CI-RED-at-merge requires a separate fix-task that restores main GREEN before close chore. (TASK-PC-BE-002 회귀 회복 saga 2026-05-20 — PR #672 squash-merged with `console-bff IT: fail 55s` → main 4 회 연속 RED → 0순위 promotion 으로 회복.)
- **`git mv review/ → done/` re-stage check** — `git mv` stages the *review*-state blob; after editing the task's Status `review → done` you MUST `git add <done-path>` again and verify with `git show :<done-path>` that the staged blob reads `done`. (Skipping this lands a `Status: review` file under `done/`.)

Lifecycle and review rules: `tasks/INDEX.md` (root) and each `projects/<name>/tasks/INDEX.md`.

---

# Required Workflow

1. Read this file.
2. Decide project-internal vs monorepo-level (`tasks/INDEX.md` § decision table).

**Project-internal**:

3. Identify the target project (above).
4. Read `PROJECT.md` and load rule layers per its `domain`/`traits`.
5. Read the target task in `<project>/tasks/ready/`. Before committing, grep its spec body and the project's `tasks/INDEX.md` for dependency markers (`선행`, `후속`, `depends on`, `blocked by`, `prerequisite`, `전제`) — read referenced tasks too.
6. Follow [`platform/entrypoint.md`](platform/entrypoint.md) for spec reading order.
7. Determine target service's `Service Type` from `specs/services/<service>/architecture.md` → read the matching `platform/service-types/<type>.md` (exactly one file).
8. Consult [`.claude/skills/INDEX.md`](.claude/skills/INDEX.md) for skill guidance.
9. `<project>/knowledge/` for design judgment only.
10. Read existing code patterns.
11. Implement + test.
12. Prepare for review.

**Monorepo-level**: read the root-`tasks/ready/` task and its dependency markers (same grep rule as project step 5), read the targeted shared file(s), enumerate any `projects/<name>/` impact, implement, verify (typically `./gradlew check` for build changes, dry-run for scripts, doc lint for docs).

---

# Hard Stop Rules

Stop immediately if any of the conditions below holds. **Every Hard Stop emission MUST follow the 4-block format defined in [`platform/lint-remediation-message-standard.md`](platform/lint-remediation-message-standard.md)** — prose stops are not acceptable. Do not attempt workaround implementation.

Canonical 4-block body for each rule lives in [`platform/hardstop-rules.md`](platform/hardstop-rules.md). The catalog below names each trigger; click through for the full stanza an agent must emit.

| ID | Condition | Reference |
|---|---|---|
| HARDSTOP-01 | No `PROJECT.md` locatable | [hardstop-rules.md#hardstop-01](platform/hardstop-rules.md#hardstop-01--no-projectmd-locatable) |
| HARDSTOP-02 | `PROJECT.md` missing/unparseable or unknown domain/trait | [hardstop-rules.md#hardstop-02](platform/hardstop-rules.md#hardstop-02--projectmd-missingunparseable-or-unknown-domaintrait) |
| HARDSTOP-03 | Shared library file contains project-specific content | [hardstop-rules.md#hardstop-03](platform/hardstop-rules.md#hardstop-03--shared-library-file-contains-project-specific-content) |
| HARDSTOP-04 | Domain/trait rule conflicts with common without `## Overrides` | [hardstop-rules.md#hardstop-04](platform/hardstop-rules.md#hardstop-04--domaintrait-rule-conflicts-with-common-without--overrides) |
| HARDSTOP-05 | Task is not in the appropriate `tasks/ready/` | [hardstop-rules.md#hardstop-05](platform/hardstop-rules.md#hardstop-05--task-is-not-in-the-appropriate-tasksready) |
| HARDSTOP-06 | Required specifications missing or in conflict | [hardstop-rules.md#hardstop-06](platform/hardstop-rules.md#hardstop-06--required-specifications-missing-or-in-conflict) |
| HARDSTOP-07 | Acceptance criteria unclear | [hardstop-rules.md#hardstop-07](platform/hardstop-rules.md#hardstop-07--acceptance-criteria-unclear) |
| HARDSTOP-08 | Required contracts missing | [hardstop-rules.md#hardstop-08](platform/hardstop-rules.md#hardstop-08--required-contracts-missing) |
| HARDSTOP-09 | Task requires architecture decision not in specs | [hardstop-rules.md#hardstop-09](platform/hardstop-rules.md#hardstop-09--task-requires-architecture-decision-not-in-specs) |
| HARDSTOP-10 | Service Type undeclared or unknown | [hardstop-rules.md#hardstop-10](platform/hardstop-rules.md#hardstop-10--service-type-undeclared-or-unknown) |

The hook [`.claude/hooks/hardstop-detect.ps1`](.claude/hooks/hardstop-detect.ps1) auto-emits the catalog's mechanically-detectable triggers (HARDSTOP-01/03/05/09/10) with the platform body's verbatim stanza. The remaining triggers (HARDSTOP-02/04/06/07/08) require agent or human judgement.

---

# Layer Rules

- **Architecture**: [`platform/architecture-decision-rule.md`](platform/architecture-decision-rule.md). Each service follows the architecture declared in its `<project>/specs/services/<service>/architecture.md`.
- **Shared Library**: [`platform/shared-library-policy.md`](platform/shared-library-policy.md). No project-specific content in `libs/` — Hard-Stop-enforced.
- **Contracts**: API and event changes must update `<project>/specs/contracts/` **before** implementation.
- **Testing**: [`platform/testing-strategy.md`](platform/testing-strategy.md) + the target service's `architecture.md` test-requirement section.

---

# Cross-Project Changes

A change affecting multiple projects (e.g. shared library rule refactor that ripples into every project) must land in **one atomic PR**:

1. The library change (under shared paths).
2. The adaptation in every affected project (under `projects/<name>/`).

Atomic cross-project commits are the primary monorepo advantage. Staggered PRs create transiently broken main — avoid.

Conventional Commit scopes (used by reviewers + release-please):

- `feat(lib):`, `refactor(lib):`, `fix(lib):` — shared library changes
- `feat(rules):`, `feat(rules-<domain>):` — rule library changes
- `feat(<project>):` — project-specific changes (e.g. `feat(wms):`)
- `!` suffix or `BREAKING CHANGE:` footer for breaking changes

Full convention + branching + PR shape: [`docs/guides/monorepo-workflow.md`](docs/guides/monorepo-workflow.md).

**Branch name constraint** — never include the substring `master` in branch names. The sandbox `--force` regex matches `master` as a substring and blocks `git push` even on feature branches.

- Rename around the noun: `task/be-161-database-design-...` (not `...-master-service-...`).
- Or use the abbreviation: `ms-`, `mst-`.
- Workaround if you hit it: `git push -u origin HEAD` (renaming the branch is cleaner).
- Encountered repeatedly across BE-052, BE-161.

**Concurrent-session worktree isolation** — multiple interactive agent sessions (or routines) running at the same time must never share the main checkout. Each concurrent task gets its own `git worktree add` directory; the main `monorepo-lab` checkout stays parked on a stable branch (ideally `main`) and is not used for task work.

- A `git checkout` / `checkout -b` in a directory another live session is using moves the **single shared HEAD + index** of that directory — the other session's uncommitted WIP gets stranded on the wrong branch, and its next `git commit` lands on *your* branch. ("branch per task" alone is insufficient; the isolation unit is the **worktree (directory)**, not the branch.)
- The `protect-main-branch.ps1` hook only blocks commits/pushes **on `main`** — it does NOT catch two sessions sharing the main checkout across two *feature* branches. There is no automated guard for this case; it is a discipline rule. (`docs/guides/monorepo-workflow.md` documents the worktree convention, but that guide is human-reference-only and assumes the harness-managed `.claude/worktrees/agent-<id>/` dispatch model, not manual multi-session worktrees.)
- Symptom: `git worktree list` shows the main checkout on an unexpected feature branch, or `git status` surfaces files from a task you are not working on. Recovery: commit only your own files by **explicit path** (leave the other session's WIP untouched), then `git checkout <their-branch>` to restore the shared HEAD — uncommitted WIP travels along and re-lands on the correct branch (0 path overlap = no conflict). Afterward move your work to its own `git worktree add` directory.
- Worktree-add Windows pitfalls (DWIM remote-branch resolution, failed-remove, prune side effects): project memory `env_git_worktree_verify_windows`. Worked incident + reflog-timestamp forensics: project memory `env_concurrent_git_branch_switch_hazard`.

**Post-merge branch hygiene** — the repo squash-merges PRs; feature/chore refs are not auto-pruned and accumulate.

- After a PR squash-merges, delete its feature + close-chore refs immediately. Stacked work uses a single tip-only PR (the tip contains its base; the base ref becomes squash-residue → delete it too).
- A ref is squash-merge-stale (safe to delete) when its task is in `origin/main`'s `tasks/done/` (or its squash commit is in `git log origin/main`).
- The auto-mode classifier blocks mass `git push origin --delete` even with a matching permission-allowlist entry (it is a higher safety layer) — mass remote-ref deletion must be run in the user's own shell; `gh pr create` / `gh pr merge --squash` pass; local `git branch -D` is fine for the agent. On a classifier block: STOP and hand the user the exact command — do not reformulate to bypass.
- **`git branch -r` is a stale local cache, not `origin` truth.** Before concluding remote-branch state or recommending a mass remote deletion, run `git fetch --prune` (or `git remote prune origin`). `git fetch origin main` updates only `main` and does NOT prune — so refs already deleted on `origin` (P3 applied) linger locally as dozens of stale tracking refs that falsely read as "needs cleanup". Prune first, confirm the real residue, then hand over only what genuinely remains (often nothing — avoid an unnecessary user `push --delete`).
- **Stacked-PR base-ref-deletion auto-close hazard.** Deleting a PR's base ref auto-closes that PR on GitHub, and `gh pr reopen` is then rejected — so `gh pr merge <base> --squash --delete-branch` is destructive-in-disguise for any child PR stacked on it. Prevention: retarget the child first (`gh pr edit <child> --base main`), or merge the base without `--delete-branch`. Recovery: `git rebase --onto origin/main <base-squash-sha>` the child, `--force-with-lease`, open a fresh PR.

**`.claude/` self-modification is classifier-blocked** — the auto-mode classifier hard-blocks editing or committing files under `.claude/hooks/`, `.claude/agents/`, `.claude/commands/` even with explicit user approval (the same higher-safety layer as mass `push --delete`). Hand the exact patch to the user to apply + commit; do not attempt a shell-write bypass. `platform/` is NOT subject to this — only `.claude/`. Detail: project memory `env_classifier_claude_self_mod_block`.

**CI path-filter constraint** — when editing `.github/workflows/` `dorny/paths-filter` configuration: never use negation patterns (the `predicate-quantifier: 'some'` negation misclassifies a file as "in"); use a pure-positive `code-changed` filter composed with the original via an outputs-layer AND; backfill new code extensions into the positive filter; add an entry per new project.

Worked examples + procedure: project memories `project_branch_hygiene_policy` (branch hygiene + stacked-PR hazard + prune-before-conclude), `project_ci_path_filter_074_075_quirk` (CI path-filter), and `env_classifier_claude_self_mod_block` (classifier `.claude/` block).

---

# Local Network Convention

Hostname-based routing via a single shared Traefik (`*.local` → `127.0.0.1`). Each project's gateway registers its hostname via docker-compose labels; backing services (postgres, redis, kafka, …) use `expose:` only (no host ports). Legacy `PORT_PREFIX` is fully retired (TASK-MONO-024). New projects must not reintroduce it.

**Full specification (master)**: [`TEMPLATE.md § Local Network Convention`](TEMPLATE.md) — target pattern, hostname allocation, DB tool access (DBeaver / Redis Insight / Kafka UI).
**Rationale**: [`ADR-MONO-001`](docs/adr/ADR-MONO-001-port-prefix-scaling.md).

---

# Recommending Tasks and Dispatching Agents

When recommending a task or implementation path, annotate with both the **analysis model** (current session) and the **recommended implementation model** based on task complexity: `(분석=<model> / 구현 권장=<model>)`. Example: `진행 권장 (분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — 단순 fix)`.

Recommended model by task type:

- **Complex domain work** — state machines, transaction design, event-driven outbox, cross-cutting refactors, contract design: **Opus**.
- **CI / docs / single-line config / lifecycle chore**: **Sonnet** or **Haiku** sufficient.

When dispatching via the Agent tool, **always pass `model=` explicitly** — do not rely on session inheritance. The current session's model is irrelevant to the dispatched work's optimum.

```
Agent(subagent_type="backend-engineer", model="opus", ...)   # complex
Agent(subagent_type="backend-engineer", model="sonnet", ...) # routine fix
```

This rule persists across session compaction and new sessions; the model annotation must precede every implementation recommendation.

Before recommending the next task:

1. **`git fetch origin main`** and check divergence (`git log HEAD..origin/main --oneline`) — origin may carry recently-merged closures the local tree hasn't picked up; recommending against stale local state duplicates already-closed work.
2. Scan **both** `ready/` queue (new candidates) and `review/` queue (open impl PRs awaiting fix, or merged PRs awaiting `review/ → done/` chore).
3. Surface review-side work to clear first — avoid open-PR pile-up.
4. Apply to root `tasks/` and each affected `projects/<name>/tasks/`.
