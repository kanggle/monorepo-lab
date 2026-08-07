#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed-erp.sh — erp 도메인 데모 데이터 (TASK-MONO-510)
# =============================================================================
# 콘솔 ERP 섹션 6화면 중 데이터에 의존하는 4개를 채운다:
#
#   /erp/masters     ← masterdata-service 5개 마스터 (부서·직급·코스트센터·사원·거래처)
#   /erp/orgview     ← read-model-service 사원 프로젝션 (Kafka 경유, AC-3)
#   /erp/approval    ← approval-service 결재 요청 목록  ⚠ 결재함(inbox)은 아래 참조
#   /erp/delegation  ← approval-service 위임 + read-model 위임 프로젝션
#
# 전량 실제 API 다. 직접-DB 0건 — erp 는 다섯 마스터 전부에 생성 엔드포인트가 있고
# 운영자 토큰이 그것을 연다(실측: `POST /api/erp/masterdata/departments` → 201).
#
# -----------------------------------------------------------------------------
# 🔴 이 도메인의 결정적 제약 — **결재함(inbox)은 이 데모에서 채울 수 없다**
# -----------------------------------------------------------------------------
# 세 가지가 겹쳐서 그렇고, 셋 다 실측했다:
#
#   1. 결재함은 `findInbox(tenantId, actorId)` — **호출자가 현재 단계 승인자인 건**만
#      돌려준다. `actorId` = JWT `sub`.
#   2. 라우트 검증이 **자기결재를 거부한다**(`ApprovalRoute.multiStage`: "submitter ∈
#      any stage" → `ApprovalRouteInvalidException`). 즉 제출자와 승인자는 달라야 한다.
#   3. 그런데 콘솔 운영자 토큰의 `sub` 는 **계정이 아니라 클라이언트 id** 다:
#
#        base 로그인 토큰   sub = 0199de70-…ad03  (계정 UUID)
#        assume 후 토큰     sub = platform-console-web
#
#      이것은 결함이 아니라 **명시된 동작**이다 — `AssumeTenantExchangeIntegrationTest`
#      가 "the assumed token's own sub is the acting console client … per the RFC 8693
#      flow" 라고 단언한다. 그리고 `oauth_clients` 에서 token-exchange grant 를 가진
#      클라이언트는 `platform-console-web` **하나뿐**이다(실측).
#
#   ⇒ 테넌트 `demo-corp` 안에서 가능한 actorId 는 **정확히 하나**다. 제출자≠승인자를
#      만족시킬 두 번째 신원이 존재하지 않으므로, 운영자의 결재함은 **구조적으로 0** 이고
#      운영자는 어떤 건도 승인·반려할 수 없다(모든 전이가 "현재 단계 승인자인가"를 본다).
#
# 그래서 이 시드는 **결재 목록**만 채운다. 운영자는 `isOperator()` 라서 목록 조회가
# `findAll(tenantId)` 로 가고(실측: `ApprovalApplicationService.list`), 승인자가 누구든
# 테넌트 전체가 보인다. 승인자에는 **시드가 만든 사원 id** 를 쓴다 — 실재하는 주체이고,
# 프로덕션에서 그 사원이 자기 계정으로 로그인하면 그대로 결재함에 뜨는 **도달 가능한
# 상태**다(존재할 수 없는 상태를 만들지 않는다 — README 의 규약).
#
# 🔵 직접-DB 로 결재함을 채우지 않은 이유: 그렇게 하면 화면은 차지만 **버튼이 동작하지
#    않는다**(승인 요청이 401/403 이 아니라 "현재 단계가 아니다" 로 거절된다). 빈 화면보다
#    나쁜 것은 **눌리는데 실패하는 화면**이다. 갭은 `TASK-MONO-515` 로 분리했다.
#
# -----------------------------------------------------------------------------
# ✅ 차단 경로 0건 — 면제를 **전부 회수했다**
# -----------------------------------------------------------------------------
# 이 시드는 한때 두 개의 `⛔ 차단` 면제를 달고 있었다. 둘 다 결함이 닫히면서 회수됐다:
#
#   TASK-ERP-BE-042  아웃박스 릴레이 미기동 → 프로젝션 대기가 `seed_fail` 로 복귀
#                    (실측: 미발행 백로그 16+1 전량 발행, 사원 4/4).
#   TASK-ERP-BE-041  상신이 항상 422 `subject_unresolved` → 상신 실패가 `seed_fail` 로
#                    복귀(실측: 차단 2 → 0, `DRAFT 1 · SUBMITTED 2`).
#
# 🔴 **고쳐진 결함의 면제를 남겨 두면 그 면제가 정확히 회귀를 가리는 장치가 된다.**
# 두 경로 모두 이제 실측으로 성립하므로, 앞으로의 실패는 알려진 결함이 아니라 **회귀**다.
# 그래서 `⛔ 차단` 분류 자체(카운터·헬퍼·요약 줄)도 함께 걷어냈다 — 아무도 세울 수 없는
# 카운터가 매 실행 `차단 0` 을 찍으면 "우리는 차단을 추적하고 있다" 로 읽히지만 실제로는
# 아무것도 추적하지 않는다. 다음 결함이 생기면 그때 **의도적으로** 다시 세운다
# (형태는 `seed/README.md` § 알려진 제품 결함을 시드의 실패와 구별하라 가 보존한다).
#
# 🔵 남은 read-model 공백 하나는 **위임 사실 프로젝션**이다 — approval 봉투에 최상위
#    `aggregateId`/`tenantId` 가 없는데 소비자는 그것을 필수로 요구해 메시지가 DLT 로
#    간다(`TASK-ERP-BE-043`). BE-041 이 닫히자 `erp.approval.submitted.v1` 도 같은
#    운명임이 실측됐다(end-offset 2 / DLT 4). 이것은 **시드가 만드는 행이 아니라 투영**의
#    문제이므로 시드에는 차단 경로가 없다 — 원본 목록은 전부 정상이다.
#
# -----------------------------------------------------------------------------
# 멱등 (AC-5)
# -----------------------------------------------------------------------------
# 마스터는 자연키(`code` / `employeeNumber`)가 있고 서버가 중복을 409 로 거절한다.
# 그래도 **먼저 목록에서 찾는다** — 2회차에 id 가 필요하기 때문이다(409 응답에는 id 가
# 없다). 결재·위임은 자연키가 없어 제목/피위임자로 탐지한다.
#
# 🔴 `Idempotency-Key` 는 매 호출 **새 UUID** 다. 고정 키는 실패 응답까지 재생하므로
#    (seed-wms.sh 가 실측한 함정) 한 번 실패한 키가 영구히 실패를 재생한다.
# 🔴 id 추출이 0건이면 **실패로 센다.** 조용히 넘어가면 뒤 단계가 빈 문자열을 URL 에
#    넣어 요청이 형태부터 깨지고, 요약은 "실패 0" 이 된다.
# =============================================================================
set -uo pipefail

SEED_DOMAIN=erp
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/demo/seed/lib.sh
[ "${SEED_LIB_LOADED:-0}" = "1" ] || source "$HERE/lib.sh"
SEED_DOMAIN=erp

ERP="http://erp.${DEMO_DOMAIN}"

# 이 슬라이스에 erp 가 없는 것은 실패가 아니다.
if ! container_up erp-platform-gateway; then
  seed_log "erp 게이트웨이가 기동돼 있지 않습니다 — 건너뜁니다"
  exit 0
fi

if ! wait_http "$ERP/api/erp/masterdata/departments" 240; then
  seed_fail "erp 게이트웨이가 240초 안에 응답하지 않았습니다"
  exit 1
fi

SEED_TOKEN="$(operator_token demo-corp)"
if [ -z "$SEED_TOKEN" ]; then
  seed_fail "운영자 토큰(assume demo-corp)을 얻지 못했습니다"
  exit 1
fi
export SEED_TOKEN

# --- 도구 -------------------------------------------------------------------
# Git Bash(msys)에는 /proc/sys/kernel/random/uuid 가 없다.
uuid() { openssl rand -hex 16 | sed -E 's/(.{8})(.{4})(.{4})(.{4})(.{12})/\1-\2-\3-\4-\5/'; }

# JSON 배열을 객체당 한 줄로 쪼갠다. 🔴 `sed -E 's/.*"key":"([^"]*)".*/\1/'` 로 통짜
# 응답을 긁으면 `.*` 가 greedy 라 **마지막** 매치를 집는다(seed-wms.sh 가 밟은 함정).
# 객체 단위로 자른 뒤 그 안에서 찾으면 필드 순서에도 의존하지 않는다.
json_objects() { printf '%s' "$1" | sed 's/},{/}\n{/g'; }

# obj_by <body> <key> <value> — 그 키=값을 가진 첫 객체(한 줄)
obj_by() { json_objects "$1" | grep -F "\"$2\":\"$3\"" | head -1; }

# field <obj> <key> — 객체 안의 문자열 필드. `"id":"` 는 앞에 따옴표가 있어야 매치되므로
# `"approverId":"…"` / `"subjectId":"…"` 에는 걸리지 않는다.
field() { printf '%s' "$1" | grep -oE "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

# ensure_master <라벨> <경로> <자연키> <키값> <body> → MASTER_ID 에 id 를 남긴다.
MASTER_ID=""
ensure_master() {
  local label="$1" path="$2" key="$3" val="$4" body="$5"
  MASTER_ID=""
  if http GET "$ERP$path?size=100"; then
    MASTER_ID="$(field "$(obj_by "$SEED_LAST_BODY" "$key" "$val")" id)"
  fi
  if [ -n "$MASTER_ID" ]; then
    SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  $label"
    return 0
  fi
  if http POST "$ERP$path" "$body" -H "Idempotency-Key: $(uuid)"; then
    MASTER_ID="$(field "$SEED_LAST_BODY" id)"
    SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  $label"
  elif [ "$SEED_LAST_STATUS" = "409" ] || [ "$SEED_LAST_STATUS" = "422" ]; then
    # 다른 실행과 경합했거나 자연키 탐지가 놓쳤다 — 다시 읽어 id 를 확보한다.
    http GET "$ERP$path?size=100" \
      && MASTER_ID="$(field "$(obj_by "$SEED_LAST_BODY" "$key" "$val")" id)"
    SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  $label (HTTP $SEED_LAST_STATUS)"
  else
    seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
    return 1
  fi
  if [ -z "$MASTER_ID" ]; then
    seed_fail "$label — id 추출 0건 (뒤 단계가 빈 id 로 깨진다)"
    return 1
  fi
  return 0
}

# --- 백엔드 준비성 -----------------------------------------------------------
# 🔵 `wait_backend` 는 **lib.sh 로 승격**됐다(2026-08-07) — 같은 게이트가 없어서
# scm 과 wms 도 `demo-up.sh` 직후 전건 500 을 냈기 때문이다. 왜 필요한지는 lib.sh
# 의 정의부 주석에 세 도메인의 실측과 함께 있다.
ready=1
wait_backend "masterdata-service" "$ERP/api/erp/masterdata/departments" || ready=0
wait_backend "approval-service"   "$ERP/api/erp/approval/requests"   60 || ready=0
wait_backend "read-model-service" "$ERP/api/erp/read-model/employees" 60 || ready=0
[ "$ready" = "1" ] || exit 1

seed_log "시작 — $ERP (운영자 토큰, assume demo-corp)"

# =============================================================================
# 1. 직급 — 다른 마스터가 참조하므로 먼저.
# =============================================================================
declare -A GRADE=()
ensure_master "직급 G1 사원"   /api/erp/masterdata/job-grades code JG-G1 \
  '{"code":"JG-G1","name":"사원","displayOrder":10,"effectiveFrom":"2026-01-01"}' \
  && GRADE[G1]="$MASTER_ID"
ensure_master "직급 G2 대리"   /api/erp/masterdata/job-grades code JG-G2 \
  '{"code":"JG-G2","name":"대리","displayOrder":20,"effectiveFrom":"2026-01-01"}' \
  && GRADE[G2]="$MASTER_ID"
ensure_master "직급 G3 부장"   /api/erp/masterdata/job-grades code JG-G3 \
  '{"code":"JG-G3","name":"부장","displayOrder":30,"effectiveFrom":"2026-01-01"}' \
  && GRADE[G3]="$MASTER_ID"

# =============================================================================
# 2. 부서 — 트리로 만든다. `/erp/orgview` 가 계층을 그리므로 평평하면 화면이 증명하는
#    것이 없다. 루트를 먼저 만들고 자식이 parentId 로 참조한다.
# =============================================================================
declare -A DEPT=()
ensure_master "부서 본사"      /api/erp/masterdata/departments code DEPT-HQ \
  '{"code":"DEPT-HQ","name":"본사","effectiveFrom":"2026-01-01"}' \
  && DEPT[HQ]="$MASTER_ID"
if [ -n "${DEPT[HQ]:-}" ]; then
  ensure_master "부서 운영본부" /api/erp/masterdata/departments code DEPT-OPS \
    "{\"code\":\"DEPT-OPS\",\"name\":\"운영본부\",\"parentId\":\"${DEPT[HQ]}\",\"effectiveFrom\":\"2026-01-01\"}" \
    && DEPT[OPS]="$MASTER_ID"
  ensure_master "부서 재무팀"   /api/erp/masterdata/departments code DEPT-FIN \
    "{\"code\":\"DEPT-FIN\",\"name\":\"재무팀\",\"parentId\":\"${DEPT[HQ]}\",\"effectiveFrom\":\"2026-01-01\"}" \
    && DEPT[FIN]="$MASTER_ID"
else
  seed_fail "본사 부서 id 가 없어 하위 부서를 만들 수 없습니다"
fi

# =============================================================================
# 3. 코스트센터 — 부서 참조 필수.
# =============================================================================
declare -A CC=()
for k in HQ OPS FIN; do
  [ -n "${DEPT[$k]:-}" ] || continue
  case "$k" in
    HQ)  nm="본사 공통";   ;;
    OPS) nm="운영 코스트센터"; ;;
    FIN) nm="재무 코스트센터"; ;;
  esac
  ensure_master "코스트센터 $nm" /api/erp/masterdata/cost-centers code "CC-$k" \
    "{\"code\":\"CC-$k\",\"name\":\"$nm\",\"departmentId\":\"${DEPT[$k]}\",\"effectiveFrom\":\"2026-01-01\"}" \
    && CC[$k]="$MASTER_ID"
done

# =============================================================================
# 4. 사원 — 부서·코스트센터·직급 셋 다 필수(@NotBlank). 결재 승인자로도 쓰인다.
# =============================================================================
declare -A EMP=()
emp() { # emp <번호> <이름> <부서키> <직급키>
  local no="$1" nm="$2" dk="$3" gk="$4"
  [ -n "${DEPT[$dk]:-}" ] && [ -n "${CC[$dk]:-}" ] && [ -n "${GRADE[$gk]:-}" ] || {
    seed_fail "사원 $nm — 선행 마스터(부서/코스트센터/직급) id 가 비어 있습니다"; return 1; }
  ensure_master "사원 $nm" /api/erp/masterdata/employees employeeNumber "$no" \
    "{\"employeeNumber\":\"$no\",\"name\":\"$nm\",\"departmentId\":\"${DEPT[$dk]}\",\"costCenterId\":\"${CC[$dk]}\",\"jobGradeId\":\"${GRADE[$gk]}\",\"effectiveFrom\":\"2026-01-01\"}" \
    && EMP["$no"]="$MASTER_ID"
}
emp EMP-0001 "김본부" HQ  G3
emp EMP-0002 "이운영" OPS G2
emp EMP-0003 "박재무" FIN G2
emp EMP-0004 "최사원" OPS G1

# =============================================================================
# 5. 거래처 — partnerType/paymentTerms.method 는 enum 이다(코드가 권위):
#    PartnerType = CUSTOMER|SUPPLIER|BOTH,
#    PaymentMethod = BANK_TRANSFER|CREDIT_CARD|CASH|CHECK.
#    세 유형을 모두 넣어 `/erp/masters` 의 partnerType 필터가 실제로 갈리게 한다.
# =============================================================================
ensure_master "거래처 공급사"     /api/erp/masterdata/business-partners code BP-SUP-001 \
  '{"code":"BP-SUP-001","name":"한빛물산","partnerType":"SUPPLIER","paymentTerms":{"termDays":30,"method":"BANK_TRANSFER"},"effectiveFrom":"2026-01-01"}'
ensure_master "거래처 고객사"     /api/erp/masterdata/business-partners code BP-CUS-001 \
  '{"code":"BP-CUS-001","name":"대성유통","partnerType":"CUSTOMER","paymentTerms":{"termDays":15,"method":"CREDIT_CARD"},"effectiveFrom":"2026-01-01"}'
ensure_master "거래처 겸업사"     /api/erp/masterdata/business-partners code BP-BOTH-001 \
  '{"code":"BP-BOTH-001","name":"세종상사","partnerType":"BOTH","paymentTerms":{"termDays":45,"method":"CHECK"},"effectiveFrom":"2026-01-01"}'

# =============================================================================
# 6. 결재 요청 — 제목으로 탐지한다(자연키 없음).
#
#    subjectId 는 **실재하는 마스터**여야 한다(submit 시 `MasterDataPort` 가 해소한다).
#    approverId 는 위 헤더의 이유로 사원 id 를 쓴다.
#
#    🔵 상태를 셋 만든다 — DRAFT 1 · SUBMITTED 2. 목록 화면이 상태 필터를 가지므로
#       한 가지 상태만 넣으면 필터가 아무것도 증명하지 못한다.
# =============================================================================
ensure_request() { # ensure_request <라벨> <제목> <subjectType> <subjectId> <approverId> <submit?>
  local label="$1" title="$2" stype="$3" sid="$4" approver="$5" do_submit="$6"
  [ -n "$sid" ] && [ -n "$approver" ] || { seed_fail "$label — subjectId/approverId 가 비어 있습니다"; return 1; }
  local id="" status=""
  if http GET "$ERP/api/erp/approval/requests?size=100"; then
    local o; o="$(obj_by "$SEED_LAST_BODY" title "$title")"
    id="$(field "$o" id)"; status="$(field "$o" status)"
  fi
  if [ -z "$id" ]; then
    if http POST "$ERP/api/erp/approval/requests" \
        "{\"subjectType\":\"$stype\",\"subjectId\":\"$sid\",\"title\":\"$title\",\"reason\":\"데모 시드\",\"approverId\":\"$approver\"}" \
        -H "Idempotency-Key: $(uuid)"; then
      id="$(field "$SEED_LAST_BODY" id)"; status="$(field "$SEED_LAST_BODY" status)"
      SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  $label"
    else
      seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
      return 1
    fi
  else
    SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  $label ($status)"
  fi
  [ -n "$id" ] || { seed_fail "$label — id 추출 0건"; return 1; }

  # 🔴 "있으면 건너뜀" 으로 만들지 않는다(seed-wms.sh 의 규약): 제출해야 하는데 아직
  # DRAFT 면 이전 실행이 도중에 깨진 것이므로 **다시 제출한다**. 이미 SUBMITTED 이상이면
  # 종착이 아니어도 재제출이 성립하지 않으므로 건너뛴다.
  if [ "$do_submit" = "submit" ] && [ "$status" = "DRAFT" ]; then
    if http POST "$ERP/api/erp/approval/requests/$id/submit" '' -H "Idempotency-Key: $(uuid)"; then
      seed_log "진행  $label → 상신"
    else
      # 🔴 `TASK-ERP-BE-041` 의 면제가 여기 있었다(422 `subject_unresolved` 를 ⛔ 차단으로
      # 셌다). 그 결함은 닫혔고 이 경로는 실측으로 성립하므로 면제를 회수했다 — 이제
      # 상신 실패는 알려진 결함이 아니라 **회귀**다.
      seed_fail "$label 상신 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
      return 1
    fi
  fi
  return 0
}

ensure_request "결재 운영본부 개편" "운영본부 조직 개편 승인 요청" \
  DEPARTMENT "${DEPT[OPS]:-}" "${EMP[EMP-0001]:-}" submit
ensure_request "결재 사원 배치"     "최사원 부서 배치 승인 요청" \
  EMPLOYEE   "${EMP[EMP-0004]:-}" "${EMP[EMP-0001]:-}" submit
ensure_request "결재 재무팀 초안"   "재무팀 예산 코드 신설 (초안)" \
  DEPARTMENT "${DEPT[FIN]:-}" "${EMP[EMP-0003]:-}" draft

# =============================================================================
# 7. 위임 — 위임자는 호출자(`sub`)이고 바디에 없다. 피위임자는 사원 id.
#    validFrom/validTo 는 **고정 리터럴**이다 — 현재시각 기준이면 2회차 실행이 다른
#    창을 만들어 멱등이 깨진다(README 규약).
# =============================================================================
#
#    🔴 `api_create_unless` 를 쓰지 않는다 — 그 헬퍼는 위치인자 5개만 받고 뒤에 붙인
#       `-H` 를 **조용히 버린다**(내부 `api_create` 가 헤더를 안 받는다). 이 엔드포인트는
#       `Idempotency-Key` 가 @RequestHeader 필수라 그대로 쓰면 400 이 난다.
ensure_delegation() { # ensure_delegation <라벨> <피위임자 id>
  local label="$1" delegate="$2"
  [ -n "$delegate" ] || { seed_fail "$label — 피위임자 사원 id 가 비어 있습니다"; return 1; }
  if http GET "$ERP/api/erp/approval/delegations" \
     && [ -n "$(obj_by "$SEED_LAST_BODY" delegateId "$delegate")" ]; then
    SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  $label"
    return 0
  fi
  if http POST "$ERP/api/erp/approval/delegations" \
      "{\"delegateId\":\"$delegate\",\"validFrom\":\"2026-01-01T00:00:00Z\",\"validTo\":\"2027-01-01T00:00:00Z\",\"reason\":\"데모 시드 — 부재 시 전결\",\"scope\":\"GLOBAL\"}" \
      -H "Idempotency-Key: $(uuid)"; then
    SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  $label"
    return 0
  fi
  seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
  return 1
}
ensure_delegation "위임 이운영에게 전결" "${EMP[EMP-0002]:-}"

# =============================================================================
# 8. 프로젝션 대기 (AC-3) — `/erp/orgview` 는 read-model-service 를 읽는다.
#    프로듀서만 시드하고 판정하면 "방금 넣은 것이 아직 안 보인다" 를 빈 화면으로
#    오진한다. 사원 수가 마스터와 같아질 때까지 기다린다.
# =============================================================================
proj_count() {
  http GET "$ERP/api/erp/read-model/employees?size=100" || return 1
  printf '%s' "$SEED_LAST_BODY" | grep -oE '"totalElements":[0-9]+' | head -1 | cut -d: -f2
}
want=0
for k in "${!EMP[@]}"; do [ -n "${EMP[$k]}" ] && want=$((want + 1)); done
if [ "$want" -gt 0 ]; then
  got=0
  for _ in $(seq 1 24); do
    got="$(proj_count)"; got="${got:-0}"
    [ "$got" -ge "$want" ] 2>/dev/null && break
    sleep 5
  done
  if [ "$got" -ge "$want" ] 2>/dev/null; then
    seed_log "프로젝션 사원 $got/$want 반영 확인 (read-model)"
  else
    # 🔴 이것은 **차단이 아니라 실패**다 — 그리고 그것이 이 분류의 요점이다.
    #
    # 원래 여기는 `TASK-ERP-BE-042`(아웃박스 릴레이가 `@EnableScheduling` 부재로 한 번도
    # 돌지 않던 결함) 때문에 `⛔ 차단` 이었다. 그 티켓이 닫히면서 이 경로는 **실측으로
    # 성립한다**(사원 4/4, 미발행 백로그 16+1 이 전량 발행). 그러므로 지금 프로젝션이
    # 따라오지 않는다면 그것은 알려진 결함이 아니라 **회귀**다.
    #
    # 고쳐진 결함의 면제를 남겨 두면 그 면제가 정확히 회귀를 가리는 장치가 된다.
    # 차단 분류의 값은 **고쳐지는 즉시 회수된다**는 데 있다 — 회수하지 않으면 그냥
    # "조용히 건너뛰기" 와 같아진다.
    seed_fail "read-model 프로젝션 사원 $got/$want — 릴레이가 멈췄을 수 있습니다(ERP-BE-042 회귀?)"
  fi
fi

seed_log "요약 — 생성 $SEED_CREATED · 기존 $SEED_EXISTING · 실패 $SEED_FAILURES"
exit $(( SEED_FAILURES > 0 ? 1 : 0 ))
