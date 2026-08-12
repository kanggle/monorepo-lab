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
# ✅ 결재함(inbox)이 찬다 — 사유가 **전부** 해소됐다 (TASK-MONO-519, 2026-08-12)
# -----------------------------------------------------------------------------
# 이 헤더는 오랫동안 *"결재함은 이 데모에서 채울 수 없다"* 였다. 네 가지가 맞물려
# 있었고 하나씩 떨어져 나갔다:
#
#   1. 결재함은 `findInbox(tenantId, actorId)` — **호출자가 현재 단계 승인자인 건**만
#      돌려준다. `actorId` = JWT `sub`. (그대로 — 결함이 아니라 명세다.)
#   2. 라우트 검증이 **자기결재를 거부한다**(`ApprovalRoute.multiStage` →
#      `SelfApprovalGuard`). 🔴 조회 필터가 아니라 **생성 시점 게이트**다 — 제출자와
#      승인자가 같으면 결재함이 "비어 보이는" 게 아니라 **넣을 행이 안 만들어진다**.
#   3. ✅ **해소됨 (TASK-MONO-515 / ADR-MONO-060 A, 2026-08-07).** assume 토큰의 `sub`
#      가 **계정 UUID** 가 됐다(전에는 `platform-console-web` 이라는 클라이언트 id).
#      ⇒ actorId 가 **콘솔 계정마다 하나**다(전에는 통틀어 하나).
#
#      🔴 그때 이 헤더는 그것을 *"결함이 아니라 명시된 동작"* 이라 적으면서
#      `AssumeTenantExchangeIntegrationTest` 를 근거로 들었는데 **그 인용이 틀렸다** —
#      그 테스트의 유일한 `sub` 단언은 **base 토큰** 대상이었고 assume 토큰에 대한
#      진술은 주석뿐이었다. 게다가 그 주석은 RFC 8693 을 거꾸로 읽고 있었다.
#      아무것도 고정하지 않는 주석이 "결정된 사항" 으로 읽혀 조사를 멈춰 세웠다.
#   4. ✅ **해소됨 (TASK-MONO-519).** ③ 이 열리자 그 아래에서 **데이터 공백**이 드러났다 —
#      `demo-corp` 안의 콘솔 신원이 **하나뿐**이라(`R__seed_demo_operator.sql` 의
#      `admin_operators` INSERT 1건, 실측) ② 를 만족시킬 상대가 없었다. 두 번째 운영자
#      `demo-requester`(auth `R__seed_demo_second_operator_credential` + admin `R__` §5)가 그 공백을 메운다.
#
# ⇒ 그래서 §6 은 **두 신원으로** 심는다:
#
#     상신자 = `requester@demo.com`  (시드 전용 로그인 — 면접관은 이 주소를 안 친다)
#     승인자 = `demo@demo.com`       (면접관이 실제로 로그인하는 그 계정)
#
#   🔴 방향이 이쪽인 이유: 반대로 하면 루프는 기계적으로 닫히지만 **면접관이 여는 화면**은
#   여전히 0 이다 — 대기 건이 아무도 로그인하지 않는 계정에 쌓인다. "결재함이 찼다" 를
#   행 개수로만 판정하면 이 두 배치를 구별하지 못한다. 알림 쪽도 같은 이유로 이 방향이
#   맞다: `RecipientResolver` 가 `APPROVAL_SUBMITTED → approverId` 이므로 인앱 알림도
#   면접관이 쥔 계정으로 간다.
#
# 🔵 승인자에 **사원 마스터 id 를 쓰지 않는다** — 예전 방식이 그랬고, 그것이 정확히
#    결재함이 0 이던 이유다(그 사원으로는 아무도 로그인할 수 없다). 초안 1건만 사원
#    승인자를 유지한다: 프로덕션에서 실제로 일어나는 형태이고, 면접관이 그 초안을
#    상신해도 **자기 결재함에 안 뜨는 것이 옳다**(만든 사람은 승인자가 될 수 없다).
#
# 🔵 직접-DB 로 결재함을 채우지 않은 이유는 그대로다: 그러면 화면은 차지만 **버튼이
#    동작하지 않는다**(승인이 401/403 이 아니라 "현재 단계가 아니다" 로 거절된다). 빈
#    화면보다 나쁜 것은 **눌리는데 실패하는 화면**이다. 이제는 그럴 필요 자체가 없다.
#
# 🔴 **알림함(erp notification-service)은 아직 0 이고, 그것은 이 시드 탓이 아니다.**
#    `EnvelopeToCommandMapper` L67 이 `tenantId == "erp"` 를 강제하는데 데모 이벤트는
#    `demo-corp` 를 싣는다 ⇒ `erp.approval.*` 이 전량 DLT 로 간다. 콘솔
#    `/erp/delegation` 의 read-model 뷰가 비는 것과 **같은 관문**이다(소비자 2개가 같은
#    코드를 갖는다). 결정은 `ADR-ERP-001`(Proposed)이 쥐고 있고 실행은
#    `TASK-ERP-BE-043` 이 HARDSTOP-09 로 정지 중이다 — **여기서 우회하지 않는다.**
#
# 🔵 ③ 이 해소되면서 **눈에 안 보이던 절반이 함께 고쳐졌다**: 게이트웨이 6/6 이
#    `X-User-Id ← sub` 이므로, 필터가 0건인 도메인(finance·scm·wms)도 감사 기록의
#    행위자가 `platform-console-web` 이었다. 이제 전 도메인이 실제 운영자를 적는다.
#    erp 결재함은 그 문제가 **화면으로 드러난 유일한 자리**였을 뿐이다.
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

# --- 두 번째 신원 (TASK-MONO-519) -------------------------------------------
# 상신자 토큰. 이것이 없으면 §6 의 두 건은 자기결재로 **생성 자체가** 거절된다.
REQUESTER_EMAIL="${DEMO_REQUESTER_EMAIL:-requester@demo.com}"
REQUESTER_TOKEN="$(operator_token demo-corp "$REQUESTER_EMAIL" "${DEMO_PASSWORD:-Demo1234!}")"
if [ -z "$REQUESTER_TOKEN" ]; then
  seed_fail "상신자 토큰($REQUESTER_EMAIL, assume demo-corp)을 얻지 못했습니다 — auth R__seed_demo_second_operator_credential / admin R__ §5 시드가 적용됐는지 확인하십시오"
  exit 1
fi

# 🔴 두 `sub` 가 **실제로 다른지**를 여기서 판정한다. 이것이 이 시드에서 가장 중요한
#    술어다: 두 시드의 `oidc_subject` 가 겹치면 TASK-MONO-515 가 고친 결함이 데이터
#    층에서 되살아나(둘이 다시 한 사람이 된다) 결재 생성이 자기결재로 거절되는데,
#    증상은 "상신이 실패한다" 로만 보여 원인이 **신원 겹침**임을 가리지 못한다.
#    토큰에서 직접 읽는다 — 시드 파일의 리터럴을 믿지 않는다(그 둘이 어긋난 것을
#    잡으려는 검사이므로 한쪽만 봐서는 성립하지 않는다).
APPROVER_SUB="$(jwt_sub "$SEED_TOKEN")"
REQUESTER_SUB="$(jwt_sub "$REQUESTER_TOKEN")"
if [ -z "$APPROVER_SUB" ] || [ -z "$REQUESTER_SUB" ]; then
  seed_fail "토큰에서 sub 를 읽지 못했습니다 (승인자 '${APPROVER_SUB:-<빈값>}' · 상신자 '${REQUESTER_SUB:-<빈값>}')"
  exit 1
fi
if [ "$APPROVER_SUB" = "$REQUESTER_SUB" ]; then
  seed_fail "승인자와 상신자의 sub 가 같습니다($APPROVER_SUB) — 두 신원이 같은 계정으로 해소됐습니다(oidc_subject 겹침?). 자기결재 금지로 결재 생성이 거절됩니다"
  exit 1
fi
seed_log "신원 2건 확인 — 승인자 sub=$APPROVER_SUB · 상신자 sub=$REQUESTER_SUB ($REQUESTER_EMAIL)"

# with_token <토큰> <명령...> — 그 호출 동안만 SEED_TOKEN 을 바꾼다.
# `http` 가 매 호출 `$SEED_TOKEN` 을 읽으므로 이 스왑만으로 신원이 갈린다.
with_token() {
  local t="$1"; shift
  local saved="$SEED_TOKEN" rc=0
  SEED_TOKEN="$t"
  "$@" || rc=$?
  SEED_TOKEN="$saved"
  return $rc
}

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
#    approverId 는 **참조 검증을 받지 않는다** — 실측: `ApprovalApplicationService.submit`
#    은 `masterDataPort.isSubjectActive(subject, …)` 만 부르고 승인자는 안 본다. 그래서
#    승인자에 계정 UUID(콘솔 로그인의 `sub`)를 넣을 수 있고, 그것이 결재함을 채우는
#    유일한 방법이다(결재함 술어가 `approver_id = JWT sub` 이므로).
#
#    🔵 상태를 셋 만든다 — DRAFT 1 · SUBMITTED 2. 목록 화면이 상태 필터를 가지므로
#       한 가지 상태만 넣으면 필터가 아무것도 증명하지 못한다.
# =============================================================================
ensure_request() { # ensure_request <라벨> <제목> <subjectType> <subjectId> <approverId> <submit?>
  local label="$1" title="$2" stype="$3" sid="$4" approver="$5" do_submit="$6"
  [ -n "$sid" ] && [ -n "$approver" ] || { seed_fail "$label — subjectId/approverId 가 비어 있습니다"; return 1; }
  local id="" status="" had_approver=""
  if http GET "$ERP/api/erp/approval/requests?size=100"; then
    local o; o="$(obj_by "$SEED_LAST_BODY" title "$title")"
    id="$(field "$o" id)"; status="$(field "$o" status)"
    had_approver="$(field "$o" approverId)"
  fi

  # 🔴 이미 있는 행의 승인자가 **기대와 다르면 실패로 센다.**
  # TASK-MONO-519 이전 시드는 승인자에 사원 마스터 id 를 넣었다. 그 시절에 만들어진
  # 데모 DB 에 이 시드를 다시 돌리면 제목 탐지가 그 행을 찾아 `존재` 로 세고 조용히
  # 넘어가는데, 결재함은 여전히 **0** 이다 — "생성 0 · 기존 N · 실패 0" 이라는 완벽한
  # 요약과 함께. 결재 요청에는 승인자 변경 API 가 없으므로 여기서 고칠 수도 없다.
  # 그래서 **선언한다**: 볼륨을 지우고 다시 심어야 한다고. 조용한 성공보다 낫다.
  if [ -n "$id" ] && [ -n "$had_approver" ] && [ "$had_approver" != "$approver" ]; then
    seed_fail "$label — 기존 행의 승인자가 '$had_approver' 입니다(기대 '$approver'). TASK-MONO-519 이전에 심긴 데이터입니다 — 승인자 변경 API 가 없으므로 erp DB 볼륨을 초기화한 뒤 다시 심으십시오"
    return 1
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

# 🔴 앞의 두 건은 **상신자 토큰으로** 만들고 상신한다. 승인자는 면접관이 로그인하는
#    계정의 `sub` 다 ⇒ `/erp/approval` 결재함에 그대로 뜬다. 만드는 쪽이 상신자여야
#    한다는 것이 핵심이다: 라우트는 **생성 시점**에 고정되고 자기결재 검사도 그때
#    돈다(`ApprovalRoute.multiStage`) — 상신만 남의 토큰으로 해도 소용없다.
with_token "$REQUESTER_TOKEN" \
  ensure_request "결재 운영본부 개편" "운영본부 조직 개편 승인 요청" \
    DEPARTMENT "${DEPT[OPS]:-}" "$APPROVER_SUB" submit
with_token "$REQUESTER_TOKEN" \
  ensure_request "결재 사원 배치"     "최사원 부서 배치 승인 요청" \
    EMPLOYEE   "${EMP[EMP-0004]:-}" "$APPROVER_SUB" submit

# 🔵 초안은 **운영자 자신이** 만들고 승인자는 사원 마스터 id 로 남긴다 — 위 헤더의
#    이유(프로덕션 형태 보존 + 면접관이 상신해도 자기 결재함에 안 뜨는 것이 옳다).
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

# =============================================================================
# 9. 결재함 판정 (TASK-MONO-519) — 이 시드가 존재하는 이유를 **재는** 단계.
#
# 🔴 "결재함 ≥ 1" 로 재지 않는다. 면접관이 데모에서 승인 버튼을 누르면 그 수는 정상적으로
#    줄고, 다음 실행이 그 정상 동작을 실패로 신고하게 된다. 그러면 그 실패가 늘 켜져 있는
#    경고가 되어 **진짜 회귀를 가린다.**
#
#    대신 **결재함 필터 자체**를 잰다: 승인자가 나이고 상태가 대기(SUBMITTED/IN_REVIEW)인
#    행의 수(목록 API 에서 셈) == 결재함이 돌려주는 원소 수. 이 등식은 승인이 몇 건
#    일어났든 성립해야 하고, 깨지면 그것은 **술어가 틀렸다**는 뜻이다 — 정확히
#    TASK-MONO-515/519 가 두 번 밟은 축(`sub` 가 무엇인가 / 그런 `sub` 가 존재하는가).
#
# 🔴 원소 수는 **BFF/API 로** 잰다. 콘솔 `/erp/approval` 은 클라이언트 렌더라 SSR HTML
#    grep 은 구조적으로 0건이고, 그 0 은 "비어 있다" 와 구별되지 않는다.
# =============================================================================
inbox_expected=""
if http GET "$ERP/api/erp/approval/requests?size=100"; then
  inbox_expected="$(json_objects "$SEED_LAST_BODY" \
    | grep -F "\"approverId\":\"$APPROVER_SUB\"" \
    | grep -cE '"status":"(SUBMITTED|IN_REVIEW)"' || true)"
fi
inbox_actual=""
if http GET "$ERP/api/erp/approval/inbox?size=100"; then
  inbox_actual="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"totalElements":[0-9]+' | head -1 | cut -d: -f2)"
fi

if [ -z "$inbox_expected" ] || [ -z "$inbox_actual" ]; then
  # 🔴 추출 0건은 "0 건" 이 아니라 **계측 실패**다. 같은 값으로 접으면 결함이 초록이 된다.
  seed_fail "결재함 판정 불가 — 기대치 '${inbox_expected:-<추출 실패>}' · 실측 '${inbox_actual:-<추출 실패>}' (마지막 HTTP $SEED_LAST_STATUS)"
elif [ "$inbox_expected" != "$inbox_actual" ]; then
  seed_fail "결재함 원소 수 $inbox_actual — 승인자 sub=$APPROVER_SUB 로 대기 중인 행은 $inbox_expected 건입니다. 결재함 술어(approver_id = JWT sub)가 어긋났습니다"
elif [ "$inbox_actual" = "0" ] && [ "$SEED_FAILURES" -gt 0 ]; then
  # 🔴 등식은 성립하지만 **원인을 여기서 진단하지 않는다.** 앞 단계가 이미 실패했으면
  #    0 은 그 실패의 결과이지 별개의 사실이 아니다. 실측(2026-08-12): 레거시 승인자
  #    때문에 §6 이 2건 실패한 실행에서 이 자리가 "이미 처리된 DB 입니다" 를 찍었다 —
  #    **틀린 진단**이었고, 진짜 원인(승인자 불일치)을 다른 이야기로 덮었다.
  seed_log "결재함 0건 (대기 행도 0) — 위 실패 $SEED_FAILURES 건의 결과입니다. 그 메시지를 보십시오"
elif [ "$inbox_actual" = "0" ]; then
  # 실패가 없는데 0 — 등식이 성립하므로 술어는 옳고, 시드가 심은 건이 이미 승인/반려된
  # 상태다. 즉 데모를 한 번 돌린 DB.
  seed_log "결재함 0건 (대기 행도 0) — 시드 결재가 이미 처리된 DB 입니다. 다시 보려면 볼륨 초기화 후 재시드"
else
  seed_log "결재함 $inbox_actual 건 = 대기 행 $inbox_expected 건 (승인자 sub=$APPROVER_SUB) — 상신 → 승인 루프가 닫혀 있습니다"
fi

seed_log "요약 — 생성 $SEED_CREATED · 기존 $SEED_EXISTING · 실패 $SEED_FAILURES"
exit $(( SEED_FAILURES > 0 ? 1 : 0 ))
