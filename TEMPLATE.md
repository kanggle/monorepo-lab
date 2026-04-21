# Template Guide

This repository is a **monorepo** for multi-domain backend/fullstack development with AI-assisted workflows. It follows a **"Discovery → Distribution"** strategy:

- **Discovery phase (current)**: Develop multiple projects side-by-side in this monorepo. Shared library (rules, platform, skills, agents) accumulates and refines as each project stresses it.
- **Distribution phase (later)**: Once the library is stable across 3+ projects, extract it into a separate **template repository** that new projects fork from.

This document explains the strategy, the library/project boundary, and the extraction plan.

---

## Three Repository Shapes (Target State)

After the full strategy plays out, three kinds of repositories exist in the ecosystem:

### 1. Dev Monorepo (this repository)

The working lab. Contains multiple projects + the library layer that serves them. The **source of truth** for the library. Active development happens here.

### 2. Template Repository (to be extracted later)

A separate repository containing **only the library layer** plus a single-project shell (flat layout, `PROJECT.md` at root). Marked as a GitHub "Template Repository" so `Use this template` works. Periodically synced from the dev monorepo. Not edited directly.

### 3. New Project Repository (created from template)

Each new project starts as a fresh fork of the Template Repository. Gets a clean git history, a flat single-project layout, and a copy of the library at the time of the fork. Owns its own evolution going forward.

---

## Library vs Project Boundary (Strict)

This is the single most important rule for keeping the template-extraction path viable.

### Shared Library Layer (at repo root)

| Path | Role |
|---|---|
| `.claude/config/` | domain/trait/service-type dispatch catalog |
| `rules/` | domain and trait rule libraries (accumulates over time) |
| `platform/` | platform-wide regulations (architecture, testing, security, observability) |
| `.claude/skills/` | implementation patterns and checklists |
| `.claude/agents/` | AI agent role definitions |
| `.claude/commands/` | slash commands |
| `tasks/templates/` | backend/frontend/integration task templates |
| `libs/` | domain-neutral shared Java libraries |
| `docs/guides/` | human-oriented workflow guides |
| `CLAUDE.md` | minimum operating rules for AI agents and developers |
| `build.gradle`, `settings.gradle`, `gradle/`, `gradlew*` | Gradle configuration |
| `.gitignore`, `.gitattributes`, `.editorconfig` | repository meta |

**Rule**: These files must remain **project-agnostic**. No project-specific service names, API paths, domain entities, or business rules may appear in them — not even as examples. Use `<placeholder>` tokens or defer to per-project specs.

### Project Content (under `projects/<project-name>/`)

| Path | Role |
|---|---|
| `PROJECT.md` | project classification declaration (name / domain / traits / service_types / ...) |
| `README.md` | project introduction |
| `apps/` | service implementations |
| `specs/contracts/` | HTTP and event contracts |
| `specs/services/` | per-service specs (`architecture.md` etc.) |
| `specs/features/` | feature specs |
| `specs/use-cases/` | use-case specs |
| `tasks/` | task lifecycle (`INDEX.md` + `ready/`, `in-progress/`, `review/`, `done/`, `archive/`) |
| `knowledge/` | design references and ADRs |
| `docs/` (not `guides/`) | project-specific docs |
| `infra/` | project-specific infrastructure configs (Prometheus, Grafana, Loki dashboards) |
| `scripts/` | project-specific scripts (topic creation, migrations, domain-specific e2e) |
| `docker-compose.yml`, `.env.example` | local dev stack |

**Rule**: These live only within their project and have no authority outside that project.

---

## Phase Timeline

### Phase 1 — Single Project (completed for wms-platform setup)

One project in the monorepo. Focus on learning what's truly common vs project-specific.

### Phase 2 — Second Project (current planning frontier)

Add a second domain project under `projects/<new-project>/`. Observe which shared library files needed no change vs needed tweaks. Tighten library abstractions that both projects use.

### Phase 3 — Third Project

Add a third project. By now, the "Rule of Three" has filtered out false generalizations. Shared library stabilizes.

### Phase 4 — Template Extraction

When the library has been stable for a month+ with no churn:

1. Run `scripts/extract-template.sh <target-dir>` (to be authored):
   - Copy the shared library layer to `<target-dir>`.
   - Replace `projects/<p>/` subtree with a flat single-project shell.
   - Replace `PROJECT.md` with a template example.
   - Reset `README.md` to template intro.
2. Push `<target-dir>` as a new GitHub repository (e.g., `project-template`).
3. In GitHub repo settings, enable "Template repository".
4. New projects use `Use this template` button to fork.

### Phase 5 — Ongoing

- New projects start from the Template Repository (flat layout).
- Dev monorepo continues to evolve; library improvements sync to the Template Repository periodically (monthly or on demand).
- Old projects in the Template-extracted shape **do not auto-update** — they own their library snapshot. Back-porting is manual.

---

## Starting a New Project in the Monorepo (Phase 2+)

Before the Template extraction, new projects are added directly to this monorepo. There are two integration styles depending on whether the project is greenfield or a pre-existing standalone repo.

### Choosing the integration style

```
Does a standalone git repo for this project already exist?
├─ No  → Option A: Greenfield (direct-include)          ← default, simpler
└─ Yes → Does it already own its own settings.gradle + libs/?
         ├─ No  → Option A: Greenfield (direct-include) — copy content in as plain subdir
         └─ Yes → Option B: Import (composite-build)    ← avoids rewriting internal paths
```

| Aspect | Option A (direct-include) | Option B (composite-build) |
|---|---|---|
| Root `settings.gradle` | `include('projects:<name>:apps:...')` | `includeBuild('projects/<name>')` |
| Gradle perspective | Single build, each service is a subproject | Two builds loosely coupled |
| `.gradle/` cache | One, at monorepo root | Two — monorepo root + nested project |
| Uses root `libs/` | Yes (shared) | Project keeps its own nested `libs/` |
| Path rewrites on import | N/A (greenfield) | None — project's `project(':libs:x')` refs resolve within its own build |
| PROJECT_TYPES in sync-portfolio.sh | `direct-include` | `composite-build` |
| Examples in this monorepo | `wms-platform` | `ecommerce-microservices-platform` |

Use Option A unless Option B's benefit (zero rewrites of many existing internal Gradle references) genuinely outweighs the cost of running two Gradle builds and managing a nested `libs/` stack.

---

### Option A — Greenfield (direct-include)

#### 1. Create project directory structure

```bash
mkdir -p projects/<new-project>/{apps,specs/{contracts/{http,events},services,features,use-cases},tasks/{ready,in-progress,review,done,archive},knowledge,docs,infra}
touch projects/<new-project>/tasks/{ready,in-progress,review,done,archive}/.gitkeep
```

#### 2. Write `PROJECT.md`

Copy the frontmatter structure from an existing project (e.g., `projects/wms-platform/PROJECT.md`). Declare `domain`, `traits`, `service_types`. Write Purpose, Domain Rationale, Trait Rationale, Out of Scope in prose.

#### 3. Write `tasks/INDEX.md`

Copy from the existing project and adjust. This file defines the task lifecycle for the new project.

#### 4. Update root `settings.gradle`

Add an `include` block for the new project's apps:

```groovy
include(
    'projects:<new-project>:apps:<service-a>',
    'projects:<new-project>:apps:<service-b>'
)
```

#### 5. Create project-level `build.gradle` (placeholder)

Start empty. Add project-wide common config (e.g., Spring Boot plugin) when multiple services share it.

#### 6. Write the first `tasks/ready/TASK-BE-001-*.md`

Typically a `<service>-bootstrap` task that stands up the first service skeleton.

#### 7. Register for portfolio sync (when ready to publish)

In `scripts/sync-portfolio.sh`:

```bash
PROJECT_REMOTES["<new-project>"]="https://github.com/<owner>/<new-project>.git"
PROJECT_TYPES["<new-project>"]="direct-include"
```

#### 8. Verify Gradle sees the new structure

```bash
./gradlew projects
```

---

### Option B — Import an existing standalone repo (composite-build)

Use this path when the incoming repo already has its own `settings.gradle`, `build.gradle`, and nested `libs/` stack that you want to preserve without rewriting every internal `project(':libs:…')` reference. The `ecommerce-microservices-platform` import (commits `0956dc6` → `dad3b41`) is the worked example.

#### 1. Import the subtree

From a fresh clone of the standalone repo's latest branch (tip of whatever branch holds all the work — NOT necessarily `main` if unpushed work sits elsewhere):

```bash
git remote add -f <name>-source /path/to/standalone/clone   # or https remote
git subtree add --prefix=projects/<name> <name>-source <branch> --squash
```

Use `--squash` for a clean single-commit import, or omit it to carry full per-commit history into the monorepo (larger `.git`, richer history).

#### 2. Wire the composite build

```groovy
// root settings.gradle
includeBuild('projects/<new-project>')
```

That's it — the project's own `settings.gradle` continues to declare its apps/libs with its own root-relative paths. No sed rewrites.

#### 3. Neutralise the nested CLAUDE.md

Replace `projects/<name>/CLAUDE.md` with a short pointer to the monorepo root `CLAUDE.md`. Example structure: see `projects/ecommerce-microservices-platform/CLAUDE.md` — declares the project is now inside a monorepo and that this file is not a source of truth.

#### 4. Reconcile shared-layer duplicates (Discovery → Distribution)

The imported project typically carries legacy copies of shared-layer content from its standalone days. Resolve them per the monorepo's boundary rules:

| Legacy location | Action |
|---|---|
| `projects/<name>/specs/rules/` | **Delete.** Root `rules/` is authoritative. If any mandatory rule was novel, add it to the matching `rules/domains/<d>.md` or `rules/traits/<t>.md` before deleting. |
| `projects/<name>/specs/platform/` | **Delete.** Root `platform/` is authoritative. Universally valuable files (e.g. `object-storage-policy.md` from the ecommerce import) **promote** to root `platform/` with project-specific examples generalised to `<placeholder>` tokens. |
| `projects/<name>/tasks/templates/` | **Delete.** Root `tasks/templates/` is shared. |
| `projects/<name>/.claude/` | **Delete entirely.** Root `.claude/` is the sole authority. Project-specific agent routing belongs in `PROJECT.md` or `specs/services/<s>/architecture.md`, not in nested agent frontmatter (see `.claude/agents/domain/README.md` L18). |

Project-specific content stays — `apps/`, `specs/contracts/`, `specs/services/`, `specs/features/`, `specs/use-cases/`, `tasks/{backlog..archive}/` (not `templates/`), `knowledge/`, `docs/` (not `guides/`), `infra/`, `k8s/`, `load-tests/`, `scripts/`, `docker-compose.yml`, `.env.example`, project-specific `.gitignore`/`.dockerignore`/`.github/workflows/`/`LICENSE`.

Domain-specific error codes, ubiquitous language, and similar content the standalone repo placed in its `specs/platform/*.md` gets absorbed into `rules/domains/<domain>.md` (for domain rules) or `platform/error-handling.md` under `[domain: <name>]` sections (for error codes). Concrete per-project details (public API route lists, rate-limit tiers) belong in `projects/<name>/specs/services/<gateway>/public-routes.md` per `platform/api-gateway-policy.md`.

#### 5. Keep the project runnable from monorepo root (optional but recommended)

If the project has a frontend (pnpm workspace) or its own docker-compose.yml, add shortcut scripts to the monorepo root `package.json` so developers need not `cd` into the project. Examples from the current monorepo:

```jsonc
{
  "scripts": {
    "<name>:install":   "pnpm --dir projects/<name> install",
    "<name>:dev":       "pnpm --dir projects/<name> dev",
    "<name>:up":        "docker compose --project-directory projects/<name> up -d",
    "<name>:down":      "docker compose --project-directory projects/<name> down"
  }
}
```

`pnpm --dir` and `docker compose --project-directory` preserve the project's own `pnpm-workspace.yaml` and `.env` scope — no root workspace, no leakage.

#### 6. Register for portfolio sync

In `scripts/sync-portfolio.sh`:

```bash
PROJECT_REMOTES["<new-project>"]="https://github.com/<owner>/<new-project>.git"
PROJECT_TYPES["<new-project>"]="composite-build"
```

The script already handles composite-build post-process: it trusts filter-repo's hoisted `settings.gradle`/`build.gradle` and strips any orphan `includeBuild(...)` line if present.

#### 7. Verify

```bash
./gradlew projects                                                # lists the included build
./gradlew :<new-project>:apps:<service>:compileJava               # composite-build path
./gradlew :projects:wms-platform:apps:master-service:compileJava  # regression check — direct-include still works
ls projects/<new-project>/specs/                                  # only contracts/features/services/use-cases
ls projects/<new-project>/.claude 2>&1                            # "No such file or directory"
```

---

## Starting a New Project from the Extracted Template (Phase 5+)

_Applies once the Template Repository exists (Phase 4)._

1. Go to the Template Repository on GitHub.
2. Click **Use this template → Create a new repository**.
3. Clone locally: `git clone git@github.com:<owner>/<new-project>.git`
4. Edit `PROJECT.md` — declare domain, traits, service_types.
5. Write the first `tasks/ready/TASK-BE-001-*.md`.
6. Start development.

The flat layout (no `projects/` level) keeps the single-project repository simple.

---

## On-Demand Rule Policy

`rules/domains/` and `rules/traits/` contain **only rule files for what has actually been declared** by some project in the monorepo. Adding a new project that declares a new `domain` or `trait` requires **adding the matching rule file in the same PR** — this is the On-Demand Policy defined in `rules/README.md`.

When adding a new rule file:

- Format: use an existing file (`rules/traits/transactional.md`, `rules/domains/wms.md`) as a structural template.
- Required sections: Scope / Mandatory Rules / Forbidden Patterns / Required Artifacts / Interaction with Common Rules / Checklist.
- **Project-specific paths forbidden**: do not write `projects/<project-name>/apps/...` or specific service names in the rule body. Rule files are library content — keep them abstract.

Also update in the same PR:

- `rules/taxonomy.md` (add the domain/trait to the narrative catalog)
- `.claude/config/domains.md` or `.claude/config/traits.md` (add to dispatch catalog)
- `.claude/config/activation-rules.md` (declare which rule categories and skills activate)

Skipping any of these causes drift.

---

## Cross-Project Library Changes

When a shared library file changes and one or more projects need to adapt, bundle everything in one PR:

```
feat(rules): add W7 rule for lot FIFO enforcement
feat(wms): apply W7 to inventory-service state spec
```

Atomic cross-project commits are the **primary advantage of the monorepo layout**. If you find yourself wanting separate PRs for library and project, stop and review — the library change is likely not yet worth the work, or the project-side adaptation is missing scope.

See `docs/guides/monorepo-workflow.md` (to be authored) for the full workflow rules.

---

## Template Extraction (Phase 4 Detail)

_Exact scripts to be authored when Phase 4 approaches. This section is a sketch._

`scripts/extract-template.sh <target-dir>` will:

1. Copy every shared library path (`.claude/`, `platform/`, `rules/`, `libs/`, `tasks/templates/`, `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md`, Gradle root files, `.git*` dotfiles) to `<target-dir>`.
2. Create an empty single-project shell: `apps/`, `specs/contracts/{http,events}/`, `specs/services/`, `specs/features/`, `specs/use-cases/`, `tasks/{ready,in-progress,review,done,archive}/`, `knowledge/`, `docs/`, `infra/` — each with a `.gitkeep`.
3. Write a `PROJECT.md.example` (not `PROJECT.md`) so users customize before committing.
4. Replace `settings.gradle` with a flat single-project version.
5. Write a new `README.md` marking the repo as a template.
6. `git init && git add -A && git commit -m "initial template from <monorepo-commit-sha>"`.

Until Phase 4, this section is preparation only.

---

## Validation

After any significant change, verify:

```bash
# Shared library has no project-specific references
grep -rE "(auth-service|product-service|order-service|payment-service)" platform/ rules/ .claude/ libs/ tasks/templates/ docs/guides/ 2>/dev/null
# Expected: no matches

# Gradle sees the expected project structure
./gradlew projects

# Each project has a PROJECT.md
find projects -maxdepth 2 -name PROJECT.md
```

AI-based validation: `.claude/commands/validate-rules.md` (if present).

---

## FAQ

**Q: Why not just start with the Template Repository pattern from day one?**
A: You don't know what's truly common until you see 3+ projects. Extracting prematurely embeds bias from the first project. The monorepo phase is a **discovery experiment** — only what actually gets reused across projects survives the "Rule of Three" filter into the eventual template.

**Q: How is the monorepo different from just one big repo with everything?**
A: The **shared/project boundary is strict and enforced** (via `CLAUDE.md` Hard Stop rules). Shared files stay project-agnostic. When you move a project's content out (for independent publication via `scripts/extract-project.sh`, Phase 4+), the shared library comes with it as a vendored snapshot. That's only clean if the boundary has been maintained.

**Q: Can I extract a single project now for a portfolio submission?**
A: Yes, see the "Portfolio Submission" section in the dev workflow guides. Briefly: `git filter-repo --subdirectory-filter projects/<p>` plus copying the shared library into the extracted repo gives a standalone project repo. The dev monorepo stays as-is.

**Q: When does the Template Repository actually get created?**
A: Around Phase 4 — when the shared library has been stable through 3+ projects without churn. Until then, this `TEMPLATE.md` is preparation, not instructions.

**Q: What if two projects need slightly different versions of the same rule?**
A: Either (1) split into a more general base rule with project-specific extensions in `projects/<p>/specs/`, or (2) let the project declare an `## Overrides` block in its `PROJECT.md` referencing the specific shared rule. Do not fork the shared rule file.

**Q: Can `platform/` files have example placeholders like `<service>`?**
A: Yes, `<placeholder>` tokens are the recommended way to illustrate patterns without hardcoding project names. Concrete examples (`auth-service`, `OrderPlaced` event) are forbidden in shared files — they create drift and leak one project's domain into others.

**Q: When should I use `composite-build` instead of `direct-include` for a new project?**
A: Only when an existing standalone repo with its own `settings.gradle` + nested `libs/` is being imported and rewriting its many internal `project(':libs:…')` references would be worse than the cost of running two Gradle builds. Greenfield projects — even when they physically live under `projects/<name>/` — should always use `direct-include`. See the integration-style decision tree in "Starting a New Project in the Monorepo".

---

## References

- `CLAUDE.md` — operating rules (Repository Layout section explains the monorepo structure)
- `rules/README.md` — rule library architecture and on-demand policy
- `docs/guides/` — human-oriented workflow guides (when present)
