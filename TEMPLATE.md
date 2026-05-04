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

### Phase 1 — Single Project ✅ (completed)

One project in the monorepo (`wms-platform`). Focus on learning what's truly common vs project-specific. Shared library bootstrapped.

### Phase 2 — Second Project ✅ (completed)

`ecommerce-microservices-platform` imported (2026-04-25, PR #58–61). Composite-build → direct-include consolidation demonstrated. Shared library survived cross-domain stress.

### Phase 3 — Third and Fourth Project ✅ (completed, 2026-05-03)

`global-account-platform` (GAP) and `fan-platform` imported. GAP elevated to standard OIDC IdP (ADR-001 ACCEPTED). All 4 projects now co-exist:

| Project | Domain | Integration style | Hostname |
|---|---|---|---|
| wms-platform | wms | direct-include | wms.local |
| ecommerce-microservices-platform | ecommerce | direct-include | ecommerce.local (web/admin) |
| global-account-platform | saas | direct-include | gap.local |
| fan-platform | fan-platform | direct-include | fan-platform.local |

"Rule of Three" baseline established. TASK-MONO-029~033 (공통규칙 정리 시리즈) completed the audit and stabilisation. Shared library is approaching Phase 4 readiness.

### Phase 4 — Template Extraction (pending decision)

When the library has been stable for a month+ with no churn, initiate extraction. Decision is the project owner's call — this document does not presuppose the timing.

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
| Examples in this monorepo | `wms-platform`, `ecommerce-microservices-platform` | (retained as fallback only — no current examples) |

Use Option A unless Option B's benefit (zero rewrites of many existing internal Gradle references) genuinely outweighs the cost of running two Gradle builds and managing a nested `libs/` stack.

---

### Option A — Greenfield (direct-include)

#### 1. Create project directory structure

```bash
mkdir -p projects/<new-project>/{apps,specs/{contracts/{http,events},services,features,use-cases,integration},tasks/{backlog,ready,in-progress,review,done,archive},knowledge,docs,infra}
touch projects/<new-project>/tasks/{backlog,ready,in-progress,review,done,archive}/.gitkeep
```

> `specs/integration/` is created here because the `PROJECT.md` GAP IdP section references `specs/integration/gap-integration.md` (see Step 2 and §"GAP IdP Integration Pattern"). `tasks/backlog/` is required by the project-level lifecycle defined in Step 3.

#### 2. Write `PROJECT.md`

Copy the frontmatter structure from an existing project (e.g., `projects/wms-platform/PROJECT.md`). The required frontmatter fields are:

```yaml
---
name: <project-name>
domain: <domain>          # must be in rules/taxonomy.md
traits: [<trait>, ...]    # each must be in rules/taxonomy.md
service_types: [rest-api, event-consumer, ...]
compliance: []            # e.g. [gdpr, pipa] — informational
data_sensitivity: internal   # internal | pii | pii-sensitive
scale_tier: startup       # informational
taxonomy_version: 0.1
---
```

Verify `domain` and each `trait` exist in `rules/taxonomy.md` (Hard Stop if not). If adding a new domain or trait, add the rule file in the same PR per the On-Demand Rule Policy below.

Write prose sections: Purpose, Domain Rationale, Trait Rationale, Service Map, GAP IdP Integration (see §"GAP IdP Integration Pattern" below), Out of Scope, Overrides.

#### 3. Write `tasks/INDEX.md`

Copy from an existing project (e.g., `projects/wms-platform/tasks/INDEX.md`) and adjust. The project-level lifecycle includes **backlog/** and **archive/** in addition to the monorepo-root lifecycle stages (ready / in-progress / review / done).

| Stage | Note |
|---|---|
| `backlog/` | Parked ideas — not yet ready for implementation |
| `ready/` | Accepted and scoped — may be implemented |
| `in-progress/` | Actively being implemented |
| `review/` | Implementation complete, awaiting review |
| `done/` | Merged |
| `archive/` | Superseded or abandoned |

Adopt the same **PR Separation Rule** as the root `tasks/INDEX.md`: spec PR / impl PR / chore (lifecycle move) PR must not be bundled.

#### 4. Write `docker-compose.yml` with Traefik hostname labels

The project must join the shared Traefik network from day one (TASK-MONO-022/024 precedent). Pick an unused `*.local` hostname and register it in `TEMPLATE.md § Local Network Convention — Hostname allocation`.

```yaml
services:
  gateway:
    expose: ["8080"]
    labels:
      - "traefik.enable=true"
      - "traefik.docker.network=traefik-net"
      - "traefik.http.routers.<new-project>.rule=Host(`<new-project>.local`)"
      - "traefik.http.routers.<new-project>.entrypoints=web"
      - "traefik.http.services.<new-project>.loadbalancer.server.port=8080"
    networks:
      - traefik-net
      - <new-project>-net

  postgres:
    expose: ["5432"]         # no host port — DB tools via docker exec or dev overlay
    networks: [<new-project>-net]

networks:
  traefik-net:
    external: true
    name: traefik-net        # explicit name so compose project-name prefix is not prepended
  <new-project>-net:
    driver: bridge
```

> `traefik.docker.network=traefik-net` is required when the gateway container is attached to multiple networks — without it Traefik may route via the wrong network and fail to reach the container. `entrypoints=web` pins the router to Traefik's HTTP (port 80) entrypoint. Both labels are present in all existing projects (fan-platform, wms, gap, ecommerce).

Backing services (postgres, redis, kafka, …) use `expose:` only — never `ports:`. See `CLAUDE.md § Local Network Convention` (authoritative) and `TEMPLATE.md § Local Network Convention` (full detail) for the DB tool access pattern.

#### 5. Write `.env.example`

```bash
# Hostname (Traefik routing — no PORT_PREFIX)
PROJECT_HOSTNAME=<new-project>.local

# GAP OIDC (if integrating with GAP IdP)
# OIDC_ISSUER_URL: GAP's issuer base URL — no trailing /oauth2/ path.
#   Spring Security appends /.well-known/openid-configuration automatically.
OIDC_ISSUER_URL=http://gap.local
# JWT_JWKS_URI: explicit JWKS endpoint (avoids OpenID discovery round-trip in dev).
JWT_JWKS_URI=http://gap.local/oauth2/jwks
```

#### 6. Update root `settings.gradle`

Add an `include` block for the new project's apps:

```groovy
include(
    'projects:<new-project>:apps:<service-a>',
    'projects:<new-project>:apps:<service-b>'
)
```

#### 7. Add shortcut scripts to root `package.json`

```jsonc
{
  "scripts": {
    "<new-project>:up":     "docker compose --project-directory projects/<new-project> up -d",
    "<new-project>:down":   "docker compose --project-directory projects/<new-project> down",
    "<new-project>:ps":     "docker compose --project-directory projects/<new-project> ps",
    "<new-project>:logs":   "docker compose --project-directory projects/<new-project> logs -f",
    "<new-project>:docker": "docker compose --project-directory projects/<new-project>"
  }
}
```

> Add `:install`, `:dev`, `:build`, `:lint`, `:pnpm` shortcuts if the project has a frontend (`pnpm --dir projects/<new-project>`). See fan-platform entries in the root `package.json` as a template for the full set.

#### 8. Create project-level `build.gradle` (placeholder)

Create `projects/<new-project>/build.gradle` with a header comment only — no plugin declarations yet. Use `projects/wms-platform/build.gradle` as a structural template (the file documents what to add when multiple services share common config). Add Spring Boot plugin or dependency blocks only when concrete duplication across service modules warrants it.

#### 9. Write the first task in `tasks/ready/`

Typically a `<service>-bootstrap` task that stands up the first service skeleton. Follow the PR Separation Rule — this spec lands in a spec PR before any implementation.

**Task ID convention** — two patterns are in use; pick one and declare it in `tasks/INDEX.md`:

| Pattern | Example | When to use |
|---|---|---|
| `TASK-BE-001-*.md` | wms-platform style | Short domain name; project namespace implied by directory |
| `TASK-<PROJECT-PREFIX>-BE-001-*.md` | `TASK-FAN-BE-001-*.md` (fan-platform) | Avoids ambiguity when task IDs may appear in cross-project context |

Choose the simpler `TASK-BE-001` form for single-domain projects; use the prefixed form if the project name is commonly abbreviated in shared contexts.

#### 10. Register for portfolio sync (when ready to publish)

In `scripts/sync-portfolio.sh`:

```bash
PROJECT_REMOTES["<new-project>"]="https://github.com/<owner>/<new-project>.git"
PROJECT_TYPES["<new-project>"]="direct-include"
```

#### 11. Verify Gradle sees the new structure

```bash
./gradlew projects
```

Expected output: new project and its app subproject(s) appear under `Project ':projects:<new-project>'`.

#### 12. Write `README.md`

Create `projects/<new-project>/README.md` introducing the project: purpose, architecture diagram (optional), local dev quick-start (hostname, `pnpm <new-project>:up`, Traefik prerequisite), and known limitations. See `projects/fan-platform/README.md` or `projects/wms-platform/README.md` as templates.

---

### Option B — Import an existing standalone repo (composite-build)

Use this path when the incoming repo already has its own `settings.gradle`, `build.gradle`, and nested `libs/` stack that you want to preserve without rewriting every internal `project(':libs:…')` reference. The `ecommerce-microservices-platform` import (commits `0956dc6` → `dad3b41`) was originally done this way; it has since been consolidated onto Option A (PR #58, 2026-04-25) once its libs were merged into root `libs/`. Treat Option B as a fallback for the rare case where library-naming conflicts genuinely block direct-include.

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

## Local Network Convention

> **Source of truth**: This section (`TEMPLATE.md § Local Network Convention`) is the **master** specification for hostname-based routing. `CLAUDE.md § Local Network Convention` is a concise summary that redirects here for full detail. If the two conflict, this document wins.

Per [ADR-MONO-001](docs/adr/ADR-MONO-001-port-prefix-scaling.md) (Status: ACCEPTED, 2026-05-02), the monorepo adopts a **hostname-based routing** model for local development: a single shared Traefik reverse proxy occupies host ports `:80`/`:443`, and every project's gateway/frontend registers a hostname with Traefik. Backing services (postgres, redis, kafka, ...) stay on the docker network with no host exposure.

This replaces the legacy `PORT_PREFIX` digit-allocation scheme, which was capped at 5 usable slots (prefix 6+ overflows the 65535 host-port limit for common service ports like 6379/8080/9092). With hostname routing, the number of concurrent projects is unbounded.

### Target pattern

Every project gateway/frontend service uses Traefik labels — never `ports:` for inter-project traffic:

```yaml
services:
  gateway:
    expose: ["8080"]                             # internal-only, no host port
    labels:
      - "traefik.enable=true"
      - "traefik.docker.network=traefik-net"
      - "traefik.http.routers.<project>.rule=Host(`<project>.local`)"
      - "traefik.http.routers.<project>.entrypoints=web"
      - "traefik.http.services.<project>.loadbalancer.server.port=8080"
    networks:
      - traefik-net
      - <project>-net

  postgres:
    expose: ["5432"]                             # internal only
    networks: [<project>-net]

networks:
  traefik-net:
    external: true                               # shared with the global Traefik stack
    name: traefik-net                            # explicit name — no compose project-name prefix
  <project>-net:
    driver: bridge
```

A monorepo-root `infra/traefik/docker-compose.yml` runs Traefik once, on host ports `:80` and `:443`. Projects join the `traefik-net` external network so Traefik can route to them by hostname.

### Hostname allocation

| Hostname | Project | Status |
|---|---|---|
| `ecommerce.local` | ecommerce-microservices-platform | hostname routing |
| `wms.local` | wms-platform | hostname routing |
| `gap.local` | global-account-platform | hostname routing |
| `fan-platform.local` | fan-platform | hostname routing |
| `scm.local` | scm-platform | hostname routing from bootstrap |
| `erp.local` | erp-platform | hostname routing from bootstrap |
| `mes.local` | mes-platform | hostname routing from bootstrap |

New projects pick an unused `*.local` hostname and register it in this table in the bootstrap PR.

### One-time developer setup

Append to `/etc/hosts` (Linux/macOS) or `C:\Windows\System32\drivers\etc\hosts` (Windows):

```
127.0.0.1  ecommerce.local wms.local gap.local fan-platform.local scm.local erp.local mes.local
```

(Or run dnsmasq with `address=/.local/127.0.0.1` for a wildcard.)

### Adding a new project (greenfield)

1. Pick an unused `*.local` hostname; add the entry to the table above in the bootstrap PR.
2. In the project's `docker-compose.yml`, declare the gateway with Traefik labels (template above) and join `traefik-net` (external).
3. Backing services use `expose:` only — never `ports:`.
4. Project's `.env.example` declares `PROJECT_HOSTNAME=<name>.local` for documentation. (No `PORT_PREFIX` needed.)
5. README documents the hostname.

### Database / queue tools (DBeaver, Redis Insight, Kafka UI)

External tools that need direct TCP access to backing services use one of:

1. **`docker exec`** — `docker exec -it <project>-postgres psql -U ...` (no host port needed).
2. **Per-developer `docker-compose.dev.yml` overlay** — adds `ports:` for the local machine only, never committed.
3. **Traefik TCP routing** — declare a TCP router with a unique hostname (e.g., `wms-postgres.local:5432` via `entryPoints: [postgres]` on Traefik). Documented in `docs/guides/dev-tooling.md`.

### Legacy `PORT_PREFIX` (removed by TASK-MONO-024)

The original three projects (ecommerce, wms, global-account-platform) used to declare `${PORT_PREFIX:-N}XXXX:YYYY` host ports under prefixes 1/2/3. TASK-MONO-024 migrated all three to hostname routing. `fan-platform` was bootstrapped directly with hostname routing (no `PORT_PREFIX` ever used). All four active projects — ecommerce, wms, GAP, fan-platform — now use `*.local` hostname routing exclusively. `PORT_PREFIX` is no longer referenced anywhere in `projects/`. New projects must not introduce it.

5-digit source ports (e.g. Jaeger UI `16686` in ecommerce) are kept as-is; they remain unprefixed and continue to publish on the host because collisions with other projects are unlikely in practice.

### Validation

```bash
# After Traefik adoption, only Traefik should publish host ports
docker compose --project-directory infra/traefik config | grep published
# Expected: 80, 443 (and optionally 8080 for Traefik dashboard)

# Project-level: no published host ports (everything internal)
docker compose --project-directory projects/<name> config | grep published
# Expected: empty (Traefik routes by hostname)

# Cross-project: hostname-based access works
curl -i http://wms.local/actuator/health
curl -i http://ecommerce.local/actuator/health
# Expected: 200 OK from each
```

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

## Standalone Portfolio Sync and Freeze Policy

`scripts/sync-portfolio.sh` extracts each project from the monorepo into its own standalone GitHub repository (full history, filter-repo based). This allows individual project repos suitable for portfolio submission while keeping development in the monorepo.

### How standalone sync works

```
monorepo main → filter-repo (keep SHARED_PATHS + projects/<name>/) → hoist to root → post-process → force-push standalone repo
```

| Config key | Where | Purpose |
|---|---|---|
| `PROJECT_REMOTES["<name>"]` | `sync-portfolio.sh` | Target GitHub remote URL |
| `PROJECT_TYPES["<name>"]` | `sync-portfolio.sh` | `direct-include` (default) or `composite-build` (fallback) |
| `PROJECT_EXCLUDE_PATHS["<name>"]` | `sync-portfolio.sh` | Paths excluded from standalone — see below |

### PROJECT_EXCLUDE_PATHS — standalone freeze policy

A project's standalone repo is not always identical to the monorepo. When a project undergoes a **major integration cutover** (e.g., migrating from a self-hosted auth service to GAP OIDC), the standalone v1 may intentionally be **frozen** at the pre-cutover state to preserve the v1 demo intact.

**Use `PROJECT_EXCLUDE_PATHS` when:**

- The monorepo has a breaking integration change that the standalone v1 must NOT receive (e.g., auth-service decommission, docker-compose overhaul, GAP OIDC cutover).
- The standalone's demo depends on components that are removed or replaced in the monorepo.

**Do NOT use `PROJECT_EXCLUDE_PATHS` for:**

- Routine bug fixes or feature additions that the standalone should receive.
- CI workflow improvements that help standalone repo CI.

**Current freeze example — ecommerce standalone v1:**

`ecommerce-microservices-platform` standalone is frozen at the state prior to the GAP OIDC cutover (TASK-MONO-027 + TASK-FE-067 + TASK-BE-132). The standalone v1 preserves the legacy self-hosted ecommerce auth-service (JWT issuer, signup, Google OAuth) so the standalone repo demonstrates an end-to-end JWT-issuing service without requiring GAP as a transitive dependency.

The following path groups are excluded from the ecommerce standalone sync:

- **GROUP A (TASK-FE-067)**: frontend NextAuth v5 + GAP OIDC cutover (web-store / admin-dashboard auth files)
- **GROUP B (TASK-BE-132)**: backend auth-service decommission (docker-compose × 3, .env.example, k8s, gateway application.yml, spec rename, deprecated contracts, deprecated feature specs)

### Dual-deploy strategy

Each project has two publication surfaces:

| Surface | Audience | State |
|---|---|---|
| Dev monorepo (`kanggle/monorepo-lab`) | Technical reviewers / AI agents | Always latest |
| Standalone project repo (`kanggle/<project>`) | Portfolio reviewers with time budget | Curated snapshot (may be frozen) |

When a project's standalone is frozen, the standalone `README.md` should document the freeze point and explain that the monorepo version is more recent. See `project_portfolio_submission_strategy.md` in the memory layer for the full dual-deploy rationale.

### Dry-run validation

```bash
./scripts/sync-portfolio.sh --dry-run <project>
# Shows: kept paths + excluded paths (PROJECT_EXCLUDE_PATHS)
```

---

## GAP IdP Integration Pattern (New Projects)

As of ADR-001 (ACCEPTED 2026-05-01), **global-account-platform (GAP) is the standard OIDC IdP** for all monorepo projects. New projects do **not** implement their own auth service — they integrate with GAP from bootstrap.

This applies to: `fan-platform`, `wms`, `ecommerce` (post-TASK-MONO-027), and all future projects.

### 1. Tenant registration

Before any code, register the new domain as a GAP tenant via the admin API:

```
POST /api/admin/tenants
Authorization: Bearer <super-admin-token>

{
  "tenantId": "<domain>",          # e.g. "erp", "scm", "mes"
  "displayName": "...",
  "tenantType": "B2B_ENTERPRISE"   # or B2C_CONSUMER
}
```

Full procedure: `projects/global-account-platform/specs/features/consumer-integration-guide.md § Phase 1`.

### 2. OIDC client registration (Flyway seed)

Register the new OIDC client(s) via a GAP Flyway seed migration (pattern: `V00XX__<description>.sql`). Reference existing seeds (V0010 = wms, V0011 = fan-platform, V0012 = ecommerce) for the INSERT pattern.

Typical client registration includes:

- `authorization_code` + PKCE (user-facing flows)
- `refresh_token`
- `client_credentials` (service-to-service, optional)
- Redirect URIs for dev / staging / prod
- Domain-specific scopes (e.g., `wms.inventory.read`)

The seed lives in `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/`.

### 3. Gateway — OAuth2 Resource Server

The new project's gateway service must be configured as an OAuth2 Resource Server that validates GAP's RS256 JWT:

```yaml
# application.yml (gateway-service)
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URL}   # http://gap.local  (dev — no trailing /oauth2/)
          jwk-set-uri: ${JWT_JWKS_URI}     # http://gap.local/oauth2/jwks
```

> Use `OIDC_ISSUER_URL` (not `OIDC_ISSUER_URI`) — this aligns with the `.env.example` variable name used by all existing projects (ecommerce, fan-platform). `JWT_JWKS_URI` is the conventional env var name for the JWKS endpoint; `OIDC_JWKS_URI` is an alias that also works but less common in the codebase. Set `issuer-uri` to `http://gap.local` (no `/oauth2/` path) — Spring Security's OpenID discovery appends `/.well-known/openid-configuration` automatically; the issuer in the issued JWT must match exactly.

Add a `TenantClaimValidator` (or equivalent) that rejects tokens with `tenant_id` != `<domain>`. Reference: `projects/ecommerce-microservices-platform/apps/gateway-service/` (post-TASK-MONO-027).

### 4. PROJECT.md — declare GAP IdP integration

Add a `## GAP IdP Integration` section to the project's `PROJECT.md` (see `projects/wms-platform/PROJECT.md` and `projects/fan-platform/PROJECT.md` as templates):

```markdown
## GAP IdP Integration

`<project>` uses [global-account-platform](../global-account-platform/PROJECT.md) (GAP)
as the standard OIDC IdP ([ADR-001](../global-account-platform/docs/adr/ADR-001-oidc-adoption.md)).
All <project> services validate GAP RS256 access tokens as OAuth2 Resource Servers and
pass only `tenant_id=<domain>` tokens.
Integration detail: [specs/integration/gap-integration.md](specs/integration/gap-integration.md).
```

### 5. Full integration guide

`projects/global-account-platform/specs/features/consumer-integration-guide.md` is the **single reference** for new consumers. It covers all 6 phases: tenant registration → OIDC client setup → gateway RS config → frontend PKCE flow → event subscription → operational checklist.

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

## Task Lifecycle and PR Separation Rule

Both root-level (`tasks/`) and project-level (`projects/<name>/tasks/`) task lifecycles follow the same PR Separation Rule defined in `tasks/INDEX.md`:

| Stage | Recommended PR shape |
|---|---|
| `(writing) → ready` | **spec PR** — adds the task file to `ready/` + updates `INDEX.md` ready list. No implementation code. |
| `ready → in-progress → review` | **impl PR** — moves the task file through `in-progress/` to `review/` and lands the implementation. Lifecycle moves and impl commits are separate commits but live in one PR. |
| `review → done` | **chore PR** — moves merged task file(s) from `review/` to `done/` + updates `INDEX.md` done list. May batch multiple merged tasks. |

**Why**: bundling spec authoring with implementation hides the `ready` lifecycle stage from `main`. External observers (AI sessions, other developers, audits) read the `ready/` queue to know what's available next. If a task only ever appears in `review/` because spec + impl shipped together, the queue signal is broken.

The root `tasks/INDEX.md § PR Separation Rule` is the authoritative definition. This section is a summary for quick reference during project bootstrap.

---

## Periodic Consistency Audit

After any major cutover (new project join, GAP IdP migration, Traefik hostname migration, shared library promotion, or similar), run a consistency audit:

| Audit scope | Reference task |
|---|---|
| `rules/` + `.claude/config/` 4-way sync | TASK-MONO-029 pattern |
| Spec drift across all project `architecture.md` files | TASK-MONO-030 pattern |
| `libs/` usage frequency + shared-library-policy compliance | TASK-MONO-031 pattern |
| `.claude/skills/agents/commands/hooks` index ↔ file sync | TASK-MONO-032 pattern |
| `TEMPLATE.md` ↔ monorepo state | TASK-MONO-033 pattern (this task) |

**Recommended audit trigger**: after each major platform-level cutover (e.g., adding a 4th or 5th project, completing a cross-project migration) or at minimum quarterly if multiple projects are co-developed.

The TASK-MONO-029~033 series (공통규칙 정리 시리즈, 2026-05-04) established the audit baseline for the 4-project (wms / ecommerce / GAP / fan-platform) monorepo state.

---

## ADR Index (Monorepo Level)

| ADR | Title | Status | Date |
|---|---|---|---|
| [ADR-MONO-001](docs/adr/ADR-MONO-001-port-prefix-scaling.md) | Hostname-based routing via Traefik (replace PORT_PREFIX) | **ACCEPTED** | 2026-05-02 |

Project-level ADRs live in `projects/<name>/docs/adr/`. The most significant project-level ADR affecting all consumers:

| ADR | Title | Status | Date |
|---|---|---|---|
| [GAP ADR-001](projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md) | GAP as standard OIDC Authorization Server (Spring Authorization Server) | **ACCEPTED** | 2026-05-01 |

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

# No PORT_PREFIX references in any project (hostname routing enforced)
grep -rn "PORT_PREFIX" projects/ 2>/dev/null
# Expected: no matches

# Each active project has Traefik labels in docker-compose.yml
for p in wms-platform ecommerce-microservices-platform global-account-platform fan-platform; do
  echo "=== $p ==="
  grep -A2 "traefik.enable" projects/$p/docker-compose.yml 2>/dev/null || echo "MISSING Traefik labels"
done
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

- `CLAUDE.md` — operating rules (Repository Layout, Hard Stop rules, Local Network Convention summary)
- `rules/README.md` — rule library architecture and on-demand policy
- `tasks/INDEX.md` — root task lifecycle + PR Separation Rule (authoritative)
- `scripts/sync-portfolio.sh` — portfolio extraction tool (PROJECT_REMOTES, PROJECT_TYPES, PROJECT_EXCLUDE_PATHS)
- `docs/adr/ADR-MONO-001-port-prefix-scaling.md` — hostname routing ADR (ACCEPTED)
- `projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md` — GAP OIDC AS ADR (ACCEPTED)
- `projects/global-account-platform/specs/features/consumer-integration-guide.md` — GAP consumer integration (single reference)
- `docs/guides/` — human-oriented workflow guides (when present)
