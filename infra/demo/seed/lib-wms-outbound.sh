#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/lib-wms-outbound.sh — wms 출고 주문을 SHIPPED 까지 미는 공용 함수
# =============================================================================
# `seed-wms.sh`(자기 데모 주문)와 `seed-ecommerce.sh`(wms 로 라우팅된 쇼핑 주문,
# TASK-MONO-818)가 **같은 함수**를 부른다. 사본을 두면 다음에 출고 계약이 바뀔 때 한쪽만
# 고쳐진다 — 그 실패 모드를 피하려고 `seed-wms.sh` 에 있던 흐름을 여기로 옮겼다.
#
# 전제: `lib.sh` 가 먼저 source 돼 있다(`http` · `seed_log` · `seed_warn` · `SEED_TOKEN`).
# 토큰은 **호출자가** 정한다 — wms 출고 행은 테넌트로 갈린다(이벤트로 온 쇼핑 주문은
# 봉투의 테넌트 `ecommerce`, `FulfillmentRequestedConsumer` → `ReceiveOrderService`).
#
# 흐름 (TASK-BE-586 / ADR-MONO-066 옵션 B 이후에만 부를 수 있다):
#   orderNo 로 주문 찾기 → 이미 SHIPPED 면 끝 → picking request(예약 회신이 만든다) →
#   피킹 확정 → 포장 단위 → 봉인 → 출하 확정(`wms.outbound.shipping.confirmed.v1` 발행)
# =============================================================================

# 첫 매칭 값 하나를 뽑는다. jq 는 데모 호스트에 없다(lib.sh 와 같은 제약).
wms_pick1()  { printf '%s' "$2" | sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" | head -1; }
wms_pick1n() { printf '%s' "$2" | sed -n "s/.*\"$1\":\([0-9]*\).*/\1/p" | head -1; }

wms_gen_uuid() {
  local h; h="$(openssl rand -hex 16)"
  printf '%s-%s-4%s-a%s-%s\n' "${h:0:8}" "${h:8:4}" "${h:13:3}" "${h:17:3}" "${h:20:12}"
}

# wms 쓰기는 매번 새 UUID Idempotency-Key 를 요구한다.
wms_idem() {
  local method="$1" url="$2" body="${3:-}"
  http "$method" "$url" "$body" -H "Idempotency-Key: $(wms_gen_uuid)"
}

# wms_ship_outbound <wms게이트웨이> <orderNo> [lotId] [cartonNo] [주문대기초]
#
#   lotId    — LOT 추적 SKU 면 확정 시 실물 lot 이 필수(LOT_REQUIRED). 비우면 `null`
#              (ecommerce SKU 는 tracking_type NONE — `seed-wms.sh` 의 입고 블록 주석).
#   cartonNo — 포장 단위 번호. 기본 `BOX-DEMO-1`.
#   주문대기초 — 주문이 아직 없을 때 기다리는 시간(이벤트로 오는 주문은 몇 초 늦게 생긴다).
#
# 결과: 0 = SHIPPED(이번에 밀었거나 이미 SHIPPED, `WMS_SHIP_RESULT` = created|existing)
#       1 = 실패(이유를 seed_warn 으로 남긴다)
#       2 = 주문을 찾지 못함(대기 시간 안에 wms 에 생기지 않았다)
# 🔵 실패해도 seed_fail 로 세지 않는다 — 주문 화면은 이미 뜨고 출하 화면만 빈다.
wms_ship_outbound() {
  local gw="$1" order_no="$2" lot="${3:-}" carton="${4:-BOX-DEMO-1}" wait_s="${5:-0}"
  local order_id order_line_id pr_id loc_id sku_id qty ver pu_id body lines lot_json i
  WMS_SHIP_RESULT=""

  for (( i=0; ; i+=3 )); do
    http GET "$gw/api/v1/outbound/orders?orderNo=$order_no&size=5" || {
      seed_warn "wms 주문 조회 실패 HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:120}"; return 1; }
    # 목록에서 이 주문의 조각만 잘라 낸다(다른 주문의 id 를 집는 것을 막는다).
    body="$(printf '%s' "$SEED_LAST_BODY" | tr '{' '\n' | grep -F "\"$order_no\"" | head -1)"
    order_id="$(wms_pick1 orderId "$body")"
    [ -n "$order_id" ] && break
    [ "$i" -ge "$wait_s" ] && { seed_warn "wms 에 orderNo=$order_no 출고 주문이 없습니다(${wait_s}초 대기)"; return 2; }
    sleep 3
  done

  http GET "$gw/api/v1/outbound/orders/$order_id" || return 1
  case "$SEED_LAST_BODY" in
    *'"status":"SHIPPED"'*) WMS_SHIP_RESULT=existing; seed_log "존재  출고 $order_no 이미 SHIPPED"; return 0 ;;
  esac

  # 예약 회신이 picking_request 를 만들 때까지 기다린다(보통 1~3초).
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    http GET "$gw/api/v1/outbound/orders/$order_id/picking-requests" || return 1
    pr_id="$(wms_pick1 pickingRequestId "$SEED_LAST_BODY")"
    [ -n "$pr_id" ] && break
    sleep 2
  done
  # 🔴 예전 문구는 *"ADR-MONO-066 경로를 확인하십시오"* 였다. 그 조언을 따르는 사람은
  # **정상 동작하는 코드를 감사하러 간다** — 실측(2026-08-15)으로 같은 DB 의 다른 두 주문은
  # `SHIPPED` 까지 갔다. 원인은 코드가 아니라 **이 볼륨의 데이터 이력**이다: `PickingRequest`
  # 는 예약 회신 이벤트를 소비할 때만 생기는데(ADR-MONO-066 B), 이 주문의 예약은 그 코드가
  # 있기 전에 이미 소비돼 끝났고 재생되지 않는다. 시드는 멱등이라 새 예약도 안 일어난다.
  # 계약 § 2.1(수동 재진입)은 BE-586 R2-b 가 도달 불가라 삭제했으므로 되살릴 API 도 없다.
  # ⇒ **남은 복구 수단은 볼륨 초기화뿐이고, 경고는 그 명령을 줘야 한다.**
  [ -n "$pr_id" ] || {
    seed_warn "$order_no 에 picking request 가 없습니다 — **코드 결함이 아니라 이 볼륨의 데이터 이력** 문제입니다"
    seed_warn "  ADR-MONO-066 이전에 예약이 소비된 주문이라 재실행으로는 낫지 않습니다(수동 재진입 API 도 없습니다)"
    seed_warn "  복구: bash infra/demo/demo-down.sh wms && docker volume rm wms_postgres-data && bash infra/demo/demo-up.sh wms"
    seed_warn "  ⚠ 그 명령은 wms 데이터를 **전부** 지웁니다(입고·재고 포함) — 시드가 다시 심습니다"
    return 1
  }

  # 이 흐름은 라인 하나짜리 주문만 다룬다(데모 주문은 전부 그렇다). 둘 이상이면 멈춘다 —
  # 첫 라인만 확정하면 나머지가 남아 출하 확정이 거절된다.
  lines="$(printf '%s' "$SEED_LAST_BODY" | grep -o '"pickingRequestLineId"' | wc -l | tr -d ' ')"
  [ "${lines:-0}" = "1" ] || { seed_warn "$order_no 의 picking request 라인이 ${lines:-0}개 — 이 시드는 1개만 다룹니다"; return 1; }

  order_line_id="$(wms_pick1 orderLineId "$SEED_LAST_BODY")"
  sku_id="$(wms_pick1 skuId "$SEED_LAST_BODY")"
  loc_id="$(wms_pick1 locationId "$SEED_LAST_BODY")"
  qty="$(wms_pick1n qtyToPick "$SEED_LAST_BODY")"
  [ -n "$order_line_id" ] && [ -n "$sku_id" ] && [ -n "$loc_id" ] && [ -n "$qty" ] || {
    seed_warn "picking request 응답에서 라인 정보를 읽지 못했습니다"; return 1; }

  # 🔴 LOT 추적 SKU 는 확정 시 lot 이 필수다(LOT_REQUIRED). 예약은 lot 없이 성립하고
  # (재고 행의 lot_id 가 NULL) 운영자가 확정 시점에 실물 lot 을 고른다.
  if [ -n "$lot" ]; then lot_json="\"$lot\""; else lot_json=null; fi

  wms_idem POST "$gw/api/v1/outbound/picking-requests/$pr_id/confirmations" "$(cat <<JSON
{"orderId":"$order_id","confirmedBy":"seed","notes":"데모 피킹 확정",
 "lines":[{"orderLineId":"$order_line_id","skuId":"$sku_id","lotId":$lot_json,
           "actualLocationId":"$loc_id","qtyConfirmed":$qty}]}
JSON
)" || { seed_warn "피킹 확정 실패 HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"; return 1; }

  wms_idem POST "$gw/api/v1/outbound/orders/$order_id/packing-units" "$(cat <<JSON
{"cartonNo":"$carton","packingType":"BOX","weightGrams":1000,
 "lines":[{"orderLineId":"$order_line_id","skuId":"$sku_id","lotId":$lot_json,"qty":$qty}]}
JSON
)" || { seed_warn "포장 단위 생성 실패 HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"; return 1; }
  pu_id="$(wms_pick1 packingUnitId "$SEED_LAST_BODY")"

  wms_idem PATCH "$gw/api/v1/outbound/packing-units/$pu_id" '{"version":0}' \
    || { seed_warn "포장 봉인 실패 HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"; return 1; }

  http GET "$gw/api/v1/outbound/orders/$order_id" || return 1
  ver="$(wms_pick1n version "$SEED_LAST_BODY")"
  wms_idem POST "$gw/api/v1/outbound/orders/$order_id/shipments" \
    "{\"carrierCode\":\"DEMO-CARRIER\",\"version\":${ver:-0}}" \
    || { seed_warn "출하 확정 실패 HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"; return 1; }

  WMS_SHIP_RESULT=created
  seed_log "생성  출하 $order_no → SHIPPED"
  return 0
}
