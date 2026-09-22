#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed-ecommerce.sh — 스토어프런트 + 콘솔 E-Commerce 데이터 시드
# =============================================================================
# TASK-MONO-506 S3.
#
# 이 스크립트는 두 신원으로, **다섯 단계**로 일한다. 순서가 곧 도메인 규칙이다:
#
#   1) 소비자 토큰 (ecommerce-web-store-client)  — 프로필 · 배송지 · 위시리스트 ·
#        **주문 5건(상태별) · 결제 · 취소**
#   2) 운영자 토큰 (platform-console-web → assume ecommerce)
#        — 셀러 · 수수료율 · 정산기간 · 알림 템플릿 · 프로모션/쿠폰 · **배송 진행**
#   3) 소비자 토큰 다시 — **리뷰**
#   4) 운영자 토큰 — **정산 기간 마감 + 지급 실행**(적립은 자동, 지급은 아니다)
#   5) 운영자 토큰 — **사후조건 단언**(TASK-MONO-710 AC-3: 주문 ≥5 · 상태 ≥4종 ·
#        배송 ≥2건. 경고가 아니라 `seed_fail` 이다)
#
# 🔴 3번이 2번 뒤에 오는 이유: review-service 는 `hasUserPurchasedProduct` 로
# 구매를 검증하고, 그 술어는 `OrderStatus.DELIVERED` 만 인정한다(소스 확인).
# 주문은 결제 직후 CONFIRMED 에서 멈추므로, **운영자가 배송을 DELIVERED 까지
# 진행시키기 전에는 리뷰를 쓸 수 없다.** 이것은 결함이 아니라 "구매자 리뷰" 규칙이며,
# 시드가 그 규칙을 우회하면(리뷰 행 직접 INSERT) 데모는 존재할 수 없는 상태를 보여준다.
# 그래서 시드는 **배송을 실제로 진행시켜** 리뷰 자격을 만든다 — 그 과정에서 콘솔
# 「배송」 탭도 함께 찬다.
#
# 상품/카테고리/변형은 여기서 만들지 않는다 — product-service 의
# `V8__seed_sample_data.sql` 이 이미 심는다(상품 8 · 카테고리 7 · 변형 28, 실측).
# =============================================================================
set -uo pipefail
SEED_DOMAIN=ecommerce
# shellcheck source=infra/demo/seed/lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

GW="http://ecommerce.${DEMO_DOMAIN}"

container_up ecommerce-gateway-service || { seed_log "게이트웨이 미기동 — 건너뜀"; exit 0; }
wait_http "$GW/api/products" 240 || { seed_fail "게이트웨이가 240초 안에 응답하지 않습니다"; seed_summary; exit $?; }

# =============================================================================
# 주문 시드 도구 (TASK-MONO-710)
# =============================================================================
# 🔴 **왜 API 층인가 — 주문은 상태 기계다.**
#
# `orders` 에 `status='DELIVERED'` 를 직접 INSERT 하면 화면 다섯 장이 즉시 찬다.
# 그리고 도메인은 거짓말을 한다: 아웃박스에 행이 없으므로 `OrderPlaced` ·
# `OrderConfirmed` · `PaymentCompleted` 가 **한 건도 발행되지 않고**, 그 결과
#   · shipping-service 는 배송 건을 만들지 않으며(배송 건은 `OrderConfirmed` 소비로 생긴다),
#   · settlement-service 는 수수료를 적립하지 않고(적립은 `PaymentCompleted` 소비다 —
#     `settlement-subscriptions.md`),
#   · wms 는 그 주문을 아예 모른다.
# 즉 **주문만 있고 배송·정산이 비는** 상태가 되는데, 그건 지금 상태보다 나쁘다.
# 지금은 «데이터가 없다» 이고 그때는 «시스템이 고장 났다» 로 읽히기 때문이다.
#
# 이 논증은 새로 만든 것이 아니다 — 이 파일 맨 위가 **리뷰에 대해 같은 말**을 이미
# 적어 뒀다("직접 INSERT 하면 데모가 «존재할 수 없는 상태» 를 보여준다"). 주문은
# 그 논증의 한 층 아래일 뿐이다. `lib.sh` 는 그 정책을 코드로 강제한다(`dbexec` 는
# `--why` 없이는 실행되지 않고, 가드 (y) 가 lib.sh 밖의 raw psql 을 막는다).
# 🔴 그러므로 **주문에 `dbexec` 를 쓰지 마라.** 막힌 엔드포인트가 없다.
#
# 만들 수 있는 상태와 그 경로(계약서 대조 완료):
#   PENDING    주문 생성만 하고 결제하지 않는다            (POST /api/orders)
#   CANCELLED  주문 생성 후 소유자가 취소                  (POST /api/orders/{id}/cancel)
#   CONFIRMED  결제 승인 → 재고 예약 → 사가가 확정          (POST /api/payments/confirm)
#   SHIPPED    위 + 운영자가 배송을 SHIPPED 로 전이         (PUT /api/shippings/{id}/status)
#   DELIVERED  위 + IN_TRANSIT → DELIVERED 까지 전이
#
# 🔴 `POST /api/admin/orders/{id}/status` 로는 SHIPPED·DELIVERED 를 만들 수 없다
# (계약서: 400 INVALID_ORDER_REQUEST). 주문은 **배송의 되돌아오는 다리**로만 그 두
# 상태에 간다(`ShippingStatusChanged` → order-service, ADR-MONO-022 §D7). 그래서
# 시드는 주문 상태가 아니라 **배송**을 움직인다.
#
# 🔵 멱등: `POST /api/orders` 는 `Idempotency-Key` 를 받고, 같은 키 + 같은 사용자의
# 재요청은 **원래 주문을 그대로** 돌려준다(중복 생성 없음 · `OrderPlaced` 재발행 없음).
# 그래서 주문에는 `api_create_unless` 식 탐지 프로브가 필요 없다 — 계약이 직접 답한다.
# 결제도 같다: 생성은 멱등(201, 부작용 없음)이고 재승인은 409 로 거절된다.
#
# 🔵 테넌트: 소비자 토큰에는 게이트웨이가 `ecommerce` 테넌트를 강제하므로 이 주문들은
# 전부 `tenant_id='ecommerce'` 에 산다 — 아래 운영자 절이 assume 하는 바로 그 테넌트다.
# =============================================================================

# 결제 후 주문이 CONFIRMED 로 전파될 때까지의 상한(초). 사가가 Kafka 두 홉을 돈다.
ORDER_WAIT_SECONDS=180

ORDER_RECIPIENT='데모 구매자'
ORDER_PHONE='010-1234-5678'
ORDER_ZIP='06236'
ORDER_ADDR1='서울특별시 강남구 테헤란로 1'
ORDER_ADDR2='10층'

ORDER_ID=""; ORDER_TOTAL=""; ORDER_LAST_STATUS=""
SHIPPING_ID=""; SHIPPING_STATUS=""

# order_place <슬롯> <상품id> — 상품 상세를 읽어 한 줄짜리 주문을 만든다.
#   성공하면 ORDER_ID / ORDER_TOTAL 을 세팅한다.
#
# 🔴 필드 이름은 계약서(`order-api.md`)를 읽고 적는다. 배송지의 키는 `recipient` 이고
# **`recipientName` 이 아니다** — 바로 옆 `/api/users/me/addresses` 는 `recipientName`
# 이라 둘이 일부러 다르다. 계약서가 2026-08-15 까지 여기를 틀리게 적고 있었고
# (TASK-BE-588), 그 예시로 만든 요청이 `400 VALIDATION_ERROR "recipient is required"`
# 로 거절됐다. 체크아웃 화면도 이 자리에서 이름을 바꿔 싣는다.
order_place() {
  local slot="$1" pid="$2"
  ORDER_ID=""; ORDER_TOTAL=""
  if ! http GET "$GW/api/products/$pid"; then
    seed_fail "주문 시드: 상품 상세 조회 실패 $pid (HTTP $SEED_LAST_STATUS)"
    return 1
  fi
  local body="$SEED_LAST_BODY" name price seller vid opt add unit
  # 🔵 `"name":` 는 `"optionName":` 과 겹치지 않는다(대문자 N) — 같은 이유로
  #    `"price":` 는 `"additionalPrice":` 를 안 먹는다. 둘 다 head -1 이 문서 순서상
  #    첫 변형의 값이므로 같은 객체에서 온다.
  name="$(printf '%s' "$body" | grep -oE '"name":"[^"]*"' | head -1 | cut -d'"' -f4)"
  price="$(printf '%s' "$body" | grep -oE '"price":[0-9]+' | head -1 | cut -d: -f2)"
  seller="$(printf '%s' "$body" | grep -oE '"sellerId":"[^"]*"' | head -1 | cut -d'"' -f4)"
  # 🔴🔴 **상품 응답에 `variantId` 라는 키는 없다.** 그 이름은 **주문 API 가 받는** 이름이고
  #    상품 상세에서 같은 개념은 `variants[].id` 다. 처음 이 줄은 `"variantId":"…"` 를 찾았고,
  #    그 식은 어떤 상품에서도 0건이라 **주문이 한 건도 안 생겼다**(2026-09-22 데모 창 실측:
  #    상품 5개 전부 `✗ 주문 시드: … variantId/price 를 추출하지 못했습니다`). ⇒ 「내가 부르는
  #    이름」으로 「남의 코퍼스」를 grep 한 것이다. 🔵 `"variants":[` 뒤부터 잘라서 첫 `"id"` 를
  #    잡는다 — 상품 자신의 `"id"` 는 그 앞에 있으므로 잘라내면 첫 변형의 것이 된다. 아래
  #    `opt`·`add` 도 head -1 이라 **같은 첫 변형**에서 오고, `price` 는 최상위다.
  vid="$(printf '%s' "$body" | sed -n 's/.*"variants":\[//p' | grep -oE '"id":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
  opt="$(printf '%s' "$body" | grep -oE '"optionName":"[^"]*"' | head -1 | cut -d'"' -f4)"
  add="$(printf '%s' "$body" | grep -oE '"additionalPrice":[0-9]+' | head -1 | cut -d: -f2)"
  # 🔴 추출 0건을 실패로 센다(이 파일의 위시리스트 블록과 같은 이유 — 탐지식의 0건은
  #    "없음" 이 아니라 식이 깨진 것이다).
  if [ -z "$vid" ] || [ -z "$price" ]; then
    seed_fail "주문 시드: 상품 $pid 에서 variantId/price 를 추출하지 못했습니다: ${body:0:200}"
    return 1
  fi
  unit=$(( price + ${add:-0} ))

  local item order_body attempt
  item="{\"productId\":\"$pid\",\"variantId\":\"$vid\",\"productName\":\"$name\",\"optionName\":\"$opt\",\"quantity\":1,\"unitPrice\":$unit,\"sellerId\":\"${seller:-default}\"}"
  order_body="{\"items\":[$item],\"shippingAddress\":{\"recipient\":\"$ORDER_RECIPIENT\",\"phone\":\"$ORDER_PHONE\",\"zipCode\":\"$ORDER_ZIP\",\"address1\":\"$ORDER_ADDR1\",\"address2\":\"$ORDER_ADDR2\"}}"

  for attempt in 1 2 3; do
    if http POST "$GW/api/orders" "$order_body" -H "Idempotency-Key: demo-seed-order-$slot"; then
      ORDER_ID="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"orderId":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
      ORDER_TOTAL="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"totalPrice":[0-9]+' | head -1 | cut -d: -f2)"
      if [ -n "$ORDER_ID" ] && [ -n "$ORDER_TOTAL" ]; then
        # 🔵 «생성/기존» 을 세지 않는다 — 멱등 재생도 201 이라 이 층에서는 둘을 가를 수
        #    없고, 가른 척하면 요약이 거짓말을 한다. 이 시드의 판정은 맨 아래 사후조건이다.
        seed_log "주문 $slot: $ORDER_ID (${name:-?} · ${ORDER_TOTAL}원)"
        return 0
      fi
      seed_fail "주문 $slot 응답에서 orderId/totalPrice 를 읽지 못했습니다: ${SEED_LAST_BODY:0:200}"
      return 1
    fi
    # 409 DUPLICATE_ORDER_REQUEST = 같은 키가 **아직 처리 중**이다. 재시도하면 원래
    # 주문으로 수렴한다(계약서). 그 외는 재시도해도 같은 답이 온다.
    [ "$SEED_LAST_STATUS" = "409" ] || break
    sleep 3
  done
  seed_fail "주문 $slot 생성 실패 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
  return 1
}

# order_pay <슬롯> <주문id> <금액> — PENDING 결제 생성 + 승인.
#
# 🔴 승인이 통과하려면 payment-service 가 `demo-pg` 프로파일이어야 한다(가짜인 것은
# **돈뿐**이고 `PaymentEventPublisher` 는 진짜라 사가가 실제로 돈다). 데모 스택은
# `demo.env` 의 `ECOMMERCE_PAYMENT_PROFILES=demo-pg` 로 그렇게 뜬다. 그 프로파일이
# 빠지면 실제 Toss 어댑터가 붙어 502 PG_CONFIRM_FAILED 가 나고, 그때 이 줄이 실패로
# 세어지는 것이 맞다 — 조용히 넘어가면 주문 셋이 PENDING 에 머문 채 화면만 반쯤 찬다.
order_pay() {
  local slot="$1" oid="$2" amt="$3"
  if ! http POST "$GW/api/payments" "{\"orderId\":\"$oid\",\"amount\":$amt}"; then
    case "$SEED_LAST_STATUS" in
      409|422) : ;;  # 이미 결제 행이 있다 — 아래 승인으로 진행한다.
      *) seed_fail "결제 생성 실패 (주문 $slot) — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"; return 1 ;;
    esac
  fi
  if http POST "$GW/api/payments/confirm" \
       "{\"paymentKey\":\"demo-seed-pay-$slot\",\"orderId\":\"$oid\",\"amount\":$amt}"; then
    seed_log "결제 승인 (주문 $slot · ${amt}원)"
    return 0
  fi
  case "$SEED_LAST_STATUS" in
    409) seed_log "결제 이미 완료됨 (주문 $slot · HTTP 409) — 멱등 재실행" ; return 0 ;;
    502) seed_fail "결제 승인이 PG 에서 거절됐습니다 (주문 $slot · HTTP 502). payment-service 프로파일에 demo-pg 가 있습니까? (demo.env ECOMMERCE_PAYMENT_PROFILES)"; return 1 ;;
    *)   seed_fail "결제 승인 실패 (주문 $slot) — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"; return 1 ;;
  esac
}

# order_wait_status <주문id> <허용 상태...> — 비동기 전파를 기다린다.
order_wait_status() {
  local oid="$1"; shift
  local want=" $* " i s=""
  for (( i=0; i<ORDER_WAIT_SECONDS; i+=5 )); do
    if http GET "$GW/api/orders/$oid"; then
      s="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"status":"[A-Z_]+"' | head -1 | cut -d'"' -f4)"
      case "$want" in *" $s "*) ORDER_LAST_STATUS="$s"; return 0 ;; esac
    fi
    sleep 5
  done
  ORDER_LAST_STATUS="${s:-?}"
  return 1
}

# shipping_of <주문id> — 배송 건이 생길 때까지 기다린다.
#   배송 건은 `OrderConfirmed` 를 소비할 때 **자동으로** 만들어진다(계약서 Notes).
#   🔴 이 조회는 **구매자 토큰으로** 해야 한다 — 아래 운영자 절의 주석 참조.
shipping_of() {
  local oid="$1" i
  SHIPPING_ID=""; SHIPPING_STATUS=""
  for (( i=0; i<120; i+=5 )); do
    if http GET "$GW/api/shippings/orders/$oid"; then
      SHIPPING_ID="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"shippingId":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
      SHIPPING_STATUS="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"status":"[A-Z_]+"' | head -1 | cut -d'"' -f4)"
      [ -n "$SHIPPING_ID" ] && return 0
    fi
    sleep 5
  done
  return 1
}

# -----------------------------------------------------------------------------
# 0. 소비자 프로필 — 이 시드에서 **유일한** 직접-DB 항목
# -----------------------------------------------------------------------------
# 🔴 시드의 편의가 아니라 **제품 결함의 우회**다. 사유를 여기 남긴다:
#
# user-service 의 컨트롤러는 4개뿐이고(Address · AdminUser · User · Wishlist)
# 전수 확인 결과 **프로필을 생성하는 엔드포인트가 존재하지 않는다.** `GET/PATCH
# /api/users/me` 는 둘 다 기존 행을 전제한다. ADR-MONO-040 이후 회원가입은 IAM(SAS)
# 이 소유하는데, user-service 에 IAM 신원을 프로비저닝하는 경로가 붙지 않았다.
#
# 실측(데모 계정, 유효한 소비자 토큰):
#     GET  /api/users/me            404 USER_PROFILE_NOT_FOUND
#     PATCH/api/users/me            404 USER_PROFILE_NOT_FOUND
#     POST /api/wishlists           404 USER_PROFILE_NOT_FOUND
#     POST /api/users/me/addresses  500 (FK fk_user_addresses_user_id 위반)
#
# 즉 스토어프런트의 `/my/profile` · `/my/wishlist` · `/my/addresses` 세 화면이
# **IAM 로그인 사용자에게는 애초에 도달 불가능**했다. → TASK-BE-575 (AC-8).
# 결함이 고쳐지면 이 블록은 `PATCH /api/users/me` 한 줄로 대체된다.
DEMO_SUB="${DEMO_ECOMMERCE_SUB:-0199de70-0000-7000-8000-00000000ec01}"
if container_up ecommerce-user-postgres; then
  dbexec --why "user-service 에 프로필 생성 엔드포인트가 존재하지 않는다(컨트롤러 4개 전수 확인). IAM 신원 프로비저닝 경로 부재 — TASK-BE-575" \
    ecommerce-user-postgres psql user_db user_user <<SQL
INSERT INTO user_profiles (id, user_id, email, name, nickname, phone, status, tenant_id, created_at, updated_at)
SELECT gen_random_uuid(), '$DEMO_SUB', 'demo@demo.com', '데모 구매자', 'demo', '010-1234-5678',
       'ACTIVE', 'ecommerce', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM user_profiles WHERE user_id = '$DEMO_SUB');
SQL
  if [ $? -eq 0 ]; then seed_log "소비자 프로필 준비됨 (user_id=$DEMO_SUB)"; else seed_fail "소비자 프로필 INSERT 실패"; fi
fi

# -----------------------------------------------------------------------------
# 1. 소비자 토큰 — "내" 데이터
# -----------------------------------------------------------------------------
CONSUMER_TOKEN="$(user_token 'ecommerce-web-store-client' "${ECOMMERCE_WEB_STORE_CLIENT_SECRET:-ecommerce-dev}" \
  "http://web.ecommerce.${DEMO_DOMAIN}/api/auth/callback/iam" \
  'openid profile email tenant.read ecommerce.consumer')"
[ -n "${CONSUMER_TOKEN:-}" ] || seed_fail "소비자 토큰 발급 실패 — 내 데이터를 시드하지 못했습니다"

DELIVER_ORDER_ID=""; REVIEW_PRODUCT_ID=""; REVIEW_PRODUCT_NAME=""
if [ -n "${CONSUMER_TOKEN:-}" ]; then
  SEED_TOKEN="$CONSUMER_TOKEN"

  # 배송지 — 체크아웃의 `address1`/`zipCode` 는 주소검색 위젯 전용 readOnly 필드다.
  # 저장된 배송지가 없으면 면접관은 결제 화면을 통과할 수 없다.
  api_create_unless '배송지(집)' "$GW/api/users/me/addresses" '테헤란로' \
    "$GW/api/users/me/addresses" \
    '{"label":"집","recipientName":"데모 구매자","phone":"010-1234-5678","zipCode":"06236","address1":"서울특별시 강남구 테헤란로 1","address2":"10층","isDefault":true}'
  api_create_unless '배송지(회사)' "$GW/api/users/me/addresses" '판교역로' \
    "$GW/api/users/me/addresses" \
    '{"label":"회사","recipientName":"데모 구매자","phone":"010-9876-5432","zipCode":"13529","address1":"경기도 성남시 분당구 판교역로 235","address2":"7층","isDefault":false}'

  # 위시리스트 — V8 시드의 실제 상품 id 를 목록에서 읽는다(하드코딩 금지).
  #
  # 🔴 추출 0건을 **반드시 실패로 센다.** 첫 판은 `"productId"` 를 찾고 있었는데 실제
  # 목록 응답의 필드는 `"id"` 다 — 루프가 0회 돌고, 로그엔 아무것도 남지 않고, 요약은
  # "실패 0" 이었다. 탐지식의 0건은 "없음" 이 아니다.
  if http GET "$GW/api/products?size=3"; then
    pids="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"id":"[0-9a-f-]{36}"' | cut -d'"' -f4 | head -3)"
    if [ -z "$pids" ]; then
      seed_fail "상품 목록에서 상품 id 를 하나도 추출하지 못했습니다: ${SEED_LAST_BODY:0:160}"
    else
      for pid in $pids; do
        api_create_unless "위시리스트 $pid" "$GW/api/wishlists/me" "$pid" \
          "$GW/api/wishlists" "{\"productId\":\"$pid\"}"
      done
    fi
  else
    seed_fail "상품 목록 조회 실패 (HTTP $SEED_LAST_STATUS) — 위시리스트를 시드할 수 없습니다"
  fi

  # ---------------------------------------------------------------------------
  # 주문 다섯 건 — 상태마다 하나씩 (TASK-MONO-710)
  # ---------------------------------------------------------------------------
  # 🔴 **여기 있던 것이 이 티켓의 결함이었다.** 이 자리에는 `GET /api/orders?size=1` 로
  # **이미 있는 주문을 주워** 쓰고, 없으면
  #     seed_warn "데모 계정의 주문이 없습니다 — 배송 진행과 리뷰 시드를 건너뜁니다"
  # 한 줄을 남기고 지나가는 블록이 있었다. 시드는 주문을 **한 번도 만들지 않았다.**
  #
  # 🔴🔴 그리고 그 경고는 한 화면이 아니라 **세 갈래**를 지웠다. `SHIP_ID` 와
  # `REVIEW_PRODUCT_ID` 가 저 `if` 안에서만 세팅되므로, 주문이 0건이면
  #   · 아래 운영자 절의 **배송 진행**(PREPARING→…→DELIVERED)과
  #   · 3절의 **리뷰**
  # 가 **매 창 통째로 건너뛰어졌다.** 요약은 "실패 0" 이었다. 데모가 그 데이터 없이는
  # 존재할 수 없는데 경고로 지나간 것 자체가 결함의 일부다(lib.sh 헤더: 시드는 실패하면
  # 아무도 무시할 수 없다). 그래서 이제 **만들고, 맨 아래에서 단언한다.**
  order_pids=""
  if http GET "$GW/api/products?size=8"; then
    order_pids="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"id":"[0-9a-f-]{36}"' | cut -d'"' -f4 | head -5)"
  else
    seed_fail "상품 목록 조회 실패 (HTTP $SEED_LAST_STATUS) — 주문을 시드할 수 없습니다"
  fi
  order_pid_count="$(printf '%s\n' $order_pids | grep -c . || true)"
  if [ "${order_pid_count:-0}" -lt 5 ]; then
    seed_fail "주문 시드에 필요한 상품 5개를 모으지 못했습니다(추출 ${order_pid_count:-0} 건) — 카탈로그 시드(V8·V19)를 확인하십시오"
  else
    ORDER_SLOT_IDS=(); ORDER_SLOT_TOTALS=()
    order_slot=0
    for order_pid in $order_pids; do
      order_slot=$((order_slot + 1))
      if order_place "$order_slot" "$order_pid"; then
        ORDER_SLOT_IDS[order_slot]="$ORDER_ID"
        ORDER_SLOT_TOTALS[order_slot]="$ORDER_TOTAL"
      fi
    done

    # 슬롯 1 → CANCELLED. 소유자 취소이고 운영자 취소가 아니다(둘 다 계약에 있지만
    # 구매자 경로가 데모의 서사다).
    if [ -n "${ORDER_SLOT_IDS[1]:-}" ]; then
      if http POST "$GW/api/orders/${ORDER_SLOT_IDS[1]}/cancel" '{}'; then
        seed_log "주문 1 취소 (CANCELLED)"
      else
        case "$SEED_LAST_STATUS" in
          422) seed_log "주문 1 이미 취소됨 (HTTP 422 ORDER_CANNOT_BE_CANCELLED) — 멱등 재실행" ;;
          *)   seed_fail "주문 1 취소 실패 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}" ;;
        esac
      fi
    fi

    # 슬롯 2 → PENDING. **아무것도 하지 않는 것이 이 슬롯의 일이다.**
    # 🔴 그리고 이 상태는 **오래 못 간다**: order-service 의 `OrderStuckDetector` 가
    # `PENDING AND payment_id IS NULL` 을 유예 1800초 뒤부터 60초마다 쓸고, 5회째에
    # `CANCELLED(PAYMENT_TIMEOUT)` 으로 **자동 취소**한다(TASK-BE-435). 즉 시드 후
    # 약 35분이 지나면 PENDING 행이 사라진다. 이건 결함이 아니라 도메인이고,
    # 아래 사후조건이 «서로 다른 상태 ≥ 4» 를 재는 이유다(5가 아니라).
    if [ -n "${ORDER_SLOT_IDS[2]:-}" ]; then
      seed_log "주문 2 는 PENDING 으로 둔다 (결제하지 않음 — 약 35분 뒤 스택 감지기가 자동 취소한다)"
    fi

    # 슬롯 3·4·5 → 결제. 결제가 완료되면 재고 예약 사가가 주문을 CONFIRMED 로 올린다
    # (`PaymentCompleted` + `OrderPlaced` 수렴 → 재고 차감 → `StockChanged(ORDER_RESERVED)`
    #  → order-service 확정). 시드는 그 경로를 **밟는 것이지 흉내내지 않는다.**
    for order_slot in 3 4 5; do
      [ -n "${ORDER_SLOT_IDS[$order_slot]:-}" ] || continue
      order_pay "$order_slot" "${ORDER_SLOT_IDS[$order_slot]}" "${ORDER_SLOT_TOTALS[$order_slot]}"
    done
    for order_slot in 3 4 5; do
      [ -n "${ORDER_SLOT_IDS[$order_slot]:-}" ] || continue
      if order_wait_status "${ORDER_SLOT_IDS[$order_slot]}" CONFIRMED SHIPPED DELIVERED; then
        seed_log "주문 $order_slot → $ORDER_LAST_STATUS"
      else
        seed_fail "주문 $order_slot 이 결제 후 ${ORDER_WAIT_SECONDS}초 안에 CONFIRMED 로 가지 않았습니다 (마지막 상태 ${ORDER_LAST_STATUS:-?}) — 재고 예약 사가(product-service)나 Kafka 를 확인하십시오"
      fi
    done

    # 배송 건 — 슬롯 4 는 **SHIPPED 에서 멈춘다**(그래야 주문 하나가 실제로 SHIPPED 에
    # 앉는다), 슬롯 5 만 DELIVERED 까지 간다(리뷰 자격).
    #
    # 🔴 배송 건 조회는 **여기서**, 소비자 토큰으로 한다.
    # `/api/shippings/orders/{orderId}` 는 `X-User-Id` 소유권을 검사하는 **구매자 전용**
    # 엔드포인트다 — 운영자 토큰으로 부르면 403 `ACCESS_DENIED "User does not have access to
    # this shipping record"` 다(실측). 첫 판은 이 조회를 운영자 블록에 두었고, `if` 가 거짓이
    # 되면서 **배송 진행 전체가 로그 한 줄 없이 통째로 건너뛰어졌다.** 상태 전이(PUT)는
    # 반대로 운영자 권한이 필요하므로, 조회와 전이의 신원이 서로 다르다.
    if [ -n "${ORDER_SLOT_IDS[4]:-}" ]; then
      if shipping_of "${ORDER_SLOT_IDS[4]}"; then
        SHIP_ID_SHIPPED="$SHIPPING_ID"; SHIP_STATUS_SHIPPED="$SHIPPING_STATUS"
      else
        seed_fail "주문 4 의 배송 건이 생기지 않았습니다 (HTTP $SEED_LAST_STATUS) — 콘솔 「배송」 탭과 SHIPPED 주문이 비게 됩니다"
      fi
    fi

    DELIVER_ORDER_ID="${ORDER_SLOT_IDS[5]:-}"
    if [ -n "$DELIVER_ORDER_ID" ]; then
      if shipping_of "$DELIVER_ORDER_ID"; then
        SHIP_ID="$SHIPPING_ID"; SHIP_STATUS="$SHIPPING_STATUS"
      else
        seed_fail "주문 5 의 배송 건이 생기지 않았습니다 (HTTP $SEED_LAST_STATUS) — 배송 진행과 리뷰를 건너뜁니다"
      fi
      if http GET "$GW/api/orders/$DELIVER_ORDER_ID"; then
        REVIEW_PRODUCT_ID="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"productId":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
        REVIEW_PRODUCT_NAME="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"productName":"[^"]*"' | head -1 | cut -d'"' -f4)"
        seed_log "리뷰 대상: order=$DELIVER_ORDER_ID product=${REVIEW_PRODUCT_NAME:-?}"
      fi
    fi
  fi
  SEED_TOKEN=""
fi

# -----------------------------------------------------------------------------
# 2. 운영자 토큰 — 백오피스 (콘솔 E-Commerce 탭이 읽는 것)
# -----------------------------------------------------------------------------
# 🔴 `demo-corp` 가 아니라 `ecommerce` 를 assume 한다 (TASK-BE-576).
#
# 두 테넌트는 서로 다른 것을 준다:
#   demo-corp  → **권한**(5개 도메인 구독에서 파생되는 *_OPERATOR 역할)
#   ecommerce  → **가시성**(스토어프런트가 쓰는 행이 실제로 사는 곳)
#
# 백오피스를 demo-corp 로 넣으면 콘솔이 **반쪽**이 된다 — 셀러·프로모션·알림 템플릿은
# 보이는데 바로 옆의 상품·주문·배송·정산은 비어 있다(둘이 다른 테넌트에 살기 때문).
# 스토어프런트 쪽을 옮길 수는 없다: 카탈로그 자체가 `tenant_id='ecommerce'` 이고
# (product-service V8 이 tenant 컬럼을 안 적어 기본값을 탄다 — 상품 8/8 · 카테고리 7/7
# 실측), 게이트웨이가 소비자 토큰에 그 테넌트를 강제한다. 그러니 **운영자가 그쪽으로
# 가야 한다.** `ecommerce` 테넌트는 ecommerce+wms 를 구독하므로 assume 하면
# ECOMMERCE_OPERATOR 를 그대로 받는다(실측: 원소 수 8/4/1/3/3 = DB 와 일치).
OP_TOKEN="$(operator_token ecommerce)"
if [ -z "${OP_TOKEN:-}" ]; then
  seed_fail "운영자 토큰 발급 실패 — 백오피스 시드를 건너뜁니다"
  seed_summary; exit $?
fi
SEED_TOKEN="$OP_TOKEN"

# 셀러 — 콘솔 「셀러」 탭 + 정산의 소유자.
SELLER_ID="${DEMO_SELLER_ID:-demo-seller}"
api_create '셀러(demo-seller)' "$GW/api/admin/sellers" \
  "{\"sellerId\":\"$SELLER_ID\",\"displayName\":\"데모 셀러\"}"
api_create '셀러 활성화' "$GW/api/admin/sellers/$SELLER_ID/provision" '{}'

# 수수료율 — PUT 이라 본디 멱등이다.
if http PUT "$GW/api/admin/settlements/commission-rates/$SELLER_ID" '{"rateBps":500}'; then
  seed_log "수수료율 5.00% 설정 ($SELLER_ID)"
else
  seed_fail "수수료율 설정 실패 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"
fi

# 정산 기간 — 경계는 고정 리터럴이다. 현재시각 기준이면 2회차 실행이 새 기간을 또 연다.
api_create_unless '정산 기간(2026-01)' "$GW/api/admin/settlements/periods" '2026-01-01' \
  "$GW/api/admin/settlements/periods" \
  '{"from":"2026-01-01T00:00:00Z","to":"2026-02-01T00:00:00Z"}'

# 🔴 위 기간은 **시드가 만드는 적립을 하나도 담지 못한다.** 적립(`commission_accrual`)은
# `PaymentCompleted` 를 소비할 때 `occurredAt = 지금` 으로 찍히는데 저 창은 2026-01 이다.
# 그래서 저 기간을 마감해 봐야 payout 이 0건이고, 콘솔 「정산 기간 지급 내역」
# (`/ecommerce/settlements/periods/[id]`)은 여전히 빈다.
#
# ⇒ **지금을 담는 두 번째 기간**을 연다. 경계는 여전히 고정 리터럴이고(현재시각 기준
# 이면 2회차가 새 기간을 또 연다), 앞 기간과 **겹치지 않게** 골랐다 —
# `[2026-03-01, 2030-01-01)` 은 `[2026-01-01, 2026-02-01)` 과 교집합이 없다.
# 🔴 겹침을 피하는 것이 중요한 이유: 계약서가 «겹치는 두 창을 둘 다 마감하면 교집합의
# 적립이 **두 번 지급된다**» 를 **의도된 잔여 위험**으로 명시해 두었다(방어 코드 없음).
# 시드가 그 모양을 데모에 구워 넣으면 안 된다.
# 🔵 탐지 마커도 겹치지 않게 골랐다: `2026-02-01` 은 앞 기간의 `to` 값이라 마커로 쓰면
# 항상 «이미 있음» 으로 읽혀 두 번째 기간이 영영 안 생긴다.
api_create_unless '정산 기간(데모 · 2026-03~2030-01)' "$GW/api/admin/settlements/periods?size=50" '2026-03-01' \
  "$GW/api/admin/settlements/periods" \
  '{"from":"2026-03-01T00:00:00Z","to":"2030-01-01T00:00:00Z"}'

# 알림 템플릿 — enum 이 권위다 (TemplateType: ORDER_PLACED · PAYMENT_COMPLETED ·
# SHIPPING_STATUS_CHANGED / NotificationChannel: EMAIL · SMS — 소스 전수 확인).
tmpl() {
  api_create_unless "알림 템플릿 $1/$2" "$GW/api/notifications/templates" "\"type\":\"$1\"" \
    "$GW/api/notifications/templates" \
    "{\"type\":\"$1\",\"channel\":\"$2\",\"subject\":\"$3\",\"body\":\"$4\"}"
}
tmpl ORDER_PLACED            EMAIL '주문이 접수되었습니다'     '주문번호 {{orderId}} 가 정상 접수되었습니다.'
tmpl PAYMENT_COMPLETED       EMAIL '결제가 완료되었습니다'     '주문번호 {{orderId}} 의 결제가 완료되었습니다.'
tmpl SHIPPING_STATUS_CHANGED SMS   '배송 상태가 변경되었습니다' '주문번호 {{orderId}} 의 배송 상태가 {{status}} 로 변경되었습니다.'

# 프로모션 + 쿠폰 발급 — 콘솔 「프로모션」 탭과 스토어프런트 `/my/coupons`.
# 쿠폰은 프로모션에서 **발급**되는 것이지 따로 만드는 것이 아니다. 도메인 모델을
# 우회해 쿠폰 행을 직접 넣으면 발급 수량·만료 규칙이 전부 비어 버린다.
#
# 🔴 필드는 DTO 전문을 읽고 적는다. 첫 판은 `grep | head -14` 로 앞부분만 보고
# `discountValue`/`maxDiscountAmount` 두 필드를 놓쳐 400 을 받았다
# ("할인 값은 양수여야 합니다"). discountType enum: FIXED · PERCENTAGE.
if container_up ecommerce-promotion-service && wait_http "$GW/api/promotions" 180; then
  api_create_unless '프로모션(신규가입 5천원)' "$GW/api/promotions?size=50" '신규 가입 축하' \
    "$GW/api/promotions" \
    '{"name":"신규 가입 축하 쿠폰","description":"데모 계정에게 지급되는 5,000원 할인 쿠폰","discountType":"FIXED","discountValue":5000,"maxDiscountAmount":5000,"maxIssuanceCount":1000,"startDate":"2026-01-01T00:00:00Z","endDate":"2027-01-01T00:00:00Z"}'

  if http GET "$GW/api/promotions?size=50"; then
    promo_id="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"(promotionId|id)":"[^"]+"' | head -1 | cut -d'"' -f4)"
    if [ -z "$promo_id" ]; then
      seed_fail "프로모션 목록에서 id 를 추출하지 못했습니다: ${SEED_LAST_BODY:0:160}"
    else
      # 발급은 Idempotency-Key 를 요구한다 — **고정 키**를 쓴다. 매번 새 키면 2회차
      # 실행이 쿠폰을 또 발급해 AC-4 가 깨진다.
      if http POST "$GW/api/promotions/$promo_id/coupons/issue" \
           "{\"userIds\":[\"$DEMO_SUB\"]}" -H 'Idempotency-Key: demo-seed-coupon-1'; then
        seed_log "쿠폰 발급 (promotion=$promo_id → demo)"
      else
        case "$SEED_LAST_STATUS" in
          409|422) seed_log "쿠폰 이미 발급됨 (HTTP $SEED_LAST_STATUS)" ;;
          *) seed_fail "쿠폰 발급 실패 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}" ;;
        esac
      fi
    fi
  else
    seed_fail "프로모션 목록 조회 실패 (HTTP $SEED_LAST_STATUS)"
  fi
else
  seed_warn "ecommerce-promotion-service 미기동/무응답 — 프로모션/쿠폰을 시드하지 않았습니다(/my/coupons · 콘솔 프로모션 탭이 빕니다)"
fi

# 배송 진행 — PREPARING → SHIPPED → IN_TRANSIT → DELIVERED.
# 전이는 한 단계씩만 허용된다(ShippingStatus.ALLOWED_TRANSITIONS, 소스 확인) —
# 곧바로 DELIVERED 를 쏘면 거절된다.
#
# 🔵 TASK-MONO-710 에서 **함수로 바꿨다**(사본을 만들지 않았다). 배송 건이 이제 둘이고
# 목적지가 서로 다르기 때문이다: 하나는 SHIPPED 에서 멈춰야 주문 하나가 실제로 SHIPPED
# 상태에 앉고, 다른 하나만 DELIVERED 까지 간다(리뷰 자격). 같은 블록을 복사했다면 다음에
# 전이 규칙이 바뀔 때 한쪽만 고쳐진다.
ship_progress() {  # <배송id> <현재상태> <최종목표>
  local sid="$1" cur="$2" final="$3" target body
  seed_log "배송 $sid 현재 상태=$cur → $final 까지 진행"
  for target in SHIPPED IN_TRANSIT DELIVERED; do
    [ "$cur" = "$final" ] && break
    body="{\"status\":\"$target\""
    [ "$target" = "SHIPPED" ] && body="$body,\"trackingNumber\":\"DEMO-1234567890\",\"carrier\":\"CJ대한통운\""
    body="$body}"
    if http PUT "$GW/api/shippings/$sid/status" "$body"; then
      cur="$target"; seed_log "  → $target"
    else
      case "$SEED_LAST_STATUS" in
        # 이미 그 상태를 지났으면 전이 거절이 정상이다(멱등 재실행).
        409|422|400)
          seed_log "  → $target 전이 불가/불필요 (HTTP $SEED_LAST_STATUS) ${SEED_LAST_BODY:0:120}" ;;
        # 🔴 여기서 403/404 가 나오면 **테넌트 불일치를 의심하라** — 소비자 토큰으로
        # 방금 조회한 배송 건이 운영자에게는 존재하지 않는 상태다. TASK-BE-576 이
        # 정확히 이 모양이었다(운영자가 `demo-corp` 를 assume 하는데 행은
        # `tenant_id=ecommerce`). 지금은 위에서 `ecommerce` 를 assume 하므로 나오지
        # 않아야 하고, 나온다면 그 assume 가 깨진 것이다. 그래서 **실패로 센다** —
        # 알려진 결함이 고쳐진 뒤에도 경고로 남겨 두면 회귀가 초록으로 보인다.
        403|404)
          seed_fail "배송 전이 $target — HTTP $SEED_LAST_STATUS. 운영자가 배송 건을 찾지 못했습니다"
          seed_warn "  → 테넌트 불일치를 의심하라(TASK-BE-576 이 그 모양이었다): 이 시드는 'ecommerce' 를 assume 해야 한다"
          break ;;
        *)
          seed_fail "배송 전이 실패 $target — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}" ;;
      esac
    fi
    [ "$cur" = "$final" ] && break
  done
}

if [ -n "${SHIP_ID_SHIPPED:-}" ]; then
  ship_progress "$SHIP_ID_SHIPPED" "${SHIP_STATUS_SHIPPED:-}" SHIPPED
else
  seed_log "SHIPPED 에서 멈출 배송 건이 없습니다 — 주문 하나가 SHIPPED 상태에 앉지 못합니다"
fi
if [ -n "${SHIP_ID:-}" ]; then
  ship_progress "$SHIP_ID" "${SHIP_STATUS:-}" DELIVERED
else
  seed_log "진행할 배송 건이 없습니다 — 리뷰 자격(DELIVERED)을 만들지 않습니다"
fi
SEED_TOKEN=""

# -----------------------------------------------------------------------------
# 3. 소비자 토큰 다시 — 리뷰 (배송 완료가 전제)
# -----------------------------------------------------------------------------
if [ -n "${CONSUMER_TOKEN:-}" ] && [ -n "$REVIEW_PRODUCT_ID" ] \
   && container_up ecommerce-review-service && wait_http "$GW/api/reviews/me" 180; then
  SEED_TOKEN="$CONSUMER_TOKEN"
  # 주문이 DELIVERED 로 반영될 때까지 기다린다 — 배송 완료는 이벤트로 주문에 전파된다.
  for _ in $(seq 1 24); do
    http GET "$GW/api/orders/$DELIVER_ORDER_ID" && \
      printf '%s' "$SEED_LAST_BODY" | grep -q '"status":"DELIVERED"' && break
    sleep 5
  done
  if printf '%s' "$SEED_LAST_BODY" | grep -q '"status":"DELIVERED"'; then
    api_create_unless "리뷰(${REVIEW_PRODUCT_NAME:-상품})" "$GW/api/reviews/me" "$REVIEW_PRODUCT_ID" \
      "$GW/api/reviews" \
      "{\"productId\":\"$REVIEW_PRODUCT_ID\",\"productName\":\"$REVIEW_PRODUCT_NAME\",\"rating\":5,\"title\":\"배송도 빠르고 만족합니다\",\"content\":\"주문한 다음 날 받았습니다. 상품 상태도 설명과 같았고 포장도 꼼꼼했습니다. 재구매 의사 있습니다.\"}"
  else
    seed_warn "주문 $DELIVER_ORDER_ID 가 DELIVERED 로 전파되지 않았습니다 — 리뷰를 시드하지 않았습니다(구매자 리뷰 규칙)"
  fi
  SEED_TOKEN=""
elif [ -n "$REVIEW_PRODUCT_ID" ]; then
  seed_warn "ecommerce-review-service 미기동/무응답 — 리뷰를 시드하지 않았습니다(/my/reviews · PDP 리뷰 섹션이 빕니다)"
fi

# 상품 이미지 (MinIO) — **아직 시드하지 않는다.** 조용히 건너뛰지 않기 위해 여기 남긴다.
#
# 왜 안 하는가: 이 티켓의 슬라이스에서 `ecommerce-minio` 를 띄우지 못했고(호스트 메모리
# 실측 9.2/11.7 GiB), **띄워 보지 않은 시드는 거짓 약속**이라 커밋하지 않았다.
#
# 왜 급하지 않은가: product-service 의 `V8__seed_sample_data.sql` 이 각 상품에 원격
# `thumbnailUrl` 을 심어 둔다 — 상품 카드는 **깨지지 않는다**. MinIO 이미지가 없으면
# 비는 것은 상품 상세의 추가 이미지 갤러리뿐이다.
#
# 붙일 때의 경로(전부 API): `POST /api/admin/products/{id}/images/upload-url` 로 presigned
# PUT 을 받아 바이트를 올리고, `POST /api/admin/products/{id}/images` 로 objectKey 를 등록한다
# (`RegisterImageRequest`: objectKey · sortOrder · isPrimary).
if container_up ecommerce-minio; then
  seed_warn "ecommerce-minio 가 떠 있지만 상품 이미지 시드는 아직 구현되지 않았습니다(상품 카드는 V8 시드의 원격 thumbnailUrl 로 표시됩니다)"
else
  seed_log "ecommerce-minio 미기동 — 상품 이미지 시드 대상 아님"
fi

# -----------------------------------------------------------------------------
# 4. 운영자 토큰 — 정산 기간 마감 + 지급 실행 (TASK-MONO-710)
# -----------------------------------------------------------------------------
# 적립(`commission_accrual`)은 **자동이다** — `PaymentCompleted` 를 소비할 때 쌓인다
# (`settlement-subscriptions.md`). 그래서 위에서 결제 셋을 승인한 것만으로 콘솔
# 「정산」(`/ecommerce/settlements`)의 적립 표는 찬다.
#
# 🔴 **지급(`seller_payout`)은 자동이 아니다.** 기간을 **마감**해야 그 창의 적립이
# 셀러별 payout 으로 접히고, 그때서야 `/ecommerce/settlements/periods/[id]` 가 내용을
# 갖는다. 마감은 운영자 행위이므로 시드가 운영자로 밟는다.
#
# 🔵 멱등: 두 번째 마감은 409 `PERIOD_ALREADY_CLOSED`, 지급 실행은 `(periodId, sellerId)`
# 로 멱등이라 이미 PAID 인 행은 건드리지 않는다(계약서).
if [ -n "${OP_TOKEN:-}" ] && container_up ecommerce-settlement-service; then
  SEED_TOKEN="$OP_TOKEN"

  # 적립이 도착할 때까지 기다린다 — 결제 승인과 적립 사이에 Kafka 한 홉이 있다.
  accrual_total=0
  for (( i=0; i<120; i+=5 )); do
    if http GET "$GW/api/admin/settlements/accruals?size=1"; then
      accrual_total="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"totalElements":[0-9]+' | head -1 | cut -d: -f2)"
      [ "${accrual_total:-0}" -gt 0 ] 2>/dev/null && break
    fi
    sleep 5
  done
  seed_log "정산 적립 ${accrual_total:-0} 건"

  if [ "${accrual_total:-0}" -gt 0 ] 2>/dev/null; then
    demo_period=""
    if http GET "$GW/api/admin/settlements/periods?size=50"; then
      # 🔴 기간 객체 **한 줄 안에서** id 와 상태를 같이 읽는다. 본문 전체 grep 이면
      #    다른 기간의 id 와 이 기간의 상태가 섞인 «키메라 행» 이 만들어진다(lib.sh
      #    `json_objects` 가 존재하는 바로 그 이유).
      demo_period="$(json_objects "$SEED_LAST_BODY" | grep -F '2026-03-01' | head -1)"
    fi
    if [ -z "$demo_period" ]; then
      seed_warn "데모 정산 기간(2026-03-01~)을 목록에서 찾지 못했습니다 — 지급 내역 화면이 빕니다"
    else
      period_id="$(printf '%s' "$demo_period" | grep -oE '"periodId":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
      period_status="$(printf '%s' "$demo_period" | grep -oE '"status":"[A-Z]+"' | head -1 | cut -d'"' -f4)"
      if [ -z "$period_id" ]; then
        seed_warn "정산 기간 객체에서 periodId 를 추출하지 못했습니다: ${demo_period:0:160}"
      else
        if [ "$period_status" = "OPEN" ]; then
          if http POST "$GW/api/admin/settlements/periods/$period_id/close" '{}'; then
            seed_log "정산 기간 마감 ($period_id)"
          else
            case "$SEED_LAST_STATUS" in
              409) seed_log "정산 기간 이미 마감됨 (HTTP 409)" ;;
              *)   seed_fail "정산 기간 마감 실패 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}" ;;
            esac
          fi
        else
          seed_log "정산 기간 $period_id 는 이미 $period_status — 마감을 건너뜁니다"
        fi

        # 지급 실행은 **모의**다(합성 참조번호, 실제 송금 없음 — 계약서가 명시).
        if http POST "$GW/api/admin/settlements/periods/$period_id/payouts/execute" '{}'; then
          seed_log "정산 지급 실행 (모의 · $period_id)"
        else
          seed_warn "정산 지급 실행 실패 (HTTP $SEED_LAST_STATUS) — 지급 내역이 PENDING 으로 남습니다"
        fi

        if http GET "$GW/api/admin/settlements/periods/$period_id/payouts?size=20"; then
          payout_total="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"totalElements":[0-9]+' | head -1 | cut -d: -f2)"
          if [ "${payout_total:-0}" -gt 0 ] 2>/dev/null; then
            seed_log "정산 지급 내역 ${payout_total} 건 (periodId=$period_id)"
          else
            seed_warn "정산 기간 $period_id 의 지급 내역이 0건입니다 — /ecommerce/settlements/periods/[id] 가 빕니다(적립이 이 창 밖입니까?)"
          fi
        fi
      fi
    fi
  else
    seed_warn "정산 적립이 0건입니다 — 결제가 settlement-service 까지 전파되지 않았습니다(/ecommerce/settlements 가 빕니다)"
  fi
  SEED_TOKEN=""
elif [ -n "${OP_TOKEN:-}" ]; then
  seed_warn "ecommerce-settlement-service 미기동 — 정산 적립/지급을 시드하지 않았습니다(콘솔 정산 탭이 빕니다)"
fi

# -----------------------------------------------------------------------------
# 5. 사후조건 — **경고가 아니라 단언이다** (TASK-MONO-710 AC-3)
# -----------------------------------------------------------------------------
# 🔴 이 시드가 조용히 비는 것을 무는 술어는 **여기**다. 날짜로 재지 않는다 —
# 방금 만든 것을 **다시 읽어서** 잰다.
#
# 🔴 읽는 쪽이 운영자 평면(`/api/admin/orders`)인 것이 중요하다. 구매자 평면으로 재면
# «만들어졌다» 만 증명하고 «콘솔이 본다» 는 증명하지 못한다 — TASK-BE-576 이 정확히
# 그 틈이었다(행은 `tenant_id=ecommerce`, 운영자는 `demo-corp` 를 assume). 콘솔이 읽는
# 바로 그 엔드포인트로 재야 화면이 찬다는 뜻이 된다.
#
# 🔴 하한이 «상태 5종» 이 아니라 **4종**인 이유: PENDING 은 설계상 **한시적**이다
# (`OrderStuckDetector` 가 ~35분 뒤 자동 취소). 5 를 요구하면 기존 볼륨에서 **성공이
# 고장난다** — 이 저장소가 여러 번 밟은 함정이다. 4 는 «비지 않았다» 의 바닥이지
# «정확히 몇 종인가» 라는 제품 사실이 아니다.
if [ -n "${OP_TOKEN:-}" ]; then
  SEED_TOKEN="$OP_TOKEN"

  # 배송 → 주문 되돌아오는 다리(`ShippingStatusChanged`)는 비동기다. SHIPPED 가 주문에
  # 반영될 때까지 잠깐 기다린다 — 안 기다리면 «아직 안 써짐» 을 «유실» 로 읽는다.
  for (( i=0; i<120; i+=5 )); do
    http GET "$GW/api/admin/orders?size=50" || break
    printf '%s' "$SEED_LAST_BODY" | grep -q '"status":"SHIPPED"' && break
    sleep 5
  done

  if http GET "$GW/api/admin/orders?size=50"; then
    order_total="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"totalElements":[0-9]+' | head -1 | cut -d: -f2)"
    order_states="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"status":"[A-Z_]+"' | cut -d'"' -f4 | sort -u)"
    order_state_count="$(printf '%s\n' "$order_states" | grep -c . || true)"
    seed_log "사후조건 — 운영자 평면 주문 ${order_total:-0} 건 · 서로 다른 상태 ${order_state_count:-0} 종 ($(printf '%s' "$order_states" | tr '\n' ' '))"

    if [ "${order_total:-0}" -lt 5 ] 2>/dev/null; then
      seed_fail "주문이 ${order_total:-0} 건입니다(하한 5) — /ecommerce/orders · orders/[id] 가 빕니다. 위 ✗ 줄에서 어느 단계가 끊겼는지 읽으십시오"
    fi
    if [ "${order_state_count:-0}" -lt 4 ] 2>/dev/null; then
      seed_fail "주문 상태가 ${order_state_count:-0} 종입니다(하한 4: CANCELLED·CONFIRMED·SHIPPED·DELIVERED) — 실제 관측: $(printf '%s' "$order_states" | tr '\n' ' ')"
    fi
  else
    seed_fail "사후조건: 운영자 평면 주문 목록 조회 실패 (HTTP $SEED_LAST_STATUS) — 콘솔 「주문」 탭도 같은 답을 받습니다"
  fi

  if http GET "$GW/api/shippings?size=1"; then
    shipping_total="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"totalElements":[0-9]+' | head -1 | cut -d: -f2)"
    seed_log "사후조건 — 배송 건 ${shipping_total:-0} 건"
    if [ "${shipping_total:-0}" -lt 2 ] 2>/dev/null; then
      seed_fail "배송 건이 ${shipping_total:-0} 건입니다(하한 2: SHIPPED 에서 멈춘 것 + DELIVERED 까지 간 것) — /ecommerce/shippings 가 빕니다"
    fi
  else
    seed_fail "사후조건: 배송 목록 조회 실패 (HTTP $SEED_LAST_STATUS)"
  fi

  # ---------------------------------------------------------------------------
  # 🔴🔴 테넌트 대조 — 이 사후조건이 **무엇을 증명하지 않는지** 를 출력에 적는다
  #      (TASK-MONO-718 AC-2)
  # ---------------------------------------------------------------------------
  # 위 단언들은 `OP_TOKEN`(= `operator_token ecommerce`)으로 읽는다. 즉 **시드가 쓴
  # 테넌트를 그대로 되읽는다** ⇒ 언제나 참이다. 그래서 § 2 의 주석이 적은
  # *"콘솔이 읽는 바로 그 엔드포인트로 재야 화면이 찬다는 뜻이 된다"* 는 **엔드포인트에
  # 대해서만** 맞고 **테넌트에 대해서는 공허하다.**
  #
  # 2026-09-22 데모 창이 그 대가를 실측했다 — 같은 순간·같은 URL:
  #   tenant=ecommerce → orders 5 · products 24 · users 1 · sellers 2
  #   tenant=demo-corp → 0 · 0 · 0 · 0
  # 그때 콘솔 촬영은 `demo-corp` 였고 ecommerce 14장 중 **9장이 빈 목록**이었는데,
  # 이 사후조건은 **초록이었다.**
  #
  # 🔵 그래서 고침은 «하한을 하나 더 두는 것» 이 아니라 **차이를 인쇄하는 것**이다
  #    (티켓이 허용한 두 갈래 중 뒤엣것). 숫자로 단언하지 않는 이유: 어느 테넌트가
  #    «옳은지» 는 TASK-MONO-718 의 소유자 결정이지 시드가 정할 일이 아니고,
  #    지금 0 이라는 사실은 **결함이 아니라 설계**다(TASK-BE-576).
  # 🔴 실패로 세지 않는다 — `seed_warn` 도 아니다. 이것은 **판정이 아니라 관측**이다.
  if OTHER_TOKEN="$(operator_token demo-corp 2>/dev/null)" && [ -n "${OTHER_TOKEN:-}" ]; then
    other_prev="$SEED_TOKEN"; SEED_TOKEN="$OTHER_TOKEN"
    if http GET "$GW/api/admin/orders?size=1"; then
      other_total="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"totalElements":[0-9]+' | head -1 | cut -d: -f2)"
      seed_log "테넌트 대조 — 이 시드는 «ecommerce» 로 쓰고 읽었다(주문 ${order_total:-0} 건). 같은 URL 을 «demo-corp» 로 읽으면 ${other_total:-0} 건이다."
      if [ "${other_total:-0}" = "0" ] 2>/dev/null; then
        seed_log "  ⇒ 🔵 콘솔이 «demo-corp» 로 열려 있으면 E-Commerce 화면은 **비어 보인다**(설계 — TASK-BE-576). 콘솔은 그때 테넌트 안내를 보여야 한다(TASK-MONO-718 ⓑ)."
      fi
    else
      seed_log "테넌트 대조 — «demo-corp» 조회가 HTTP $SEED_LAST_STATUS 로 답했다(대조만, 판정 아님)"
    fi
    SEED_TOKEN="$other_prev"
  else
    seed_log "테넌트 대조 — «demo-corp» 운영자 토큰을 못 받아 대조를 건너뛴다(판정 아님)"
  fi

  SEED_TOKEN=""
fi

seed_summary
