#!/usr/bin/env bash
# Portfolio sync — monorepo-lab → individual project repos
#
# Extracts each project under projects/<name>/ into its own standalone
# GitHub repo with full git history preserved. Re-runnable via force-push.
#
# Usage:
#   ./scripts/sync-portfolio.sh              # sync all configured projects
#   ./scripts/sync-portfolio.sh wms-platform # sync single project
#   ./scripts/sync-portfolio.sh --dry-run    # show what would happen
#
# Requirements:
#   - bash + git
#   - docker (runs git-filter-repo in a container to avoid Python install)
#   - gh cli (for repo existence check; optional)
#
# Strategy:
#   1. Clone monorepo into a temp workdir (bare-ish: .git only needed)
#   2. Run git-filter-repo inside python:3-alpine container to:
#      a. Keep only shared-library paths + the target project's path
#      b. Hoist projects/<name>/ → root via --path-rename
#   3. Post-process: patch settings.gradle to drop projects/<name>/ prefix
#   4. Force-push to the target remote
#
# History preservation (a): full history. Only commits that touched
# kept paths remain; paths are rewritten throughout. SHAs change (expected).

set -euo pipefail

# ───────────────────────── Configuration ─────────────────────────

# Project → remote URL mapping. Add new projects here.
declare -A PROJECT_REMOTES=(
    ["wms-platform"]="https://github.com/kanggle/wms-platform.git"
)

# Shared paths kept at extracted repo root.
# NOTE: these are filter-repo --path values (file or directory prefix match).
SHARED_PATHS=(
    "libs/"
    "platform/"
    "rules/"
    ".claude/"
    "tasks/templates/"
    "docs/guides/"
    "build.gradle"
    "settings.gradle"
    "gradle/"
    "gradlew"
    "gradlew.bat"
    "gradle.properties"
    ".gitignore"
    ".gitattributes"
    ".dockerignore"
    ".editorconfig"
    ".github/"
    "CLAUDE.md"
    "TEMPLATE.md"
)

MONOREPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_ROOT="${TMPDIR:-/tmp}/portfolio-sync"
FILTER_REPO_IMAGE="python:3.11-alpine"

# ───────────────────────── Helpers ─────────────────────────

log()  { printf '\e[1;34m[sync]\e[0m %s\n' "$*"; }
warn() { printf '\e[1;33m[warn]\e[0m %s\n' "$*" >&2; }
fail() { printf '\e[1;31m[fail]\e[0m %s\n' "$*" >&2; exit 1; }

sync_project() {
    local project="$1"
    local dry_run="$2"
    local remote="${PROJECT_REMOTES[$project]:-}"

    [ -n "$remote" ] || fail "Unknown project: $project. Configure in PROJECT_REMOTES."
    [ -d "$MONOREPO_DIR/projects/$project" ] || fail "projects/$project not found in monorepo."

    log "Project:  $project"
    log "Remote:   $remote"
    log "Source:   $MONOREPO_DIR"

    local workdir="$TEMP_ROOT/$project"
    log "Workdir:  $workdir"

    if [ "$dry_run" = "1" ]; then
        log "[dry-run] Would clone monorepo, run filter-repo, force-push to $remote"
        log "[dry-run] Kept paths:"
        for p in "${SHARED_PATHS[@]}" "projects/$project/"; do
            printf '             %s\n' "$p"
        done
        return 0
    fi

    # ── Step 1: Clone ──
    rm -rf "$workdir"
    mkdir -p "$TEMP_ROOT"
    log "Cloning monorepo..."
    GIT_CLONE_PROTECTION_ACTIVE=false git clone --no-local --quiet "$MONOREPO_DIR" "$workdir"

    # ── Step 2: Build filter-repo args + run inside Docker ──
    log "Running git-filter-repo in Docker (this takes ~1 min)..."

    # Build --path arguments. Quote each path.
    local path_args=""
    for p in "${SHARED_PATHS[@]}" "projects/$project/"; do
        path_args+="--path '$p' "
    done
    # Path rename: projects/<project>/ -> (empty, hoists to root)
    local rename_arg="--path-rename 'projects/$project/:'"

    # Run the filter inside a container. Mount workdir as /repo.
    # MSYS_NO_PATHCONV=1 stops Git Bash from mangling the /repo paths into
    # C:/Program Files/Git/repo. --force because fresh clone has a remote,
    # filter-repo refuses by default.
    MSYS_NO_PATHCONV=1 docker run --rm \
        -v "$(cygpath -w "$workdir" 2>/dev/null || echo "$workdir"):/repo" \
        -w /repo \
        "$FILTER_REPO_IMAGE" \
        sh -c "
            apk add --no-cache git >/dev/null 2>&1 && \
            pip install --quiet git-filter-repo && \
            git config --global user.email 'sync@portfolio' && \
            git config --global user.name 'Portfolio Sync' && \
            git filter-repo --force $path_args $rename_arg
        "

    cd "$workdir"

    # ── Step 3: Post-process ──
    # After --path-rename, the project's placeholder build.gradle overwrote the
    # monorepo root build.gradle (which declared plugins + subprojects block).
    # Restore the real root build.gradle from the live monorepo and patch
    # settings.gradle colon-separated subproject paths + rootProject.name.
    log "Post-processing root build.gradle + settings.gradle..."

    cp "$MONOREPO_DIR/build.gradle" build.gradle

    # settings.gradle uses colon-separated include paths (Gradle convention)
    sed -i "s|'projects:$project:|'|g" settings.gradle
    sed -i "s|rootProject.name = '.*'|rootProject.name = '$project'|" settings.gradle

    # Patch CI workflows: colon Gradle task refs + slash file paths
    if [ -d .github/workflows ]; then
        for yml in .github/workflows/*.yml .github/workflows/*.yaml; do
            [ -f "$yml" ] || continue
            sed -i "s|:projects:$project:|:|g; s|projects/$project/||g" "$yml"
        done
    fi

    local changed=0
    git add build.gradle settings.gradle .github/workflows/ 2>/dev/null || true
    if ! git diff --cached --quiet; then
        git commit -m "chore(portfolio): restore root build.gradle + rewrite paths for standalone layout

Extraction via git-filter-repo --path-rename projects/$project/:
- overwrote the monorepo root build.gradle with the project's
  placeholder — restore from monorepo root (plugins + subprojects block)
- left settings.gradle with colon-separated include paths referencing
  the monorepo layout — rewrite to the hoisted layout
- left .github/workflows/*.yml with monorepo-style Gradle task refs
  and file paths — rewrite for standalone CI" --quiet
        changed=1
    fi

    # ── Step 4: Push ──
    log "Setting remote..."
    git remote remove origin 2>/dev/null || true
    git remote add origin "$remote"

    log "Force-pushing to $remote..."
    git push --force origin main

    log "✓ Sync complete: $project → $remote"
    log "  Commits pushed: $(git rev-list --count HEAD)"
}

# ───────────────────────── Main ─────────────────────────

main() {
    local dry_run=0
    local target=""

    for arg in "$@"; do
        case "$arg" in
            --dry-run) dry_run=1 ;;
            -h|--help)
                cat <<EOF
Usage: $0 [--dry-run] [PROJECT]

With no PROJECT: sync all configured projects.
With --dry-run: report what would be done without executing.

Configured projects:
EOF
                for p in "${!PROJECT_REMOTES[@]}"; do
                    printf '  %s → %s\n' "$p" "${PROJECT_REMOTES[$p]}"
                done
                exit 0
                ;;
            *) target="$arg" ;;
        esac
    done

    # Pre-flight checks
    command -v docker >/dev/null || fail "docker required (filter-repo runs in container)"
    command -v git >/dev/null || fail "git required"

    if [ -n "$target" ]; then
        sync_project "$target" "$dry_run"
    else
        for p in "${!PROJECT_REMOTES[@]}"; do
            sync_project "$p" "$dry_run"
        done
    fi
}

main "$@"
