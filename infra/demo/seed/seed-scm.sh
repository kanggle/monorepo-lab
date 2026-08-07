#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed-scm.sh — scm 도메인 데모 데이터 (TASK-MONO-510)
# =============================================================================
# 콘솔 SCM 섹션 6화면 중 데이터에 의존하는 4개를 표적으로 한다:
#
#   /scm/config        ← demand-planning 정책 + SKU-공급사 매핑
#   /scm/replenishment ← demand-planning 보충 제안(suggestions)
#   /scm/procurement   ← procurement 발주(PO) — DRAFT / SUBMITTED / CONFIRMED
#   /scm/inventory     ← inventory-visibility 스냅샷 (읽기만 — 아래 참조)
#
# (`/scm` 개요와 `/scm/guide` 는 데이터 없이도 렌더된다 — MONO-506 스윕 실측.)
#
# -----------------------------------------------------------------------------
# 🔴 페이로드는 **DTO 를 읽어서** 맞췄다 — 추측하지 마라
# -----------------------------------------------------------------------------
# 1회차에 e2e 테스트의 잘린 스니펫에서 형태를 추측했다가 8건 전부 422 로 튕겼다:
#   MappingRequest  → `currency` 누락        PolicyRequest → `reorderQty` 누락
#   DraftPO.Line    → 필드명이 `skuCode` 아닌 **`sku`**, `lineNo` 필수
#   inventory 경로  → `/api/v1/inventory/...` 가 아니라 **`/api/inventory-visibility/...`**
# 서버가 정확한 필드명을 돌려줬기에 한 번에 고칠 수 있었다. 형태의 출처는 항상
# `*Request.java` 이지 테스트 발췌가 아니다.
#
# -----------------------------------------------------------------------------
# 🔵 멱등은 서버가 이미 강제한다 — 목록을 훑지 않는다
# -----------------------------------------------------------------------------
# `POST /api/v1/procurement/po` 는 `Idempotency-Key` 를 **필수 헤더로 강제**한다
# (TASK-BE-445). 고정 키를 쓰면 2회차는 서버가 같은 PO 를 돌려주므로, 응답 본문을
# poNumber 로 훑는 자체 멱등 로직이 필요 없다. 요청에 poNumber 가 아예 없다는 점도
# 그 방향을 강제한다(번호는 서버가 만든다).
#
# 🔴 그리고 비-2xx 를 "없음"으로 번역하지 않는다 — 조회가 401/403/404 인 것과
# 200 + 빈 목록인 것은 다른 사건이고, 갈라서 보고한다. (TASK-ERP-BE-041 이 정확히
# 그 혼동이었다: 401 이 "마스터가 ACTIVE 아님" 으로 번역됐다.)
# =============================================================================
set -uo pipefail

SEED_DOMAIN="scm"
# shellcheck source=infra/demo/seed/lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

SCM="http://scm.${DEMO_DOMAIN}"

seed_log "시작 — $SCM (운영자 토큰, assume demo-corp)"

if ! wait_http "$SCM/api/v1/procurement/po" 240; then
  seed_fail "scm 게이트웨이가 240초 안에 응답하지 않았습니다"
  seed_summary; exit $?
fi

if ! SEED_TOKEN="$(operator_token demo-corp)"; then
  seed_fail "운영자 토큰(assume demo-corp)을 얻지 못했습니다"
  seed_summary; exit $?
fi
export SEED_TOKEN

# 🔴 위 `wait_http` 는 **엣지**만 잰다. 그것만 믿고 진행했더니 2026-08-07 `demo-up.sh scm`
# 직후 8건이 전부 500 이었고, 컨테이너가 healthy 가 된 뒤 같은 스크립트를 재실행하니
# 실패 0 으로 수렴했다 — 시드가 아니라 게이트가 틀렸다. 세 백엔드를 **각각** 본다.
scm_ready=1
wait_backend "procurement-service"         "$SCM/api/v1/procurement/po?page=0&size=1"          || scm_ready=0
wait_backend "demand-planning-service"     "$SCM/api/v1/demand-planning/suggestions?size=1" 60 || scm_ready=0
wait_backend "inventory-visibility-service" "$SCM/api/v1/inventory-visibility/snapshot?size=1" 60 || scm_ready=0
[ "$scm_ready" = "1" ] || { seed_summary; exit $?; }

# --- 데모 고정 식별자 (랜덤 금지 — 2회차가 "두 배"가 된다) --------------------
SUPPLIER_CODE="SUP-DEMO-01"
SKU_A="SKU-DEMO-A1"
SKU_B="SKU-DEMO-B2"
CURRENCY="KRW"
SEED_TENANT="demo-corp"

# =============================================================================
# 0. 공급사 — 🔴 이 도메인에는 등록 API 가 **없다**
# =============================================================================
# `POST /po` 는 `SUPPLIER_NOT_FOUND` 로 거절하는데(실측), 저장소 전체를 훑어도
# suppliers 를 만드는 컨트롤러가 **한 개도 없다**. scm e2e 도 같은 이유로 직접 DB 에
# 넣는다(`ProcurementDbFixtures.insertActiveSupplier`) — 즉 직접-DB 는 이 도메인에서
# 편법이 아니라 **유일한 경로**다.
#
# 🔵 그래서 이것은 시드의 한계가 아니라 **제품의 갭**이다: 데모 운영자는 어떤 화면·API
# 로도 공급사를 만들 수 없다. 콘솔 SCM 섹션에 공급사 관리 화면이 없는 것과 같은 뿌리다.
# ⇒ 착수자가 판단할 후속 후보로 티켓에 적는다(이 슬라이스에서 고치지 않는다).
PG_C="scm-platform-postgres"; PG_DB="scm_procurement"; PG_U="scm"; PG_P="scm"

seed_supplier() {
  local existing
  existing="$(dbquery "$PG_C" psql "$PG_DB" "$PG_U" "$PG_P" \
      "select count(*) from suppliers where id='$SUPPLIER_CODE';" 2>/dev/null | tr -d ' \r\n')"
  if [ "${existing:-0}" = "1" ]; then
    SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  공급사 $SUPPLIER_CODE"
    return 0
  fi
  if dbexec --why "scm 에는 공급사 등록 API 가 없다 — 저장소 전 컨트롤러 0건이고 scm e2e 도 ProcurementDbFixtures 로 직접 넣는다" \
      "$PG_C" psql "$PG_DB" "$PG_U" "$PG_P" <<SQL
INSERT INTO suppliers (id, tenant_id, name, status, created_at, updated_at, version)
VALUES ('$SUPPLIER_CODE', '$SEED_TENANT', 'demo supplier', 'ACTIVE', now(), now(), 0);
SQL
  then
    SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  공급사 $SUPPLIER_CODE"
    return 0
  fi
  seed_fail "공급사 $SUPPLIER_CODE 를 넣지 못했습니다 (direct-DB)"
  return 1
}
seed_supplier

# =============================================================================
# 1. /scm/config — SKU-공급사 매핑 + 재고 정책
# =============================================================================
# PUT upsert 라 본질적으로 멱등하다(같은 본문 재적용 = 같은 상태).
CONFIG_OK=0
put_dp() {
  local label="$1" url="$2" body="$3"
  if http PUT "$url" "$body"; then
    SEED_CREATED=$((SEED_CREATED + 1)); seed_log "반영  $label"
    CONFIG_OK=$((CONFIG_OK + 1)); return 0
  fi
  seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
  return 1
}

for sku in "$SKU_A" "$SKU_B"; do
  # MappingRequest(supplierId, defaultOrderQty>=1, leadTimeDays>=0, currency[3])
  put_dp "SKU-공급사 매핑 $sku" \
    "$SCM/api/v1/demand-planning/sku-supplier-map/$sku" \
    "{\"supplierId\":\"$SUPPLIER_CODE\",\"defaultOrderQty\":100,\"leadTimeDays\":3,\"currency\":\"$CURRENCY\"}"

  # PolicyRequest(reorderPoint>=0, safetyStock>=0, reorderQty>=1)
  put_dp "재고 정책 $sku" \
    "$SCM/api/v1/demand-planning/policies/$sku" \
    "{\"reorderPoint\":10,\"safetyStock\":5,\"reorderQty\":50}"
done

# =============================================================================
# 2. /scm/replenishment — 보충 제안
# =============================================================================
# 제안은 POST 로 만드는 것이 아니라 **정책·매핑이 선 상태에서 재고 신호에 반응해**
# demand-planning 이 생성한다. 그래서 만들지 않고 기다린다.
#
# 🔴 대기 결과를 세 가지로 갈라 적는다. 하나로 합치면 다음 사람이 오독한다:
#     (a) 조회가 비-2xx      → 물어보지 못했다 = seed_fail
#     (b) 200 + 제안 있음    → 존재
#     (c) 200 + 0건          → 관측(실패 아님). 도메인이 아직 신호를 못 받은 것이다
await_suggestion() {
  local sku="$1" timeout="${2:-60}" i
  for (( i=0; i<timeout; i+=5 )); do
    if http GET "$SCM/api/v1/demand-planning/suggestions?skuCode=$sku"; then
      if printf '%s' "$SEED_LAST_BODY" | grep -q '"id"'; then
        printf '%s' "$SEED_LAST_BODY" | sed -E 's/.*"id"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/'
        return 0
      fi
    else
      seed_fail "제안 조회 $sku — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"
      return 1
    fi
    sleep 5
  done
  return 2
}

if SUGGESTION_ID="$(await_suggestion "$SKU_A" 60)"; then
  SEED_EXISTING=$((SEED_EXISTING + 1))
  seed_log "존재  보충 제안 $SKU_A (id=${SUGGESTION_ID:0:12}…)"
elif [ $? -eq 2 ]; then
  # 🔴 여기서 "정책·매핑은 반영됐다" 고 단정하지 않는다 — 위 단계의 실제 성공 수를 센다.
  seed_log "관측  보충 제안 $SKU_A — 조회 200, 60초 동안 0건 (config 반영 $CONFIG_OK/4)"
  seed_log "      제안 생성에는 재고 신호가 더 필요하다 ⇒ /scm/replenishment 는 빈 채로 남는다"
fi

# =============================================================================
# 3. /scm/procurement — 발주(PO) 세 상태
# =============================================================================
# DraftPurchaseOrderRequest(supplierId, currency[3], lines[{lineNo>0, sku, quantity>0, unitPrice>=0}])
# poNumber 는 요청에 없다 — 서버가 만든다.
po_body() {
  cat <<JSON
{"supplierId":"$SUPPLIER_CODE","currency":"$CURRENCY",
 "lines":[{"lineNo":1,"sku":"$1","quantity":"100","unitPrice":"1000"}]}
JSON
}

PO_ID=""
create_po() {
  local label="$1" key="$2" sku="$3"
  PO_ID=""
  # 고정 Idempotency-Key — 2회차는 서버가 같은 PO 를 돌려준다(BE-445).
  # 🔴 2xx 를 "생성"으로 세지 않는다. 멱등 replay 도 2xx 다(BE-445 는 저장된 응답을
  # 그대로 돌려준다) — 그래서 3회 실행 로그에 "생성"이 9번 찍혔는데 실제 PO 는 3건이었다.
  # 판정은 **산출물 수량**으로 한다: POST 전후 행 수가 같으면 replay 다.
  local before after
  before="$(dbquery "$PG_C" psql "$PG_DB" "$PG_U" "$PG_P" \
      "select count(*) from purchase_orders;" 2>/dev/null | tr -d ' \r\n')"
  if http POST "$SCM/api/v1/procurement/po" "$(po_body "$sku")" -H "Idempotency-Key: $key"; then
    after="$(dbquery "$PG_C" psql "$PG_DB" "$PG_U" "$PG_P" \
        "select count(*) from purchase_orders;" 2>/dev/null | tr -d ' \r\n')"
    if [ "${after:-0}" -gt "${before:-0}" ] 2>/dev/null; then
      SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  $label"
    else
      SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  $label (멱등 replay — 행 수 $before 불변)"
    fi
    # 🔴 **첫** id 를 집는다. `sed -E 's/.*"id".../` 는 greedy 라 **마지막** id —
    # 즉 `lines[0].id` — 를 집었고, 그것도 UUID 처럼 생겨서 non-empty 검사를 통과한 뒤
    # 전이가 전부 `PO_NOT_FOUND` 로 떨어졌다. 비어 있지 않다는 것은 옳다는 것이 아니다.
    PO_ID="$(printf '%s' "$SEED_LAST_BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)"
    [ -n "$PO_ID" ] || { seed_fail "$label — 응답에서 id 를 못 뽑았다 (뒤 전이가 빈 id 로 깨진다)"; return 1; }
    return 0
  fi
  seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
  return 1
}

# 🔴 status 코드를 상태로 번역하지 않는다.
# 첫 판은 `409|422 → "이미 그 상태"` 로 뭉갰는데, 실제로 온 422 는 **DRAFT 를 confirm
# 할 수 없다**는 뜻이었다(submit 이 앞서 실패했으므로). 실패가 양성 판정으로 번역됐다.
# 이제 전이 후 **PO 를 다시 읽어 status 를 확인**하고, 확인된 것만 성공으로 센다.
po_status() {
  http GET "$SCM/api/v1/procurement/po/$1" \
    && printf '%s' "$SEED_LAST_BODY" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4
}

po_transition() {
  local label="$1" po="$2" verb="$3" want="$4"
  [ -n "$po" ] || { seed_fail "$label — PO id 가 비어 있다"; return 1; }
  http POST "$SCM/api/v1/procurement/po/$po/$verb" '{}' -H "Idempotency-Key: seed-$po-$verb"
  local st body; st="$SEED_LAST_STATUS"; body="$SEED_LAST_BODY"

  local now; now="$(po_status "$po")"
  if [ "$now" = "$want" ]; then
    SEED_CREATED=$((SEED_CREATED + 1)); seed_log "전이  $label (status=$now 확인)"
    return 0
  fi

  # 데모 스택에는 supplier-mock 이 없다 — 지문이 정확히 맞을 때만 관측으로 센다.
  if [ "$st" = "503" ] && printf '%s' "$body" | grep -q 'SUPPLIER_UNAVAILABLE' \
     && printf '%s' "$body" | grep -q 'supplier-mock'; then
    seed_log "관측  $label 불가 — supplier-mock 이 데모 스택에 없다 (status=$now 유지)"
    seed_log "      submit 은 공급사 어댑터로 나가는 실제 호출이다. 저장소 어느 compose 에도"
    seed_log "      supplier-mock 서비스 정의가 없다(실측) ⇒ DRAFT 밖으로 못 나간다"
    return 0
  fi

  seed_fail "$label — HTTP $st (status=$now) ${body:0:160}"
  return 1
}

create_po "발주 초안 (DRAFT)" "seed-scm-po-0001" "$SKU_A"

if create_po "발주 상신 (SUBMITTED)" "seed-scm-po-0002" "$SKU_A"; then
  po_transition "SCM-PO-0002 → SUBMITTED" "$PO_ID" "submit" "SUBMITTED"
fi

if create_po "발주 확정 (CONFIRMED)" "seed-scm-po-0003" "$SKU_B"; then
  CONFIRM_PO="$PO_ID"
  po_transition "SCM-PO-0003 → SUBMITTED" "$CONFIRM_PO" "submit" "SUBMITTED"
  # confirm 은 SUBMITTED 에서만 유효하다 — submit 이 막힌 스택에서는 시도 자체가 무의미하다.
  if [ "$(po_status "$CONFIRM_PO")" = "SUBMITTED" ]; then
    po_transition "SCM-PO-0003 → CONFIRMED" "$CONFIRM_PO" "confirm" "CONFIRMED"
  else
    seed_log "생략  SCM-PO-0003 → CONFIRMED — 선행 SUBMITTED 가 없다(위 관측 참조)"
  fi
fi

# =============================================================================
# 4. /scm/inventory — 가시성 스냅샷 (읽기만)
# =============================================================================
# 🔵 여기는 **쓰지 않는다.** inventory-visibility 는 다른 도메인의 이벤트를 투영하는
# read-model 이다. 시드가 직접 행을 넣으면 화면은 채워지지만 그것은 "투영이 동작한다"
# 는 증거가 아니라 "시드가 넣었다" 는 증거일 뿐이다. 읽어서 상태만 기록한다.
if http GET "$SCM/api/v1/inventory-visibility/snapshot?size=20"; then
  if printf '%s' "$SEED_LAST_BODY" | grep -q '"sku"'; then
    seed_log "확인  재고 가시성 스냅샷 존재 (투영 동작)"
  else
    seed_log "관측  재고 가시성 — 200 인데 스냅샷 0건"
    seed_log "      /scm/inventory 는 빈다. 투영에는 wms 재고 이벤트가 필요하다(이 슬라이스 범위 밖)"
  fi
else
  seed_fail "재고 가시성 조회 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"
fi

seed_summary
