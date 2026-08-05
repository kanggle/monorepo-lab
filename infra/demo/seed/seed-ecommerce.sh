#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed-ecommerce.sh — 스토어프런트 + 콘솔 E-Commerce 데이터 시드
# =============================================================================
# TASK-MONO-506 S3.
#
# 이 스크립트는 두 신원으로, **세 단계**로 일한다. 순서가 곧 도메인 규칙이다:
#
#   1) 소비자 토큰 (ecommerce-web-store-client)  — 프로필 · 배송지 · 위시리스트
#   2) 운영자 토큰 (platform-console-web → assume demo-corp)
#        — 셀러 · 수수료율 · 정산기간 · 알림 템플릿 · 프로모션/쿠폰 · **배송 진행**
#   3) 소비자 토큰 다시 — **리뷰**
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

  # 배송을 진행시킬 주문 하나를 고른다(리뷰 자격의 전제). 주문이 없으면 배송도 없다 —
  # 그건 결함이 아니라 "아직 아무도 사지 않았다" 이므로 경고만 남긴다.
  if http GET "$GW/api/orders?size=1"; then
    DELIVER_ORDER_ID="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"orderId":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
    [ -n "$DELIVER_ORDER_ID" ] || DELIVER_ORDER_ID="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"id":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
  fi
  if [ -n "$DELIVER_ORDER_ID" ] && http GET "$GW/api/orders/$DELIVER_ORDER_ID"; then
    REVIEW_PRODUCT_ID="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"productId":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
    REVIEW_PRODUCT_NAME="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"productName":"[^"]*"' | head -1 | cut -d'"' -f4)"
    seed_log "리뷰 대상 후보: order=$DELIVER_ORDER_ID product=${REVIEW_PRODUCT_NAME:-?}"

    # 🔴 배송 건 조회는 **여기서**, 소비자 토큰으로 한다.
    # `/api/shippings/orders/{orderId}` 는 `X-User-Id` 소유권을 검사하는 **구매자 전용**
    # 엔드포인트다 — 운영자 토큰으로 부르면 403 `ACCESS_DENIED "User does not have access to
    # this shipping record"` 다(실측). 첫 판은 이 조회를 운영자 블록에 두었고, `if` 가 거짓이
    # 되면서 **배송 진행 전체가 로그 한 줄 없이 통째로 건너뛰어졌다.** 상태 전이(PUT)는
    # 반대로 운영자 권한이 필요하므로, 조회와 전이의 신원이 서로 다르다.
    if http GET "$GW/api/shippings/orders/$DELIVER_ORDER_ID"; then
      SHIP_ID="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"shippingId":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)"
      SHIP_STATUS="$(printf '%s' "$SEED_LAST_BODY" | grep -oE '"status":"[A-Z_]+"' | head -1 | cut -d'"' -f4)"
      [ -n "$SHIP_ID" ] || seed_fail "배송 응답에서 shippingId 를 추출하지 못했습니다: ${SEED_LAST_BODY:0:160}"
    else
      seed_warn "주문 $DELIVER_ORDER_ID 의 배송 건 조회 실패 (HTTP $SEED_LAST_STATUS) — 배송 진행과 리뷰를 건너뜁니다"
    fi
  else
    seed_warn "데모 계정의 주문이 없습니다 — 배송 진행과 리뷰 시드를 건너뜁니다"
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
if [ -n "${SHIP_ID:-}" ]; then
  cur="${SHIP_STATUS:-}"
  seed_log "배송 $SHIP_ID 현재 상태=$cur → DELIVERED 까지 진행"
  for target in SHIPPED IN_TRANSIT DELIVERED; do
    [ "$cur" = "DELIVERED" ] && break
    body="{\"status\":\"$target\""
    [ "$target" = "SHIPPED" ] && body="$body,\"trackingNumber\":\"DEMO-1234567890\",\"carrier\":\"CJ대한통운\""
    body="$body}"
    if http PUT "$GW/api/shippings/$SHIP_ID/status" "$body"; then
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
  done
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

seed_summary
