#!/usr/bin/env bash
# init-project.sh — Initialize a new project from this template.
#
# Usage:
#   ./scripts/init-project.sh --name my-platform --domain saas --traits transactional,regulated
#   ./scripts/init-project.sh                       # interactive mode
#
# See TEMPLATE.md for the full guide.

set -euo pipefail

#
# Defaults
#
NAME=""
DOMAIN=""
TRAITS=""
SERVICE_TYPES="rest-api,event-consumer"
INTERACTIVE=1
FORCE=0

#
# Helpers
#
fail() { printf 'error: %s\n' "$*" >&2; exit 1; }
warn() { printf 'warn:  %s\n' "$*" >&2; }
info() { printf 'info:  %s\n' "$*"; }

usage() {
  cat <<'EOF'
Initialize a new project from this template.

Usage:
  scripts/init-project.sh [options]

Options:
  --name <project-name>         Project name (lowercase, dash-separated)
  --domain <domain>             Primary domain (must exist in .claude/config/domains.md)
  --traits <t1,t2,...>          Comma-separated traits (each must exist in .claude/config/traits.md)
  --service-types <st1,...>     Comma-separated service types (default: rest-api,event-consumer)
  --non-interactive             Fail if any required value is missing instead of prompting
  --force                       Overwrite existing PROJECT.md without prompting
  -h, --help                    Show this help

Examples:
  scripts/init-project.sh --name my-platform --domain saas --traits transactional,regulated,audit-heavy
  scripts/init-project.sh                           # interactive
EOF
}

#
# Parse args
#
while [[ $# -gt 0 ]]; do
  case "$1" in
    --name)            NAME="$2"; shift 2 ;;
    --domain)          DOMAIN="$2"; shift 2 ;;
    --traits)          TRAITS="$2"; shift 2 ;;
    --service-types)   SERVICE_TYPES="$2"; shift 2 ;;
    --non-interactive) INTERACTIVE=0; shift ;;
    --force)           FORCE=1; shift ;;
    -h|--help)         usage; exit 0 ;;
    *)                 fail "unknown option: $1" ;;
  esac
done

#
# Repo root sanity check
#
if [[ ! -f PROJECT.md || ! -d .claude/config || ! -d rules || ! -d platform ]]; then
  fail "must be run from the template repo root (expected PROJECT.md, .claude/config/, rules/, platform/)"
fi

#
# Catalog loaders
#
catalog_domains() {
  grep -E '^- [a-z][a-z0-9-]*$' .claude/config/domains.md | sed 's/^- //'
}
catalog_traits() {
  grep -E '^- [a-z][a-z0-9-]*$' .claude/config/traits.md | sed 's/^- //'
}

#
# Interactive fallback
#
if [[ -z "$NAME" ]]; then
  if [[ "$INTERACTIVE" == "1" ]]; then
    read -rp "Project name (lowercase, dash-separated): " NAME
  else
    fail "--name is required in --non-interactive mode"
  fi
fi

if [[ -z "$DOMAIN" ]]; then
  if [[ "$INTERACTIVE" == "1" ]]; then
    printf 'available domains:\n'
    catalog_domains | sed 's/^/  - /'
    read -rp "Domain: " DOMAIN
  else
    fail "--domain is required in --non-interactive mode"
  fi
fi

if [[ -z "$TRAITS" ]]; then
  if [[ "$INTERACTIVE" == "1" ]]; then
    printf 'available traits:\n'
    catalog_traits | sed 's/^/  - /'
    read -rp "Traits (comma-separated): " TRAITS
  else
    fail "--traits is required in --non-interactive mode"
  fi
fi

#
# Validate name
#
if ! [[ "$NAME" =~ ^[a-z][a-z0-9-]*$ ]]; then
  fail "project name must be lowercase alphanumeric with dashes (got: '$NAME')"
fi

#
# Validate domain against catalog
#
if ! catalog_domains | grep -qx "$DOMAIN"; then
  printf 'valid domains:\n' >&2
  catalog_domains | sed 's/^/  - /' >&2
  fail "domain '$DOMAIN' not in catalog (.claude/config/domains.md)"
fi

#
# Split and trim traits / service-types
#
trim() { local v="$1"; v="${v## }"; v="${v%% }"; printf '%s' "$v"; }

IFS=',' read -ra TRAIT_ARRAY_RAW <<< "$TRAITS"
TRAIT_ARRAY=()
for t in "${TRAIT_ARRAY_RAW[@]}"; do
  TRAIT_ARRAY+=("$(trim "$t")")
done

IFS=',' read -ra ST_ARRAY_RAW <<< "$SERVICE_TYPES"
ST_ARRAY=()
for s in "${ST_ARRAY_RAW[@]}"; do
  ST_ARRAY+=("$(trim "$s")")
done

#
# Validate each trait against catalog
#
for t in "${TRAIT_ARRAY[@]}"; do
  if ! catalog_traits | grep -qx "$t"; then
    printf 'valid traits:\n' >&2
    catalog_traits | sed 's/^/  - /' >&2
    fail "trait '$t' not in catalog (.claude/config/traits.md)"
  fi
done

#
# Check rule file presence (warn, don't block — on-demand policy)
#
MISSING_RULES=0
if [[ ! -f "rules/domains/${DOMAIN}.md" ]]; then
  warn "rules/domains/${DOMAIN}.md does not exist."
  warn "  Per on-demand policy (rules/README.md §On-Demand Generation Policy),"
  warn "  you must author this file in the same change that declares the domain."
  MISSING_RULES=1
fi
for t in "${TRAIT_ARRAY[@]}"; do
  if [[ ! -f "rules/traits/${t}.md" ]]; then
    warn "rules/traits/${t}.md does not exist."
    warn "  Per on-demand policy, you must author this file in the same change."
    MISSING_RULES=1
  fi
done
if [[ "$MISSING_RULES" == "1" ]]; then
  warn ""
  warn "Proceeding anyway — init script will not auto-generate rule file stubs."
  warn "See TEMPLATE.md for authoring guidance."
  warn ""
fi

#
# Confirm overwrite of PROJECT.md
#
if [[ -f PROJECT.md && "$FORCE" != "1" ]]; then
  if [[ "$INTERACTIVE" == "1" ]]; then
    read -rp "PROJECT.md exists. Overwrite? [y/N] " ans
    case "$ans" in
      y|Y) ;;
      *)   fail "aborted by user" ;;
    esac
  else
    fail "PROJECT.md exists. Use --force to overwrite in --non-interactive mode."
  fi
fi

#
# Build YAML fragments
#
join_csv() {
  local out=""
  for v in "$@"; do
    if [[ -z "$out" ]]; then out="$v"; else out="$out, $v"; fi
  done
  printf '%s' "$out"
}

TRAITS_YAML=$(join_csv "${TRAIT_ARRAY[@]}")
ST_YAML=$(join_csv "${ST_ARRAY[@]}")

TRAIT_BULLETS=""
for t in "${TRAIT_ARRAY[@]}"; do
  TRAIT_BULLETS="${TRAIT_BULLETS}- **${t}**: TODO — [rules/traits/${t}.md](rules/traits/${t}.md)의 규칙이 적용되는 근거를 적으세요."$'\n'
done

#
# Rewrite PROJECT.md
#
cat > PROJECT.md <<EOF
---
name: ${NAME}
domain: ${DOMAIN}
traits: [${TRAITS_YAML}]
service_types: [${ST_YAML}]
compliance: []
data_sensitivity: internal
scale_tier: startup
taxonomy_version: 0.1
---

# ${NAME}

## Purpose

TODO: 프로젝트 목적을 1~2 단락으로 기술하세요.

## Domain Rationale

\`${DOMAIN}\`을 선택한 이유:

TODO: 이 도메인을 고른 근거를 기술하세요. [rules/domains/${DOMAIN}.md](rules/domains/${DOMAIN}.md)의 bounded context와 ubiquitous language를 참조하여 작성합니다.

## Trait Rationale

${TRAIT_BULLETS}
## Out of Scope (의도적 제외)

TODO: 명시적으로 제외하는 domain/trait과 그 근거를 기술하세요. 현재 프로젝트에서 다루지 않는 경계를 명확히 해두면 나중에 범위가 흐려질 때 기준이 됩니다.

## Overrides

현재 명시적 override 없음. 공통/도메인/특성 규칙을 모두 기본값대로 따른다.

예외가 필요한 경우 다음 형식으로 기록:

\`\`\`
- **rule**: rules/traits/<trait>.md#<rule-id>
- **reason**: <why>
- **scope**: <which service(s)>
- **expiry**: <date or condition>
\`\`\`
EOF
info "PROJECT.md rewritten"

#
# Rewrite settings.gradle rootProject.name
#
if [[ -f settings.gradle ]]; then
  sed -i.bak "s|^rootProject\.name = .*|rootProject.name = '${NAME}'|" settings.gradle
  rm -f settings.gradle.bak
  info "settings.gradle rootProject.name = '${NAME}'"
fi

#
# Rewrite README.md first-line heading
#
if [[ -f README.md ]]; then
  sed -i.bak "1s|^# .*|# ${NAME}|" README.md
  rm -f README.md.bak
  info "README.md heading = # ${NAME}"
fi

#
# Copy .env.example → .env if missing
#
if [[ -f .env.example && ! -f .env ]]; then
  cp .env.example .env
  info ".env copied from .env.example (fill in values before running)"
fi

#
# Final summary
#
cat <<EOF

---

Init complete.

Declared:
  name          = ${NAME}
  domain        = ${DOMAIN}
  traits        = [${TRAITS_YAML}]
  service_types = [${ST_YAML}]

Next steps:
  1. Edit PROJECT.md — replace each TODO section (Purpose, Domain Rationale, Trait Rationale, Out of Scope)
  2. Fill in .env (copied from .env.example)
  3. Re-initialize git if this is a fresh clone:
       rm -rf .git && git init && git add -A && git commit -m "init ${NAME}"
  4. If any rule file was flagged as missing above, author it now — see TEMPLATE.md and rules/README.md
  5. Start writing specs:
       specs/services/<service>/architecture.md
       specs/contracts/http/<service>-api.md
  6. Create the first task in tasks/ready/

See TEMPLATE.md for the full template guide.
EOF
