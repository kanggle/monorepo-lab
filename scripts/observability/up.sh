#!/usr/bin/env bash
# Bring up the worktree-isolated ephemeral observability stack.
#
# See:
# - docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md
# - infra/observability/README.md
# - tasks/done/TASK-MONO-065-observability-stack-scaffolding.md
#
# Usage:
#   ./scripts/observability/up.sh             # bring up against wms-platform-bootrun network
#   ./scripts/observability/up.sh --network <name>   # override network name
#
# Exit codes: 0 success / 1 pre-flight failure / 2 health timeout / 3 IO error.

set -euo pipefail

# ---------- 4-block remediation messages (OBSERVE-SCAFFOLD-NN) ---------------

emit_4block() {
  local id="$1" why="$2" file="$3"
  cat >&2 <<EOF

[VIOLATION] $id at $file
[WHY] $why
[REMEDIATION] Choose one:
$4
[REFERENCE] docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md § 2.3 D3
EOF
}

# ---------- Derive WORKTREE_HASH ---------------------------------------------

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$REPO_ROOT" ]; then
  emit_4block "OBSERVE-SCAFFOLD-01a" \
    "up.sh must be invoked from inside a git worktree; current directory is not under any git repo." \
    "$(pwd)" \
    "  1. cd into the monorepo-lab worktree root, then retry.
  2. If invoking from a script, set REPO_ROOT explicitly via env var (currently unsupported)."
  exit 1
fi

# Cross-OS sha256 (sha256sum on Linux/Rancher, shasum -a 256 on macOS).
if command -v sha256sum >/dev/null 2>&1; then
  WORKTREE_HASH="$(printf '%s' "$REPO_ROOT" | sha256sum | head -c 8)"
else
  WORKTREE_HASH="$(printf '%s' "$REPO_ROOT" | shasum -a 256 | head -c 8)"
fi
export WORKTREE_HASH

# ---------- Parse args -------------------------------------------------------

WMS_NETWORK="${WMS_NETWORK:-wms-platform-bootrun_default}"
while [ $# -gt 0 ]; do
  case "$1" in
    --network) WMS_NETWORK="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--network <docker-network-name>]"
      exit 0
      ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done
export WMS_NETWORK

PROJECT="wms-observability-${WORKTREE_HASH}"
COMPOSE_FILE="$REPO_ROOT/infra/observability/docker-compose.yml"

# ---------- Pre-flight: docker daemon ---------------------------------------

if ! docker info >/dev/null 2>&1; then
  emit_4block "OBSERVE-SCAFFOLD-01" \
    "Docker daemon is not reachable from the current shell." \
    "$(pwd)" \
    "  1. Start Docker Desktop / Rancher Desktop / dockerd and re-run up.sh.
  2. If using a remote DOCKER_HOST, verify the connection string with 'docker info' first."
  exit 1
fi

# ---------- Pre-flight: wms-platform network --------------------------------

if ! docker network inspect "$WMS_NETWORK" >/dev/null 2>&1; then
  emit_4block "OBSERVE-SCAFFOLD-02" \
    "The wms-platform docker network '$WMS_NETWORK' does not exist. Vector's docker_logs source has no containers to observe." \
    "$WMS_NETWORK" \
    "  1. Bring up the wms-platform bootRun stack first:
       docker compose -f projects/wms-platform/docker-compose.bootrun.yml up -d
     then retry up.sh.
  2. Pass --network <name> to attach to a different existing network."
  exit 1
fi

# ---------- WMS_SCRAPE_TARGETS_URLS derivation -------------------------------

# Build the prometheus_scrape endpoints list from the running wms services on
# the target network. Each wms service exposes /actuator/prometheus on its
# internal port 8080 (Spring Boot default).
WMS_SCRAPE_TARGETS_URLS=""
while IFS= read -r container; do
  [ -z "$container" ] && continue
  service_name="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$container" 2>/dev/null || true)"
  [ -z "$service_name" ] && continue
  case "$service_name" in
    *-service|gateway*|master*|inbound*|outbound*|inventory*|notification*|admin*)
      url="http://${service_name}:8080/actuator/prometheus"
      WMS_SCRAPE_TARGETS_URLS="${WMS_SCRAPE_TARGETS_URLS}${WMS_SCRAPE_TARGETS_URLS:+,}\"${url}\""
      ;;
  esac
done < <(docker network inspect "$WMS_NETWORK" --format '{{ range $i, $c := .Containers }}{{ $i }}{{ "\n" }}{{ end }}')
export WMS_SCRAPE_TARGETS_URLS

# ---------- Bring up --------------------------------------------------------

START_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
START_EPOCH="$(date +%s)"

echo "[up.sh] WORKTREE_HASH=$WORKTREE_HASH project=$PROJECT network=$WMS_NETWORK"
echo "[up.sh] scrape targets: ${WMS_SCRAPE_TARGETS_URLS:-(none — wms services not yet up)}"

docker compose -f "$COMPOSE_FILE" -p "$PROJECT" up -d

# ---------- Wait for health -------------------------------------------------

deadline=$((START_EPOCH + 30))
while [ "$(date +%s)" -lt "$deadline" ]; do
  healthy=$(docker compose -f "$COMPOSE_FILE" -p "$PROJECT" ps --format json | \
    awk -F'"Health":"' '/"Health"/ { split($2,a,"\""); print a[1] }' | \
    grep -c '^healthy$' || true)
  if [ "$healthy" = "3" ]; then
    break
  fi
  sleep 2
done

if [ "$healthy" != "3" ]; then
  emit_4block "OBSERVE-SCAFFOLD-03" \
    "Stack health check timeout — at least one of (vector / victorialogs / victoriametrics) did not report healthy within 30 s." \
    "$COMPOSE_FILE" \
    "  1. Inspect logs: docker compose -p $PROJECT logs
  2. Verify image versions are pullable (check internet connectivity).
  3. Tear down and retry: ./scripts/observability/down.sh && ./scripts/observability/up.sh"
  exit 2
fi

# ---------- Write port file -------------------------------------------------

PORTS_DIR="$REPO_ROOT/.observability"
mkdir -p "$PORTS_DIR" || {
  emit_4block "OBSERVE-SCAFFOLD-04" \
    "Cannot create port file directory at $PORTS_DIR — IO failure or permission denied." \
    "$PORTS_DIR" \
    "  1. Check write permission on the worktree root.
  2. Remove any stale .observability file (not directory) blocking the path."
  exit 3
}

vl_port="$(docker compose -f "$COMPOSE_FILE" -p "$PROJECT" port victorialogs 9428 | awk -F: '{ print $NF }')"
vm_port="$(docker compose -f "$COMPOSE_FILE" -p "$PROJECT" port victoriametrics 8428 | awk -F: '{ print $NF }')"
vec_port="$(docker compose -f "$COMPOSE_FILE" -p "$PROJECT" port vector 8686 | awk -F: '{ print $NF }')"
# Trace layer (ADR-MONO-007a). Best-effort: an older stack without the
# victoriatraces service leaves this blank (query-traces.sh then emits
# OBSERVE-QUERY-02 with a re-cycle hint).
vt_port="$(docker compose -f "$COMPOSE_FILE" -p "$PROJECT" port victoriatraces 10428 2>/dev/null | awk -F: '{ print $NF }')"

cat > "$PORTS_DIR/ports.env" <<EOF
WORKTREE_HASH=${WORKTREE_HASH}
PROJECT=${PROJECT}
NETWORK=${WMS_NETWORK}
VICTORIALOGS_PORT=${vl_port}
VICTORIAMETRICS_PORT=${vm_port}
VECTOR_PORT=${vec_port}
VICTORIATRACES_PORT=${vt_port}
STARTED_AT=${START_AT}
EOF

ELAPSED=$(($(date +%s) - START_EPOCH))

cat <<EOF

[up.sh] OK in ${ELAPSED}s. Stack project: $PROJECT

  VictoriaLogs:    http://127.0.0.1:${vl_port}
                   curl 'http://127.0.0.1:${vl_port}/select/logsql/query?query=*'
  VictoriaMetrics: http://127.0.0.1:${vm_port}
                   curl 'http://127.0.0.1:${vm_port}/api/v1/query?query=up'
  VictoriaTraces:  http://127.0.0.1:${vt_port}
                   curl 'http://127.0.0.1:${vt_port}/select/jaeger/api/traces/<trace_id>'
  Vector admin:    http://127.0.0.1:${vec_port}/health

  Ports recorded in: $PORTS_DIR/ports.env
  Tear down with:    ./scripts/observability/down.sh

EOF
