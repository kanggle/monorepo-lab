# TASK-PC-FE-132 — 상품 상세 클라이언트 리페치가 405 로 깨지는 버그 (detail GET 프록시 라우트 누락)

- **Status**: review
- **Project**: platform-console
- **Service**: console-web
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (누락 프록시 GET 라우트 1개 추가 + 회귀 테스트)

## Goal

콘솔 E-Commerce > 상품 상세에서 클라이언트(`useProduct`)의 상세 리페치가 `GET /api/ecommerce/products/{id}` 를 호출하는데, 해당 프록시 라우트 핸들러(`app/api/ecommerce/products/[id]/route.ts`)가 `PATCH`·`DELETE` 만 export 하고 **`GET` 핸들러가 없어 Next.js 가 `405 Method Not Allowed` 를 반환**하는 버그를 고친다. detail GET 프록시 핸들러를 추가해 마운트-시·mutation-성공-후 리페치가 정상 동작하게 한다.

근본 원인 (런타임 진단 2026-06-25):

- 사용자 신고 증상: 상품 상세에서 재고 조정(증가) 후 화면이 갱신되지 않음 — 관측된 에러는 `405 Method Not Allowed http://localhost:3000/api/ecommerce/products/{id}` (경로에 `/stock` 없음).
- 재고 조정 PATCH 자체는 `/api/ecommerce/products/{id}/stock` (핸들러 존재) 로 가서 **백엔드 증가는 성공**한다. 405 는 그 다음 단계 — `useAdjustStock` 의 `onSuccess` → `invalidate(qc, productId)` 가 detail 쿼리를 무효화 → `useProduct` 가 `GET /api/ecommerce/products/{id}` 로 리페치하면서 발생한다.
- `useProduct(product.id, product)` 는 `staleTime: 0` + `READ_QUERY_REFETCH`(refetchOnMount) 라 SSR 로 `initialData` 가 시드돼도 **상세 페이지 진입 직후에도** 같은 GET 리페치를 한 번 발사 → 진입만 해도 405 가 난다.
- `app/api/ecommerce/products/[id]/route.ts` 는 `PATCH`(update)·`DELETE`(delete) 만 export. 컬렉션 라우트(`products/route.ts`) 에는 `GET`(list) 가 있으나 detail 라우트에는 누락 → 라우트는 매칭되지만 메서드 미지원 → `405`. (동일 "프록시 라우트 누락" 버그 클래스: PC-FE-121/123.)
- 서버 측 read 함수 `getProduct(id)` 는 이미 `products-api.ts` 에 존재(public detail path, `ECOMMERCE_PUBLIC_BASE_URL`, contract row #2). SSR 경로(`getProductDetailSectionState`)가 이미 이를 사용하므로 프록시 GET 은 같은 함수를 재사용하면 된다.

## Scope

**In scope** (console-web only):

1. `src/app/api/ecommerce/products/[id]/route.ts` — `GET` 핸들러 추가. `getProduct(id)` 를 호출해 `ProductDetail` JSON 을 반환하고, 에러는 컬렉션 라우트와 동일하게 `mapEcommerceError(err, requestId)` 로 매핑(404 PRODUCT_NOT_FOUND / 503 degrade / 401 등). `getProduct` import 추가.
2. `tests/unit/ecommerce-products-proxy.test.ts` — 회귀 테스트 추가: (a) `getProduct` resolve → `GET` 핸들러가 `200` + 상세 JSON 반환, upstream 이 `GET http://ecommerce.local/api/products/{id}`(public base) 로 도메인-페이싱 토큰 부착해 호출됨, (b) upstream `404 PRODUCT_NOT_FOUND` → `404` 매핑 보존, (c) `503` → `503`(섹션 degrade) 보존.

**Out of scope**:

- 백엔드(product-service) — admin controller 에 `GET /{id}` 가 없는 것은 설계대로(상세 read 는 public path); producer 계약 불변. 콘솔 프록시 seam 만 보강.
- `useProduct`/`ProductDetail`/`useAdjustStock` 클라이언트 코드 변경 불필요 — 프록시가 GET 을 200 으로 응답하면 기존 리페치·invalidate 가 올바르게 동작.
- 형제 detail 라우트(variant 개별 GET 등) — 본 surface 는 상세 단건 GET 만 누락. variant 개별 read 는 상세 응답에 임베드돼 별도 GET 불필요.

## Acceptance Criteria

- **AC-1 — detail GET 동작.** `GET /api/ecommerce/products/{id}` 프록시가 `getProduct(id)` 결과를 `200` + `ProductDetail` JSON 으로 반환한다(405 제거). upstream 호출은 도메인-페이싱 IAM 토큰 부착, `X-Tenant-Id` 없음.
- **AC-2 — 에러 매핑.** upstream `404 PRODUCT_NOT_FOUND` → `404`, `503` → `503`(ecommerce 섹션 degrade), `401`(세션 부재) → 호출 전 차단 등 `mapEcommerceError` 매핑이 컬렉션 라우트와 동일하게 적용된다.
- **AC-3 — 회귀 없음.** 기존 `PATCH`(update)·`DELETE`(delete, 멱등 404→204 포함) 핸들러는 변경 없이 그대로 동작한다.
- **AC-4 — 게이트.** console-web `pnpm lint` + `pnpm tsc --noEmit` + `pnpm vitest run` GREEN(신규 회귀 테스트 포함).

## Related Specs

- `console-integration-contract.md` § 2.4.10 — ecommerce product operator CRUD; row #2 `GET /products/{id}`(detail, public read path, FLAT 에러 봉투). 콘솔 same-origin 프록시 원칙(§ 2.3 / § 2.4).
- `PROJECT.md` § Out of Scope — 콘솔은 도메인 API 로의 위임 + 결과 표시(read 프록시 포함).
- TASK-PC-FE-081 (ADR-MONO-031 Phase 1a) — ecommerce products console 흡수(이 프록시 라우트 + `useProduct`/`getProduct` 의 출처).

## Related Contracts

- ecommerce `product-service` `GET /api/products/{id}`(public) — 성공 `200` `ProductDetailResponse`(variants[] + images[]); 대상 없음 → `404 PRODUCT_NOT_FOUND`(FLAT 봉투 `{code, message, timestamp}`).
- console-web `getProduct`(`products-api.ts`) — `callEcommerce` 로 `ECOMMERCE_PUBLIC_BASE_URL` 에 GET, 비-2xx 4xx 는 `ApiError` 로 표면화 → 프록시 GET 의 `mapEcommerceError` 분기 기준.
- console-web `useProduct`(`use-ecommerce-products.ts`) — `apiClient.get('/api/ecommerce/products/{id}')` 로 same-origin 프록시 호출(마운트 리페치 + mutation invalidate).

## Edge Cases

- **존재하지 않는 ID 직접 진입**(잘못된 URL) → upstream `404 PRODUCT_NOT_FOUND` → 프록시 `404` → SSR 단계에서 이미 not-found 처리되므로 클라이언트 리페치 404 는 기존 read-query 에러 경로(상세는 `initialData` 폴백) 와 정합.
- **503/timeout**(인프라 degrade) → `EcommerceUnavailableError` → ecommerce 섹션만 degrade(보존). detail GET 도 동일.
- **public vs admin base** — read 는 `ECOMMERCE_PUBLIC_BASE_URL`(admin 컨트롤러에 GET /{id} 없음). 테스트로 upstream URL 이 `…/api/products/{id}`(admin 아님) 임을 핀.

## Failure Scenarios

- GET 핸들러를 admin base(`ECOMMERCE_ADMIN_BASE_URL`)로 잘못 배선하면 upstream 404(admin 에 GET /{id} 부재) → `getProduct` 재사용으로 회피(이미 public base). 테스트로 upstream path 단언.
- 405 가 mutation 성공 후 리페치에서만 나는 게 아니라 **상세 진입 마운트 리페치에서도** 발생 — 회귀 테스트는 핸들러 존재(메서드 매칭) + 200 응답으로 두 경로 모두 커버.
- detail GET 이 비-404/비-503 에러를 잘못 삼키면 실패가 조용히 사라짐 → `mapEcommerceError` 단일 경로로 매핑해 컬렉션 라우트와 동일 시맨틱 유지.
