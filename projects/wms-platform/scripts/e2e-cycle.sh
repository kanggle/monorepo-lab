#!/usr/bin/env bash
# TASK-BE-048 verification helper: exercises docker-compose.e2e.yml through
# one full cold-start cycle and asserts that all 4 platform services reach
# the `healthy` state without restarting. Exits non-zero on any failure.
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.e2e.yml}"
CYCLE_LABEL="${1:-unnamed}"
DEADLINE_SEC=300
SERVICES=(gap-e2e-auth gap-e2e-account gap-e2e-admin gap-e2e-security)

echo "=== cycle ${CYCLE_LABEL}: down -v ==="
docker compose -f "${COMPOSE_FILE}" down -v >/dev/null 2>&1 || true

echo "=== cycle ${CYCLE_LABEL}: up -d ==="
docker compose -f "${COMPOSE_FILE}" up -d >/dev/null

start=$(date +%s)
while :; do
  now=$(date +%s)
  elapsed=$(( now - start ))
  if (( elapsed > DEADLINE_SEC )); then
    echo "!!! cycle ${CYCLE_LABEL}: timed out after ${DEADLINE_SEC}s"
    docker compose -f "${COMPOSE_FILE}" ps
    exit 1
  fi

  all_healthy=1
  for svc in "${SERVICES[@]}"; do
    status=$(docker inspect -f '{{.State.Health.Status}}' "${svc}" 2>/dev/null || echo "missing")
    restarts=$(docker inspect -f '{{.RestartCount}}' "${svc}" 2>/dev/null || echo "0")
    if [[ "${status}" != "healthy" ]]; then
      all_healthy=0
    fi
    if (( restarts > 0 )); then
      echo "!!! cycle ${CYCLE_LABEL}: ${svc} restarted (count=${restarts})"
      docker logs --tail=80 "${svc}" || true
      exit 1
    fi
  done

  if (( all_healthy == 1 )); then
    echo "=== cycle ${CYCLE_LABEL}: all services healthy in ${elapsed}s ==="
    break
  fi
  sleep 3
done

echo "=== cycle ${CYCLE_LABEL}: UnknownHostException grep ==="
for svc in "${SERVICES[@]}"; do
  if docker logs "${svc}" 2>&1 | grep -q "UnknownHostException"; then
    echo "!!! cycle ${CYCLE_LABEL}: ${svc} logged UnknownHostException"
    docker logs --tail=40 "${svc}"
    exit 1
  fi
done
echo "=== cycle ${CYCLE_LABEL}: OK ==="
