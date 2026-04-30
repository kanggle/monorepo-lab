#!/usr/bin/env bash
# validate-project.sh — Check PROJECT.md declarations against the template rule library.
#
# Exit codes:
#   0 — all checks pass (warnings allowed for on-demand missing rule files)
#   1 — at least one FAIL (hard stop: invalid catalog value, missing service-type file, etc.)
#   2 — script misuse (run from wrong dir, missing dependencies)
#
# Usage:
#   ./scripts/validate-project.sh
#   ./scripts/validate-project.sh --strict   # treat WARN as FAIL

set -uo pipefail

STRICT=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --strict) STRICT=1; shift ;;
    -h|--help)
      cat <<'EOF'
Validate PROJECT.md against the template rule library.

Usage:
  scripts/validate-project.sh [--strict]

Checks:
  - PROJECT.md frontmatter has name/domain/traits/service_types
  - domain is in .claude/config/domains.md catalog
  - each trait is in .claude/config/traits.md catalog
  - each service_type has a corresponding platform/service-types/<type>.md file
  - rules/domains/<domain>.md exists (WARN if missing — on-demand policy)
  - rules/traits/<trait>.md exists for each trait (WARN if missing — on-demand policy)

Flags:
  --strict   Treat WARN as FAIL (exit 1 if any on-demand rule file is missing)
EOF
      exit 0 ;;
    *) printf 'error: unknown option: %s\n' "$1" >&2; exit 2 ;;
  esac
done

#
# Helpers
#
RED=$'\033[31m'
YELLOW=$'\033[33m'
GREEN=$'\033[32m'
RESET=$'\033[0m'

FAIL_COUNT=0
WARN_COUNT=0
OK_COUNT=0

fail() { printf '%sFAIL%s %s\n' "$RED" "$RESET" "$*" >&2; FAIL_COUNT=$((FAIL_COUNT+1)); }
warn() { printf '%sWARN%s %s\n' "$YELLOW" "$RESET" "$*" >&2; WARN_COUNT=$((WARN_COUNT+1)); }
ok()   { printf '%sOK%s   %s\n' "$GREEN" "$RESET" "$*"; OK_COUNT=$((OK_COUNT+1)); }

#
# Repo root sanity
#
if [[ ! -f PROJECT.md ]]; then
  printf 'error: PROJECT.md not found (run from template repo root)\n' >&2
  exit 2
fi
if [[ ! -d .claude/config || ! -d rules || ! -d platform ]]; then
  printf 'error: expected .claude/config/, rules/, platform/ directories\n' >&2
  exit 2
fi

#
# Extract frontmatter fields
#
NAME=$(grep -E '^name:' PROJECT.md | head -1 | sed 's/^name:[[:space:]]*//' || true)
DOMAIN=$(grep -E '^domain:' PROJECT.md | head -1 | sed 's/^domain:[[:space:]]*//' || true)
TRAITS_LINE=$(grep -E '^traits:' PROJECT.md | head -1 | sed 's/^traits:[[:space:]]*//' || true)
ST_LINE=$(grep -E '^service_types:' PROJECT.md | head -1 | sed 's/^service_types:[[:space:]]*//' || true)

parse_yaml_array() {
  # Convert "[a, b, c]" → "a b c"
  printf '%s' "$1" | sed 's/^\[//; s/\]$//; s/,/ /g' | tr -s ' '
}
TRAITS=$(parse_yaml_array "$TRAITS_LINE")
SERVICE_TYPES=$(parse_yaml_array "$ST_LINE")

#
# Catalog loaders
#
catalog_domains() { grep -E '^- [a-z][a-z0-9-]*$' .claude/config/domains.md | sed 's/^- //'; }
catalog_traits()  { grep -E '^- [a-z][a-z0-9-]*$' .claude/config/traits.md  | sed 's/^- //'; }

#
# 1. name
#
if [[ -z "$NAME" ]]; then
  fail "PROJECT.md — 'name:' field missing or empty"
else
  if [[ "$NAME" =~ ^[a-z][a-z0-9-]*$ ]]; then
    ok "name = $NAME"
  else
    fail "name '$NAME' not in kebab-case format (lowercase alphanumeric + dash)"
  fi
fi

#
# 2. domain
#
if [[ -z "$DOMAIN" ]]; then
  fail "PROJECT.md — 'domain:' field missing or empty"
elif ! catalog_domains | grep -qx "$DOMAIN"; then
  fail "domain '$DOMAIN' not in catalog (.claude/config/domains.md)"
else
  ok "domain = $DOMAIN (catalog OK)"
fi

#
# 3. traits
#
if [[ -z "$TRAITS" ]]; then
  warn "PROJECT.md — 'traits:' is empty (project declares no traits)"
else
  for t in $TRAITS; do
    if ! catalog_traits | grep -qx "$t"; then
      fail "trait '$t' not in catalog (.claude/config/traits.md)"
    else
      ok "trait $t (catalog OK)"
    fi
  done
fi

#
# 4. service_types — must have corresponding platform/service-types/<type>.md file
#
if [[ -z "$SERVICE_TYPES" ]]; then
  warn "PROJECT.md — 'service_types:' is empty"
else
  for s in $SERVICE_TYPES; do
    if [[ -f "platform/service-types/${s}.md" ]]; then
      ok "service_type $s (platform/service-types/${s}.md exists)"
    else
      fail "service_type '$s' — platform/service-types/${s}.md not found"
    fi
  done
fi

#
# 5. rules/domains/<domain>.md (on-demand — WARN if missing)
#
if [[ -n "$DOMAIN" ]] && catalog_domains | grep -qx "$DOMAIN"; then
  if [[ -f "rules/domains/${DOMAIN}.md" ]]; then
    ok "rules/domains/${DOMAIN}.md present"
  else
    warn "rules/domains/${DOMAIN}.md missing (on-demand policy — author required)"
  fi
fi

#
# 6. rules/traits/<trait>.md (on-demand — WARN if missing)
#
for t in $TRAITS; do
  if catalog_traits | grep -qx "$t"; then
    if [[ -f "rules/traits/${t}.md" ]]; then
      ok "rules/traits/${t}.md present"
    else
      warn "rules/traits/${t}.md missing (on-demand policy — author required)"
    fi
  fi
done

#
# Summary
#
printf '\n'
printf '=== validation summary ===\n'
printf '  checks passed: %d\n' "$OK_COUNT"
printf '  warnings:      %d\n' "$WARN_COUNT"
printf '  failures:      %d\n' "$FAIL_COUNT"
printf '\n'

if [[ "$FAIL_COUNT" -gt 0 ]]; then
  printf '%sproject validation FAILED%s\n' "$RED" "$RESET" >&2
  exit 1
fi

if [[ "$STRICT" == "1" && "$WARN_COUNT" -gt 0 ]]; then
  printf '%sproject validation FAILED (--strict: warnings treated as failures)%s\n' "$RED" "$RESET" >&2
  exit 1
fi

printf '%sproject validation OK%s\n' "$GREEN" "$RESET"
exit 0
