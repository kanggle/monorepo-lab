#!/usr/bin/env bash
# Query the worktree's ephemeral VictoriaMetrics instance via PromQL.
#
# Spec source: this script has no single specs/ source — it is a cross-cutting
# helper that operates across every wms service's Micrometer metric surface. See:
#   .claude/skills/cross-cutting/observability-query/SKILL.md
#   docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md § 2.4 D4
#   infra/observability/README.md (operator guide)
#
# Usage:
#   query-metrics.sh '<PromQL>'                       # instant query (now)
#   query-metrics.sh '<PromQL>' --range 5m            # range query, now-5m .. now, step 15s
#   query-metrics.sh '<PromQL>' --range 5m --step 30s # range query with custom step
#
# Output: JSON response from VictoriaMetrics (.data.result array).
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

QUERY=""
RANGE_DURATION=""
STEP="15s"
while [ $# -gt 0 ]; do
  case "$1" in
    --range) RANGE_DURATION="$2"; shift 2 ;;
    --step)  STEP="$2"; shift 2 ;;
    -h|--help)
      cat <<HELP
Usage:
  $0 '<PromQL>'                                  # instant
  $0 '<PromQL>' --range 5m                       # range (now-5m..now, step 15s)
  $0 '<PromQL>' --range 5m --step 30s            # range with custom step

Examples:
  $0 'up'
  $0 'jvm_memory_used_bytes{area="heap"}'
  $0 --range 5m 'rate(http_server_requests_seconds_count[1m])'

See .claude/skills/cross-cutting/observability-query/SKILL.md for the series reference.
HELP
      exit 0 ;;
    *) QUERY="$1"; shift ;;
  esac
done

if [ -z "$QUERY" ]; then
  echo "[query-metrics.sh] missing query argument; pass -h for usage" >&2
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

if [ -z "${VICTORIAMETRICS_PORT:-}" ]; then
  emit_4block "OBSERVE-QUERY-02" \
    "Port file present but VICTORIAMETRICS_PORT not set — corrupt port file." \
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
    printf '%s' "$1" | sed 's/ /%20/g; s/"/%22/g; s/{/%7B/g; s/}/%7D/g; s/|/%7C/g; s/(/%28/g; s/)/%29/g; s/\[/%5B/g; s/\]/%5D/g'
  fi
}

ENCODED="$(url_encode "$QUERY")"

# ---------- Duration parsing for range queries -----------------------------

parse_duration_to_seconds() {
  local input="$1"
  case "$input" in
    *s) echo "${input%s}" ;;
    *m) echo $(( ${input%m} * 60 )) ;;
    *h) echo $(( ${input%h} * 3600 )) ;;
    *d) echo $(( ${input%d} * 86400 )) ;;
    *)  echo "$input" ;;  # assume seconds if no suffix
  esac
}

# ---------- Build URL ------------------------------------------------------

if [ -n "$RANGE_DURATION" ]; then
  RANGE_SECS="$(parse_duration_to_seconds "$RANGE_DURATION")"
  END_TS="$(date +%s)"
  START_TS="$(( END_TS - RANGE_SECS ))"
  STEP_SECS="$(parse_duration_to_seconds "$STEP")"
  URL="http://127.0.0.1:${VICTORIAMETRICS_PORT}/api/v1/query_range?query=${ENCODED}&start=${START_TS}&end=${END_TS}&step=${STEP_SECS}"
else
  URL="http://127.0.0.1:${VICTORIAMETRICS_PORT}/api/v1/query?query=${ENCODED}"
fi

# ---------- Curl + classify response ----------------------------------------

HTTP_BODY="$(mktemp)"
trap 'rm -f "$HTTP_BODY"' EXIT
HTTP_CODE="$(curl -sS -o "$HTTP_BODY" -w "%{http_code}" "$URL" || echo "000")"

case "$HTTP_CODE" in
  200)
    if command -v jq >/dev/null 2>&1; then
      RESULT_COUNT="$(jq '.data.result | length' < "$HTTP_BODY" 2>/dev/null || echo 0)"
    else
      RESULT_COUNT="$(grep -o '"result":\s*\[[^]]*' "$HTTP_BODY" | head -c 5000 | wc -c)"
    fi

    if [ "$RESULT_COUNT" = "0" ]; then
      cat "$HTTP_BODY"
      emit_4block "OBSERVE-QUERY-04" \
        "Query succeeded but returned an empty result set." \
        "$URL" \
        "  1. Widen the range: --range 1h instead of --range 5m, or drop --range for an instant query at 'now'.
  2. Verify the metric is being emitted: query 'up' to see scrape target health first.
  3. Check the metric name spelling against the service's Micrometer registration site."
      exit 3
    else
      cat "$HTTP_BODY"
      exit 0
    fi
    ;;
  400)
    PARSER_MSG="$(head -c 500 "$HTTP_BODY")"
    emit_4block "OBSERVE-QUERY-03" \
      "VictoriaMetrics returned 400 — query syntax error: $PARSER_MSG" \
      "$URL" \
      "  1. Consult the PromQL primer in .claude/skills/cross-cutting/observability-query/SKILL.md.
  2. Common issues: unmatched brackets, missing metric name, range vector outside rate/increase.
  3. The web UI at http://127.0.0.1:${VICTORIAMETRICS_PORT}/vmui can validate queries interactively (humans only)."
    exit 2
    ;;
  000|5*)
    emit_4block "OBSERVE-QUERY-02" \
      "VictoriaMetrics unreachable or returned 5xx — stack may be mid-teardown or container crashed." \
      "$URL" \
      "  1. docker compose -p \$PROJECT ps to check container state.
  2. ./scripts/observability/down.sh && ./scripts/observability/up.sh to re-cycle.
  3. docker logs to inspect crashed containers."
    exit 2
    ;;
  *)
    emit_4block "OBSERVE-QUERY-02" \
      "Unexpected HTTP status from VictoriaMetrics: $HTTP_CODE" \
      "$URL" \
      "  1. Inspect the response: curl -v '$URL' for headers.
  2. Re-cycle the stack if state seems corrupted."
    exit 2
    ;;
esac
