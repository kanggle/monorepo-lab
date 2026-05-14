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

### HARDSTOP-01 — No `PROJECT.md` locatable

```
[VIOLATION] HARDSTOP-01: No `PROJECT.md` is locatable for the current working context at <cwd>.
[WHY] Every implementation request must resolve to exactly one project so rule layers (domain + traits) can be loaded; without `PROJECT.md` the rule resolver has no anchor and would silently default — Identify the Target Project is a CLAUDE.md prerequisite, not a fallback.
[REMEDIATION] Choose one:
  1. Move the working location into an existing project: `cd projects/<name>/` where `<name>` matches the request scope (see `docs/project-overview.md` § 2.1 for the 5 active projects).
  2. If the request is monorepo-level (touching `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/templates/`, `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md`), reframe as a root task per `tasks/INDEX.md` § "When to Use Root vs Project Tasks" and operate from repo root.
  3. If a new project is genuinely needed, file a `tasks/ready/TASK-MONO-XXX-bootstrap-<project>.md` and pause; do not implement before the project skeleton lands.
[REFERENCE] CLAUDE.md § Identify the Target Project (Read First)
```

### HARDSTOP-02 — `PROJECT.md` missing/unparseable or unknown domain/trait

```
[VIOLATION] HARDSTOP-02: `<project>/PROJECT.md` is missing/unparseable, or declares `domain`/`trait` not in `rules/taxonomy.md`, at `projects/<name>/PROJECT.md`.
[WHY] The dispatch catalog at `.claude/config/` and the narrative at `rules/taxonomy.md` jointly enumerate every valid domain/trait; an unknown tag means the rule layer cannot resolve and the session would proceed with no domain/trait constraints loaded.
[REMEDIATION] Choose one:
  1. Fix the typo / restore the missing frontmatter in `projects/<name>/PROJECT.md` and verify each declared `domain`/`trait` appears in `rules/taxonomy.md` plus the three dispatch files (`.claude/config/activation-rules.md` + `domains.md` + `traits.md`).
  2. If a new domain/trait is genuinely needed, add it to `rules/taxonomy.md` + all three `.claude/config/*.md` files + the corresponding `rules/domains/<d>.md` or `rules/traits/<t>.md` file in the same PR (per `rules/README.md` drift-prevention rule).
  3. If neither applies, ask the project owner (declared in `<project>/PROJECT.md` owner field) and pause.
[REFERENCE] CLAUDE.md § Project Classification + rules/README.md § Routing Layer
```

### HARDSTOP-03 — Shared library file contains project-specific content

```
[VIOLATION] HARDSTOP-03: Shared library file `<path>` contains project-specific content (service name / API path / domain entity / project-bound logic) at `<file>:<line>`.
[WHY] Shared paths (`platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `docs/guides/`) must remain project-agnostic so every project can adopt them unchanged; mixing project-specific content here breaks the Library vs Project boundary that this rule library is built on.
[REMEDIATION] Choose one:
  1. Move the offending content back to the owning project under `projects/<name>/` (apps / specs / knowledge / docs as appropriate) and keep the shared file generic.
  2. If the content is genuinely cross-service / cross-project, propose promotion via `docs/adr/ADR-MONO-XXX-<slug>.md` proposing a generic abstraction, and PAUSE this task until the ADR is ACCEPTED.
  3. If the content is documentation noise (example / illustration), replace it with an abstract placeholder (`<service>`, `<entity>`) per existing precedent.
[REFERENCE] platform/shared-library-policy.md § Forbidden in Shared Libraries
```

### HARDSTOP-04 — Domain/trait rule conflicts with common without `## Overrides`

```
[VIOLATION] HARDSTOP-04: `rules/domains/<d>.md` or `rules/traits/<t>.md` at `<file>:<line>` conflicts with a common rule but does not carry an explicit `## Overrides` block referencing the specific common rule being relaxed.
[WHY] Common rules win unless a domain/trait file explicitly overrides them; an implicit conflict means the conflict resolution falls through to "common wins" and the domain/trait rule never takes effect — a silent dead branch.
[REMEDIATION] Choose one:
  1. Add an `## Overrides` block to the conflicting domain/trait file citing the common-rule ID/section being relaxed and the reason (see `rules/README.md` § Conflict Rules for the canonical block shape).
  2. Reword the domain/trait rule so it no longer conflicts with common (additive specialization, not relaxation).
  3. Open `docs/adr/ADR-MONO-XXX-<slug>.md` proposing the override at common-rule level (promoting the relaxation into common itself), and PAUSE until ACCEPTED.
[REFERENCE] rules/README.md § Conflict Rules
```

### HARDSTOP-05 — Task is not in the appropriate `tasks/ready/`

```
[VIOLATION] HARDSTOP-05: Task `<task-id>` is not in the appropriate `tasks/ready/` directory at `<path>`.
[WHY] Only tasks in `ready/` may be implemented; `in-progress/` / `review/` / `done/` tasks are frozen, and unfiled work bypasses lifecycle review. The ready-queue signal is the public surface external observers read to know what's available.
[REMEDIATION] Choose one:
  1. If the work is new, author the task file in the correct `tasks/ready/` (root `tasks/ready/` for monorepo-level work per `tasks/INDEX.md`; `projects/<name>/tasks/ready/` for project-internal work) and land it via a spec PR before any impl commits.
  2. If the work is a fix to an already-merged task, create a new fix task in `ready/` referencing the original task ID in its Goal section (per `tasks/INDEX.md` § Review Rules).
  3. If unclear which lifecycle applies, consult `tasks/INDEX.md` § "When to Use Root vs Project Tasks" decision table.
[REFERENCE] CLAUDE.md § Task Rules + tasks/INDEX.md § Move Rules
```

### HARDSTOP-06 — Required specifications missing or in conflict

```
[VIOLATION] HARDSTOP-06: Required specifications under `<project>/specs/` are missing or conflict at `<file>:<line>` (or the task's Related Specs list points to non-existent paths).
[WHY] Specifications are the source of truth per CLAUDE.md § Core Principles; implementing without — or against — them produces drift that future spec-vs-code audits will flag as "implementation, not spec, is the de-facto truth", which is the failure mode this rule library was built to prevent.
[REMEDIATION] Choose one:
  1. Update the missing spec under `<project>/specs/contracts/` / `services/` / `features/` / `use-cases/` first (per the Source of Truth Priority order); land the spec change before any implementation commit.
  2. If the conflict is between two existing specs, resolve at the higher layer of the Source of Truth Priority (CLAUDE.md § Source of Truth Priority — layer 6 contracts beats layer 7 services beats layer 8 features beats layer 9 use-cases).
  3. If the spec direction itself is contested, open `<project>/docs/adr/ADR-<scope>-XXX-<slug>.md` and PAUSE until ACCEPTED.
[REFERENCE] CLAUDE.md § Source of Truth Priority + CLAUDE.md § Core Principles
```

### HARDSTOP-07 — Acceptance criteria unclear

```
[VIOLATION] HARDSTOP-07: Task `<task-id>` Acceptance Criteria section is missing, empty, or insufficiently specific to verify completion at `<task-path>`.
[WHY] AC is the contract between author and implementer; ambiguous AC turns "review" into a re-design conversation, blowing PR cycle budget. Every required task section (Goal / Scope / AC / Related Specs / Related Contracts / Edge Cases / Failure Scenarios) exists because skipping it has bitten prior PRs.
[REMEDIATION] Choose one:
  1. Author concrete, verifiable AC checkboxes in the task file (each line testable as PASS / FAIL by reading code or running a command) and land via a spec PR before implementation.
  2. If the task is small enough that AC = "implement and merge", state that explicitly in the AC section with one line ("AC: the change described in Goal is implemented and CI green") to avoid leaving the section empty.
  3. If AC genuinely requires upstream clarification, ask the task owner and pause; do not invent AC to unblock yourself.
[REFERENCE] CLAUDE.md § Task Rules + tasks/INDEX.md § Move Rules
```

### HARDSTOP-08 — Required contracts missing

```
[VIOLATION] HARDSTOP-08: Required contract under `<project>/specs/contracts/` is missing or out of sync with the task's Related Contracts list at `<file>:<line>`.
[WHY] API and event contracts must be updated *before* implementation per CLAUDE.md § Layer Rules; implementing first and "documenting later" produces contract-vs-code drift that consumers cannot rely on, which is the primary failure mode contract-first development exists to prevent.
[REMEDIATION] Choose one:
  1. Author / update the contract under `<project>/specs/contracts/http/` or `<project>/specs/contracts/events/` first and land the contract change (typically a separate contract PR or co-bundled with the spec) before the implementation commit.
  2. If the contract change is breaking, also update consumer code in the same PR (per CLAUDE.md § Cross-Project Changes — atomic PR rule) and mark the commit `feat(<scope>)!:` or with a `BREAKING CHANGE:` footer.
  3. If the contract direction is contested, open `<project>/docs/adr/ADR-<scope>-XXX-<slug>.md` proposing the contract and PAUSE.
[REFERENCE] CLAUDE.md § Layer Rules + platform/event-driven-policy.md (events) or platform/api-gateway-policy.md (HTTP)
```

### HARDSTOP-09 — Task requires architecture decision not in specs

```
[VIOLATION] HARDSTOP-09: Task `<task-id>` requires an architecture decision (state machine shape / transaction boundary / event taxonomy / cross-service contract) that is not documented in `<project>/specs/services/<service>/architecture.md` or any ADR.
[WHY] Architecture decisions made implicitly during implementation produce code that later cannot be defended against "why was this chosen" review questions — and shape every downstream task that builds on the same service. The Architecture Decision Rule (`platform/architecture-decision-rule.md`) forbids choosing architecture during implementation.
[REMEDIATION] Choose one:
  1. Author / update `<project>/specs/services/<service>/architecture.md` recording the decision (chosen style + rejected alternatives + reason) and land the spec change before any code commit.
  2. If the decision is significant (cross-service, irreversible, or shapes other services), record it in `<project>/docs/adr/ADR-<scope>-XXX-<slug>.md` and PAUSE until ACCEPTED.
  3. If the decision is reversible and local (single class / single endpoint), implement with an inline comment citing the choice + one-line reason and file a follow-up `tasks/ready/` task to backfill the architecture.md update.
[REFERENCE] CLAUDE.md § Layer Rules + platform/architecture-decision-rule.md
```

### HARDSTOP-10 — Service Type undeclared or unknown

```
[VIOLATION] HARDSTOP-10: Target service's `Service Type` is undeclared in `<project>/specs/services/<service>/architecture.md`, or the declared type is not present in `platform/service-types/INDEX.md`.
[WHY] Service Type is the orthogonal axis (independent of domain/trait) that determines which `platform/service-types/<type>.md` file is loaded per the Required Workflow — without it, the type-specific rule layer is empty and the session would proceed with no service-type guidance.
[REMEDIATION] Choose one:
  1. Declare the Service Type in `<project>/specs/services/<service>/architecture.md` under the standard "Service Type" header, choosing one of the types in `platform/service-types/INDEX.md`.
  2. If the existing service-type catalog has no fit, open `tasks/ready/TASK-MONO-XXX-add-service-type-<name>.md` proposing the new type with a `platform/service-types/<new-type>.md` file + INDEX update; PAUSE until landed.
  3. If the service is genuinely typeless (e.g. a test fixture or non-service shared module), reframe — it should not be under `specs/services/` in the first place.
[REFERENCE] CLAUDE.md § Required Workflow step 7 + platform/service-types/INDEX.md
```

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

Before recommending the next task, **first run `git fetch origin main`** and check for divergence (`git log HEAD..origin/main --oneline`) — origin may carry recently-merged closures the local tree hasn't picked up, and recommending against stale local state can duplicate already-closed work. Then scan **both** the `ready/` queue (new candidates) **and** the `review/` queue (open impl PRs awaiting review fix, or merged PRs awaiting `review/ → done/` chore). Surface review-side work that should be cleared first to avoid open-PR pile-up. Apply to both root `tasks/` and each affected `projects/<name>/tasks/`.
