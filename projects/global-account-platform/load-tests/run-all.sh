#!/usr/bin/env bash
#
# 전체 부하 테스트 실행 스크립트
#
# 사용법:
#   ./load-tests/run-all.sh              # 모든 시나리오 순차 실행
#   ./load-tests/run-all.sh auth         # 인증 시나리오만 실행
#   ./load-tests/run-all.sh search       # 검색 시나리오만 실행
#   ./load-tests/run-all.sh order        # 주문 시나리오만 실행
#   ./load-tests/run-all.sh payment      # 결제 시나리오만 실행
#   ./load-tests/run-all.sh e2e          # E2E 흐름만 실행
#
# 전제조건:
#   1. docker-compose up -d 로 전체 스택이 실행 중
#   2. k6가 로컬에 설치되어 있거나, docker 방식 사용
#
# 환경변수:
#   BASE_URL  — 게이트웨이 URL (기본: http://localhost:8080)
#   USE_DOCKER — docker 기반 k6 사용 여부 (기본: false)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
BASE_URL="${BASE_URL:-http://localhost:8080}"
USE_DOCKER="${USE_DOCKER:-false}"
TARGET="${1:-all}"

mkdir -p "${RESULTS_DIR}"

SCENARIOS=("auth" "search" "order" "payment" "e2e")
SCENARIO_FILES=(
  "auth-load-test.js"
  "search-load-test.js"
  "order-load-test.js"
  "payment-load-test.js"
  "e2e-flow-load-test.js"
)

run_k6() {
  local scenario_file="$1"
  local result_name="$2"
  local timestamp
  timestamp=$(date +%Y%m%d-%H%M%S)

  echo "=========================================="
  echo " Running: ${result_name} (${scenario_file})"
  echo " Target:  ${BASE_URL}"
  echo " Time:    ${timestamp}"
  echo "=========================================="

  if [ "${USE_DOCKER}" = "true" ]; then
    docker run --rm --network host \
      -v "${SCRIPT_DIR}/scenarios:/scripts" \
      -v "${SCRIPT_DIR}/lib:/scripts/lib" \
      -v "${RESULTS_DIR}:/results" \
      -e "BASE_URL=${BASE_URL}" \
      grafana/k6:0.50.0 run \
        --out "json=/results/${result_name}-${timestamp}.json" \
        --summary-export "/results/${result_name}-${timestamp}-summary.json" \
        "/scripts/${scenario_file}"
  else
    k6 run \
      -e "BASE_URL=${BASE_URL}" \
      --out "json=${RESULTS_DIR}/${result_name}-${timestamp}.json" \
      --summary-export "${RESULTS_DIR}/${result_name}-${timestamp}-summary.json" \
      "${SCRIPT_DIR}/scenarios/${scenario_file}"
  fi

  echo ""
  echo " Completed: ${result_name}"
  echo " Results:   ${RESULTS_DIR}/${result_name}-${timestamp}-summary.json"
  echo ""
}

run_scenario() {
  local idx="$1"
  run_k6 "${SCENARIO_FILES[$idx]}" "${SCENARIOS[$idx]}"
}

# 게이트웨이 헬스체크
echo "Checking gateway health at ${BASE_URL}..."
if ! curl -sf "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
  echo "ERROR: Gateway is not reachable at ${BASE_URL}/actuator/health"
  echo "Make sure 'docker-compose up -d' is running."
  exit 1
fi
echo "Gateway is healthy."
echo ""

if [ "${TARGET}" = "all" ]; then
  for i in "${!SCENARIOS[@]}"; do
    run_scenario "$i"
  done
  echo "=========================================="
  echo " All load tests completed!"
  echo " Results directory: ${RESULTS_DIR}"
  echo "=========================================="
else
  found=false
  for i in "${!SCENARIOS[@]}"; do
    if [ "${SCENARIOS[$i]}" = "${TARGET}" ]; then
      run_scenario "$i"
      found=true
      break
    fi
  done
  if [ "${found}" = "false" ]; then
    echo "ERROR: Unknown scenario '${TARGET}'"
    echo "Available: ${SCENARIOS[*]}"
    exit 1
  fi
fi
