#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed-wms.sh — WMS 도메인 데이터 시드
# =============================================================================
# TASK-MONO-510 (MONO-506 의 S4 슬라이스).
#
# 이 시드는 **입고 흐름 한 벌**을 실제 API 로 끝까지 밟는다:
#
#   ASN 생성 → 검수 시작 → 검수 기록 → 적치 지시 → 적치 확정 → (재고 반영)
#   그리고 출고 주문 1건
#
# 즉 이 스크립트가 통과한다는 것은 콘솔의 `/wms/inbound` · `/wms/inventory` ·
# `/wms/outbound` 가 채워진다는 뜻이고, 동시에 **그 6개 엔드포인트가 살아 있다는
# 검증**이다(MONO-506 의 원칙: 넣는 행위가 곧 검증).
#
# -----------------------------------------------------------------------------
# 🔵 마스터 데이터는 여기서 넣지 않는다 — 이미 저장소가 갖고 있다
# -----------------------------------------------------------------------------
# 창고·존·로케이션·SKU·거래처는 각 서비스의 `db/seed/V99..V103` 이 **고정 UUID**로
# 심고, `infra/demo/wms-devseed.override.yml` 이 그 Flyway 위치를 데모에서 열어 준다.
# 그래서 아래 ID 들은 하드코딩이지만 **떠도는 상수가 아니라 그 마이그레이션의 값**이다.
# (API 로 넣을 수 없는 이유 = `MASTER_WRITE` 를 아무도 못 받는다 → TASK-MONO-514.
#  전문은 wms-devseed.override.yml 헤더.)
#
# -----------------------------------------------------------------------------
# 🔴 출고는 **주문 생성까지**가 현실적 목표다
# -----------------------------------------------------------------------------
# 예약(allocation)은 `INVENTORY_RESERVE` 역할을 요구하는데 `OperatorRoleDerivation`
# 이 그것을 주지 않고, 배송은 도달 불가 TMS 스텁(`WMS_TMS_BASE_URL`)에 의존한다.
# 주문까지 넣으면 `/wms/outbound` 화면은 채워진다 — 그 이상은 이 시드의 몫이 아니다.
#
# -----------------------------------------------------------------------------
# 🔴 계측 함정 (재현 시 먼저 읽을 것)
# -----------------------------------------------------------------------------
# · `Idempotency-Key` 는 **UUID** 여야 한다(아니면 400 `must be a UUID`).
# · **같은 키는 실패 응답까지 재생한다** — 두 번째 403 의 타임스탬프가 첫 번째와
#   바이트 단위로 같았다. 그래서 이 스크립트는 매 호출마다 새 키를 만든다.
# · 게이트웨이는 `/api/v1/**` 만 받는다(`/api/...` 는 404).
# · Git Bash(msys)에는 `/proc/sys/kernel/random/uuid` 가 없다 → openssl 로 만든다.
# · SKU-APPLE-001 은 **LOT 추적** 대상이라 검수에 lot 이 필요하다. `lotId` 를 모르는
#   상태에서는 `lotNo` 만 줘도 된다(계약 §2.2 "lot reconciled later").
# =============================================================================
set -uo pipefail
SEED_DOMAIN=wms
# shellcheck source=infra/demo/seed/lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

GW="http://wms.${DEMO_DOMAIN}"

container_up wms-gateway-service || { seed_log "게이트웨이 미기동 — 건너뜀"; exit 0; }

# --- 마스터 고정 UUID (db/seed/V99..V103) ------------------------------------
WAREHOUSE_ID=01910000-0000-7000-8000-000000000001
LOCATION_ID=01910000-0000-7000-8000-000000001001
SKU_ID=01910000-0000-7000-8000-000000000403
SUPPLIER_ID=01910000-0000-7000-8000-000000000801

ASN_NO="ASN-DEMO-0001"
ORDER_NO="SO-DEMO-0001"
QTY=100
QTY_PASSED=95
QTY_DAMAGED=3
QTY_SHORT=2

# --- UUID v4 (openssl) -------------------------------------------------------
gen_uuid() {
  local h; h="$(openssl rand -hex 16)"
  printf '%s-%s-4%s-a%s-%s\n' "${h:0:8}" "${h:8:4}" "${h:13:3}" "${h:17:3}" "${h:20:12}"
}

# idem <method> <url> [body] — 매번 새 Idempotency-Key 로 호출한다.
idem() {
  local method="$1" url="$2" body="${3:-}"
  http "$method" "$url" "$body" -H "Idempotency-Key: $(gen_uuid)"
}

# json_field <key> — SEED_LAST_BODY 에서 문자열 값 하나를 긁는다(jq 없음).
#
# 🔴 `sed -E 's/.*"id":"([^"]*)".*/\1/'` 를 쓰면 안 된다. `.*` 가 greedy 라 **마지막**
# `"id"` 를 집는다 — ASN 생성 응답에서는 그것이 `lines[].id` 라서, 뒤이은 검수 호출이
# 라인 id 를 ASN id 로 보내 404 `ASN_NOT_FOUND` 가 났다(실측). grep 로 첫 매치만 딴다.
json_field() {
  printf '%s' "$SEED_LAST_BODY" | grep -oE "\"$1\":\"[^\"]*\"" | head -1 | sed -E 's/.*:"([^"]*)"/\1/'
}

# json_nested_first <배열키> <원소키> — `"lines":[{"id":"..."` 처럼 배열 첫 원소의 값.
json_nested_first() {
  printf '%s' "$SEED_LAST_BODY" | sed -E "s/.*\"$1\":\\[\\{\"$2\":\"([^\"]*)\".*/\1/"
}

wait_http "$GW/api/v1/inbound/asns" 240 \
  || { seed_fail "게이트웨이가 240초 안에 응답하지 않습니다"; seed_summary; exit $?; }

SEED_TOKEN="$(operator_token demo-corp)" \
  || { seed_fail "operator_token(demo-corp) 실패 — 인증 경로가 끊겼습니다"; seed_summary; exit $?; }
export SEED_TOKEN

# =============================================================================
# 1) 입고 흐름 — ASN → 검수 → 적치
# =============================================================================
# 멱등: 목록에 ASN_NO 가 이미 있으면 흐름 전체를 건너뛴다. ASN 은 상태 기계라
# "생성만 다시" 가 성립하지 않는다 — 2회차에 CLOSED 인 ASN 에 검수를 걸면 422 다.
# 🔵 가드는 "있으면 건너뜀" 이 아니다. 있는데 **중간 상태**면 이전 실행이 흐름 도중
# 깨졌다는 뜻이고, 그것을 조용히 건너뛰면 화면은 빈 채로 시드는 초록이 된다 — 이
# 저장소가 반복해서 당한 모양이다. 그래서 종착 상태일 때만 건너뛰고, 중간이면 실패로 센다.
ASN_FOUND=0
ASN_STATE=""
if http GET "$GW/api/v1/inbound/asns?size=100" && printf '%s' "$SEED_LAST_BODY" | grep -qF "\"$ASN_NO\""; then
  ASN_FOUND=1
  ASN_STATE="$(printf '%s' "$SEED_LAST_BODY" \
    | sed -E "s/.*\"asnNo\":\"$ASN_NO\"[^}]*\"status\":\"([A-Z_]*)\".*/\1/")"
  # 🔴 유효성 술어. `sed` 는 매치에 실패해도 **입력을 그대로 돌려준다** — 그러면
  # ASN_STATE 에 JSON 본문 전체가 들어앉는데, `-n` 검사만으로는 그것이 참으로 보인다.
  # 응답의 필드 순서가 바뀌면 조용히 이 길로 빠지므로, 상태 토큰 모양인지 직접 묻는다.
  case "$ASN_STATE" in
    CREATED|INSPECTING|INSPECTED|IN_PUTAWAY|PUTAWAY_DONE|CLOSED|CANCELLED) ;;
    *) seed_fail "$ASN_NO 는 목록에 있는데 status 를 읽지 못했습니다 — 응답 형태가 바뀌었을 수 있습니다(계약 §1.3 확인). 파싱 결과 앞 80자: ${ASN_STATE:0:80}"
       ASN_STATE="" ; ASN_FOUND=2 ;;
  esac
fi

if [ "$ASN_FOUND" = "2" ]; then
  : # 위에서 이미 실패로 셌다 — 상태를 모르는 채로 흐름을 다시 밟지 않는다
elif [ "$ASN_FOUND" = "1" ]; then
  case "$ASN_STATE" in
    PUTAWAY_DONE|CLOSED)
      SEED_EXISTING=$((SEED_EXISTING + 1))
      seed_log "존재  입고 흐름 ($ASN_NO, status=$ASN_STATE) — 건너뜀" ;;
    *)
      seed_fail "$ASN_NO 이 중간 상태($ASN_STATE)로 남아 있습니다 — 이전 실행이 흐름 도중 실패했습니다. 원인을 고친 뒤 해당 ASN 을 정리하고 다시 실행하세요" ;;
  esac
else
  if ! idem POST "$GW/api/v1/inbound/asns" "$(cat <<JSON
{"asnNo":"$ASN_NO","supplierPartnerId":"$SUPPLIER_ID","warehouseId":"$WAREHOUSE_ID",
 "expectedArriveDate":"2026-08-20","notes":"데모 입고 — 사과 100박스",
 "lines":[{"skuId":"$SKU_ID","lotId":null,"expectedQty":$QTY}]}
JSON
)"; then
    seed_fail "ASN 생성 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
  else
    SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  ASN $ASN_NO"
    ASN_ID="$(json_field id)"
    ASN_LINE_ID="$(json_nested_first lines id)"

    if ! idem POST "$GW/api/v1/inbound/asns/$ASN_ID/inspection:start" '{"version":0}'; then
      seed_fail "검수 시작 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
    else
      seed_log "진행  검수 시작"
      if ! idem POST "$GW/api/v1/inbound/asns/$ASN_ID/inspection" "$(cat <<JSON
{"notes":"외관 양호 — 파손 $QTY_DAMAGED, 부족 $QTY_SHORT",
 "lines":[{"asnLineId":"$ASN_LINE_ID","qtyPassed":$QTY_PASSED,"qtyDamaged":$QTY_DAMAGED,
           "qtyShort":$QTY_SHORT,"lotId":null,"lotNo":"L-20260820-A"}],
 "version":1}
JSON
)"; then
        seed_fail "검수 기록 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
      else
        seed_log "진행  검수 기록 (합격 $QTY_PASSED)"
        if ! idem POST "$GW/api/v1/inbound/asns/$ASN_ID/putaway:instruct" "$(cat <<JSON
{"lines":[{"asnLineId":"$ASN_LINE_ID","destinationLocationId":"$LOCATION_ID","qtyToPutaway":$QTY_PASSED}],
 "version":2}
JSON
)"; then
          seed_fail "적치 지시 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
        else
          seed_log "진행  적치 지시"
          INSTR_ID="$(json_field putawayInstructionId)"
          # 🔵 ASN 응답의 라인 키는 `id` 인데 적치 지시 응답은 `putawayLineId` 다
          # (계약 §3.1). 같은 "lines" 라도 키가 다르다 — 여기서 `id` 를 쓰면 URL 이
          # 조용히 깨져 curl 이 상태코드조차 못 내고 빈 실패가 된다(실측).
          PUTAWAY_LINE_ID="$(json_nested_first lines putawayLineId)"
          if ! idem POST "$GW/api/v1/inbound/putaway/$INSTR_ID/lines/$PUTAWAY_LINE_ID:confirm" \
                 "{\"actualLocationId\":\"$LOCATION_ID\",\"qtyConfirmed\":$QTY_PASSED}"; then
            seed_fail "적치 확정 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
          else
            seed_log "진행  적치 확정 → 재고 반영 (비동기)"
          fi
        fi
      fi
    fi
  fi
fi

# =============================================================================
# 2) 출고 주문 1건
# =============================================================================
# 🔴 **이 주문은 콘솔 `/wms/outbound` 에 뜨지 않는다.** 그래도 만든다 — 아래를 읽을 것.
#
# 데모 운영자 토큰은 `tenant_id=demo-corp` 라 outbound-service 가 **restricted** 로
# 판정하고, 조회를 `tenant_id=demo-corp` AND `source=FULFILLMENT_ECOMMERCE` 로 고정한다
# (`OrderQueryCommand.withTenantScope`). 그런데 그 pinning 은 **쿼리 커맨드에만** 있고
# 생성 경로는 `tenant_id` 를 설정하지 않는다 ⇒ 여기서 만든 주문은
# `tenant_id=NULL · source=MANUAL` 이라 **만든 주체의 조회 조건에 절대 안 걸린다.**
# 실측: 생성 201 / 같은 토큰 조회 `200 {"content":[],"totalElements":0}`. → `TASK-BE-581`.
#
# 그럼에도 만드는 이유: ① `POST /outbound/orders` 가 실제로 동작한다는 **검증**이고
# (이 시드의 원칙: 넣는 행위가 곧 검증), ② wms 네이티브 스코프 호출자에게는 보이며,
# ③ BE-581 이 어느 선택지로 닫히든 이 행은 유효하다.
#
# 🔴 `dbexec` 로 `tenant_id` 만 박아 화면을 채우지 **말 것** — `source=MANUAL` 인데
# 테넌트가 붙은, 제품이 만들 수 없는 행이 된다(README "도메인 규칙을 우회하지 마라").
# 🔴 outbound-service 의 마스터 read-model 은 **비어 있고, 저절로 차지 않는다.**
#
# 실측(2026-08-06): `outbound_db` 의 warehouse/sku/partner_snapshot 전부 0행.
# 이유가 구조적이다 — 그 스냅샷은 `master.*` Kafka 이벤트로만 채워지는데, 데모의
# 마스터 데이터는 **Flyway 가 직접 심어서** 이벤트가 한 번도 발행된 적이 없다.
# inbound·inventory 는 각자 `db/seed/V99__seed_dev_masterref.sql` 로 이 구멍을
# 메웠는데 **outbound 에는 그 파일이 없다**(실측: 6개 서비스 중 master/inbound/
# inventory 만 db/seed 보유). 즉 비대칭이고, 그래서 출고 주문은 데모에서
# 구조적으로 생성 불가였다 — `PARTNER_INVALID_TYPE` / `WAREHOUSE_NOT_FOUND`.
# → 별도 티켓(AC-8): outbound-service 에 형제와 같은 db/seed 를 추가할 것.
#
# 그 파일 추가는 **제품 코드 변경**이라 이 티켓의 Out of Scope 다. 그래서 여기서는
# 티켓이 마련해 둔 유료 경로(`dbexec --why`)로 스냅샷만 채우고, **주문 생성 자체는
# 실제 API 로** 한다 — 검증되는 것은 주문 엔드포인트이지 INSERT 가 아니다.
#
# CUSTOMER 거래처는 어느 시드에도 없다(마스터 V103 은 SUPPLIER 계열만 심는다).
# `...802` 는 그 시드의 번호 규약을 이어 새로 부여한 고정 UUID 다.
CUSTOMER_ID=01910000-0000-7000-8000-000000000802

dbexec --why "outbound-service 에는 db/seed 가 없어(형제 inbound/inventory 는 있다) 마스터 read-model 이 영구히 0행이고, master.* 이벤트는 데모에서 발행되지 않는다(마스터 데이터가 Flyway 직접 삽입). 이것 없이는 POST /outbound/orders 가 구조적으로 불가 — 별도 티켓 대상." \
  wms-postgres psql outbound_db postgres <<SQL
INSERT INTO warehouse_snapshot (id, warehouse_code, status, cached_at, master_version)
VALUES ('$WAREHOUSE_ID', 'WH01', 'ACTIVE', '2026-04-18T00:00:00Z', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sku_snapshot (id, sku_code, tracking_type, status, cached_at, master_version)
VALUES ('$SKU_ID', 'SKU-APPLE-001', 'LOT', 'ACTIVE', '2026-04-18T00:00:00Z', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO partner_snapshot (id, partner_code, partner_type, status, cached_at, master_version)
VALUES ('$CUSTOMER_ID', 'CUS-001', 'CUSTOMER', 'ACTIVE', '2026-04-18T00:00:00Z', 0)
ON CONFLICT (id) DO NOTHING;
SQL

if http GET "$GW/api/v1/outbound/orders?size=100" && printf '%s' "$SEED_LAST_BODY" | grep -qF "\"$ORDER_NO\""; then
  SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  출고 주문 $ORDER_NO"
elif idem POST "$GW/api/v1/outbound/orders" "$(cat <<JSON
{"orderNo":"$ORDER_NO","customerPartnerId":"$CUSTOMER_ID","warehouseId":"$WAREHOUSE_ID",
 "requiredShipDate":"2026-08-25","notes":"데모 출고 — 사과 10박스",
 "lines":[{"lineNo":1,"skuId":"$SKU_ID","lotId":null,"qtyOrdered":10}]}
JSON
)"; then
  SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  출고 주문 $ORDER_NO"
elif [ "$SEED_LAST_STATUS" = "409" ]; then
  # 🔴 409 는 실패가 아니라 **서버가 중복을 올바로 거절한 것**이다(lib.sh api_create 와
  # 같은 규약). 특히 이 경로에서 실제로 났다: 앞선 실행이 게이트웨이 504 를 받았는데
  # **쓰기는 성공해 있었다** — 그래서 존재 확인 GET 도 504 로 실패해 POST 로 내려왔고,
  # 그 POST 가 409 를 받았다. 504 를 "안 만들어졌다" 로 읽으면 여기서 거짓 실패가 난다.
  SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  출고 주문 $ORDER_NO (HTTP 409)"
else
  seed_fail "출고 주문 $ORDER_NO — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
fi

seed_summary
