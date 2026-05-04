# Monorepo Workflow Guide

Practical reference for day-to-day development in this monorepo. This guide covers the mechanics — branch naming, task lifecycle, agent dispatch, CI, hooks, and known conflict patterns.

**Source-of-truth hierarchy** (authoritative, do not contradict):

| Topic | Master document |
|---|---|
| AI agent rules, hard stops, project classification | `CLAUDE.md` |
| New project bootstrap, template extraction, hostname routing | `TEMPLATE.md` |
| Monorepo task lifecycle, PR Separation Rule | `tasks/INDEX.md` |
| Project domain / trait classification | `projects/<name>/PROJECT.md` |

This guide is a developer quick-reference. When this document conflicts with a master document, the master document wins.

---

## 1. Overview — Daily Dev Workflow

The core loop:

```
spec / contract  →  task (ready/)  →  impl (branch + PR)
     ↑                                           ↓
     └──── review (APPROVE | FIX → new ticket) ← task (review/)
```

Every change follows this loop — features, fixes, and chores alike. No code lands without a task. No task moves to `done/` without a review pass.

Standard day-to-day steps:

1. Check `tasks/ready/` for the next implementable task.
2. Create a branch (see §2) — always in a worktree, never directly on `main`.
3. Implement and push.
4. Open a PR, move the task file to `review/`, update `tasks/INDEX.md`.
5. After review approval, merge; a chore PR moves the task file to `done/`.

See `CLAUDE.md § Required Workflow` for the full mandatory pre-implementation reading order.

---

## 2. Branch Patterns

| Prefix | When to use | Example |
|---|---|---|
| `feature/` | New functionality | `feature/TASK-BE-042-order-outbox` |
| `chore/` | Lifecycle moves, config, CI, docs | `chore/TASK-MONO-038-monorepo-workflow-guide` |
| `spec/` | Spec-only or contract-only PR (no implementation) | `spec/TASK-BE-045-payment-contract` |
| `fix/` | Bug fix referencing the originating task | `fix/TASK-BE-044-fix-TASK-BE-040` |

Rules:

- Branch name **must** include the task ID.
- Branch off `main` only. Never branch off `in-progress` or `review` branches.
- AI agents always operate inside a **git worktree** (`.claude/worktrees/agent-<id>/`). The main checkout remains clean. See §4 for agent dispatch.
- Direct commits to `main` are blocked by `protect-main-branch.ps1` (see §7).

---

## 3. Task Lifecycle

Full lifecycle definition: `tasks/INDEX.md § Lifecycle` and `tasks/INDEX.md § PR Separation Rule`.

```
(writing) → ready  →  in-progress  →  review  →  done
```

### PR Separation Rule (summary)

Each lifecycle transition lands in its own PR — **never bundle spec authoring with implementation**:

| Stage | PR shape | Content |
|---|---|---|
| `(writing) → ready` | **spec PR** | Task file added to `ready/` + `INDEX.md` ready list updated. No implementation. |
| `ready → in-progress → review` | **impl PR** | Task file moves through `in-progress/` → `review/`; implementation commits. Lifecycle moves + impl live in one PR, as separate commits. |
| `review → done` | **chore PR** | Merged task file(s) move from `review/` to `done/`; `INDEX.md` done list updated with outcome. Multiple tasks may be batched. |

**Why**: when spec + impl ship together, the `ready/` queue never reflects the task — other developers and AI sessions cannot see it. The queue is a signal; don't break it.

### Fix tasks

If review finds a critical issue:
1. Create a **new fix task** in `ready/` referencing the original (`"Fix issue found in TASK-MONO-XXX"`).
2. Move the **original task** to `done/` with review verdict recorded in `INDEX.md`.
3. Never edit a task file after it moves to `review/` or `done/`.

### Monorepo-level vs project-level tasks

- Changes to `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/templates/`, `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md`, root build files, or cross-project structural changes → root `tasks/ready/` (this directory).
- Changes inside a single `projects/<name>/` → that project's `projects/<name>/tasks/ready/`.

Decision table: `tasks/INDEX.md § When to Use Root vs Project Tasks`.

---

## 4. Agent Dispatch

### Worktree isolation

Each AI agent subagent runs inside a dedicated git worktree under `.claude/worktrees/agent-<id>/`. This keeps the main checkout clean and allows parallel tasks without branch conflicts.

```bash
# Worktrees are managed automatically by the coordinator agent.
# Manually inspect active worktrees:
git worktree list
```

### Model selection

Annotate every task recommendation with analysis model and recommended implementation model:

```
진행 권장 (분석=Sonnet 4.6 / 구현 권장=Opus 4.7 — 복잡한 도메인 설계)
진행 권장 (분석=Sonnet 4.6 / 구현 권장=Sonnet 4.6 — 단순 docs/config)
```

| Task type | Recommended model |
|---|---|
| State machines, outbox design, cross-cutting refactors, contract design | Opus |
| CI, docs, single-line config, lifecycle chore PRs | Sonnet or Haiku |

When dispatching via the `Agent` tool, pass `model` explicitly — never rely on session inheritance.

### Agent roles

Agents are split into `common/` (always available) and `domain/<domain>/` (loaded only when project domain matches). See `.claude/agents/common/` and `.claude/agents/domain/` for role definitions.

Key agents:

| Agent | File | When used |
|---|---|---|
| `backend-engineer` | `common/backend-engineer.md` | Spring Boot implementation |
| `frontend-engineer` | `common/frontend-engineer.md` | Next.js / pnpm workspace |
| `code-reviewer` | `common/code-reviewer.md` | Review pass after impl |
| `coordinator` | `common/coordinator.md` | `/process-tasks` pipeline orchestration |

Full catalog: see agent files under `.claude/agents/common/` (one `.md` per role).

### Before dispatching

Before recommending the next task, scan **both** the `ready/` queue (new candidates) and the `review/` queue (open PRs awaiting fix or chore close). Surface review-side work first to avoid open-PR pile-up.

---

## 5. sync-portfolio.sh — Standalone Extraction

`scripts/sync-portfolio.sh` extracts each project into its own GitHub repository (full history, `git-filter-repo` based). Projects in the standalone repos are suitable for portfolio submission.

### Basic usage

```bash
# Sync all configured projects
./scripts/sync-portfolio.sh

# Sync a single project
./scripts/sync-portfolio.sh wms-platform

# Dry run — show what would happen without pushing
./scripts/sync-portfolio.sh --dry-run
./scripts/sync-portfolio.sh wms-platform --dry-run
```

### How it works

```
monorepo main → filter-repo (keep SHARED_PATHS + projects/<name>/) → hoist to root → post-process → force-push standalone repo
```

Shared paths kept at extracted repo root: `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/templates/`.

Project-level `tasks/` lifecycle (ready/in-progress/review/done) is **excluded** from the extracted repo — it is a development artefact, not part of the deliverable.

### Registering a new project

In `scripts/sync-portfolio.sh`:

```bash
PROJECT_REMOTES["<new-project>"]="https://github.com/<owner>/<new-project>.git"
PROJECT_TYPES["<new-project>"]="direct-include"   # or composite-build
```

Full policy detail: `TEMPLATE.md § Standalone Portfolio Sync and Freeze Policy`.

### Freeze policy

Once a project reaches its `v1` milestone, `PROJECT_EXCLUDE_PATHS` in the script gates which pending changes propagate to the standalone repo. Changes under excluded paths do not appear in the standalone until explicitly removed from the exclude list. See TASK-MONO-028 done entry in `tasks/INDEX.md` for a concrete example.

---

## 6. CI Job Areas

CI is defined in `.github/workflows/ci.yml`. Jobs are loosely ordered by dependency:

| Job | Trigger | What it covers |
|---|---|---|
| `build-and-test` | push / PR | `./gradlew check` for all backend modules (libs + all project services). Excludes `@Tag("integration")` tests. |
| `boot-jars` | push / PR | `:bootJar` verify-only for all services — catches Spring Boot autoconfig issues. |
| `frontend-checks` | push / PR | `pnpm turbo lint && turbo build` across all frontend packages. |
| `frontend-unit-tests` | push / PR | `pnpm turbo test` across all frontend packages. |
| `frontend-e2e-smoke` | push / PR | Playwright smoke specs (home / login / auth-guard) — no backend stack required. |
| `frontend-e2e` | push / PR (gated) | Full Playwright suite with Docker-compose backend stack. Gated to `kanggle/monorepo-lab`. |
| `integration` (per project) | push / PR | `@Tag("integration")` Testcontainers tests — requires Docker-in-Docker runner. |
| `e2e-tests` | push / PR (wms) | WMS E2E suite using pre-built Docker images. |

### Pre-existing fail areas

Some test families are intentionally skipped in CI due to environment constraints (Docker unavailable on runner):

- WMS / GAP integration tests that require Docker on Windows CI runners.
- Full-stack Playwright `frontend-e2e` is gated to the canonical repo only.

When adding a new backend service, add its `:check` task to the `build-and-test` step in `ci.yml`. Pattern: `./gradlew :projects:<name>:apps:<service>:check`.

---

## 7. Hook Bypass Rules

Two hooks enforce repository hygiene. They activate on Claude Code tool use (PreToolUse).

### `protect-main-branch.ps1`

Blocks:

- `git push ... main` / `git push ... master`
- `git push --force` / `git push -f`
- `git reset --hard origin/main`

**Bypass condition**: the current working directory matches `portfolio-sync` OR the command contains `portfolio-sync`. This allows `scripts/sync-portfolio.sh` to force-push to standalone repos (intentional — those repos are derived artefacts, not protected branches).

There is **no other legitimate bypass**. If you feel blocked by this hook, you are likely on the wrong branch. Create a PR branch instead.

### `rule-consistency-check.ps1`

Runs on every `Edit` / `Write` operation. Checks that:

- Any new `domain` or `trait` referenced in a modified file exists in `rules/taxonomy.md` and the config catalogs.
- Shared library files (`platform/`, `rules/`, `.claude/`, `libs/`) do not contain project-specific content (service names, API paths, domain entities).

Hook bypass for legitimate rule additions: add the new domain/trait to `rules/taxonomy.md` + `.claude/config/domains.md` or `.claude/config/traits.md` + `.claude/config/activation-rules.md` in the **same PR** as the new rule file. See `TEMPLATE.md § On-Demand Rule Policy`.

---

## 8. Common Conflict Patterns

These patterns surfaced repeatedly across TASK-MONO-029 through TASK-MONO-037. Knowing them reduces rework.

### Pattern 1: `tasks/INDEX.md` review header missing

**What happens**: an impl PR moves a task to `review/`, but `tasks/INDEX.md` is not updated to list the task under `## review`. Chore PR then cannot find what to move to `done/`.

**Fix**: the impl PR must update `INDEX.md` in the same commit that moves the task file to `review/`. Double-check the `## in-progress` → `## review` transition in `INDEX.md` before opening the PR.

### Pattern 2: Agent commits directly to `main`

**What happens**: an agent running outside a worktree attempts `git commit` on the `main` branch. `protect-main-branch.ps1` blocks the subsequent `git push`, but the commit already exists locally and pollutes `main`.

**How it occurred (TASK-MONO-032)**: the agent bypassed the worktree convention, committed on `main` locally, and was blocked from pushing. Recovery required a local `git reset --hard origin/main` (manual step, outside the agent session).

**Prevention**: always confirm `git branch` shows a worktree branch (e.g., `worktree-agent-<id>`) before any commit. Coordinators must pass the worktree path to subagents explicitly.

### Pattern 3: Spec + impl bundled in one PR

**What happens**: a task file appears in `review/` without ever having been in `ready/` on `main`. The queue signal is broken; other AI sessions reading `ready/` cannot see the planned work.

**Fix**: file the spec PR first. Merge it to `main`. Then open the impl PR. See §3 for the PR Separation Rule.

### Pattern 4: `done/` task entries modified after merge

**What happens**: a follow-up or fix task rewrites content in a `done/` task file to reflect the updated understanding. This violates the "do not modify after `review/` or `done/`" rule and makes the audit trail unreliable.

**Fix**: create a new task that documents the correction or follow-up. Reference the original task ID in the new task's Goal section.

### Pattern 5: Shared library file containing project-specific content

**What happens**: a rule file under `rules/`, a skill under `.claude/skills/`, or a platform doc under `platform/` includes a specific service name (e.g., `auth-service`), API path, or domain entity. The `rule-consistency-check.ps1` hook or a manual audit (TASK-MONO-029 series) catches this.

**Fix**: replace the concrete example with a `<placeholder>` token. If the content is inherently project-specific, move it to `projects/<name>/specs/` or `projects/<name>/knowledge/`.

### Pattern 6: TEMPLATE.md reference to non-existent guide

**What happens**: TEMPLATE.md references `docs/guides/monorepo-workflow.md` (or similar) before the file is authored. Follow-up tasks like this one (TASK-MONO-038) address the gap.

**Prevention**: when adding a reference to a `to-be-authored` guide in a master document, immediately file a follow-up task in `tasks/ready/` in the same PR.

### Pattern 7: INDEX.md `(empty)` placeholder left after population

**What happens**: after a task moves from one lifecycle stage to another, the `(empty)` placeholder remains in the vacated section rather than being removed, creating noise in the ledger.

**Fix**: remove the `(empty)` line when the section receives its first item; restore it when the section becomes empty again.

---

## 9. Reference Documents

| Document | What it governs |
|---|---|
| `CLAUDE.md` | Minimum operating rules: project classification, hard stops, required workflow, source-of-truth priority |
| `TEMPLATE.md` | Discovery → Distribution strategy, new project bootstrap (Options A/B), standalone portfolio sync, hostname routing spec, GAP IdP integration pattern |
| `tasks/INDEX.md` | Monorepo-level task lifecycle, PR Separation Rule, move rules, when to use root vs project tasks |
| `projects/<name>/PROJECT.md` | Project domain/trait declaration (activates rule bundles); service map; GAP IdP integration declaration |
| `platform/entrypoint.md` | Spec reading order for implementation tasks |
| `rules/taxonomy.md` | Authoritative catalog of all declared domains and traits |
| `.claude/config/activation-rules.md` | Dispatch table: which rule categories and skills activate per domain/trait |
| `docs/guides/dev-tooling.md` | DB / queue tool access (DBeaver, Redis Insight, Kafka UI) via docker exec, dev overlay, or Traefik TCP |
| `docs/adr/ADR-MONO-001-port-prefix-scaling.md` | Decision rationale for hostname-based routing (PORT_PREFIX retirement) |
