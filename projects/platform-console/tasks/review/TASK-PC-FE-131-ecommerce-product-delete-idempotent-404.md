# TASK-PC-FE-131 — 이미 삭제된 상품을 다시 삭제하면 "상품을 삭제하지 못했습니다" 가 뜨는 버그 (DELETE 404 비멱등)

- **Status**: review
- **Project**: platform-console
- **Service**: console-web
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (프록시 라우트 단순 멱등 매핑)

## Goal

콘솔 E-Commerce > 상품(products)에서 **이미 삭제된 상품을 다시 삭제하면** 백엔드가 `404 PRODUCT_NOT_FOUND` 를 반환하고, UI 가 이를 하드 실패("상품을 삭제하지 못했습니다." / "상품 삭제 실패")로 노출하는 버그를 고친다. 삭제의 목표 상태(상품이 카탈로그에서 사라짐)는 이미 충족됐으므로, **삭제는 멱등(idempotent)하게 — 404 를 성공으로 — 처리**해 운영자에게 혼란스러운 실패를 보여주지 않는다.

근본 원인 (런타임 진단 2026-06-23):

- 같은 상품에 대한 두 번의 `DELETE /admin/products/{id}` 가 관측됨 — 1차는 `200`(soft-delete 성공, `deleted_at` 기록), ~43초 뒤 2차는 같은 ID 에 대해 `404`. 백엔드 `DeleteProductService.delete()` 는 `findById(...).orElseThrow(ProductNotFoundException)` → `GlobalExceptionHandler` 가 `404 PRODUCT_NOT_FOUND` 로 매핑한다(이미 soft-deleted 된 행은 `findById`/`findByFilters` 의 `deleted_at IS NULL` 필터에 안 잡힘 → 재삭제는 항상 404).
- console-web 프록시 `DELETE /api/ecommerce/products/[id]` (`app/api/ecommerce/products/[id]/route.ts`) 는 upstream 404 를 그대로 통과시켜 `mapEcommerceError` 로 매핑 → `useDeleteProduct` 의 `onError` → `messageForCode(code, '상품을 삭제하지 못했습니다.')` 가 노출된다.
- 1차 삭제가 느리게(콜드 Kafka 토픽 첫 발행 대기로 ~13초) 응답해 화면이 멈춘 듯 보였고, 운영자가 같은 상품 삭제를 재시도 → 404 가 실패로 표시된 것이 사용자 신고 증상이다. (콜드 토픽 latency 자체는 환경 요인이며 본 task 범위 밖 — 아래 Out of scope.)

멱등 삭제는 계약 § 2.4 line 59("Operator mutating actions MUST be idempotent on the domain side; the console … renders the result") 와 `PROJECT.md` § Out of Scope("쓰기 작업은 각 도메인 API에 위임하며 콘솔은 **멱등 호출 + 결과 표시**만") 의 명시 원칙과 정합한다. 이는 **consumer(콘솔) 측 결과 렌더링** 변경이며 producer 계약/동작은 바뀌지 않는다.

## Scope

**In scope** (console-web only):

1. `src/app/api/ecommerce/products/[id]/route.ts` — `DELETE` 핸들러에서 upstream 에러가 `ApiError && status === 404` (`PRODUCT_NOT_FOUND`)이면 `mapEcommerceError` 로 404 를 전파하는 대신 **`204 No Content`** 를 반환한다(멱등 삭제). 그 외 모든 에러(401/403/409/422/503/timeout)는 **현재 매핑 그대로 유지**.
2. `tests/unit/ecommerce-products-proxy.test.ts` — 회귀 테스트 추가: (a) `deleteProduct` 가 `ApiError(404,'PRODUCT_NOT_FOUND')` 를 throw → 핸들러가 `204` 반환, (b) 정상 삭제(`deleteProduct` resolve) → `204` 반환(기존 동작 보존), (c) 503/기타 에러 → 여전히 `mapEcommerceError` 경로(실패 응답) 유지.

**Out of scope**:

- 백엔드(product-service) — `DELETE` 의 404 동작은 정상이고 producer 계약 불변(§ 5 Change Rule). 멱등성은 콘솔 seam 에서만 처리.
- 변형(variant) 삭제(#8, `404 VARIANT_NOT_FOUND`) · 이미지 삭제(#14) · 프로모션 삭제 등 형제 delete facet — 동일 멱등 원칙이 적용 가능하나 본 task 는 **상품 삭제(#5)** 에 한정한다(신고 증상). 일관성 후속은 별도 task 후보.
- 콜드 Kafka 토픽 첫 발행 시 동기 publish 가 트랜잭션 경로를 ~13초 블록하는 latency — 환경/아키텍처 사안(outbox 비동기화)으로 별개. 토픽 생성 후엔 빠르며 재발하지 않음.
- UI mutation(`useDeleteProduct`) · `ProductsScreen`/`ProductDetail` 변경 불필요 — 프록시가 204 를 주면 `onSuccess` → 목록 invalidate 가 이미 올바르게 동작(목록은 `deleted_at IS NULL` 로 삭제분을 안 보여줌).

## Acceptance Criteria

- **AC-1 — 멱등 삭제.** 이미 삭제된(또는 존재하지 않는) 상품에 대한 프록시 `DELETE /api/ecommerce/products/[id]` 가 upstream `404 PRODUCT_NOT_FOUND` 를 받으면 **`204`** 를 반환한다(클라이언트 `onSuccess` 경로 → 실패 다이얼로그 안 뜸).
- **AC-2 — 정상 삭제 보존.** 살아있는 상품 삭제(upstream 204)는 그대로 `204` 를 반환한다.
- **AC-3 — 그 외 에러 보존.** 401/403/409/422/503/timeout 등 비-404 에러는 기존 `mapEcommerceError` 매핑(상태·코드·degrade 시맨틱)을 그대로 유지한다(회귀 없음).
- **AC-4 — 게이트.** console-web `pnpm lint` + `pnpm tsc --noEmit` + `pnpm vitest run` GREEN(신규 회귀 테스트 포함).

## Related Specs

- `console-integration-contract.md` § 2.4 (line 59) — operator mutating actions 멱등 원칙("the console … renders the result").
- `console-integration-contract.md` § 2.4.10 — ecommerce product/order operator CRUD; #5 `DELETE /admin/products/{id}` (mutation, confirm-gated, FLAT 에러 봉투, `Idempotency-Key` 없음 → confirm-gate + producer state guards 의존).
- `PROJECT.md` § Out of Scope(transactional 미선언) — "콘솔은 멱등 호출 + 결과 표시만".
- TASK-PC-FE-081 (ADR-MONO-031 Phase 1a) — ecommerce products console 흡수(이 프록시 라우트의 출처).

## Related Contracts

- ecommerce `product-service` `DELETE /api/admin/products/{id}` — 성공 `204 No Content`; 대상 없음 → `404 PRODUCT_NOT_FOUND` (FLAT 봉투 `{code, message, timestamp}`, `GlobalExceptionHandler.handleProductNotFound`). soft-delete 이므로 재삭제는 항상 404.
- console-web `callEcommerce`(`ecommerce-client.ts`) — 비-2xx 4xx 는 `throw new ApiError(res.status, e.code, e.message, e.timestamp)`; 즉 404 는 `ApiError(404, 'PRODUCT_NOT_FOUND', …)` 로 표면화된다(핸들러 분기 기준).

## Edge Cases

- **존재한 적 없는 임의 ID** 삭제도 `404 PRODUCT_NOT_FOUND` → 동일하게 `204`(멱등; 목표 상태=부재 충족). 잘못된 ID 마스킹 우려는 삭제 시맨틱에선 수용 가능(ID 는 목록/상세에서 선택됨, 자유 입력 아님).
- **404 가 PRODUCT_NOT_FOUND 가 아닌 다른 코드**로 올 경우: 본 surface 의 `DELETE /admin/products/{id}` 404 는 `PRODUCT_NOT_FOUND` 단일(handler 확인됨). 방어적으로 `status===404` 기준으로 204 매핑하되, 코드 기반(`PRODUCT_NOT_FOUND`)으로 좁혀도 무방 — 구현 시 둘 중 명시.
- **변형/이미지 삭제**는 본 task 가 건드리지 않음 → 여전히 404 전파(의도된 범위 한정; 후속 task 여지).
- 503/timeout(인프라 degrade)은 멱등 대상 아님 → `EcommerceUnavailableError` 로 ecommerce 섹션만 degrade(보존).

## Failure Scenarios

- 멱등 매핑을 status 가 아닌 잘못된 조건(예: 메시지 문자열)으로 분기하면 깨지기 쉬움 → `ApiError.status === 404`(+ 선택적으로 `code==='PRODUCT_NOT_FOUND'`) 로 고정하고 테스트로 핀.
- 404→204 매핑이 비-404 경로까지 삼키면 실패가 조용히 사라짐(503 degrade·403 권한 거부 은폐) → AC-3 + 회귀 테스트로 비-404 매핑 보존을 단언.
- 형제 facet(variant/image/promotion) 에 같은 비멱등 404 함정이 남아 있음 — 동일 증상 재현 시 본 task 패턴(프록시 404→204)을 해당 핸들러에 확장하는 후속 task 로 처리.
