#!/usr/bin/env bash
# Query the worktree's ephemeral VictoriaTraces instance by trace_id.
#
# Spec source: this script has no single specs/ source — it is a cross-cutting
# helper that operates across every service's emitted trace surface. See:
#   .claude/skills/cross-cutting/observability-query/SKILL.md § Trace queries
#   docs/adr/ADR-MONO-007a-trace-layer.md § 2 D6 (/observe trace stub -> full)
#   infra/observability/README.md (operator guide)
#
# Usage:
#   query-traces.sh <trace_id>
#
# Output: the trace's span tree as JSON on stdout (VictoriaTraces Jaeger-compat
#   /select/jaeger/api/traces/<trace_id> response).
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

TRACE_ID=""
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      cat <<HELP
Usage: $0 <trace_id>

Examples:
  $0 0af7651916cd43dd8448eb211c80319c

Returns the span tree for one trace_id from VictoriaTraces. Use the
console-web SSR span as the tree root; for a console dashboard fan-out the
tree spans console-web -> console-bff -> per-domain producers.

See .claude/skills/cross-cutting/observability-query/SKILL.md § Trace queries.
HELP
      exit 0 ;;
    *) TRACE_ID="$1"; shift ;;
  esac
done

if [ -z "$TRACE_ID" ]; then
  echo "[query-traces.sh] missing trace_id argument; pass -h for usage" >&2
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

if [ -z "${VICTORIATRACES_PORT:-}" ]; then
  emit_4block "OBSERVE-QUERY-02" \
    "Port file present but VICTORIATRACES_PORT not set — stack predates the trace layer (ADR-MONO-007a) or corrupt port file." \
    "$PORTS_FILE" \
    "  1. ./scripts/observability/down.sh && ./scripts/observability/up.sh to re-cycle with the trace layer.
  2. Verify the victoriatraces service is in infra/observability/docker-compose.yml (ADR-MONO-007a / TASK-MONO-143)."
  exit 2
fi

# ---------- Curl + classify response ----------------------------------------

URL="http://127.0.0.1:${VICTORIATRACES_PORT}/select/jaeger/api/traces/${TRACE_ID}"

HTTP_BODY="$(mktemp)"
trap 'rm -f "$HTTP_BODY"' EXIT
HTTP_CODE="$(curl -sS -o "$HTTP_BODY" -w "%{http_code}" "$URL" || echo "000")"

# Count spans in the Jaeger-compat response (data[0].spans[]). jq when present;
# otherwise a coarse grep on "spanID".
count_spans() {
  if command -v jq >/dev/null 2>&1; then
    jq -r '[.data[]?.spans[]?] | length' "$HTTP_BODY" 2>/dev/null || echo "0"
  else
    grep -o '"spanID"' "$HTTP_BODY" 2>/dev/null | wc -l | tr -d ' '
  fi
}

case "$HTTP_CODE" in
  200)
    SPANS="$(count_spans)"
    if [ "${SPANS:-0}" -eq 0 ]; then
      emit_4block "OBSERVE-QUERY-06" \
        "No trace found for trace_id=$TRACE_ID (empty span set)." \
        "$URL" \
        "  1. Confirm the trace_id is correct (copy from the X-Request-Id-correlated log line or the SSR span).
  2. Allow trace-export flush latency — producers batch-export; retry after a few seconds.
  3. Verify OTEL_EXPORTER_OTLP_ENDPOINT is set on the services so they export to Vector -> VictoriaTraces."
      exit 3
    fi
    cat "$HTTP_BODY"
    # A complete console dashboard fan-out tree is 7 spans (console-web SSR +
    # console-bff aggregation + 5 producers). Fewer may be a partial tree
    # (broken span chain) — surface as a non-fatal OBSERVE-QUERY-07 hint while
    # still returning the data on stdout.
    if [ "${SPANS:-0}" -lt 7 ]; then
      emit_4block "OBSERVE-QUERY-07" \
        "Trace found but only $SPANS span(s) — fewer than the 7-span console fan-out tree; possible broken span chain (a layer dropped/regenerated trace_id)." \
        "$URL" \
        "  1. If the dashboard only invoked a subset of domains, fewer spans is expected — not an error.
  2. Otherwise check each layer propagates W3C traceparent: console-web (instrumentation.ts) -> console-bff (RestClient ObservationRegistry) -> producers (micrometer-tracing-bridge-otel).
  3. A layer that starts a NEW root (no parent) indicates it dropped the inbound traceparent."
      exit 0
    fi
    exit 0
    ;;
  400)
    PARSER_MSG="$(head -c 500 "$HTTP_BODY")"
    emit_4block "OBSERVE-QUERY-03" \
      "VictoriaTraces returned 400 — bad trace_id format or query error: $PARSER_MSG" \
      "$URL" \
      "  1. trace_id must be the hex W3C trace id (no dashes), e.g. 0af7651916cd43dd8448eb211c80319c.
  2. The web UI at http://127.0.0.1:${VICTORIATRACES_PORT}/select/vmui can validate interactively (humans only)."
    exit 2
    ;;
  404)
    emit_4block "OBSERVE-QUERY-06" \
      "VictoriaTraces returned 404 for trace_id=$TRACE_ID — trace not found." \
      "$URL" \
      "  1. Allow export flush latency and retry.
  2. Confirm the trace_id and that the services exported to Vector -> VictoriaTraces."
    exit 3
    ;;
  000|5*)
    emit_4block "OBSERVE-QUERY-02" \
      "VictoriaTraces unreachable or 5xx — stack may be mid-teardown or container crashed." \
      "$URL" \
      "  1. docker compose -p \$PROJECT ps to check container state.
  2. ./scripts/observability/down.sh && ./scripts/observability/up.sh to re-cycle.
  3. docker logs victoriatraces to inspect."
    exit 2
    ;;
  *)
    emit_4block "OBSERVE-QUERY-02" \
      "Unexpected HTTP status from VictoriaTraces: $HTTP_CODE" \
      "$URL" \
      "  1. Inspect the response: curl -v '$URL' for headers.
  2. Re-cycle the stack if state seems corrupted."
    exit 2
    ;;
esac
