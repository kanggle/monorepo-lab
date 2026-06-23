# TASK-PC-FE-121 — 콘솔 E-Commerce 상품 운영 목록 프록시 GET 핸들러 누락 수정

- **Status**: ready
- **Project**: platform-console
- **App**: console-web (Next.js)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (single-route 누락 핸들러 추가 + 테스트)

## Goal

E-Commerce 상품 운영 화면에서 상태 필터(ON_SALE/SOLD_OUT/HIDDEN)를 선택해 "조회"하거나 페이지를 이동하면 에러가 나는 버그를 수정한다.

근본 원인: 같은-출처 프록시 라우트 `src/app/api/ecommerce/products/route.ts` 에 **GET 핸들러가 없다**(POST만 존재). 초기 화면은 서버 컴포넌트가 `initialData` 로 seed 하므로 정상 렌더되지만, 클라이언트 `useProducts` 훅이 필터/페이지 변경 시 `GET /api/ecommerce/products?status=…` 를 호출하면 Next.js 가 405 Method Not Allowed 를 반환 → 화면이 degrade/에러 처리된다. 형제 facet(`sellers`/`users`/`orders`)은 모두 GET 핸들러를 가지고 있어 products 만 누락된 상태였다.

서버측 클라이언트 함수 `listProducts({ status, categoryId, page, size })` 는 [`products-api.ts`](../../apps/console-web/src/features/ecommerce-ops/api/products-api.ts) 에 이미 존재 — 프록시 라우트 배선만 추가하면 된다.

## Scope

**In scope** (console-web only):

1. `src/app/api/ecommerce/products/route.ts` — `listProducts` 를 호출하는 `GET` 핸들러 추가. `users`/`sellers` 프록시 패턴 그대로: status/categoryId/page/size 쿼리 파싱 → `listProducts` → `NextResponse.json`, 에러는 `mapEcommerceError`.
2. `tests/unit/ecommerce-products-proxy.test.ts` — GET 핸들러 테스트 2건 추가(필터+페이지네이션 업스트림 전달 + 도메인-facing 토큰 검증; 503 degrade).

**Out of scope**: 클라이언트 훅/화면 변경(이미 정상 — GET만 누락), 다른 facet, 업스트림 product-service 변경, 신규 쿼리 파라미터.

## Acceptance Criteria

- **AC-1 — GET 동작.** `GET /api/ecommerce/products?status=HIDDEN&page=0&size=20` 가 200 + `ProductListResponse` 를 반환하고, 업스트림 `GET {ECOMMERCE_ADMIN_BASE_URL}/products?status=HIDDEN&page=0&size=20` 로 status·page·size 를 전달한다.
- **AC-2 — 인증 불변식 (§ 2.4.10).** 도메인-facing IAM OIDC 토큰을 서버측 부착(NOT operator 토큰), `X-Tenant-Id` 미전송.
- **AC-3 — 회복탄력성.** 업스트림 503/timeout/network → 503(섹션만 degrade) 패스스루.
- **AC-4 — 게이트.** 신규 포함 proxy 단위테스트 전건 GREEN(`vitest run`) + `tsc --noEmit` clean + `next lint` clean.

## Related Specs

- console-integration-contract § 2.4.10 #1 — `GET /admin/products` (paginated summaries).

## Related Contracts

- ecommerce product-service `GET /api/admin/products?categoryId&status&page&size` (FLAT 에러 봉투 `{ code, message, timestamp }`).

## Edge Cases

- 초기 페이지(status 없음, page 0)는 `useProducts` 가 `initialData` seed + `refetchOnMount:false` 라 GET 을 호출하지 않음 → 핸들러 부재로도 정상이었음(버그가 필터/페이지 변경 시에만 표출된 이유).
- `page`/`size` 미지정 시 `undefined` 전달 → `listProducts` 가 기본값(page 0 / size 20, max 100 clamp) 적용.
- status 는 producer enum(ON_SALE/SOLD_OUT/HIDDEN) 이지만 라우트는 문자열 그대로 전달 — 미래 status 도 업스트림이 판정(프록시는 tolerant).

## Failure Scenarios

- GET 누락 회귀 시 필터/페이지 조회가 405 로 깨짐 → 신규 proxy 테스트(GET 200 + 업스트림 전달 단언)가 검출.
- operator 토큰 오부착/`X-Tenant-Id` 누출 → AC-2 단언이 검출(`Authorization` 도메인-facing only, `X-Tenant-Id` undefined).
