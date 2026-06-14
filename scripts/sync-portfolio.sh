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
#   3. Post-process per integration type (see PROJECT_TYPES below):
#      - direct-include: restore monorepo-root build.gradle + sed-rewrite
#                        'projects:<name>:' include paths
#      - composite-build: trust filter-repo's hoisted settings.gradle +
#                         build.gradle; strip orphan includeBuild lines;
#                         replace monorepo-pointer CLAUDE.md
#   4. Force-push to the target remote
#
# History preservation (a): full history. Only commits that touched
# kept paths remain; paths are rewritten throughout. SHAs change (expected).

set -euo pipefail

# ───────────────────────── Configuration ─────────────────────────

# Project → remote URL mapping. Add new projects here.
declare -A PROJECT_REMOTES=(
    ["wms-platform"]="https://github.com/kanggle/wms-platform.git"
    ["ecommerce-microservices-platform"]="https://github.com/kanggle/ecommerce-microservices-platform.git"
    ["iam-platform"]="https://github.com/kanggle/iam-platform.git"
    ["fan-platform"]="https://github.com/kanggle/fan-platform.git"
    ["scm-platform"]="https://github.com/kanggle/scm-platform.git"
    ["finance-platform"]="https://github.com/kanggle/finance-platform.git"
    ["erp-platform"]="https://github.com/kanggle/erp-platform.git"
)

# Project → integration type. Governs how post-process rewrites gradle files.
#   direct-include — monorepo root `settings.gradle` declares each project
#                    subproject with `projects:<name>:...` paths; project has
#                    only a placeholder `build.gradle`. Post-process restores
#                    the monorepo root `build.gradle` and sed-rewrites the
#                    `projects:<name>:` prefixes.
#   composite-build — monorepo root `settings.gradle` pulls the project in via
#                     `includeBuild('projects/<name>')`; the project owns its
#                     own real `settings.gradle` + `build.gradle` with its own
#                     `apps:...` / `libs:...` include paths (no monorepo prefix).
#                     Post-process trusts filter-repo's --path-rename output
#                     and only strips the orphan monorepo `includeBuild(...)`
#                     line if filter-repo preserved it.
# Default (unmapped): direct-include — preserves backwards compatibility.
#
# Note: ecommerce-microservices-platform was originally imported as
# composite-build (commit 0956dc6) but consolidated onto direct-include
# in 2026-04-25 (PR #58) once its nested libs/ stack was merged into
# root libs/. The composite-build code path below is retained for
# future imports where library-naming conflicts make direct-include
# infeasible.
declare -A PROJECT_TYPES=(
    ["wms-platform"]="direct-include"
    ["ecommerce-microservices-platform"]="direct-include"
    ["iam-platform"]="direct-include"
    ["fan-platform"]="direct-include"
    ["scm-platform"]="direct-include"
    ["finance-platform"]="direct-include"
    ["erp-platform"]="direct-include"
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

# Per-project path EXCLUSIONS — applied via `git filter-repo --invert-paths`
# AFTER the main extraction. These are project-relative paths inside the
# project directory; they are stripped from history before the standalone
# repo is force-pushed.
#
# Why exclude paths from a project's standalone sync?
#   - The standalone portfolio repo is a curated snapshot, not always
#     identical to what's in the monorepo. For example: portfolio v1 of
#     `ecommerce-microservices-platform` (kanggle/ecommerce-microservices-platform)
#     is FROZEN at the state prior to the IAM OIDC cutover (TASK-MONO-027 +
#     TASK-FE-067 + TASK-BE-132). The standalone v1 deliberately preserves
#     the legacy self-hosted ecommerce auth-service (signup / login / refresh /
#     Google OAuth) so the standalone repo demonstrates an end-to-end
#     JWT-issuing service without requiring IAM as a transitive dependency.
#   - The monorepo has since cut over to IAM OIDC (auth-service decommissioned
#     from docker-compose / k8s / .env / gateway config / specs). These
#     IAM-cutover changes must NOT be synced into the standalone v1, or the
#     standalone's self-hosted auth-service demo will break.
#   - Solution: exclude all IAM-cutover-related paths from the ecommerce sync.
#     The ecommerce standalone v1 retains its own auth flow intact.
#
# ecommerce-microservices-platform exclusions split into two groups:
#   GROUP A — TASK-FE-067 (PR #148): frontend NextAuth v5 + IAM OIDC cutover
#   GROUP B — TASK-BE-132 (PR #150): backend auth-service decommission
#             (docker-compose / .env / k8s / gateway config / spec rename /
#              deprecated contracts / deprecated feature specs)
declare -A PROJECT_EXCLUDE_PATHS=(
    ["ecommerce-microservices-platform"]="\
apps/web-store/src/shared/auth \
apps/web-store/src/middleware.ts \
apps/web-store/src/app/api/auth \
apps/web-store/.env.local.example \
apps/web-store/e2e/helpers/auth.ts \
apps/web-store/e2e/account-type-guard.spec.ts \
specs/integration/iam-integration.md \
specs/services/web-store/architecture.md \
docker-compose.yml \
docker-compose.ci.yml \
docker-compose.bootrun.yml \
.env.example \
k8s \
apps/gateway-service/src/main/resources/application.yml \
specs/services/auth-service-deprecated \
specs/services/auth-service \
specs/contracts/http/auth-api.md \
specs/contracts/events/auth-events.md \
specs/features/authentication.md \
specs/features/user-management.md"
)

MONOREPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_ROOT="${TMPDIR:-/tmp}/portfolio-sync"
FILTER_REPO_IMAGE="python:3.11-alpine"

# ───────────────────────── Helpers ─────────────────────────

log()  { printf '\e[1;34m[sync]\e[0m %s\n' "$*"; }
warn() { printf '\e[1;33m[warn]\e[0m %s\n' "$*" >&2; }
fail() { printf '\e[1;31m[fail]\e[0m %s\n' "$*" >&2; exit 1; }

# ───────────────────────── Post-process: direct-include ─────────────────────────
# After --path-rename, the project's placeholder build.gradle overwrote the
# monorepo root build.gradle (which declared plugins + subprojects block).
# Restore the real root build.gradle and sed-rewrite the colon-separated
# `projects:<name>:` include paths.
post_process_direct_include() {
    local project="$1"

    log "  restoring monorepo root build.gradle..."
    cp "$MONOREPO_DIR/build.gradle" build.gradle

    log "  rewriting settings.gradle include paths..."
    # settings.gradle uses colon-separated include paths (Gradle convention)
    sed -i "s|'projects:$project:|'|g" settings.gradle
    sed -i "s|rootProject.name = '.*'|rootProject.name = '$project'|" settings.gradle

    # Patch CI workflows: colon Gradle task refs + slash file paths
    if [ -d .github/workflows ]; then
        log "  rewriting .github/workflows/*.yml task refs..."
        for yml in .github/workflows/*.yml .github/workflows/*.yaml; do
            [ -f "$yml" ] || continue
            sed -i "s|:projects:$project:|:|g; s|projects/$project/||g" "$yml"
        done
    fi

    # Patch subproject *.gradle files — cross-project dependsOn and
    # project(':projects:...') lookups are hardcoded with the monorepo prefix.
    # After hoisting, `:projects:<name>:apps:...` must become `:apps:...`.
    log "  rewriting subproject *.gradle files..."
    while IFS= read -r -d '' gradle_file; do
        if grep -q "projects:$project:\|projects/$project/" "$gradle_file"; then
            sed -i "s|:projects:$project:|:|g; s|projects/$project/||g" "$gradle_file"
        fi
    done < <(find apps libs -name "*.gradle" -print0 2>/dev/null)
}

direct_include_commit_msg() {
    local project="$1"
    cat <<EOF
chore(portfolio): restore root build.gradle + rewrite paths for standalone layout

Extraction via git-filter-repo --path-rename projects/$project/:
- overwrote the monorepo root build.gradle with the project's
  placeholder — restore from monorepo root (plugins + subprojects block)
- left settings.gradle with colon-separated include paths referencing
  the monorepo layout — rewrite to the hoisted layout
- left .github/workflows/*.yml and subproject *.gradle files with
  monorepo-style Gradle task refs and file paths (cross-project
  dependsOn, project() lookups) — rewrite for standalone CI + build
EOF
}

# ───────────────────────── Post-process: composite-build ─────────────────────────
# A composite-build project owns its own real settings.gradle + build.gradle
# inside projects/<name>/. filter-repo's --path-rename hoists those to the root
# of the extracted repo, colliding with and winning over the monorepo-root
# copies. The project's gradle paths are already root-relative (`apps:...`,
# `libs:...`), so no sed rewrites are needed.
#
# What we DO need to clean up:
#   - monorepo root `settings.gradle` might still be sitting at root because of
#     filter-repo collision resolution. If so, it will contain `includeBuild(
#     'projects/<name>')` referencing a now-deleted path. Strip that line and
#     any monorepo-style `include('projects:...')` lines.
#   - CLAUDE.md at root is the monorepo's pointer version (replaced during
#     Phase 2 import). After extraction it's misleading. Replace with the
#     monorepo-root CLAUDE.md (which has the full operating rules — readable as
#     a standalone even though it uses monorepo framing; project-specific
#     framing is a future improvement).
#   - .github/workflows/*.yml may contain `:projects:<name>:` task refs or
#     `projects/<name>/` paths — same rewrite as direct-include (harmless
#     no-op if they don't contain these).
post_process_composite_build() {
    local project="$1"

    # Strip orphan includeBuild or include('projects:...') lines from
    # settings.gradle in case filter-repo kept the monorepo-root version.
    if [ -f settings.gradle ] && grep -qE "includeBuild\(['\"]projects/$project['\"]|include\(['\"]projects:$project:" settings.gradle; then
        log "  stripping orphan monorepo includeBuild/include lines from settings.gradle..."
        sed -i "/includeBuild(['\"]projects\\/$project['\"])/d" settings.gradle
        sed -i "/include(['\"]projects:$project:/d" settings.gradle
    fi

    # Replace monorepo-pointer CLAUDE.md with monorepo-root CLAUDE.md so the
    # extracted repo has usable guidance at root. (Future: synthesize a
    # standalone-specific CLAUDE.md that strips monorepo framing.)
    if [ -f CLAUDE.md ] && grep -q "member of the \`monorepo-lab\` monorepo" CLAUDE.md; then
        log "  replacing monorepo-pointer CLAUDE.md with standalone copy..."
        cp "$MONOREPO_DIR/CLAUDE.md" CLAUDE.md
    fi

    # CI workflows: same rewrite as direct-include (harmless no-op otherwise)
    if [ -d .github/workflows ]; then
        for yml in .github/workflows/*.yml .github/workflows/*.yaml; do
            [ -f "$yml" ] || continue
            if grep -q ":projects:$project:\|projects/$project/" "$yml"; then
                log "  rewriting CI workflow $yml..."
                sed -i "s|:projects:$project:|:|g; s|projects/$project/||g" "$yml"
            fi
        done
    fi
}

composite_build_commit_msg() {
    local project="$1"
    cat <<EOF
chore(portfolio): clean up monorepo scaffolding for standalone layout

Extraction via git-filter-repo --path-rename projects/$project/: for a
Gradle composite-build project. The project owns its own real
settings.gradle + build.gradle, which filter-repo hoists to root intact.
Remaining cleanup:
- stripped any residual monorepo includeBuild('projects/$project') or
  include('projects:$project:...') lines from the root settings.gradle
- replaced the monorepo-pointer CLAUDE.md with the monorepo-root copy so
  the extracted repo has usable guidance at root
- rewrote any \`:projects:$project:\` / \`projects/$project/\` refs in
  .github/workflows/*.yml
EOF
}


sync_project() {
    local project="$1"
    local dry_run="$2"
    local remote="${PROJECT_REMOTES[$project]:-}"
    local ptype="${PROJECT_TYPES[$project]:-direct-include}"

    [ -n "$remote" ] || fail "Unknown project: $project. Configure in PROJECT_REMOTES."
    [ -d "$MONOREPO_DIR/projects/$project" ] || fail "projects/$project not found in monorepo."
    case "$ptype" in
        direct-include|composite-build) ;;
        *) fail "Unknown project type '$ptype' for $project. Expected: direct-include | composite-build" ;;
    esac

    log "Project:  $project"
    log "Remote:   $remote"
    log "Type:     $ptype"
    log "Source:   $MONOREPO_DIR"

    local workdir="$TEMP_ROOT/$project"
    log "Workdir:  $workdir"

    if [ "$dry_run" = "1" ]; then
        log "[dry-run] Would clone monorepo, run filter-repo, force-push to $remote"
        log "[dry-run] Kept paths:"
        for p in "${SHARED_PATHS[@]}" "projects/$project/"; do
            printf '             %s\n' "$p"
        done
        local dry_excludes="${PROJECT_EXCLUDE_PATHS[$project]:-}"
        if [ -n "$dry_excludes" ]; then
            log "[dry-run] Excluded paths (--invert-paths after extraction):"
            for p in $dry_excludes; do
                printf '             %s\n' "$p"
            done
        fi
        return 0
    fi

    # ── Step 0: Source-state guard ──
    # The clone in Step 1 uses local refs (--no-local copies all refs but the
    # default HEAD follows the source's local `main`). If local main has drifted
    # from origin/main, the sync would publish the wrong state to the standalone
    # repo. Refuse to proceed unless local main exactly matches origin/main.
    log "Verifying source repo main matches origin/main..."
    git -C "$MONOREPO_DIR" fetch origin main --quiet 2>/dev/null || \
        warn "  could not fetch origin main; comparing against last-known origin/main"
    local main_sha origin_sha
    main_sha=$(git -C "$MONOREPO_DIR" rev-parse main 2>/dev/null || echo "")
    origin_sha=$(git -C "$MONOREPO_DIR" rev-parse origin/main 2>/dev/null || echo "")
    if [ -z "$main_sha" ] || [ -z "$origin_sha" ]; then
        fail "  could not resolve main / origin/main in $MONOREPO_DIR. Ensure both refs exist."
    fi
    if [ "$main_sha" != "$origin_sha" ]; then
        local ahead behind
        ahead=$(git -C "$MONOREPO_DIR" rev-list --count "$origin_sha..$main_sha" 2>/dev/null || echo "?")
        behind=$(git -C "$MONOREPO_DIR" rev-list --count "$main_sha..$origin_sha" 2>/dev/null || echo "?")
        fail "  source repo's local main has diverged from origin/main (ahead $ahead, behind $behind).
                Run 'git -C $MONOREPO_DIR fetch origin main && git -C $MONOREPO_DIR merge --ff-only origin/main' first,
                or sync from a fresh clone (git clone https://github.com/<owner>/<monorepo>)."
    fi

    # ── Step 1: Clone ──
    rm -rf "$workdir"
    mkdir -p "$TEMP_ROOT"
    log "Cloning monorepo..."
    GIT_CLONE_PROTECTION_ACTIVE=false git clone --no-local --quiet "$MONOREPO_DIR" "$workdir"

    # ── Step 2: Build filter-repo args + write runner script ──
    log "Running git-filter-repo in Docker (this takes ~1 min)..."

    # Write a shell script into the workdir so Docker can execute it.
    # This avoids multi-level quoting issues when passing filter-repo args
    # through sh -c "..." variable substitution.
    local runner="$workdir/_filter_repo_run.sh"
    {
        printf '#!/bin/sh\n'
        # Fail LOUDLY: a silent `apk add git` failure used to leave git
        # uninstalled, so git-filter-repo (a git wrapper) died with an obscure
        # FileNotFoundError mid-run, leaving the workdir unfiltered and the
        # outer script aborting before push with no visible cause. `set -e` +
        # an explicit git presence check surface the real failure point.
        printf 'set -e\n'
        printf 'apk add --no-cache git\n'
        printf 'command -v git >/dev/null 2>&1 || { echo "FATAL: git not installed in container (apk add git failed — check container network/DNS to dl-cdn.alpinelinux.org)"; exit 1; }\n'
        printf 'pip install --quiet git-filter-repo\n'
        printf "git config --global user.email 'sync@portfolio'\n"
        printf "git config --global user.name 'Portfolio Sync'\n"
        # Step 1 (direct-include only): pre-remove projects/<name>/settings.gradle
        # from ALL historical commits. Root settings.gradle is in SHARED_PATHS; the
        # project-level copy existed in old commits (composite-build era) and causes
        # a collision when --path-rename hoists it to the same root path. Removing it
        # first lets Step 2 proceed without collision. --force bypasses the
        # "fresh clone" guard since filter-repo was not run before.
        if [ "$ptype" = "direct-include" ]; then
            printf "git filter-repo --force --path 'projects/%s/settings.gradle' --invert-paths\n" "$project"
        fi
        # Step 2: main extraction — keep SHARED_PATHS + project dir, hoist project to root.
        printf 'git filter-repo --force'
        for p in "${SHARED_PATHS[@]}" "projects/$project/"; do
            printf " --path '%s'" "$p"
        done
        printf " --path-rename 'projects/%s/:'" "$project"
        printf '\n'
        # Step 3 (optional): per-project exclusions — strip paths that belong
        # only in the monorepo and should NOT bleed into the standalone repo.
        # Runs after Step 2 so the paths are already hoisted (project-relative).
        local excludes_str
        excludes_str="${PROJECT_EXCLUDE_PATHS[$project]:-}"
        if [ -n "$excludes_str" ]; then
            printf 'git filter-repo --force --invert-paths'
            for p in $excludes_str; do
                printf " --path '%s'" "$p"
            done
            printf '\n'
        fi
    } > "$runner"

    # Run the filter inside a container. Mount workdir as /repo.
    # MSYS_NO_PATHCONV=1 stops Git Bash from mangling the /repo paths into
    # C:/Program Files/Git/repo.
    MSYS_NO_PATHCONV=1 docker run --rm \
        -v "$(cygpath -w "$workdir" 2>/dev/null || echo "$workdir"):/repo" \
        -w /repo \
        "$FILTER_REPO_IMAGE" \
        sh /repo/_filter_repo_run.sh

    cd "$workdir"

    # The filter-repo runner script leaked into the workdir. Remove it before
    # the post-process commit picks it up via `git add -A`, otherwise it ends
    # up force-pushed to the standalone repo as an orphan file at the root.
    rm -f _filter_repo_run.sh

    # ── Step 3: Post-process ──
    log "Post-processing for $ptype project..."

    local commit_msg=""
    case "$ptype" in
        direct-include) post_process_direct_include "$project"; commit_msg=$(direct_include_commit_msg "$project") ;;
        composite-build) post_process_composite_build "$project"; commit_msg=$(composite_build_commit_msg "$project") ;;
    esac

    git add -A 2>/dev/null || true
    if ! git diff --cached --quiet; then
        git commit -m "$commit_msg" --quiet
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
