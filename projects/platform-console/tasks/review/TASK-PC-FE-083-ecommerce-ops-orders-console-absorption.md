# Task ID

TASK-PC-FE-083

# Title

ADR-MONO-031 Phase 1b(orders) — console-web `features/ecommerce-ops` **orders** 흡수: ecommerce 주문 운영(목록/상세/상태전이)을 platform-console `/ecommerce/orders` 로 가져온다. console-web Route Handler → ecommerce gateway 직접 호출(ADR-017 D2.A, BFF write leg 없음), 도메인-facing IAM OIDC 토큰, `ConsoleSidebarNav` ecommerce 그룹에 `주문` leaf 추가. products(PC-FE-081) 패턴 미러 + 주문 상태머신(허용 전이 게이트 + 400/422/409 inline). **image(presigned) 는 후속 facet**(PC-FE-082).

# Status

review

# Owner

frontend

# Task Tags

- platform-console
- console-integration
- adr-mono-031
- ecommerce
- console-web

---

# Dependency Markers

- **선행 (prerequisite, done)**:
  - `TASK-MONO-252` — ADR-031 ACCEPTED + 계약 § 2.4.10 (product/order CRUD 바인딩). 본 태스크가 그 계약의 consumer-side(**orders #15-17**) 구현.
  - `TASK-BE-366` — ecommerce 백엔드 `AdminOrderController` operator-plane authz(read+write `validateAdminRole` 제거). **본 흡수의 차단 해소** — 콘솔 OPERATOR 가 주문 read + 상태전이 호출 가능.
  - `TASK-PC-FE-081` — products 흡수(`features/ecommerce-ops` 골격: `_proxy.ts`, `products-api.ts` 의 `callEcommerce`/`parseEcommerceError` 패턴, eligibility, sidebar ecommerce 그룹). 본 태스크가 그 feature 에 orders 를 **나란히** 추가(같은 feature, 같은 auth/credential 모델 재사용).
- **근거 (ADR/계약)**: [ADR-MONO-031](../../../../docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md) Phase 1b; `console-integration-contract.md` § 2.4.10 (#15 list / #16 detail / #17 change status).
- **선례 (미러)**: 본 feature 의 products 슬라이스(`products-api.ts` / `products-state.ts` / `use-ecommerce-products.ts` / `app/api/ecommerce/products/**` / `app/(console)/ecommerce/products/**`) — orders 는 동형 미러.
- **model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (확립된 products 패턴의 반복 미러 + 작은 상태머신; PC-FE-081 task 의 "orders/image 반복은 Sonnet" 가이드).

---

# Goal

ecommerce 주문 운영을 platform-console 안에서 수행할 수 있게 한다(admin-dashboard orders 화면의 콘솔 등가물). console-web 이 ecommerce gateway 의 order admin API(`/api/admin/orders/**`)를 **Route Handler 로 직접 호출**(ADR-017 D2.A)하고, 도메인-facing IAM OIDC 토큰(`getDomainFacingToken()`)으로 인증하며, tenant scope 는 JWT claim 이 권위(X-Tenant-Id 헤더 없음). 범위 = 주문 **목록(status 필터/페이지) / 상세(아이템·배송지) / 상태전이**(CONFIRMED/SHIPPED/DELIVERED/CANCELLED). UI 는 현재 status 기준 **허용 전이만** 노출하고, producer state guard 거부(400/422/409)를 actionable inline 으로 표면화한다.

# Scope

**platform-console console-web 내부만**. ecommerce 백엔드 0-change(BE-366 으로 이미 operator-plane). BFF 0-change(write leg 없음, ADR-017 D2.A). products 슬라이스 파일 0-change(orders 파일만 추가; 공유 `_proxy.ts` 의 mapper/`newRequestId` 재사용은 import 만).

## In scope

### A. feature (`src/features/ecommerce-ops/`)
- `api/orders-api.ts` — server 전용 ecommerce gateway 클라이언트(목록/상세/상태전이). base URL `ECOMMERCE_ADMIN_BASE_URL`. 도메인-facing 토큰. `products-api.ts` 의 `callEcommerce`/`parseEcommerceError` 와 **동일한 단일 하드닝 호출부 패턴**을 재사용(공유 헬퍼로 추출하거나 orders-api 안에 동형 재현 — 단, products-api 를 수정하지 말 것; 추출 시 새 `api/ecommerce-client.ts` 로 두고 products-api 는 그대로 두는 방식은 PR 크기 키우므로 **orders-api 안에 동형 재현 권장**).
- `api/order-types.ts` — Zod 스키마 + TS 타입. producer DTO 정합(`AdminOrderListResponse`/`AdminOrderDetailResponse`/`AdminOrderStatusChangeRequest`/`AdminOrderStatusChangeResponse` — 아래 § 부록 형상). read 는 `.passthrough()` 관용. `ORDER_STATUS_VALUES`(PENDING/CONFIRMED/SHIPPED/DELIVERED/CANCELLED/STUCK_RECOVERY_FAILED) + **허용 전이 맵**(`allowedTransitions(status)`).
- `api/orders-state.ts` — server section state(eligibility + degrade/forbidden/notFound 분기), products-state 동형. `getOrdersSectionState(eligible, params)` + `getOrderDetailSectionState(eligible, id)`.
- `hooks/use-ecommerce-orders.ts` — TanStack Query 훅(list/detail) + `useChangeOrderStatus` mutation, `invalidateQueries`(list + detail).
- `components/` — `OrdersScreen`(목록+status 필터+페이지), `OrderDetail`(상세: 아이템·금액·배송지+상태전이 액션), `OrderStatusDialog`(confirm-gated 상태전이; 허용 전이만 버튼 노출). 기존 `ConfirmDialog` 재사용.
- `index.ts` — orders export 추가(기존 products export 유지).

### B. Route Handlers (`src/app/api/ecommerce/orders/`)
- `route.ts` — (선택) read proxy 또는 server-component 직접 fetch. products 는 list/detail 을 server-state 로 읽고 client refetch 는 proxy GET 으로 함 → 동형: read 도 proxy GET route 제공(`hooks` 의 client refetch 용).
- `[id]/route.ts` — `GET`(detail refetch, client hook 용).
- `[id]/status/route.ts` — `POST`(상태전이). `runtime='nodejs'`, Zod body parse(`{status}`), 도메인-facing 토큰, 에러 매핑(공유 `mapEcommerceError`). **Idempotency-Key 미부착**(producer 미정의).
- list/detail read 는 server component(`orders-state.ts`)에서 직접 fetch; client refetch 는 위 GET proxy.

### C. Pages (`src/app/(console)/ecommerce/orders/`)
- `page.tsx`(목록), `[id]/page.tsx`(상세). server component, eligibility pre-flight(`resolveEcommerceEligibility()` 재사용 — `_eligibility.ts` 는 products 디렉터리에 있으니 orders 디렉터리에서 import 경로 조정 또는 공유 위치로 두되 products 파일 미수정). degrade/not-eligible/forbidden/notFound 분기(products page 패턴 미러). 기존 `/ecommerce` 섹션 페이지에 orders 진입 링크 추가(선택).

### D. Nav
- `src/shared/ui/ConsoleSidebarNav.tsx` — ecommerce `children` 에 `주문` leaf 추가: `{ href: '/ecommerce/orders', label: '주문', testid: 'nav-ecommerce-orders' }` (기존 운영/상품 child 뒤). 주석의 "Orders/image are later facets" 문구 갱신.

## Out of scope (후속 facet)
- **product image (presigned upload, #11-14)** — PC-FE-082.
- users/promotions/shippings/notifications — Phase 2~5(백엔드 tenant_id 선행).
- admin-dashboard 삭제 — Phase 6.
- 환불(refund)/STUCK_RECOVERY 운영 액션 — 본 계약 § 2.4.10 #17 은 status 전이만(CONFIRMED/SHIPPED/DELIVERED/CANCELLED). refund 는 별도 facet.

# Acceptance Criteria

- **AC-1**: `/ecommerce/orders` 목록(status 필터/페이지) + `[id]` 상세(아이템·금액·배송지) 렌더(server component, eligibility+degrade 분기); 타 도메인 섹션과 동형 안전(일시 장애 시 섹션만 degrade, 셸 유지).
- **AC-2**: 상태전이가 Route Handler → ecommerce gateway `POST /api/admin/orders/{id}/status` 직접 호출로 동작. 도메인-facing OIDC 토큰, `getOperatorToken()` 미사용(테스트로 핀), X-Tenant-Id 헤더 미부착(JWT claim 권위).
- **AC-3**: UI 가 현재 status 기준 **허용 전이만** 노출(PENDING→{CONFIRMED,CANCELLED}, CONFIRMED→{SHIPPED,CANCELLED}, SHIPPED→{DELIVERED}, DELIVERED/CANCELLED/STUCK_RECOVERY_FAILED=전이없음). 상태전이 confirm-gated. producer 거부(잘못된 전진 **400**, 취소불가 **422**, 낙관락 **409**, not-found 404)를 actionable inline 으로 표면화(crash 금지, 성공 시 detail+list invalidate). Idempotency-Key 미부착.
- **AC-4**: `ConsoleSidebarNav` ecommerce 그룹에 `주문` leaf(`nav-ecommerce-orders`) 추가. 기존 운영/상품 leaf testid/href 불변.
- **AC-5**: BFF 0-change(write leg 없음); ecommerce 백엔드 0-change; **products 슬라이스 파일 0-change**(orders 파일만 추가).
- **AC-6 (빌드)**: console-web `pnpm test`(vitest) + `pnpm lint` + `pnpm build`(tsc) GREEN. 신규 컴포넌트/route/hook/api 단위 테스트(products 슬라이스 테스트 수준 — 허용 전이 맵, getOperatorToken 미호출 핀, 400/422/409 매핑, eligibility 분기).
- **AC-7**: § 3 IAM-parity matrix 불변(additive domain scope, 카운트 16 유지). 계약 § 2.4.10 본문 추가 변경 없음(이미 #15-17 바인딩 존재).

# Related Specs / Code

- 계약: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 (#15 list / #16 detail / #17 change status).
- spec: `projects/platform-console/specs/services/console-web/architecture.md` (ecommerce-ops phase 노트 — 이미 존재, 변경 불필요).
- 선례(미러, 같은 feature): `src/features/ecommerce-ops/api/{products-api,products-state,types}.ts`, `hooks/use-ecommerce-products.ts`, `src/app/api/ecommerce/products/**`, `src/app/(console)/ecommerce/products/**`.
- 백엔드(소비, 0-change): ecommerce `AdminOrderController` `/api/admin/orders/**`(BE-366 operator-plane), DTO `AdminOrder{List,Detail,StatusChange{Request,Response}}Response`, `OrderStatus` enum, `GlobalExceptionHandler`(order-service).
- nav: `src/shared/ui/ConsoleSidebarNav.tsx`(ecommerce children).

# Related Contracts

- `console-integration-contract.md` § 2.4.10 (#15-17 order endpoints). 신규 백엔드 계약 0(기존 admin API 소비).

# Edge Cases / Failure Scenarios

- **credential 혼용**: `getOperatorToken()` 사용 시 ADR-017 D4 위반 → 반드시 `getDomainFacingToken()`. 테스트로 핀.
- **BFF write leg 유혹**: 상태전이를 BFF 로 라우팅하면 ADR-017 D2.A 위반 → Route Handler 직접.
- **허용되지 않은 전이 노출**: UI 가 terminal/불가 전이 버튼을 노출하면 안 됨(허용 전이 맵 기준). producer 가 최종 권위이므로 거부 시 inline.
- **400 vs 422 구분**: 잘못된 전진(예 PENDING→SHIPPED)=`InvalidOrderException`→**400**; 취소불가(SHIPPED/DELIVERED→CANCELLED)=`OrderCannotBeCancelledException`→**422**. 둘 다 inline actionable, 메시지 구분.
- **멱등 no-op**: 같은 target 재요청(예 이미 CONFIRMED 인데 CONFIRMED)은 producer 가 200 no-op → confirm-gate 로 충분, 별도 처리 불요.
- **Idempotency 가짜 부착**: producer 미정의 → confirm-gate + state guard.
- **eligibility/degrade**: catalog/gateway 일시 장애 시 `/ecommerce/orders` 가 콘솔 셸 깨면 안 됨 — 섹션만 degrade.
- **tenant 누출**: X-Tenant-Id 헤더 수동 부착 금지(JWT claim 권위) — order 는 tenant_id 격리(BE side).
- **producer DTO 불일치**: 응답 형태가 DTO 와 어긋나면 read `.passthrough()` 관용으로 미래 필드 무시; 미지 status 는 generic string 으로 렌더(throw 금지).

# Notes

## 부록 — producer DTO 형상(소비, 검증완료 2026-06-14)

- **#15 list** `GET /admin/orders?status&page&size` → `AdminOrderListResponse`:
  `{ content: [{ orderId, userId, status, totalPrice(long), itemCount(int), firstItemName, createdAt(Instant) }], page, size, totalElements(long) }`
- **#16 detail** `GET /admin/orders/{id}` → `AdminOrderDetailResponse`:
  `{ orderId, userId, status, totalPrice(long), items: [{ productId, variantId, productName, optionName, quantity, unitPrice, sellerId }], shippingAddress: { recipient, phone, zipCode, address1, address2 }, createdAt, updatedAt }`
- **#17 status** `POST /admin/orders/{id}/status` body `{ status: <NotBlank> }` → `{ orderId, status }`
- **에러 봉투**: flat `{ code, message, timestamp }`(product-service 와 동일 공유 `ErrorResponse`). order-service `GlobalExceptionHandler` 매핑: OrderNotFound→404, InvalidOrder(잘못된 전진)→400, OrderCannotBeCancelled→422, InvalidOrderStatus(미지 status)→400, 낙관락→409.
- **상태머신**(`Order.java` 확인): confirm(PENDING→CONFIRMED), ship(CONFIRMED→SHIPPED), deliver(SHIPPED→DELIVERED), cancel((PENDING|CONFIRMED)→CANCELLED). confirm/ship/deliver 는 이미 target 이면 no-op 200.

## 운영 메모

- 격리 worktree `.wt/pc-fe-083`(브랜치 `task/pc-fe-083-ecommerce-ops-orders-console`, base=origin/main `2516a857a`). 메인 체크아웃(다른 세션 pc-fe-079 점유) 미접촉.
- ⚠️ 동시 세션 활발. 머지 직전 root/platform-console `tasks/` PC-FE 최대값 재확인.
- 머지 후 close-chore: review→done Status 만, narrative 는 커밋. 3-dim 검증.
