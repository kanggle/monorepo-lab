#!/usr/bin/env bash
# extract-template.sh — produce a clean single-project template repo from monorepo
#
# Usage:
#   ./scripts/extract-template.sh <target-dir>
#   ./scripts/extract-template.sh --dry-run <target-dir>
#   ./scripts/extract-template.sh --init-git <target-dir>
#   ./scripts/extract-template.sh --verbose <target-dir>
#
# Flags can be combined: --dry-run --verbose <target-dir>
#
# <target-dir> must not exist or be empty (unless --dry-run).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MONOREPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SOURCE_SHA="$(git -C "$MONOREPO_ROOT" rev-parse HEAD 2>/dev/null || echo "unknown")"

# ───────────────────────── Flags ─────────────────────────

DRY_RUN=0
INIT_GIT=0
VERBOSE=0
TARGET_DIR=""

for arg in "$@"; do
    case "$arg" in
        --dry-run)  DRY_RUN=1 ;;
        --init-git) INIT_GIT=1 ;;
        --verbose)  VERBOSE=1 ;;
        -h|--help)
            cat <<EOF
Usage: $0 [--dry-run] [--init-git] [--verbose] <target-dir>

Produce a clean single-project template repo at <target-dir>.

Flags:
  --dry-run   List what would be copied without writing anything.
  --init-git  After copy, run: git init && git add -A && git commit.
  --verbose   Print each file as it is copied.
  --help      Show this message.

<target-dir> must not exist or must be empty (unless --dry-run).
On repeated runs, remove the directory first: rm -rf <target-dir>
EOF
            exit 0
            ;;
        *)  TARGET_DIR="$arg" ;;
    esac
done

# ───────────────────────── Helpers ─────────────────────────

log()     { printf '[extract] %s\n' "$*"; }
verbose() { [ "$VERBOSE" = "1" ] && printf '[extract] %s\n' "$*" || true; }
drylog()  { [ "$DRY_RUN" = "1" ] && printf '[dry-run] %s\n' "$*" || true; }

FILE_COUNT=0
BYTE_COUNT=0

copy_tree() {
    # copy_tree <src> <dst> [extra rsync excludes...]
    local src="$1"
    local dst="$2"
    shift 2

    if [ "$DRY_RUN" = "1" ]; then
        drylog "would copy tree: $src -> $dst"
        if [ -d "$src" ]; then
            local cnt
            cnt=$(find "$src" -type f \
                ! -path '*/.gradle/*' \
                ! -path '*/build/*' \
                ! -path '*/.idea/*' \
                ! -path '*/node_modules/*' \
                | wc -l)
            drylog "  (approx $cnt files)"
        fi
        return
    fi

    mkdir -p "$dst"
    # rsync with build-artifact excludes; caller may pass additional excludes
    local rsync_args=(-a --exclude='.gradle/' --exclude='build/' --exclude='.idea/' --exclude='node_modules/')
    for ex in "$@"; do
        rsync_args+=(--exclude="$ex")
    done
    if [ "$VERBOSE" = "1" ]; then
        rsync_args+=(--verbose)
    fi
    if command -v rsync >/dev/null 2>&1; then
        rsync "${rsync_args[@]}" "$src/" "$dst/"
    else
        # fallback: cp with manual pruning (rsync not available on all systems)
        cp -r "$src/." "$dst/"
        # Remove build artifacts that cp -r may have included
        find "$dst" -type d \( -name '.gradle' -o -name 'build' -o -name '.idea' -o -name 'node_modules' \) -exec rm -rf {} + 2>/dev/null || true
    fi
    local cnt
    cnt=$(find "$dst" -type f | wc -l)
    FILE_COUNT=$((FILE_COUNT + cnt))
    verbose "copied $src -> $dst ($cnt files)"
}

copy_file() {
    local src="$1"
    local dst="$2"
    if [ "$DRY_RUN" = "1" ]; then
        drylog "would copy file: $src -> $dst"
        return
    fi
    if [ -f "$src" ]; then
        mkdir -p "$(dirname "$dst")"
        cp "$src" "$dst"
        FILE_COUNT=$((FILE_COUNT + 1))
        verbose "copied $src -> $dst"
    else
        verbose "skip (not found): $src"
    fi
}

touch_gitkeep() {
    local path="$1"
    if [ "$DRY_RUN" = "1" ]; then
        drylog "would create: $path/.gitkeep"
        return
    fi
    mkdir -p "$path"
    touch "$path/.gitkeep"
    FILE_COUNT=$((FILE_COUNT + 1))
    verbose "created $path/.gitkeep"
}

write_file() {
    local dst="$1"
    # Content comes from heredoc via stdin
    if [ "$DRY_RUN" = "1" ]; then
        drylog "would write: $dst"
        return
    fi
    mkdir -p "$(dirname "$dst")"
    cat > "$dst"
    FILE_COUNT=$((FILE_COUNT + 1))
    verbose "wrote $dst"
}

# ───────────────────────── Pre-flight ─────────────────────────

if [ -z "$TARGET_DIR" ]; then
    printf 'Error: <target-dir> is required.\nRun: %s --help\n' "$0" >&2
    exit 1
fi

if [ "$DRY_RUN" = "0" ]; then
    if [ -e "$TARGET_DIR" ]; then
        # Non-empty check: count any file/dir inside
        local_count=$(find "$TARGET_DIR" -mindepth 1 -maxdepth 1 | wc -l)
        if [ "$local_count" -gt 0 ]; then
            printf 'Error: <target-dir> already exists and is non-empty: %s\nRemove it first: rm -rf %s\n' \
                "$TARGET_DIR" "$TARGET_DIR" >&2
            exit 1
        fi
    fi
fi

log "Monorepo root: $MONOREPO_ROOT"
log "Target dir:    $TARGET_DIR"
log "Source SHA:    $SOURCE_SHA"
[ "$DRY_RUN" = "1" ] && log "Mode: DRY-RUN (no writes)"
[ "$DRY_RUN" = "0" ] && log "Mode: LIVE"

# ───────────────────────── Step 1: Copy shared library trees ─────────────────────────

log "Copying shared library layer..."

copy_tree "$MONOREPO_ROOT/.claude"         "$TARGET_DIR/.claude"
copy_tree "$MONOREPO_ROOT/platform"        "$TARGET_DIR/platform"
copy_tree "$MONOREPO_ROOT/rules"           "$TARGET_DIR/rules"
copy_tree "$MONOREPO_ROOT/libs"            "$TARGET_DIR/libs"
copy_tree "$MONOREPO_ROOT/tasks/templates" "$TARGET_DIR/tasks/templates"
copy_tree "$MONOREPO_ROOT/docs/guides"     "$TARGET_DIR/docs/guides"

# ───────────────────────── Step 2: Copy root files ─────────────────────────

log "Copying root files..."

# CLAUDE.md and TEMPLATE.md: annotate at top to clarify this is a template distribution
if [ "$DRY_RUN" = "0" ]; then
    mkdir -p "$TARGET_DIR"
    for md_file in CLAUDE.md TEMPLATE.md; do
        if [ -f "$MONOREPO_ROOT/$md_file" ]; then
            {
                printf '<!-- NOTE: this file is template-distributed from monorepo-lab.\n'
                printf '     Project-specific edits go in projects/<name>/... not here. -->\n\n'
                cat "$MONOREPO_ROOT/$md_file"
            } > "$TARGET_DIR/$md_file"
            FILE_COUNT=$((FILE_COUNT + 1))
            verbose "wrote annotated $md_file"
        fi
    done
else
    drylog "would write annotated: $TARGET_DIR/CLAUDE.md"
    drylog "would write annotated: $TARGET_DIR/TEMPLATE.md"
fi

# Gradle root files
for f in build.gradle gradle.properties gradlew gradlew.bat; do
    copy_file "$MONOREPO_ROOT/$f" "$TARGET_DIR/$f"
done
copy_tree "$MONOREPO_ROOT/gradle" "$TARGET_DIR/gradle"

# Repo meta files
for f in .gitignore .gitattributes .editorconfig .npmrc; do
    copy_file "$MONOREPO_ROOT/$f" "$TARGET_DIR/$f"
done

# ───────────────────────── Step 3: infra/traefik (shared dev infra) ─────────────────────────

if [ -d "$MONOREPO_ROOT/infra/traefik" ]; then
    log "Copying infra/traefik (shared Traefik dev infra)..."
    copy_tree "$MONOREPO_ROOT/infra/traefik" "$TARGET_DIR/infra/traefik"
fi

# ───────────────────────── Step 4: scripts (sanitized) ─────────────────────────

log "Copying scripts (sanitized)..."

if [ "$DRY_RUN" = "0" ]; then
    mkdir -p "$TARGET_DIR/scripts"
    # Copy this script itself
    cp "$SCRIPT_DIR/extract-template.sh"           "$TARGET_DIR/scripts/extract-template.sh"
    cp "$SCRIPT_DIR/verify-template-readiness.sh"  "$TARGET_DIR/scripts/verify-template-readiness.sh"
    FILE_COUNT=$((FILE_COUNT + 2))

    # sync-portfolio.sh: copy as .example with PROJECT_REMOTES / PROJECT_TYPES /
    # PROJECT_EXCLUDE_PATHS maps cleared to <placeholder> rows.
    if [ -f "$SCRIPT_DIR/sync-portfolio.sh" ]; then
        sed \
            -e '/^declare -A PROJECT_REMOTES=/,/^)/{
                /^declare -A PROJECT_REMOTES=/{ n; s/.*/    ["<project-name>"]="<remote-url>"/ }
                /^\["[^<]/d
            }' \
            -e '/^declare -A PROJECT_TYPES=/,/^)/{
                /^declare -A PROJECT_TYPES=/{ n; s/.*/    ["<project-name>"]="direct-include"/ }
                /^\["[^<]/d
            }' \
            -e '/^declare -A PROJECT_EXCLUDE_PATHS=/,/^)/{
                /^declare -A PROJECT_EXCLUDE_PATHS=/{ n; s/.*/    # ["<project-name>"]="path1 path2"/ }
                /^\["[^<]/d
            }' \
            "$SCRIPT_DIR/sync-portfolio.sh" > "$TARGET_DIR/scripts/sync-portfolio.sh.example"
        FILE_COUNT=$((FILE_COUNT + 1))
        verbose "wrote sanitized scripts/sync-portfolio.sh.example"
    fi
else
    drylog "would copy: scripts/extract-template.sh"
    drylog "would copy: scripts/verify-template-readiness.sh"
    drylog "would write sanitized: scripts/sync-portfolio.sh.example"
fi

# ───────────────────────── Step 5: Replace settings.gradle ─────────────────────────

log "Writing template settings.gradle..."
write_file "$TARGET_DIR/settings.gradle" <<'SETTINGS'
rootProject.name = '<project-name>'

// Shared libraries (at repo root — do not remove or rename these)
include(
    'libs:java-common',
    'libs:java-messaging',
    'libs:java-observability',
    'libs:java-security',
    'libs:java-test-support',
    'libs:java-web',
    'libs:java-web-servlet'
)

// Your project services — rename <project-name> to your actual project directory name,
// and add one entry per app under projects/<project-name>/apps/<service>/.
//
// Example:
//   include(
//       'projects:<project-name>:apps:gateway-service',
//       'projects:<project-name>:apps:my-service'
//   )
//
// TODO: replace the placeholder below with your actual project includes.
// include(
//     'projects:<project-name>:apps:<service>'
// )
SETTINGS

# ───────────────────────── Step 6: Write template README.md ─────────────────────────

log "Writing template README.md..."
write_file "$TARGET_DIR/README.md" <<'README_EOF'
# Project Template

This repository is a **template** extracted from the monorepo-lab shared library.

## Getting started

1. Click "Use this template" (or clone and reinitialise git).
2. Rename `projects/<placeholder>/` to your actual project name.
3. Rename `projects/<placeholder>/PROJECT.md.example` to `PROJECT.md` and fill in the `## TODO` sections.
4. Update `settings.gradle` — replace the placeholder include block with your project's services.
5. Read `CLAUDE.md` for operating rules and `TEMPLATE.md` for the full bootstrap guide.

## Readiness and extraction

To verify the shared library is ready for extraction from the source monorepo, run:

```bash
bash scripts/verify-template-readiness.sh
```

When that exits 0, run the extraction:

```bash
bash scripts/extract-template.sh <target-dir>
```

See `TEMPLATE.md` for the full Phase 5 guide.

## Structure

| Path | Role |
|---|---|
| `.claude/` | AI agent config: skills, agents, commands |
| `platform/` | Platform-wide regulations |
| `rules/` | Domain and trait rule libraries |
| `libs/` | Shared Java libraries |
| `tasks/templates/` | Task templates |
| `docs/guides/` | Human-oriented workflow guides |
| `projects/<name>/` | Your project (rename from `<placeholder>`) |
README_EOF

# ───────────────────────── Step 7: Write template root tasks/INDEX.md ─────────────────────────

log "Writing template root tasks/INDEX.md..."
write_file "$TARGET_DIR/tasks/INDEX.md" <<'TASKS_INDEX'
# Tasks Index — Monorepo Root

This lifecycle is reset for fresh template usage. Populate with your monorepo-level tasks.

Repo-root tasks cover cross-project changes: shared library (`libs/`, `platform/`, `rules/`, `.claude/`),
root build files, CI workflows, and structural monorepo changes.

---

## Lifecycle

ready -> in-progress -> review -> done

Only tasks in `ready/` may be implemented.

---

## When to Use Root vs Project Tasks

- Changes confined to a single `projects/<name>/`: use that project's `tasks/`.
- Changes to shared paths (`libs/`, `platform/`, `rules/`, `.claude/`, root `build.gradle`,
  `settings.gradle`, `.github/workflows/`, `scripts/`, `CLAUDE.md`, `TEMPLATE.md`) or
  cross-project structural changes: use root `tasks/`.

---

## PR Separation Rule

| Stage | Recommended PR shape |
|---|---|
| `(writing) -> ready` | spec PR — adds task file to `ready/` + updates this INDEX.md ready list. No implementation code. |
| `ready -> in-progress -> review` | impl PR — lifecycle move + implementation in one PR. Lifecycle and impl as separate commits. |
| `review -> done` | chore PR — moves merged task file(s) from `review/` to `done/` + updates done list. May batch. |

---

## Task List

### ready

(empty)

### in-progress

(empty)

### review

(empty)

### done

(empty)
TASKS_INDEX

# ───────────────────────── Step 8: Create empty project shell ─────────────────────────

log "Creating empty single-project shell at projects/<placeholder>/..."

SHELL_BASE="$TARGET_DIR/projects/<placeholder>"

# Directory stubs with .gitkeep
touch_gitkeep "$SHELL_BASE/apps"
touch_gitkeep "$SHELL_BASE/specs/contracts/http"
touch_gitkeep "$SHELL_BASE/specs/contracts/events"
touch_gitkeep "$SHELL_BASE/specs/services"
touch_gitkeep "$SHELL_BASE/specs/features"
touch_gitkeep "$SHELL_BASE/specs/use-cases"
touch_gitkeep "$SHELL_BASE/specs/integration"
touch_gitkeep "$SHELL_BASE/tasks/backlog"
touch_gitkeep "$SHELL_BASE/tasks/ready"
touch_gitkeep "$SHELL_BASE/tasks/in-progress"
touch_gitkeep "$SHELL_BASE/tasks/review"
touch_gitkeep "$SHELL_BASE/tasks/done"
touch_gitkeep "$SHELL_BASE/tasks/archive"
touch_gitkeep "$SHELL_BASE/knowledge"
touch_gitkeep "$SHELL_BASE/docs"
touch_gitkeep "$SHELL_BASE/infra"

# PROJECT.md.example
write_file "$SHELL_BASE/PROJECT.md.example" <<'PROJECT_EOF'
---
name: <project-name>
domain: <domain>               # TODO: must be in rules/taxonomy.md
traits: [<trait>, ...]         # TODO: each must be in rules/taxonomy.md
service_types: [rest-api, event-consumer]
compliance: []
data_sensitivity: internal
scale_tier: startup
taxonomy_version: 0.1
---

## TODO: Purpose

Describe what this project does and what problem it solves.

## TODO: Domain Rationale

Explain why this domain classification was chosen over alternatives.

## TODO: Trait Rationale

Explain why each declared trait was chosen and what surface it activates.

## TODO: Service Map

| Service | Type | Role |
|---|---|---|
| gateway-service | rest-api | API gateway, JWT validation, routing |
| <service-name> | <type> | <role> |

## TODO: GAP IdP Integration

Describe how this project integrates with the Global Account Platform (GAP) OIDC IdP.
Reference: projects/iam-platform/specs/features/consumer-integration-guide.md

## TODO: Out of Scope

List features and concerns explicitly not covered by this project.

## TODO: Overrides

List any shared platform/ or rules/ behaviors this project overrides, with justification.
Each override must reference the specific rule being relaxed and include an ## Overrides block.
PROJECT_EOF

# README.md.example
write_file "$SHELL_BASE/README.md.example" <<'PROJ_README'
# <project-name>

## Overview

TODO: brief description of the project.

## Services

| Service | Port (local) | Role |
|---|---|---|
| gateway-service | via <project-name>.local | API gateway |

## Quick start

```bash
docker compose up -d
```

Access via: http://<project-name>.local (Traefik hostname routing — see CLAUDE.md)

## Development

- Specs: `specs/`
- Tasks: `tasks/`
- Architecture: `specs/services/<service>/architecture.md`
PROJ_README

# build.gradle.example
write_file "$SHELL_BASE/build.gradle.example" <<'BUILD_EOF'
// Project-level build.gradle placeholder.
// This file is intentionally minimal — individual service build.gradle files
// under apps/<service>/ declare their own dependencies.
//
// Rename to build.gradle and customise if project-level configuration is needed.
BUILD_EOF

# docker-compose.yml.example — Traefik hostname routing template (from TEMPLATE.md Option A Step 4)
write_file "$SHELL_BASE/docker-compose.yml.example" <<'DC_EOF'
# docker-compose.yml — <project-name>
# Traefik hostname routing (see CLAUDE.md § Local Network Convention)
# Access via: http://<project-name>.local

services:
  gateway:
    image: <project-name>-gateway:latest
    expose: ["8080"]
    labels:
      - "traefik.enable=true"
      - "traefik.docker.network=traefik-net"
      - "traefik.http.routers.<project-name>.rule=Host(`<project-name>.local`)"
      - "traefik.http.routers.<project-name>.entrypoints=web"
      - "traefik.http.services.<project-name>.loadbalancer.server.port=8080"
    networks:
      - traefik-net
      - <project-name>-net

  postgres:
    image: postgres:16-alpine
    expose: ["5432"]       # no host port — DB tools via docker exec or dev overlay
    environment:
      POSTGRES_DB: <project-name>_db
      POSTGRES_USER: app
      POSTGRES_PASSWORD: ${DB_PASSWORD:-changeme}
    networks:
      - <project-name>-net

networks:
  traefik-net:
    external: true
    name: traefik-net      # explicit name so compose project-name prefix is not prepended
  <project-name>-net:
    driver: bridge
DC_EOF

# .env.example — PROJECT_HOSTNAME + GAP OIDC vars (from TEMPLATE.md Option A Step 5)
write_file "$SHELL_BASE/.env.example" <<'ENV_EOF'
# Hostname (Traefik routing — no PORT_PREFIX)
PROJECT_HOSTNAME=<project-name>.local

# GAP OIDC (if integrating with GAP IdP)
# OIDC_ISSUER_URL: GAP's issuer base URL — no trailing /oauth2/ path.
#   Spring Security appends /.well-known/openid-configuration automatically.
OIDC_ISSUER_URL=http://iam.local
# JWT_JWKS_URI: explicit JWKS endpoint (avoids OpenID discovery round-trip in dev).
JWT_JWKS_URI=http://iam.local/oauth2/jwks
ENV_EOF

# tasks/INDEX.md.example — structural shell from scm-platform/tasks/INDEX.md (task list stripped)
write_file "$SHELL_BASE/tasks/INDEX.md.example" <<'TASK_INDEX_EOF'
# Tasks Index — <project-name>

This document defines task lifecycle, naming, and move rules for the **<project-name>** project.
Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks;
this file covers <project-name>-internal tasks only.

---

# Lifecycle

backlog -> ready -> in-progress -> review -> done -> archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-<PREFIX>-BE-XXX`: backend (Spring Boot service implementations)
- `TASK-<PREFIX>-INT-XXX`: cross-service integration / E2E (Testcontainers, Docker Compose)
- `TASK-<PREFIX>-FE-XXX`: frontend

TODO: replace <PREFIX> with your project's task prefix (e.g. WMS, ECO, GAP, SCM).

---

# Move Rules

## backlog -> ready
Allowed only when:
- related specs exist
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready -> in-progress
Allowed only when implementation starts.

## in-progress -> review
Allowed only when:
- implementation is complete
- tests are added
- contract / spec updates are completed if required

## review -> done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/`.
- Fix tasks must include the original task ID in their Goal section.
- Do not modify a task file after it moves to `review/` or `done/`.

### PR Separation Rule (lifecycle <-> PR boundary)

Each lifecycle transition lands in its own PR. Never bundle spec authoring with implementation.

| Stage | Recommended PR shape |
|---|---|
| `(writing) -> ready` | spec PR — adds the task file to `ready/` + updates this INDEX.md ready list. No implementation code. |
| `ready -> in-progress -> review` | impl PR — moves through `in-progress/` to `review/` + implementation. Lifecycle and impl as separate commits in one PR. |
| `review -> done` | chore PR — moves merged task file(s) from `review/` to `done/` + updates done list. May batch multiple tasks. |

The repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) is the authoritative definition. This summary applies the same rule at the project level.

## done -> archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty)

## ready

(empty)

## in-progress

(empty)

## review

(empty)

## done

(empty)

## archive

(empty)
TASK_INDEX_EOF

# ───────────────────────── Step 9: Compute final size ─────────────────────────

if [ "$DRY_RUN" = "0" ]; then
    BYTE_COUNT=$(du -sb "$TARGET_DIR" 2>/dev/null | awk '{print $1}' || \
                 du -sk "$TARGET_DIR" 2>/dev/null | awk '{print $1*1024}' || echo 0)
fi

# ───────────────────────── Step 10: Optional git init ─────────────────────────

if [ "$INIT_GIT" = "1" ] && [ "$DRY_RUN" = "0" ]; then
    log "Initialising git repository at $TARGET_DIR..."
    # Use -b main so the initial branch matches GitHub's default ("main") — avoids
    # a manual `git branch -m master main` step before pushing to a fresh GitHub
    # repo (TASK-MONO-073). Falls back to plain `init` if the git version
    # predates -b support (git < 2.28, unlikely on modern systems).
    if ! git -C "$TARGET_DIR" init -b main --quiet 2>/dev/null; then
        git -C "$TARGET_DIR" init --quiet
        git -C "$TARGET_DIR" symbolic-ref HEAD refs/heads/main 2>/dev/null || true
    fi
    git -C "$TARGET_DIR" add -A
    git -C "$TARGET_DIR" -c user.name="template-extract" \
        -c user.email="template-extract@monorepo-lab" \
        commit -m "initial template from monorepo-lab@$SOURCE_SHA" --quiet
    log "Git repository initialised (1 commit on main)."
elif [ "$INIT_GIT" = "1" ] && [ "$DRY_RUN" = "1" ]; then
    drylog "would run: git init -b main && git add -A && git commit -m 'initial template from monorepo-lab@$SOURCE_SHA'"
fi

# ───────────────────────── Summary ─────────────────────────

log ""
log "=== Summary ==="
if [ "$DRY_RUN" = "1" ]; then
    log "Mode:       DRY-RUN (no files written)"
else
    log "Files:      $FILE_COUNT"
    log "Size:       $BYTE_COUNT bytes"
    log "Target:     $TARGET_DIR"
fi
log "Source SHA: $SOURCE_SHA"
log "Done."
