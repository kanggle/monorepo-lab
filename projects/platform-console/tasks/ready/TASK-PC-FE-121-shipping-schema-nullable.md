# TASK-PC-FE-121 — 콘솔 배송 스키마를 프로듀서 wire shape에 정렬 (userId optional, tracking/carrier nullable)

- **Status**: ready
- **Project**: platform-console
- **App**: console-web (Next.js)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (스키마 정렬 버그픽스)

## Goal

콘솔 E-Commerce > 배송(`/ecommerce/shippings`) 화면이 **배송 데이터가 1건이라도 존재하면** "배송 운영 정보를 일시적으로 불러올 수 없습니다"로 degrade 하던 버그를 고친다.

근본 원인: shipping-service 의 list/detail DTO(`ShippingSummary` / `ShippingResponse`)는 **`userId` 를 노출하지 않고**, 미설정 `trackingNumber`/`carrier` 를 **`null`(absent 아님)** 로 반환한다. 그런데 콘솔 `ShippingSummarySchema`/`ShippingSchema` 가 `userId: z.string()`(필수) + `trackingNumber/carrier: z.string().optional()`(null 불허)로 정의되어 있어, 비어있지 않은 응답에서 Zod 파싱이 실패 → `EcommerceUnavailableError` → 섹션 degrade. 빈 목록(`content: []`)에서는 파싱이 통과하므로 **데이터가 없을 때만 정상**으로 보이는 잠복 버그였다.

## Scope

**In scope** (console-web only):

1. `src/features/ecommerce-ops/api/shipping-types.ts` — `ShippingSummarySchema` 와 `ShippingSchema` 에서:
   - `userId`: `z.string()` → `z.string().optional()` (프로듀서가 미노출).
   - `trackingNumber`, `carrier`: `z.string().optional()` → `z.string().nullable().optional()` (프로듀서가 `null` 반환).

**Out of scope**: 프로듀서(shipping-service) 변경, UI 컴포넌트 변경(`ShippingsScreen` 은 `userId` 미사용 + `carrier/trackingNumber ?? '—'` 처리라 무변경), 다른 ecommerce 섹션 스키마.

## Acceptance Criteria

- **AC-1 — 데이터 렌더.** ecommerce 테넌트에 배송 행이 존재할 때 `/ecommerce/shippings` 가 degrade 없이 목록(상태 PREPARING/SHIPPED/IN_TRANSIT/DELIVERED, 운송장/택배사 `null` 포함)을 렌더한다.
- **AC-2 — 빈 목록 회귀 없음.** 배송 행이 0건이면 종전대로 빈 상태로 정상 렌더(파싱 실패 없음).
- **AC-3 — 게이트.** 기존 shipping 단위테스트(api/state/proxy/nav, 47건) GREEN + `tsc --noEmit` clean + `next lint` clean. CI `vitest run` GREEN.

## Related Specs

- `console-integration-contract.md` § 2.4.10.3 (ecommerce shipping operator surface) — 배송 list/detail 응답 형태(userId 미포함, nullable tracking/carrier)의 계약 근거.

## Related Contracts

- shipping-service: `ShippingSummary` (application/result) + `ShippingResponse` (interfaces/rest/dto/response) — 둘 다 `userId` 없음, `trackingNumber`/`carrier` nullable.

## Edge Cases

- `trackingNumber: null` (PREPARING 행) — `.nullable()` 로 수용, UI 는 `?? '—'`.
- 프로듀서가 향후 `userId` 를 추가해도 `.optional()` 이므로 호환(여전히 파싱 통과).
- `.passthrough()` 유지 — 응답의 추가 필드(updatedAt 등)는 그대로 통과.

## Failure Scenarios

- 스키마가 다시 엄격(userId 필수/non-null tracking)해지면 데이터 존재 시 degrade 회귀 → AC-1 이 검출.
