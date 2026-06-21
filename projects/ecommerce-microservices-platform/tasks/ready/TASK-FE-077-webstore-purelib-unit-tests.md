# TASK-FE-077 — web-store 미커버 순수 lib 단위테스트 (coupon format-discount + order shipping-steps)

- **Status**: ready
- **Project**: ecommerce-microservices-platform
- **App**: web-store (Next.js)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (test-only, 동작 무변경)

## Goal

web-store 는 핵심 유틸(calculate-discount/calculate-total/mask-phone/validate-phone 등)에 좋은 단위테스트 커버리지를 갖지만, **분기 로직이 있는 순수 lib 2개가 무커버**다(테스트 파일에서 import 0건 확인). 회귀 시 *조용히 잘못 표시*되는 두 모듈에 vitest 단위테스트를 추가한다. 소스 변경 0 — **테스트만 추가**.

대상(기존 테스트 부재 확인):

1. `src/features/coupon/lib/format-discount.ts` — 쿠폰 할인 표시 포맷.
   - `formatDiscountValue` — FIXED → `"N원 할인"`(locale 그룹핑) vs PERCENTAGE → `"N% 할인"`.
   - `formatMaxDiscount` — PERCENTAGE && `maxDiscountAmount > 0` → `"최대 N원"`, 그 외 → `null`(경계: `maxDiscountAmount=0`, FIXED).
2. `src/features/order/lib/shipping-steps.ts` — 배송 단계 진행.
   - `getStepIndex` — `SHIPPING_STEPS.findIndex`(미지정 status → -1).
   - `getDeliveredDate` — status 가드(≠DELIVERED → null) + statusHistory 에서 DELIVERED 항목 `changedAt` 조회(없으면 null).

## Scope

**In scope** (web-store only, 신규 테스트 파일만):

1. `src/__tests__/format-discount.test.ts` — formatDiscountValue(FIXED/PERCENTAGE)·formatMaxDiscount(PERCENTAGE+양수 → "최대", PERCENTAGE+0 → null, FIXED → null).
2. `src/__tests__/shipping-steps.test.ts` — SHIPPING_STEPS 순서/길이, getStepIndex(4 단계 각 인덱스 + 미지정 → -1), getDeliveredDate(DELIVERED+history → changedAt, 비-DELIVERED → null, DELIVERED+history無 → null).

**Out of scope**: 소스 변경 일체, 신규 기능, 이미 커버된 모듈 재테스트.

## Acceptance Criteria

- **AC-1 — format-discount.** FIXED `discountValue=5000` → `"5,000원 할인"`; PERCENTAGE `15` → `"15% 할인"`. formatMaxDiscount: PERCENTAGE `maxDiscountAmount=3000` → `"최대 3,000원"`; PERCENTAGE `0` → `null`; FIXED → `null`.
- **AC-2 — shipping-steps.** getStepIndex: PREPARING=0, SHIPPED=1, IN_TRANSIT=2, DELIVERED=3. getDeliveredDate: status=DELIVERED + history 에 DELIVERED 항목 → 그 `changedAt`; status≠DELIVERED → null; status=DELIVERED 이나 history 에 DELIVERED 부재 → null.
- **AC-3 — 게이트.** `next lint` clean + `tsc --noEmit` clean(둘 다 로컬 검증) + CI `vitest run` GREEN(web-store 는 vitest4×Node24 로컬 미기동 → CI Node20 권위, [[env_webstore_vitest4_node24_module_evaluator]]). 소스 diff 0(테스트 파일만 추가).

## Related Specs

- `specs/contracts/http/shipping-api.md` — ShippingStatus/statusHistory 형태(shipping-steps 픽스처 근거).
- `specs/contracts/http/promotion-api.md` (or coupon) — DiscountType FIXED/PERCENTAGE + maxDiscountAmount 의미.

## Related Contracts

- 없음(소비 코드 무변경 — 순수 로직 회귀 테스트 추가만).

## Edge Cases

- `toLocaleString()` 그룹핑: Node 20+ full-ICU 기본 로케일에서 천단위 콤마(`5,000`) — CI Node20 기준 단언.
- `getStepIndex` 미지정 status → `findIndex` -1 (호출처가 -1 처리 가정; 본 테스트는 계약 고정).
- `getDeliveredDate` 는 status 가 DELIVERED 라도 history 에 DELIVERED 항목 없으면 null(둘 다 필요).

## Failure Scenarios

- 없음(런타임 동작 무변경, 소스 unchanged). 본 테스트가 미래 회귀 검출 장치.
