#!/usr/bin/env bash
# verify-template-readiness.sh — check preconditions before Phase 5 template extraction
#
# Usage:
#   ./scripts/verify-template-readiness.sh
#   ./scripts/verify-template-readiness.sh --no-git
#   ./scripts/verify-template-readiness.sh --ignore=platform/error-handling.md
#
# Exit code:
#   0        = all checks pass (ready for extraction)
#   1..255   = number of blocking failures (capped at 255)
#
# Output per check: [PASS], [FAIL: reason], or [WARN: reason]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MONOREPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ───────────────────────── Flags ─────────────────────────

NO_GIT=0
EXTRA_IGNORES=()

for arg in "$@"; do
    case "$arg" in
        --no-git)       NO_GIT=1 ;;
        --ignore=*)     EXTRA_IGNORES+=("${arg#--ignore=}") ;;
        -h|--help)
            cat <<EOF
Usage: $0 [--no-git] [--ignore=<path>] ...

Run all template extraction readiness checks.

Flags:
  --no-git            Skip git-history-dependent checks (no-churn, CI baseline).
  --ignore=<path>     Add a path to the boundary-check false-positive ignore list.
                      Repeated as needed. Ignored paths are logged for audit.
  --help              Show this message.

Exit code = number of blockers (0 = READY, 1+ = NOT READY).
EOF
            exit 0
            ;;
        *) printf 'Unknown flag: %s\n' "$arg" >&2; exit 1 ;;
    esac
done

cd "$MONOREPO_ROOT"

# ───────────────────────── State ─────────────────────────

BLOCKERS=0
WARNINGS=0

pass()  { printf '[PASS] %s\n' "$*"; }
fail()  { printf '[FAIL] %s\n' "$*"; BLOCKERS=$((BLOCKERS + 1)); }
warn()  { printf '[WARN] %s\n' "$*"; WARNINGS=$((WARNINGS + 1)); }

# ───────────────────────── Check 1: Boundary check ─────────────────────────
# Verify shared library paths contain no project-specific service names.
#
# False-positive exclusions (intentional occurrences — do not remove without audit):
#   platform/error-handling.md   — explicit cross-domain error-code catalog (L134 framing),
#                                  lists service names as illustrative examples
#   rules/domains/*.md           — domain-scoped files; service-name examples are intentional
#   libs/java-messaging test     — test fixtures may use domain names for realistic stubs
#   TEMPLATE.md                  — meta-document that describes all projects by design;
#                                  historical narrative mentions (Phase 3/4) are intentional
#   CLAUDE.md                    — operating rules; references project names in examples
#   docs/guides/                 — human guides may use concrete project examples
#   Lines with e.g. / @code /    — Javadoc examples and inline documentation markers
#   spring.application.name=     — application property examples in platform guides

printf '\n--- Check 1: Shared library boundary (no project-specific service names) ---\n'

if [ "${#EXTRA_IGNORES[@]}" -gt 0 ]; then
    printf '[audit] Extra ignore paths supplied:\n'
    for ig in "${EXTRA_IGNORES[@]}"; do
        printf '        --ignore=%s\n' "$ig"
    done
fi

# Only scan the truly project-agnostic paths; meta-docs (TEMPLATE.md, CLAUDE.md, docs/guides/)
# are excluded from strict name scanning because they document all projects by design.
SEARCH_PATHS=(
    platform/
    rules/
    .claude/
    libs/
    tasks/templates/
)

# Always-excluded file path prefixes (intentional service-name occurrences).
# Note: these match the start of the grep output line (file:line:content format).
#   platform/error-handling.md  — cross-domain error-code catalog (intentional by design)
#   rules/domains/              — domain rule files (service examples are intentional)
#   libs/java-messaging/.../test/ — test fixtures use realistic service names for stubs
#     (path prefix check uses grep -E, so the pattern matches anywhere in the line start)
ALWAYS_EXCLUDE_PATH_PREFIXES=(
    "platform/error-handling.md"
    "rules/domains/"
    "libs/java-messaging/src/test/"
)
# Content-level patterns that indicate the match is an example/fixture, not a boundary violation:
#   e.g.                        — inline documentation examples
#   @code                       — Javadoc code examples
#   spring.application.name=    — application property examples
#   <...-service-name>          — placeholder token (angle brackets indicate template variable)
CONTENT_EXCLUDE_PATTERN="(e\\.g\\.|@code|spring\\.application\\.name=|<[a-z-]+-service-name>)"

# Run the search; capture results for filtering
raw_violations=""
for sp in "${SEARCH_PATHS[@]}"; do
    if [ -e "$sp" ]; then
        raw_violations+=$(grep -rn --include='*.md' --include='*.java' --include='*.yml' --include='*.yaml' \
            -E "(auth-service|product-service|order-service|payment-service|inventory-service|master-service|community-service)" \
            "$sp" 2>/dev/null || true)
        raw_violations+=$'\n'
    fi
done

# Filter out known false positives
filtered_violations=""
while IFS= read -r line; do
    [ -z "$line" ] && continue
    # Skip always-excluded path prefixes
    skip=0
    for prefix in "${ALWAYS_EXCLUDE_PATH_PREFIXES[@]}"; do
        if echo "$line" | grep -q "^$prefix"; then
            skip=1
            break
        fi
    done
    [ "$skip" = "1" ] && continue
    # Skip content-level false positives (e.g., @code, spring.application.name=)
    if echo "$line" | grep -qE "$CONTENT_EXCLUDE_PATTERN"; then
        continue
    fi
    # Skip user-supplied --ignore paths
    for ig in "${EXTRA_IGNORES[@]}"; do
        if echo "$line" | grep -q "^$ig"; then
            printf '[audit] suppressed by --ignore=%s: %s\n' "$ig" "$line"
            skip=1
            break
        fi
    done
    [ "$skip" = "1" ] && continue
    filtered_violations+="$line"$'\n'
done <<< "$raw_violations"

if [ -z "$(echo "$filtered_violations" | tr -d '[:space:]')" ]; then
    pass "No project-specific service names found in shared library paths."
else
    fail "Project-specific service names found in shared library. Violations:"
    while IFS= read -r v; do
        [ -z "$v" ] && continue
        printf '        %s\n' "$v"
    done <<< "$filtered_violations"
    printf '  Remediation: move content to projects/<name>/specs/ or replace with <placeholder> tokens.\n'
fi

# ───────────────────────── Check 2: Phase 4 outstanding tasks in done/ ─────────────────────────

printf '\n--- Check 2: Phase 4 outstanding tasks must be in done/ ---\n'

PHASE4_TASKS=(
    "TASK-SCM-BE-002d-procurement-testcontainers-it.md"
    "TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md"
)

for task_file in "${PHASE4_TASKS[@]}"; do
    # Check done/
    done_path="projects/scm-platform/tasks/done/$task_file"
    if [ -f "$done_path" ]; then
        pass "$task_file is in done/."
        continue
    fi
    # Check if it's stuck in a non-done lifecycle dir
    blocking_location=""
    for lifecycle_dir in ready in-progress review backlog; do
        candidate="projects/scm-platform/tasks/$lifecycle_dir/$task_file"
        if [ -f "$candidate" ]; then
            blocking_location="$lifecycle_dir"
            break
        fi
    done
    if [ -n "$blocking_location" ]; then
        fail "$task_file is in $blocking_location/ (expected done/)."
        printf '  Remediation: complete implementation and merge task to done/.\n'
    else
        fail "$task_file not found in any lifecycle directory."
        printf '  Remediation: ensure the task file exists and has been moved to done/.\n'
    fi
done

# ───────────────────────── Check 3: No-churn 1-month gate ─────────────────────────
# Shared library paths must have had no commits in the last month,
# EXCEPT commits from this very tooling (the readiness scripts themselves).
#
# Allow-list rationale: authoring extract-template.sh and verify-template-readiness.sh
# is part of the readiness preparation, not a substantive library change. If the only
# recent commits touching shared paths are from this task, churn is effectively zero.
# We detect them by checking that the changed files are strictly within
# scripts/extract-template.sh, scripts/verify-template-readiness.sh, and TEMPLATE.md.

printf '\n--- Check 3: No shared-library churn in the last month ---\n'

if [ "$NO_GIT" = "1" ]; then
    warn "Skipped (--no-git flag)."
else
    SHARED_GIT_PATHS=(
        "libs/"
        "platform/"
        "rules/"
        ".claude/"
        "tasks/templates/"
        "CLAUDE.md"
        "TEMPLATE.md"
    )

    recent_commits=$(git log --since="1 month ago" --pretty=format:"%H" -- \
        "${SHARED_GIT_PATHS[@]}" 2>/dev/null || true)

    if [ -z "$recent_commits" ]; then
        pass "No shared-library commits in the last month."
    else
        # Filter out allow-listed commits (only touched readiness tooling)
        real_churn_commits=""
        while IFS= read -r sha; do
            [ -z "$sha" ] && continue
            # Get changed files for this commit
            changed_files=$(git diff-tree --no-commit-id -r --name-only "$sha" 2>/dev/null || true)
            # Check if every changed file is in the allow-list
            is_tooling_only=1
            while IFS= read -r changed_file; do
                [ -z "$changed_file" ] && continue
                case "$changed_file" in
                    scripts/extract-template.sh|\
                    scripts/verify-template-readiness.sh|\
                    TEMPLATE.md|\
                    tasks/in-progress/TASK-MONO-047*|\
                    tasks/done/TASK-MONO-047*|\
                    tasks/review/TASK-MONO-047*)
                        # Allowed — readiness tooling only
                        ;;
                    *)
                        is_tooling_only=0
                        break
                        ;;
                esac
            done <<< "$changed_files"
            if [ "$is_tooling_only" = "0" ]; then
                real_churn_commits+="$sha"$'\n'
            fi
        done <<< "$recent_commits"

        if [ -z "$(echo "$real_churn_commits" | tr -d '[:space:]')" ]; then
            pass "Recent commits only touched readiness tooling (allow-listed). Effective churn = 0."
        else
            commit_count=$(echo "$real_churn_commits" | grep -c '[a-f0-9]' || true)
            fail "Shared library has $commit_count commit(s) in the last month (non-tooling)."
            printf '  Commits:\n'
            while IFS= read -r sha; do
                [ -z "$sha" ] && continue
                subject=$(git log --format="%s" -1 "$sha" 2>/dev/null || echo "(unknown)")
                printf '    %s  %s\n' "${sha:0:8}" "$subject"
            done <<< "$real_churn_commits"
            printf '  Remediation: wait until 1 month has passed since the last library change.\n'
        fi
    fi
fi

# ───────────────────────── Check 4: CI baseline green ─────────────────────────

printf '\n--- Check 4: CI baseline green on main ---\n'

if [ "$NO_GIT" = "1" ]; then
    warn "Skipped (--no-git flag)."
elif ! command -v gh >/dev/null 2>&1; then
    warn "gh CLI not found. Install GitHub CLI to enable CI status check."
else
    ci_conclusion=$(gh run list --branch main --workflow ci.yml --limit 1 \
        --json conclusion --jq '.[0].conclusion' 2>/dev/null || echo "unknown")
    if [ "$ci_conclusion" = "success" ]; then
        pass "Latest main ci.yml run is success."
    elif [ "$ci_conclusion" = "unknown" ] || [ -z "$ci_conclusion" ]; then
        warn "Could not determine CI status (no runs found or gh API error)."
    else
        fail "Latest main ci.yml run is '$ci_conclusion' (expected 'success')."
        printf '  Remediation: fix CI failures on main before extracting template.\n'
    fi
fi

# ───────────────────────── Check 5: All projects PROJECT.md valid ─────────────────────────

printf '\n--- Check 5: All projects have valid PROJECT.md frontmatter ---\n'

# Extract valid domains and traits from taxonomy.md using grep
# taxonomy.md uses "#### <domain>" and "#### <trait>" headings for each entry
TAXONOMY_FILE="$MONOREPO_ROOT/rules/taxonomy.md"

extract_taxonomy_values() {
    # Domains are declared as level-4 headings (#### domain-name)
    # Traits are declared as level-3 headings (### trait-name)
    # Both use lowercase-with-hyphens names.
    # tr -d '\r' strips Windows CRLF endings that break grep -qx matching.
    {
        grep -E '^#### [a-z]' "$TAXONOMY_FILE" 2>/dev/null | sed 's/^#### //'
        grep -E '^### [a-z]' "$TAXONOMY_FILE" 2>/dev/null | sed 's/^### //'
    } | tr -d ' \r' | sort -u
}

VALID_TAXONOMY_VALUES=$(extract_taxonomy_values)

project_check_pass=1
while IFS= read -r project_md; do
    project_dir="$(dirname "$project_md")"
    project_name="$(basename "$project_dir")"

    # Required frontmatter fields
    for field in name domain traits service_types taxonomy_version; do
        if ! grep -q "^$field:" "$project_md" 2>/dev/null; then
            fail "$project_name/PROJECT.md: missing required field '$field'."
            project_check_pass=0
        fi
    done

    # domain value must be in taxonomy.md
    domain_val=$(grep '^domain:' "$project_md" 2>/dev/null | head -1 | sed 's/^domain: *//' | tr -d ' "\r')
    if [ -n "$domain_val" ]; then
        if ! echo "$VALID_TAXONOMY_VALUES" | grep -qx "$domain_val"; then
            fail "$project_name/PROJECT.md: domain '$domain_val' not found in rules/taxonomy.md."
            project_check_pass=0
        fi
    fi

    # Each trait must be in taxonomy.md
    traits_line=$(grep '^traits:' "$project_md" 2>/dev/null | head -1 || true)
    if [ -n "$traits_line" ]; then
        # traits: [transactional, integration-heavy] or traits: []
        traits_raw=$(echo "$traits_line" | sed 's/^traits: *//' | tr -d '[] \r' | tr ',' '\n')
        while IFS= read -r trait; do
            [ -z "$trait" ] && continue
            if ! echo "$VALID_TAXONOMY_VALUES" | grep -qx "$trait"; then
                fail "$project_name/PROJECT.md: trait '$trait' not found in rules/taxonomy.md."
                project_check_pass=0
            fi
        done <<< "$traits_raw"
    fi

done < <(find projects -maxdepth 2 -name PROJECT.md 2>/dev/null)

project_md_count=$(find projects -maxdepth 2 -name PROJECT.md 2>/dev/null | wc -l)
if [ "$project_check_pass" = "1" ]; then
    pass "All $project_md_count projects have valid PROJECT.md frontmatter."
fi

# ───────────────────────── Check 6: No PORT_PREFIX legacy ─────────────────────────

printf '\n--- Check 6: No PORT_PREFIX legacy in projects/ ---\n'

port_prefix_hits=$(grep -rn "PORT_PREFIX" projects/ \
    --include='*.yml' --include='*.yaml' --include='*.env' \
    --include='*.md' --include='*.sh' --include='*.properties' \
    --include='*.txt' --include='*.conf' --include='*.gradle' \
    --include='*.env.example' 2>/dev/null || true)
if [ -z "$port_prefix_hits" ]; then
    pass "No PORT_PREFIX references found under projects/."
else
    fail "PORT_PREFIX references found (should be replaced by Traefik hostname routing):"
    while IFS= read -r hit; do
        [ -z "$hit" ] && continue
        printf '        %s\n' "$hit"
    done <<< "$port_prefix_hits"
    printf '  Remediation: see ADR-MONO-001 and CLAUDE.md § Local Network Convention.\n'
fi

# ───────────────────────── Final summary ─────────────────────────

printf '\n=== Readiness Summary ===\n'
if [ "$BLOCKERS" -eq 0 ] && [ "$WARNINGS" -eq 0 ]; then
    printf 'READY — all checks passed. Raise ADR-MONO-003 candidate and run scripts/extract-template.sh.\n'
elif [ "$BLOCKERS" -eq 0 ]; then
    printf 'READY (with %d warning(s)) — no blockers. Review warnings before extracting.\n' "$WARNINGS"
else
    printf 'NOT READY: %d blocker(s), %d warning(s).\n' "$BLOCKERS" "$WARNINGS"
    printf 'Resolve all [FAIL] items above before running scripts/extract-template.sh.\n'
fi

exit "$((BLOCKERS > 255 ? 255 : BLOCKERS))"
