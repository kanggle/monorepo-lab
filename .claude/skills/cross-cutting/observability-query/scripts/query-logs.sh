#!/usr/bin/env bash
# Query the worktree's ephemeral VictoriaLogs instance via LogQL.
#
# Spec source: this script has no single specs/ source — it is a cross-cutting
# helper that operates across every wms service's emitted log surface. See:
#   .claude/skills/cross-cutting/observability-query/SKILL.md
#   docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md § 2.4 D4
#   infra/observability/README.md (operator guide)
#
# Usage:
#   query-logs.sh '<LogQL>' [--limit N]
#
# Output: newline-delimited JSON on stdout, one line per matching event.
# Failures: 4-block OBSERVE-QUERY-NN remediation on stderr.

set -euo pipefail

REF_LINK=".claude/skills/cross-cutting/observability-query/SKILL.md § Failure modes"

emit_4block() {
  local id="$1" why="$2" file="$3" remediation="$4"
  cat >&2 <<EOF

[VIOLATION] $id at $file
[WHY] $why
[REMEDIATION] Choose one:
$remediation
[REFERENCE] $REF_LINK
EOF
}

# ---------- Arg parsing ----------------------------------------------------

LIMIT=100
QUERY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --limit) LIMIT="$2"; shift 2 ;;
    -h|--help)
      cat <<HELP
Usage: $0 '<LogQL query>' [--limit N]

Examples:
  $0 '{service="master-service"} |= "PartnerCreated"'
  $0 '{level="ERROR"}' --limit 500
  $0 '{traceId="abc123"}'

See .claude/skills/cross-cutting/observability-query/SKILL.md for the field reference.
HELP
      exit 0 ;;
    *) QUERY="$1"; shift ;;
  esac
done

if [ -z "$QUERY" ]; then
  echo "[query-logs.sh] missing query argument; pass -h for usage" >&2
  exit 1
fi

# ---------- Resolve port file ----------------------------------------------

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$REPO_ROOT" ]; then
  emit_4block "OBSERVE-QUERY-01" \
    "Not invoked from inside a git worktree; cannot resolve .observability/ports.env." \
    "$(pwd)" \
    "  1. cd into the monorepo-lab worktree root and retry.
  2. The skill assumes a per-worktree stack — operating outside a worktree is unsupported."
  exit 1
fi

PORTS_FILE="$REPO_ROOT/.observability/ports.env"
if [ ! -f "$PORTS_FILE" ]; then
  emit_4block "OBSERVE-QUERY-01" \
    "Stack not up: $PORTS_FILE does not exist." \
    "$PORTS_FILE" \
    "  1. Manual mode: ./scripts/observability/up.sh
  2. Gradle mode:  ./gradlew :projects:wms-platform:apps:gateway-service:e2eTest -Pobservability=on
  3. If the stack was running and the port file was deleted, run ./scripts/observability/down.sh && ./scripts/observability/up.sh to re-cycle."
  exit 1
fi

# shellcheck disable=SC1090
source "$PORTS_FILE"

if [ -z "${VICTORIALOGS_PORT:-}" ]; then
  emit_4block "OBSERVE-QUERY-02" \
    "Port file present but VICTORIALOGS_PORT not set — corrupt port file." \
    "$PORTS_FILE" \
    "  1. ./scripts/observability/down.sh && ./scripts/observability/up.sh to re-cycle.
  2. If down.sh fails, manually 'docker compose -p \$PROJECT down' then retry."
  exit 2
fi

# ---------- URL-encode the query -------------------------------------------

url_encode() {
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
  elif command -v jq >/dev/null 2>&1; then
    jq -rn --arg q "$1" '$q | @uri'
  else
    printf '%s' "$1" | sed 's/ /%20/g; s/"/%22/g; s/{/%7B/g; s/}/%7D/g; s/|/%7C/g'
  fi
}

ENCODED="$(url_encode "$QUERY")"
URL="http://127.0.0.1:${VICTORIALOGS_PORT}/select/logsql/query?query=${ENCODED}&limit=${LIMIT}"

# ---------- Curl + classify response ----------------------------------------

HTTP_BODY="$(mktemp)"
trap 'rm -f "$HTTP_BODY"' EXIT
HTTP_CODE="$(curl -sS -o "$HTTP_BODY" -w "%{http_code}" "$URL" || echo "000")"

case "$HTTP_CODE" in
  200)
    LINES="$(wc -l < "$HTTP_BODY" | tr -d ' ')"
    if [ "$LINES" -eq 0 ]; then
      emit_4block "OBSERVE-QUERY-04" \
        "Query returned no matching log lines within the window." \
        "$URL" \
        "  1. Widen the matcher (e.g. drop the |= phrase filter, broaden the level enum).
  2. Verify the service actually emitted what you expect; a unit test fixture run can confirm.
  3. Check the worktree label matches: most queries do not need it, but cross-worktree concurrent runs may need {worktree=\"\$WORKTREE_HASH\"}."
      exit 3
    elif [ "$LINES" -ge "$LIMIT" ]; then
      cat "$HTTP_BODY"
      emit_4block "OBSERVE-QUERY-05" \
        "Result reached the limit ($LIMIT). More entries likely exist but were truncated." \
        "$URL" \
        "  1. Pass --limit 500 (or higher) to widen.
  2. Refine the query with a narrower matcher to focus on the relevant subset."
      exit 4
    else
      cat "$HTTP_BODY"
      exit 0
    fi
    ;;
  400)
    PARSER_MSG="$(head -c 500 "$HTTP_BODY")"
    emit_4block "OBSERVE-QUERY-03" \
      "VictoriaLogs returned 400 — query syntax error: $PARSER_MSG" \
      "$URL" \
      "  1. Consult the LogQL primer in .claude/skills/cross-cutting/observability-query/SKILL.md.
  2. Common issues: unmatched braces, missing service label, |= operator quoting.
  3. The web UI at http://127.0.0.1:${VICTORIALOGS_PORT}/select/vmui can validate queries interactively (humans only)."
    exit 2
    ;;
  000|5*)
    emit_4block "OBSERVE-QUERY-02" \
      "VictoriaLogs unreachable or returned 5xx — stack may be mid-teardown or container crashed." \
      "$URL" \
      "  1. docker compose -p \$PROJECT ps to check container state.
  2. ./scripts/observability/down.sh && ./scripts/observability/up.sh to re-cycle.
  3. docker logs to inspect crashed containers."
    exit 2
    ;;
  *)
    emit_4block "OBSERVE-QUERY-02" \
      "Unexpected HTTP status from VictoriaLogs: $HTTP_CODE" \
      "$URL" \
      "  1. Inspect the response: curl -v '$URL' for headers.
  2. Re-cycle the stack if state seems corrupted."
    exit 2
    ;;
esac
