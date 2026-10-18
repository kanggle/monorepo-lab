# Task ID

TASK-PC-FE-346

# Title

`ADR-MONO-083` P8 콘솔 후속 (2) — E-commerce 쓰기 버튼을 영역 `*_WRITE` 역할로 숨김 (`permission-map.ts` `writeRequires` · 계약 § 2.4.3.4 «Read-only sets hide write buttons»)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (규칙 · 데이터는 있다 — 7 화면 배선 + 시험)
> 기안 2026-10-16 UTC — `TASK-PC-FE-345` 에서 분리(그 티켓 § 범위 결정). 서버는 이미 라이브(`TASK-MONO-795`, 28차 창) — 머지 게이트 없음.

# Dependency Markers

- **선행**: `TASK-PC-FE-345`(E-commerce 메뉴 `serverRequires` · 진입 역할 가드 — 머지 뒤 착수, 같은 `permission-map.ts` 행을 고친다).
- 같은 모양: `TASK-PC-FE-342`(SCM — `ReplenishmentScreen` · `SeedConfigScreen` 의 `canWrite` · «조회 전용» 문구 · 페이지가 `canWriteOn` 으로 계산).

# Goal

`TASK-PC-FE-345` 는 E-commerce 메뉴 신호만 고쳤다. 영역 조회 세트(`ecommerce-read` · 주문 운영 세트만 등)를 가진 운영자에게 다른 영역의 쓰기 버튼이 보이고, 누르면 서버 `403` 이다. 계약 § 2.4.3.4 규칙대로 화면별 `writeRequires` 를 선언하고 버튼을 숨긴다.

# Scope

## In Scope

- 계약 먼저: § 2.4.3.4 «Declared today» 표에 E-commerce 7 화면 행 · «Scales with P5–P8» 의 P8 문장에서 «버튼 미선언» 을 지운다.
- `permission-map.ts` E-commerce 행 `writeRequires`:

  | 화면(하위 라우트 포함) | 버튼 | `writeRequires` |
  |---|---|---|
  | `/ecommerce/products` (`/new` · `/[id]` · `/[id]/edit`) | 등록 · 수정 · 삭제 · 옵션 · 재고 · 이미지 | `ECOMMERCE_PRODUCT_WRITE` |
  | `/ecommerce/sellers` (`/new` · `/[id]`) | 등록 · 활성화 · 정지 · 종료 · 구성원 초대 | `ECOMMERCE_PRODUCT_WRITE` |
  | `/ecommerce/orders` (`/[id]`) | 상태 전이 | `ECOMMERCE_ORDER_WRITE` |
  | `/ecommerce/shippings` | 상태 · 추적 갱신 | `ECOMMERCE_SHIPPING_WRITE` |
  | `/ecommerce/promotions` (`/new` · `/[id]` · `/[id]/edit`) | 등록 · 수정 · 삭제 · 쿠폰 발급 | `ECOMMERCE_PROMOTION_WRITE` |
  | `/ecommerce/settlements` (`/periods/[id]`) | 수수료율 · 기간 개시 · 마감 · 지급 실행 | `ECOMMERCE_SETTLEMENT_WRITE` |
  | `/ecommerce/notifications/templates` (`/new` · `/[id]/edit`) | 등록 · 수정 | `ECOMMERCE_NOTIFICATION_WRITE` |

- 각 화면 컴포넌트가 `canWrite` 를 받아 버튼 · «새로 만들기» 링크를 숨기고 «조회 전용» 문구를 보인다. `new` · `edit` 라우트에 직접 들어온 쓰기 권한 없는 운영자는 폼 대신 «조회 전용» 안내(서버가 어차피 403).
- 페이지는 `getDomainBusinessRoles({ ecommerce })`(`app/(console)/_lib/domain-business-roles.ts`) + `canWriteOn(href, roles)`.

## Out of Scope

- 메뉴 신호(`TASK-PC-FE-345`) · 서버 판정(795) · 회원 화면(조회만 — 버튼 없음).

# Acceptance Criteria

- [x] **AC-0** — 7 화면의 쓰기 버튼 · 링크 · 라우트 전수(컴포넌트 · testid)와 위 표를 서버(`OrderOperatorAccess` · `ProductOperatorAccess` · 각 `OperatorRoleGuard` · `TemplateController`)와 대조. e2e · smoke · federation 의 해당 testid 사용처 grep. → 아래 § AC-0 실측.
- [x] **AC-1** — 계약 개정이 코드보다 먼저. → red 커밋 `ac348f63b`(계약 § 2.4.3.4 `Declared today` E-commerce 7행 + `Scales with P5–P8` P8 «done»).
- [x] **AC-2** — 🔴 대조군: `ecommerce-read` → 7 화면 쓰기 버튼 없음 + «조회 전용» / `ecommerce-order-operate` 만 → 주문 상태 전이 버튼 보임, 다른 화면 버튼 없음 / 상품 운영 세트 → 상품 · 판매자 버튼 보임. → `ecommerce-write-buttons.test.tsx`(화면 11종 × canWrite false/true + ImageManager) · `ecommerce-write-buttons-pages.test.tsx`(실제 `resolveEcommerceCanWrite` → `getDomainBusinessRoles` → `canWriteOn` 체인: 읽기 세트 · 주문 운영 세트 · 상품 운영 세트 · 데모 운영자 13 역할 · 진입 역할 단독 · 다른 영역 역할) · `console-nav-business-permission.test.ts` § TASK-PC-FE-346(행 선언 + 같은 대조군 3종). `new`/`edit` 7 라우트는 폼 대신 «조회 전용» 안내.
- [x] **AC-3** — fail-open: row 12 실패 · 테넌트 없음 · 샘플 방문자 → 버튼 보임. → 페이지 11종 × (row 12 실패 · 활성 테넌트 없음 · 샘플 방문자 · 레지스트리 테넌트 미상) 모두 `canWrite=true`, `new`/`edit` 7종은 row 12 실패 시 폼 — `ecommerce-write-buttons-pages.test.tsx`. (`getDomainBusinessRoles` 가 `undefined` 를 돌려주고 `canWriteOn(…, undefined)` 가 `true` — 새 규칙 없음.)
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다. → 배포 상태 — 머지 후 기록.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P8
- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` § 업무 역할

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4

# Edge Cases

- 판매자 화면 = 상품 영역 역할(795 Q1).
- 정산 지급 실행에 통제 역할은 없다(795 Q2) — `ECOMMERCE_SETTLEMENT_WRITE` 하나.
- 데모 운영자(legacy-compat)는 13 역할 전부 — 데모 · nightly e2e 의 버튼은 그대로 보여야 한다.

# Failure Scenarios

1. 진입 역할(`ECOMMERCE_OPERATOR`)을 `writeRequires` 에 넣는다 — row 12 에 없어 모든 운영자의 버튼이 숨는다(345 의 가드가 막는다).
2. 버튼만 숨기고 `new` · `edit` 라우트는 연다 — 폼을 다 채운 뒤 403.
3. 선언만 하고 배선을 빠뜨린 화면 — 계약이 숨는다고 말하는 버튼이 보인다.

# AC-0 실측 (2026-10-18 UTC)

서버 정본 = `projects/ecommerce-microservices-platform/apps/*` (`TASK-MONO-795`). 콘솔 쓰기 호출 = `src/app/api/ecommerce/**/route.ts` 가 export 하는 `POST`/`PUT`/`PATCH`/`DELETE` 29개 — 아래 표의 버튼이 그 29개를 빠짐없이 덮는다(그 밖의 쓰기 호출 없음).

**결론: 서버와 티켓 표의 역할이 7 화면 모두 일치한다 — 역할 편차 0.** (아래 «표와 다른 점» 은 역할이 아니라 화면 구성에 대한 것.)

| 화면(하위 라우트) | 쓰기 컨트롤 (컴포넌트 · testid) | 역할 | 서버 근거 (file:line) |
|---|---|---|---|
| `/ecommerce/products` | `ProductsScreen` `product-new-link`; `ProductsTable` `product-edit-{i}` · `product-delete-{i}` | `ECOMMERCE_PRODUCT_WRITE` | `AdminProductController.java:196` POST · `:211` PATCH · `:225` DELETE (`ProductOperatorAccess.java:44` `requireWrite`) |
| `/ecommerce/products/[id]` | `ProductDetail` `product-detail-edit` · `product-detail-delete`; `VariantAddRow` `variant-add-row` · `variant-add-submit`; `VariantTable` `variant-editbtn-{i}` · `variant-save-{i}` · `variant-delete-{i}`; 재고 `stock-adjust-section` · `stock-adjust-open-{variantId}`; `ImageManager` `image-set-primary-{i}` · `image-delete-{i}` · `image-upload-field` | 〃 | variants `AdminProductController.java:240,257,273` · stock `:295` · images `AdminProductImageController.java:77,88,102,114` |
| `/ecommerce/products/new` · `/[id]/edit` | `ProductForm` (등록 · 수정 submit) | 〃 | 위 POST `:196` / PATCH `:211` |
| `/ecommerce/sellers` | `SellersScreen` `seller-new-link` | `ECOMMERCE_PRODUCT_WRITE` (🔵 판매자 = 상품 영역 역할 — 795 Q1) | `AdminSellerController.java:95` POST (`ProductOperatorAccess.requireWrite`) |
| `/ecommerce/sellers/[id]` | `SellerDetail` `seller-action-provision` · `seller-action-suspend` · `seller-action-close`; `SellerMembers` `seller-invite-form` · `seller-invite-email` · `seller-invite-submit` | 〃 | `AdminSellerController.java:109` provision · `:122` suspend · `:135` close; `AdminSellerMemberController.java:50` invitations |
| `/ecommerce/sellers/new` | `SellerRegisterForm` | 〃 | `AdminSellerController.java:95` |
| `/ecommerce/orders/[id]` (목록엔 쓰기 컨트롤 없음) | `OrderStatusDialog` `order-status-btn-confirmed` · `order-status-btn-cancelled` | `ECOMMERCE_ORDER_WRITE` | `AdminOrderController.java:101` POST `/{id}/status` (`OrderOperatorAccess.java:36` `requireWrite`) |
| `/ecommerce/shippings` | `ShippingsTable` `shipping-transition-{i}` (→ `ShipFormDialog` 포함) · `shipping-refresh-{i}` | `ECOMMERCE_SHIPPING_WRITE` | `ShippingCommandService.java:92` (`PUT /{id}/status`) · `RefreshTrackingService.java:44` (`POST /{id}/refresh-tracking`) (`OperatorRoleGuard.java:33`) |
| `/ecommerce/promotions` | `PromotionsScreen` `promotion-new-link`; `PromotionsTable` `promotion-delete-{i}` | `ECOMMERCE_PROMOTION_WRITE` | `PromotionCommandService.java:31` create · `:48` update · `:68` delete (`OperatorRoleGuard.java:33`) |
| `/ecommerce/promotions/[id]` | `PromotionDetail` `promotion-detail-edit` · `promotion-detail-delete` · `promotion-detail-issue` (`CouponIssueDialog`) | 〃 | 위 + `CouponCommandService.java:73` issueCoupons |
| `/ecommerce/promotions/new` · `/[id]/edit` | `PromotionForm` | 〃 | `:31` / `:48` |
| `/ecommerce/settlements` | `CommissionRateLookup` ▸ `CommissionRateForm` `settlements-rate-set-form` · `settlements-rate-set-submit`; `PeriodOpenForm` `settlements-period-open-form` · `settlements-period-open-submit`; `SettlementPeriodsTable` `period-close-{i}` | `ECOMMERCE_SETTLEMENT_WRITE` | `SettlementController.java:77` PUT commission-rates · `SettlementPeriodController.java:52` open · `:62` close (`OperatorRoleGuard.java:33`) |
| `/ecommerce/settlements/periods/[id]` | `PeriodPayoutsScreen` `settlements-payouts-execute` | 〃 (🔵 지급 실행에 통제 역할 없음 — 795 Q2) | `SettlementPeriodController.java:109` payouts/execute |
| `/ecommerce/notifications/templates` | `NotificationsScreen` `notification-new-link`; `NotificationsTable` `notification-edit-{i}` | `ECOMMERCE_NOTIFICATION_WRITE` | `TemplateController.java:76` POST · `:93` PUT (`requireWrite` `:106`) |
| `/ecommerce/notifications/templates/new` · `/[id]/edit` | `TemplateForm` | 〃 | `:76` / `:93` |

**표와 다른 점 (역할은 같고 화면 구성이 다르다 — 전부 기록):**

1. 티켓 표의 셀러 «활성화» = 화면의 «프로비저닝» 버튼(`seller-action-provision`), «종료» = «폐점»(`seller-action-close`). 같은 버튼 — 이름만 다르다.
2. `/ecommerce/orders` **목록**에는 쓰기 컨트롤이 없다(상세 링크뿐). 상태 전이는 `/ecommerce/orders/[id]` 에만 있다 ⇒ 목록 화면은 `canWrite` 를 받지 않고 «조회 전용» 도 보이지 않는다(숨긴 것이 없다). 티켓 AC-2 의 «7 화면 «조회 전용»» 은 «숨긴 컨트롤이 있는 화면» 으로 읽는다 — 주문은 상세에서 확인.
3. 상태 전이가 하나도 열려 있지 않은 주문(예: DELIVERED)은 `canWrite=false` 여도 «조회 전용» 문구를 내지 않는다(숨긴 것이 없으므로).
4. 수수료율 설정 폼은 별도 라우트가 아니라 `/ecommerce/settlements` 의 «수수료율 조회» 구역 안에 있다 — 화면 단위로 숨긴다(구역 단위 note 아님).
5. 서버에는 검색 재색인(`SearchAdminController` — `ECOMMERCE_PRODUCT_WRITE`, 795 Q1)이 있지만 **콘솔에 호출자가 없다** — 버튼 없음, 선언 없음.
6. 쓰기 권한 판정은 `X-User-Role` 만 본다 — 플랫폼 와일드카드 읽기(`X-Tenant-Id='*'`, `OrderOperatorAccess.java:30` / `ProductOperatorAccess.java:38`)는 **읽기에만** 있고 쓰기에는 없다. 활성 테넌트가 `*` 인 운영자는 레지스트리의 ecommerce 테넌트 목록에 없어 «알 수 없음» 이 아니라 «역할 0» 으로 계산된다(= 344 `/ledger` 와 같은 입력). 서버도 그 토큰에 업무 역할이 없으면 쓰기를 403 한다 — 일치.

**e2e · smoke · federation 의 해당 testid 사용처 grep (repo 전체 `*.spec.ts` · `e2e*/` · `tests/e2e/` · 스크립트 · 워크플로):** 위 쓰기 testid 를 쓰는 spec 은 **0건**. E-commerce 경로를 거는 spec 은 `e2e-smoke/legacy-demo-redirects.spec.ts:17-19`(`/demo/ecommerce → /ecommerce/orders` 리다이렉트만) · `e2e-smoke/sample-visitor-ecommerce.spec.ts:13-25`(`/ecommerce/orders` 목록의 배너 + 표 — 샘플 방문자, 쓰기 컨트롤 없음) 둘뿐이고 둘 다 영향 없다. 쓰기 testid 의 사용처는 `tests/unit/` 의 단위 시험뿐(이 티켓이 `canWrite` 를 넘기도록 고친다).

**구현 메모.** 레지스트리 테넌트는 `resolveEcommerceEligibility()` 가 이미 읽는 카탈로그에서 얻는다(`tenants` 추가 — 두 번째 호출 없음). `app/(console)/ecommerce/_write-access.ts` 의 `resolveEcommerceCanWrite(href, eligibility)` 가 `canWriteOn(href, getDomainBusinessRoles({ ecommerce: tenants }))` 를 한 곳에서 한다 — 페이지 18곳(화면 11 + 폼 7)이 같은 줄을 부른다. 화면 컴포넌트의 `canWrite` 는 **필수** prop.

# 구현 기록 (2026-10-18 UTC)

- red `ac348f63b` — 계약 + 시험 + 동작 무변경 배관. 3개 시험 파일 217건 중 **117건 단언 실패**(AssertionError 110 · TestingLibrary 요소 없음 7), `tsc` rc=0.
- green — `permission-map.ts` 7행 `writeRequires`(`ECOMMERCE_WRITE` 상수 + 서버 근거 주석) · 화면 컴포넌트 22개의 `canWrite`(**필수** prop) · 페이지 18곳 배선(`resolveEcommerceCanWrite`) · 기존 시험의 렌더 호출 25곳에 `canWrite` 추가(필수 prop 이라 `tsc` 가 잡았다) · `ecommerce-seller-detail-page.test.tsx` 의 `_eligibility` / `_write-access` 목 보강.
- 게이트: vitest **401 파일 · 4769 시험 통과** rc=0 · `tsc --noEmit` rc=0 · `next lint` rc=0 (경고 1건은 기존 `received-invitations/route.ts` 의 `_req` — 이 변경 밖).
- 🔵 가드가 없는 곳(남김): 페이지 배선은 `ecommerce-write-buttons-pages.test.tsx` 가 **열거**(`SCREEN_PAGES` 11 + `FORM_PAGES` 7)로만 보장한다 — 새 E-commerce 라우트를 더하면 이 목록에도 더해야 한다(자동 발견 아님). 필수 prop 은 화면 컴포넌트까지만 강제하고, 페이지가 `resolveEcommerceCanWrite` 를 부르는지는 이 열거 시험만 본다.
