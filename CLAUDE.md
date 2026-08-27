# CLAUDE.md

Minimum operating rules for AI agents and developers in this monorepo. **Catalog + safety net** — full detail lives in the canonical files linked below.

---

## Repository Layout

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
└── projects/<project>/    ← one directory per project (8 active)
    ├── PROJECT.md         ← project classification (domain, traits)
    ├── apps/ specs/ tasks/ knowledge/ docs/ infra/
    └── libs/              ← optional: project-scoped shared modules (see below)
```

**Shared vs project boundary** (strict, Hard-Stop-enforced):

- **Shared (repo root)**: `platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `tasks/INDEX.md` + monorepo-level `tasks/{ready,…}/`, `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md` — **must remain project-agnostic** (no service names, API paths, domain entities).
- **Project-specific (`projects/<name>/`)**: `PROJECT.md`, `apps/`, `specs/`, project `tasks/`, `knowledge/`, `docs/` (except `guides/`), `infra/`, and — when two or more of that project's services share a concept — `libs/`.

**Project-scoped shared modules (`projects/<name>/libs/<module>/`)** — for code shared by **several services of one project** but owned by that project's domain:

- **When**: two or more services in the same project declare the same domain concept, and that concept carries a **product decision of that project** (a supported-value whitelist, a domain policy) rather than a purely technical mechanism. Repo-root `libs/` is for the technical mechanisms; it must stay project-agnostic, so a project's product decision cannot live there. Keeping the copies per-service instead is the failure mode this exists to avoid — a change lands in one copy and silently not the sibling.
- **Gradle**: `include 'projects:<project>:libs:<module>'` in the root `settings.gradle`, alongside that project's `apps:*` entries. Consumers declare it with `implementation`, never `api`. The module must not depend on any service module (one-way, per [`platform/shared-library-policy.md`](platform/shared-library-policy.md) § Dependency Rule).
- **Constraint**: it is still project-specific content — it lives under `projects/<name>/` (so it travels with the project on template extraction) and is **not** subject to the project-agnostic rule that binds repo-root `libs/`. The reverse also holds: it must not be promoted to repo-root `libs/` merely because a second project wants it; that promotion is an ADR decision.
- **Introducing one requires an ADR** in the owning project's `docs/adr/`, because it is an architecture decision about ownership (`platform/architecture-decision-rule.md`), and the CI/guard surface must be checked — most repo guards key on `projects/*/apps/*` and will not see a `libs/` module, including the gradle-task list of the project's CI job (a module reached only as a compile dependency never runs its own tests).

See [`TEMPLATE.md`](TEMPLATE.md) for the Discovery → Distribution strategy.

---

## Identify the Target Project (Read First)

Before reading any spec or starting implementation:

1. **Identify the target project** — walk up from the working location to the nearest ancestor with a `PROJECT.md` (typically `projects/<name>/PROJECT.md`).
2. **If ambiguous** (multiple projects touched in one request, or no project mentioned) — **ask the user**. Do not guess.
3. **If no `PROJECT.md` is locatable** — STOP and report (request is outside any defined project).

Path conventions in this document:

- **Repo-root-relative** (start with `platform/`, `rules/`, `.claude/`, `libs/`, `tasks/`, `docs/guides/`) — unambiguous.
- **Project-relative** (`PROJECT.md`, `apps/`, `specs/`, `tasks/ready/`, etc.) — **inside the target project directory**. Prefix with the resolved project path when interpreting.

---

## Project Classification

After the target project is identified, resolve rule layers:

1. Read `PROJECT.md` → obtain `domain` and `traits`.
2. Missing or unparseable frontmatter → **STOP**.
3. Verify each declared `domain`/`trait` against [`rules/taxonomy.md`](rules/taxonomy.md) (authoritative narrative) and the dispatch catalogs at [`.claude/config/`](.claude/config/) (`activation-rules.md` + `domains.md` + `traits.md`).
4. Undeclared/unknown tags → **Hard Stop**.
5. Load detail files per [`rules/README.md`](rules/README.md) resolution order: `rules/common.md` → `rules/domains/<domain>.md` → `rules/traits/<trait>.md` (each declared trait).
6. Missing trait/domain file = "no additional constraint beyond common" (on-demand policy in `rules/README.md`). Do not auto-generate stubs.

Agents/skills split: `common/` (always) vs `domain/<domain>/` (matched domain only). See [`.claude/agents/domain/README.md`](.claude/agents/domain/README.md) and [`.claude/skills/domain/README.md`](.claude/skills/domain/README.md).

---

## Core Principles

- Specifications are the source of truth.
- Work must be executed through tasks.
- Implementation is picked up only from the target project's `tasks/ready/`. The task file then moves to `in-progress/` and is the working document there until it moves to `review/`; `review/` and `done/` are the frozen stages.
- Follow the standard workflow: plan → implement → test → review.
- If specifications are missing, unclear, or conflicting, **stop and report**.

---

## Source of Truth Priority

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

## Task Rules

- Do not implement work without a task.
- **Project-internal work** (changes inside a single `projects/<name>/`) → task in that project's `tasks/ready/`, follow `projects/<name>/tasks/INDEX.md`.
- **Monorepo-level work** (shared paths: `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/templates/`, `docs/guides/`, root `build.gradle`/`settings.gradle`/`.github/workflows/`/`scripts/`/`package.json`, `CLAUDE.md`, `TEMPLATE.md`, or cross-project structural changes) → task in repo-root `tasks/ready/`, follow [`tasks/INDEX.md`](tasks/INDEX.md) § "When to Use Root vs Project Tasks".
  - **Exception**: when an ACCEPTED ADR (or its per-project execution slice) has already explicitly authorized touching specific shared paths as part of one project's scope, filing the task under that project's `tasks/ready/` instead of root is an acceptable deviation — verify by reading the ADR's actual scope section and reviewing the shared-path diff for HARDSTOP-03 compliance (project-agnostic wording), not by reflexively re-filing to root.
- Specs win over tasks. If implementation requires spec or contract changes, update them first.
- Tasks must contain all required sections: **Goal / Scope / Acceptance Criteria / Related Specs / Related Contracts / Edge Cases / Failure Scenarios**.
- **Agent-delegated plan-exact implementation** — when a single agent implements an approved plan specifying exact field/interface names (schema, API contract, function signatures), diff the actual changed files (`gh pr diff`/`git diff`) against the plan's literal spec before merging or relaying "done" — the agent's own completion report may only disclose the deviations it chose to flag.
- **Objective merge verification before any close chore** — a "merged it" statement is not proof. Verify **three dimensions** before moving `review/ → done/`: (a) `gh pr view <n> --json state,mergedAt,mergeCommit,statusCheckRollup` returns `state=MERGED`; (b) `git log origin/main` tip matches the squash commit; (c) the impl PR's pre-merge `gh pr checks <n>` snapshot had **0 failing required checks** (CI-RED-at-merge time creates a main regression — `statusCheckRollup` of the merged PR is the authoritative record). If any of the three fails: STOP. CI-RED-at-merge requires a separate fix-task that restores main GREEN before close chore. (Worked incident behind this rule: [`platform/git-workflow-policy.md` § Merge-Verification Worked Incident](platform/git-workflow-policy.md#merge-verification-worked-incident).) When polling CI programmatically, never use the **exit code** of `gh pr checks <n>` to decide GREEN — a still-pending check makes it exit non-zero, so an exit-code gate misreads "pending" as "failed"; parse its text / `--json` output (or `gh pr view --json statusCheckRollup`) and check each check's state explicitly.
- **`git mv review/ → done/` re-stage check** — `git mv` stages the *review*-state blob; after editing the task's Status `review → done` you MUST `git add <done-path>` again and verify with `git show :<done-path>` that the staged blob reads `done`. (Skipping this lands a `Status: review` file under `done/`.)

Lifecycle and review rules: `tasks/INDEX.md` (root) and each `projects/<name>/tasks/INDEX.md`.

---

## Required Workflow

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

## Hard Stop Rules

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

## Layer Rules

- **Architecture**: [`platform/architecture-decision-rule.md`](platform/architecture-decision-rule.md). Each service follows the architecture declared in its `<project>/specs/services/<service>/architecture.md`.
- **Shared Library**: [`platform/shared-library-policy.md`](platform/shared-library-policy.md). No project-specific content in `libs/` — Hard-Stop-enforced.
- **Contracts**: API and event changes must update `<project>/specs/contracts/` **before** implementation.
- **Testing**: [`platform/testing-strategy.md`](platform/testing-strategy.md) + the target service's `architecture.md` test-requirement section.

---

## Cross-Project Changes

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

**Git / branch / worktree discipline** — full procedures + worked incidents in [`platform/git-workflow-policy.md`](platform/git-workflow-policy.md). Catalog (safety-net one-liners):

- **Branch names**: never the substring `master` (the sandbox `--force` regex matches it and blocks the push even on feature branches); rename around the noun or use `ms-`.
- **Concurrent-session isolation**: each concurrent task gets its own `git worktree add` directory; the main checkout stays parked on `main`. The isolation unit is the **directory**, not the branch — a `checkout` in a shared dir strands the other session's WIP on your branch. Dispatch subagents with **absolute** worktree paths (a relative path lands edits in the parked main checkout).
- **Post-merge hygiene**: delete feature + close-chore refs once squash-merge-stale (task in `origin/main` `tasks/done/`). Attempt a mass `push --delete` first when every ref is confirmed stale; on an **actual** classifier block, hand the user the exact command — never reformulate to bypass. `git fetch --prune` before concluding remote state. Deleting a stacked PR's base ref auto-closes the child — retarget first.
- **Classifier gating: attempt once, hand off only on an actual block.** Never pre-emptively hand off because a table says a path is blocked — the classifier is external, changes silently, and has been observed to change *within one session*. A wasted attempt costs one round-trip; a wrong assumption costs a needless hand-off and files a false completion note. **The axis is the edit, not the path**: edits that *change what is permitted* (role catalogs, permission matrices, authorization constants) get gated **even outside `.claude/`**, while prose/comment edits to the same file pass. On a real block, hand the exact patch to the user; never shell-write around it. Observation log (dates + which task observed each path, including `.claude/settings.json` currently **unverified**) and the separate destructive axis (self-merge, `--force-with-lease`, `docker volume rm`): [`platform/git-workflow-policy.md` § Agent Self-Modification](platform/git-workflow-policy.md).
- **CI path-filter**: never negation patterns in `dorny/paths-filter`; use a pure-positive `code-changed` filter AND-composed at the outputs layer; add an entry per new project.
- **Shared-file task series**: a series of tasks that each edit the same shared file (nav config, barrel `index.ts`, shared `types.ts`) should reuse one worktree and merge serially, not run in parallel worktrees — parallelizing guarantees a same-file merge conflict at the end of the series.
- **Self-merge / force-push need explicit authorization**: a general "proceed to completion" instruction does not authorize merging your own just-authored PR or `git push --force-with-lease` — get explicit confirmation for each, or push to a new ref instead of forcing.
- **Local-only override markers**: before staging a broad session diff, grep new/modified files for a self-declared "LOCAL DEMO ONLY / DO NOT COMMIT" header marker and confirm include/exclude with the user explicitly — don't assume either way. Full detail: `platform/git-workflow-policy.md` § Local-Only Override File Markers.
- **`Agent(isolation:"worktree")` scaffold branch**: after the dispatched agent's task-branch PR merges, `git worktree remove` alone leaves both the task branch and the harness's own `worktree-agent-<id>` branch as local refs — delete both explicitly with `git branch -D` once confirmed merge-stale.
- **Post-merge nightly check**: the console and web-store full-stack e2e suites run only in `nightly-e2e.yml`, not `ci.yml` — a route/nav/testid/heading change can merge green having never been exercised. Grep the e2e spec dirs before merging such a change, and check the next nightly run on `main` once after.

---

## Local Network Convention

Hostname-based routing via a single shared Traefik (`*.local` → `127.0.0.1`). Each project's gateway registers its hostname via docker-compose labels; backing services (postgres, redis, kafka, …) use `expose:` only (no host ports). Legacy `PORT_PREFIX` is fully retired (TASK-MONO-024). New projects must not reintroduce it.

**Full specification (master)**: [`TEMPLATE.md § Local Network Convention`](TEMPLATE.md) — target pattern, hostname allocation, DB tool access (DBeaver / Redis Insight / Kafka UI).
**Rationale**: [`ADR-MONO-001`](docs/adr/ADR-MONO-001-port-prefix-scaling.md).

---

## Recommending Tasks and Dispatching Agents

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
