# Task ID

TASK-PC-FE-086

# Title

platform-console **ecommerce-ops promotions absorption** — ADR-MONO-031 **Phase 3b** (console absorption of the standalone admin-dashboard promotion-management area). Add a **full-CRUD** promotions list/detail/create/update/delete + coupon-issue slice to `features/ecommerce-ops` (console-web Route Handler → ecommerce gateway `/api/promotions`), mirroring the **products** slice (TASK-PC-FE-081, the CRUD template — NOT the read-only users slice). The backend tenant isolation prerequisite landed in **TASK-BE-368** (Phase 3a).

# Status

ready

# Owner

frontend

# Task Tags

- code
- frontend
- console-web
- ecommerce-ops
- adr-031
- absorption
- crud

---

# Dependency Markers

- **선행 (prerequisite)**: **TASK-BE-368** (Phase 3a — promotion-service row-level `tenant_id` + operator-plane `/api/promotions` tenant isolation; the reads/writes this slice consumes are now tenant-scoped) + **TASK-PC-FE-081** (products slice — the exact CRUD replica template) + **TASK-MONO-252** (Phase 0 — ADR-031 ACCEPTED + `console-integration-contract.md` § 2.4.10).
- **mirror / reference**: **TASK-PC-FE-081 products slice** (`features/ecommerce-ops` types/products-api/products-state/use-ecommerce-products/ProductsScreen/ProductDetail/**ProductForm**/**ConfirmDialog**/**StockAdjustDialog** + `app/api/ecommerce/products/**` mutation route handlers (POST/PATCH/DELETE + `[id]/stock`) + `app/(console)/ecommerce/products/**` pages (list/detail/**new**/**edit**) + `_eligibility.ts` + `ConsoleSidebarNav` `상품` leaf). promotions = the **full-CRUD** twin. The coupon-issue secondary action mirrors **StockAdjustDialog** (a form-wrapped ConfirmDialog whose `reason`/`userIds` ride in the BODY).
- **blocks / 후속**: ADR-031 **Phase 4~5** (shippings → notifications) and **Phase 6** (admin-dashboard deletion, D7 gate).

# Goal

콘솔 `features/ecommerce-ops` 에 **promotions 풀-CRUD 슬라이스** 추가 — products 슬라이스 미러. admin-dashboard `promotion-management`(목록/상세/생성/수정/삭제 + 쿠폰발급)를 콘솔로 흡수. Route Handler → ecommerce gateway `/api/promotions` 직접(BFF write leg 없음, ADR-017 D2.A), 도메인-facing OIDC 토큰(`getDomainFacingToken()`, never `getOperatorToken()`), `tenant_id` 는 JWT claim(no `X-Tenant-Id`), mutation 은 confirm-gate(ConfirmDialog) + producer state-guard(409/422 inline), no `Idempotency-Key`. **SoT = `console-integration-contract.md` § 2.4.10.2(본 task 가 추가) + ecommerce `promotion-api.md` § /api/promotions.**

# Scope

## In Scope

### A. console-web `features/ecommerce-ops` promotions slice (products 미러)
- `api/types.ts` — promotion Zod 스키마 추가(기존 product/order/user 타입 옆): `PromotionSummarySchema`(promotionId/name/discountType/discountValue/issuedCount/maxIssuanceCount/startDate/endDate/status), `PromotionListSchema`(content[]/page/size/totalElements), `PromotionDetailSchema`(+description/maxDiscountAmount/createdAt/updatedAt), write body 스키마(`CreatePromotionBody`/`UpdatePromotionBody`/`IssueCouponBody{userIds:[]}`), `PROMOTION_STATUS_VALUES`(ACTIVE/SCHEDULED/ENDED), `DISCOUNT_TYPE_VALUES`(FIXED/PERCENTAGE). read 는 `.passthrough()` 관용.
- `api/promotions-api.ts` — server-side 단일 call site(products-api `callEcommerce` 패턴 복제): `listPromotions(params)`/`getPromotion(id)`(read) + `createPromotion(body)`/`updatePromotion(id,body)`/`deletePromotion(id)`/`issueCoupons(id,body)`(mutation). `getDomainFacingToken`, `ECOMMERCE_TIMEOUT_MS`, flat 에러봉투, 401/403→ApiError·503/timeout→EcommerceUnavailableError, no `X-Tenant-Id`/`Idempotency-Key`. base = `ECOMMERCE_ADMIN_BASE_URL`, path `/promotions`.
- `api/promotions-state.ts` — `getPromotionsSectionState(eligible,params)` + `getPromotionDetailSectionState(eligible,id)` (products-state 미러: 401→redirect('/login'), 403→forbidden, 404→notFound, 503→degraded).
- `hooks/use-ecommerce-promotions.ts` — `usePromotions`/`usePromotion`(query) + `useCreatePromotion`/`useUpdatePromotion`/`useDeletePromotion`/`useIssueCoupons`(mutation, list/detail invalidate). query key `['ecommerce-promotions',…]`.
- `components/PromotionsScreen.tsx`(목록+status 필터+페이지+삭제 confirm), `PromotionDetail.tsx`(상세+수정/삭제 버튼+쿠폰발급 — status별 게이팅: 수정=ENDED 아닐 때, 쿠폰발급=ACTIVE), `PromotionForm.tsx`(생성/수정 폼: name/description/discountType/discountValue/maxDiscountAmount/maxIssuanceCount/startDate/endDate + confirm-gate), `CouponIssueDialog.tsx`(StockAdjustDialog 미러 — userIds textarea + confirm; body 로 전송). `ConfirmDialog` 는 products 것 재사용.
- `index.ts` — promotions export 추가.

### B. Route Handlers (proxy)
- `app/api/ecommerce/promotions/_proxy.ts` 또는 products `_proxy` 재사용(`mapEcommerceError`/`newRequestId`/`badRequest`).
- `route.ts` — `GET` 목록(query status/page/size) + `POST` 생성(Zod body parse).
- `[id]/route.ts` — `GET` 단건 + `PATCH`(또는 producer 가 PUT 이면 PUT) 수정 + `DELETE`(204).
- `[id]/coupons/issue/route.ts` — `POST` 쿠폰발급(Zod `{userIds:[]}` parse). products `[id]/stock` 미러.
  > ⚠️ producer 계약 확인: 수정은 **PUT** `/api/promotions/{id}`(products 는 PATCH). promotions-api/route 는 producer 의 실제 메서드(PUT)에 맞춤.

### C. Pages (server component, 미러)
- `app/(console)/ecommerce/promotions/page.tsx`(목록) + `[id]/page.tsx`(상세) + `new/page.tsx`(생성) + `[id]/edit/page.tsx`(수정). products `_eligibility` 재사용(또는 promotions-local). waterfall: registryDegraded→notEligible→forbidden→degraded(→상세 notFound)→happy.

### D. Sidebar nav
- `ConsoleSidebarNav.tsx` ecommerce children 에 `{ href:'/ecommerce/promotions', label:'프로모션', testid:'nav-ecommerce-promotions' }` 추가(상품/주문/사용자 옆).

### E. 계약 + 테스트
- `console-integration-contract.md` **§ 2.4.10.2** promotions sub-binding 추가(§2.4.10 cross-cutting 상속; producer = promotion-service `PromotionController` 6 EP: 목록/단건 read + create/update/delete/issue mutation). **본 task 가 계약 먼저 갱신 후 구현.**
- vitest: promotions-api(자격증명 핀·resilience·mutation 메서드/경로·flat 에러 400/403/404/422 매핑), proxy(body parse→422·passthrough), state/스키마, nav. products 테스트 패턴 미러.

## Out of Scope

- promotion-service 백엔드 — TASK-BE-368(Phase 3a, 별도 PR).
- consumer coupon UI(`/api/coupons/me` 소비자 표면) — operator 흡수 아님.
- admin-dashboard `promotion-management` 삭제 — Phase 6(전 영역 parity 후 D7 gate).
- shippings/notifications 슬라이스 — Phase 4~5.
- OIDC scope 추가(PC-FE-081 결정 답습).

# Acceptance Criteria

- **AC-1** `features/ecommerce-ops` 에 promotions 풀-CRUD 슬라이스(api/types/state/hooks/PromotionsScreen/PromotionDetail/PromotionForm/CouponIssueDialog) — products 미러.
- **AC-2** Route Handler: `GET /api/ecommerce/promotions`(목록)·`POST`(생성)·`GET /[id]`(단건)·`PUT|PATCH /[id]`(수정, **producer 메서드에 일치**)·`DELETE /[id]`(204)·`POST /[id]/coupons/issue`(발급) — ecommerce gateway 직접, `getDomainFacingToken()`(getOperatorToken 미사용), no `X-Tenant-Id`/`Idempotency-Key`. BFF leg 0.
- **AC-3** Pages 4개(목록/상세/생성/수정) — eligibility waterfall + 상세 notFound(404 PROMOTION_NOT_FOUND). 사이드바 `프로모션` leaf.
- **AC-4** mutation = ConfirmDialog confirm-gate; producer state-guard 인라인(400 VALIDATION_ERROR / 403 ACCESS_DENIED / 404 PROMOTION_NOT_FOUND / 422 PROMOTION_ALREADY_ENDED·PROMOTION_HAS_ISSUED_COUPONS·PROMOTION_NOT_ACTIVE·COUPON_LIMIT_EXCEEDED); 수정=ENDED 아닐 때만·쿠폰발급=ACTIVE 일 때만.
- **AC-5** 계약 § 2.4.10.2 추가(§2.4.10 상속, producer 6 EP 명시).
- **AC-6** 검증 GREEN: **tsc + pnpm lint + vitest** 3종(`env_console_web_local_verify_needs_lint` — push 전 lint 필수). products/orders/users 슬라이스·BFF·백엔드 0-change.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 + **§ 2.4.10.2**(본 task)
- `projects/ecommerce-microservices-platform/specs/contracts/http/promotion-api.md` § /api/promotions(producer 계약 — create/list/detail/update/delete/issue)
- `projects/platform-console/apps/console-web/architecture.md` ecommerce-ops phase 노트

# Related Contracts

- producer = ecommerce `promotion-api.md`: `POST /api/promotions`(생성 201 {promotionId}), `GET /api/promotions?status&page&size`(목록), `GET /api/promotions/{id}`(단건), `PUT /api/promotions/{id}`(수정), `DELETE /api/promotions/{id}`(204), `POST /api/promotions/{id}/coupons/issue`({userIds:[]}→201 {issuedCount}). 에러 flat `{code,message,timestamp}`: 400/403/404 PROMOTION_NOT_FOUND/422 family. **operator-plane CRUD.**

# Edge Cases

- **producer 메서드 = PUT(수정)**: products 는 PATCH, promotion 은 **PUT** 전체교체 — promotions-api/route 가 PUT 사용. body 는 전체 필드(부분 PATCH 아님).
- **status 게이팅**: 수정 버튼 = status≠ENDED, 쿠폰발급 = status=ACTIVE. UI 가 비활성/숨김 + producer 422(PROMOTION_ALREADY_ENDED/NOT_ACTIVE) 인라인.
- **삭제 가드**: PROMOTION_HAS_ISSUED_COUPONS(422) → 발급된 쿠폰 있으면 삭제 불가 인라인.
- **404 = 빈 상태**: 단건 PROMOTION_NOT_FOUND → notFound 빈상태.
- **discountType 표시**: FIXED=₩, PERCENTAGE=% 포매팅(admin-dashboard 답습).
- **사이드바 rebase 충돌**: ecommerce children 단일 라인 추가로 최소화.
- **PII/토큰 미로깅**(§2.4.10 cross-cutting).

# Failure Scenarios

- BFF write leg 추가 → ADR-017 D2.A 위반 → Route Handler 직접만.
- `getOperatorToken()` 사용 → ecommerce 401/403 → 반드시 `getDomainFacingToken()`.
- 수정 route 를 PATCH 로 보내면 producer(PUT)가 405/미매칭 → producer 메서드 PUT 확인 후 일치.
- pnpm lint 생략 push → no-unused-vars CI 두 프런트 잡 RED → push 전 3종 필수.
- mutation 에 confirm-gate 누락 → one-click 변이(보안) → ConfirmDialog 필수.

# Notes

- 분석=Opus 4.8 / **구현 권장=Sonnet** (products 슬라이스의 CRUD mechanical replica — 필드/엔드포인트/메서드(PUT) 차이 + 쿠폰발급 secondary action). worktree=`monorepo-lab-pcfe085`(브랜치 `task/pc-fe-086-…`), console-web node_modules = main junction. 검증 3종 = `npx tsc --noEmit` + `pnpm lint` + `pnpm test`.
- ADR-031 Phase 3 = (a) BE-368 백엔드 tenant_id[PR#1529] + (b) 본 task 콘솔 CRUD 흡수. 본 task 가 (b).
