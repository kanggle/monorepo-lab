#!/usr/bin/env bash
# =============================================================
# E2E Healthcheck Script
# docker compose 환경의 모든 서비스 헬스체크 상태를 확인한다.
# =============================================================
set -euo pipefail

# ── 설정 ──────────────────────────────────────────────────────
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
MAX_RETRIES="${MAX_RETRIES:-30}"
RETRY_INTERVAL="${RETRY_INTERVAL:-5}"

# 서비스 목록: "이름 URL"
SERVICES=(
  "gateway-service    ${GATEWAY_URL}/actuator/health"
  "auth-service       http://localhost:8081/actuator/health"
  "product-service    http://localhost:8082/actuator/health"
  "search-service     http://localhost:8085/actuator/health"
  "order-service      http://localhost:8086/actuator/health"
  "payment-service    http://localhost:8087/actuator/health"
  "batch-worker       http://localhost:8088/actuator/health"
)

# ── 색상 ──────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

# ── 카운터 ────────────────────────────────────────────────────
PASS=0
FAIL=0

# ── 함수 ──────────────────────────────────────────────────────
log_pass() { echo -e "  ${GREEN}[PASS]${NC} $1"; PASS=$((PASS + 1)); }
log_fail() { echo -e "  ${RED}[FAIL]${NC} $1 — $2"; FAIL=$((FAIL + 1)); }
log_info() { echo -e "  ${YELLOW}[INFO]${NC} $1"; }

check_http_health() {
  local name="$1"
  local url="$2"
  local attempt=1

  while [ "$attempt" -le "$MAX_RETRIES" ]; do
    local status
    status=$(curl -sf -o /dev/null -w "%{http_code}" "$url" 2>/dev/null) || status="000"

    if [ "$status" -ge 200 ] && [ "$status" -lt 300 ]; then
      log_pass "$name (HTTP $status)"
      return 0
    fi

    if [ "$attempt" -lt "$MAX_RETRIES" ]; then
      log_info "$name 아직 준비 안 됨 (HTTP $status), 재시도 $attempt/$MAX_RETRIES..."
      sleep "$RETRY_INTERVAL"
    fi
    attempt=$((attempt + 1))
  done

  log_fail "$name" "HTTP $status (최대 재시도 횟수 초과)"
  return 1
}

check_redis() {
  local attempt=1
  while [ "$attempt" -le "$MAX_RETRIES" ]; do
    if command -v redis-cli &>/dev/null; then
      if redis-cli -h localhost -p 6379 ping 2>/dev/null | grep -q PONG; then
        log_pass "redis"
        return 0
      fi
    else
      if curl -sf -o /dev/null "http://localhost:6379" 2>/dev/null || nc -z localhost 6379 2>/dev/null; then
        log_pass "redis (port open)"
        return 0
      fi
    fi

    if [ "$attempt" -lt "$MAX_RETRIES" ]; then
      log_info "redis 아직 준비 안 됨, 재시도 $attempt/$MAX_RETRIES..."
      sleep "$RETRY_INTERVAL"
    fi
    attempt=$((attempt + 1))
  done

  log_fail "redis" "연결 실패 (최대 재시도 횟수 초과)"
  return 1
}

check_kafka() {
  local attempt=1
  while [ "$attempt" -le "$MAX_RETRIES" ]; do
    if nc -z localhost 9093 2>/dev/null; then
      log_pass "kafka (port 9093 open)"
      return 0
    fi

    if [ "$attempt" -lt "$MAX_RETRIES" ]; then
      log_info "kafka 아직 준비 안 됨, 재시도 $attempt/$MAX_RETRIES..."
      sleep "$RETRY_INTERVAL"
    fi
    attempt=$((attempt + 1))
  done

  log_fail "kafka" "포트 9093 연결 실패 (최대 재시도 횟수 초과)"
  return 1
}

# ── 메인 ──────────────────────────────────────────────────────
echo ""
echo "============================================"
echo " E2E Healthcheck — 서비스 상태 확인"
echo "============================================"
echo ""

# 인프라 서비스 체크
echo "── 인프라 서비스 ──"
check_redis || true
check_http_health "elasticsearch" "http://localhost:9200/_cluster/health" || true
check_kafka || true
echo ""

# 백엔드 서비스 체크
echo "── 백엔드 서비스 ──"
for entry in "${SERVICES[@]}"; do
  name=$(echo "$entry" | awk '{print $1}')
  url=$(echo "$entry" | awk '{print $2}')
  check_http_health "$name" "$url" || true
done
echo ""

# 프론트엔드 서비스 체크
echo "── 프론트엔드 서비스 ──"
check_http_health "web-store" "http://localhost:3000/" || true
echo ""

# ── 결과 요약 ─────────────────────────────────────────────────
echo "============================================"
echo -e " 결과: ${GREEN}PASS $PASS${NC} / ${RED}FAIL $FAIL${NC}"
echo "============================================"
echo ""

if [ "$FAIL" -gt 0 ]; then
  echo -e "${RED}일부 서비스가 헬스체크에 실패했습니다.${NC}"
  exit 1
fi

echo -e "${GREEN}모든 서비스가 정상 기동되었습니다.${NC}"
exit 0
