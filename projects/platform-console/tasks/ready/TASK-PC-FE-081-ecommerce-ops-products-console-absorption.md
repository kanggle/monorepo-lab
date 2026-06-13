# Task ID

TASK-PC-FE-081

# Title

ADR-MONO-031 Phase 1b — console-web `features/ecommerce-ops` **products** 흡수: ecommerce 상품 운영 CRUD(목록/상세/등록/수정/삭제 + variant inline + stock 조정)를 platform-console `/ecommerce/products` 로 가져온다. console-web Route Handler → ecommerce gateway 직접 호출(ADR-017 D2.A, BFF write leg 없음), 도메인-facing IAM OIDC 토큰, `ConsoleSidebarNav` ecommerce 그룹 + `ecommerce.operator` scope. **image(presigned) + orders 는 후속 facet**(PC-FE-082/083).

# Status

ready

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
  - `TASK-MONO-252` — ADR-031 ACCEPTED + 계약 § 2.4.10 (product/order CRUD 바인딩). 본 태스크가 그 계약의 consumer-side(products) 구현.
  - `TASK-BE-366` — ecommerce 백엔드 product admin 컨트롤러 operator-plane authz (validateAdminRole 제거). **본 흡수의 차단 해소** — 콘솔 OPERATOR 가 이제 product write 호출 가능.
  - `TASK-MONO-241` — `/ecommerce` 드릴인 페이지(health 카드) 존재. 본 태스크가 그 섹션에 products 운영 표면을 추가.
- **근거 (ADR/계약)**: [ADR-MONO-031](../../../../docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md) Phase 1b; `console-integration-contract.md` § 2.4.10 (#1-9 product endpoints).
- **선례 (미러)**: `features/wms-outbound-ops` (read+write feature) + `app/api/wms/outbound/[orderId]/ship/route.ts` (write Route Handler).
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (첫 ecommerce write 슬라이스 — feature 구조 + Route Handler mutation + auth scope + nav 설계; 이후 orders/image 반복은 Sonnet).

---

# Goal

ecommerce 상품 운영을 platform-console 안에서 수행할 수 있게 한다(admin-dashboard products 화면의 콘솔 등가물). console-web 이 ecommerce gateway 의 product admin API(`/api/admin/products/**`)를 **Route Handler 로 직접 호출**(ADR-017 D2.A)하고, 도메인-facing IAM OIDC 토큰(`getDomainFacingToken()`)으로 인증하며, tenant scope 는 JWT claim 이 권위(X-Tenant-Id 헤더 없음). v1 범위 = 상품 **목록/상세/등록/수정/삭제 + variant inline CRUD + stock 조정**. image(presigned, #11-14) 와 orders 는 후속.

# Scope

**platform-console console-web 내부만**. ecommerce 백엔드 0-change(BE-366 으로 이미 operator-plane). BFF 0-change(write leg 없음, ADR-017 D2.A).

## In scope

### A. feature (`src/features/ecommerce-ops/`)
- `api/products-api.ts` — server 전용 ecommerce gateway 클라이언트(목록/상세/등록/수정/삭제/variant/stock). base URL `ECOMMERCE_ADMIN_BASE_URL` (계약 § 2.4.10; 기존 ecommerce snapshot leg 의 base URL/host 재사용). 도메인-facing 토큰.
- `api/types.ts` — Zod 스키마 + TS 타입(Product/Variant/Stock + request bodies). ecommerce producer DTO 형태 정합(`AdminProductController` request/response — register/update/variant/stock).
- `api/errors.ts` 또는 공유 — `makeProxyErrorMapper('ecommerce', ...)` (401→재로그인/403/404/409/422/503).
- `components/` — `ProductsScreen`(목록+필터), `ProductDetail`(상세+variant+stock), `ProductForm`(등록/수정), `VariantEditor`(inline), `StockAdjustDialog`(confirm-gated). wms-outbound `OutboundOpsScreen`/`OutboundActionDialog` 패턴 미러. 공유 UI(DataTable 등 console-web 기존 shared) 재사용.
- `hooks/use-ecommerce-products.ts` — TanStack Query 훅(list/detail) + mutation(create/update/delete/variant/stock), `invalidateQueries`.
- `index.ts`.

### B. Route Handlers (`src/app/api/ecommerce/products/`)
- `route.ts` — `POST` (register). `runtime='nodejs'`, Zod body parse, 도메인-facing 토큰, 에러 매핑.
- `[id]/route.ts` — `PATCH`(update), `DELETE`.
- `[id]/variants/route.ts` — `POST`; `[id]/variants/[variantId]/route.ts` — `PATCH`, `DELETE`.
- `[id]/stock/route.ts` — `PATCH` (confirm-gated, stock adjust).
- read(list/detail)는 server component 에서 직접 fetch 또는 read route handler — 기존 도메인 섹션 패턴 따름.
- mutation 규율: confirm-gate, **Idempotency-Key/version 없음**(producer 미정의 — BE 확인됨; producer state guard 의존, 409/422 actionable surface).

### C. Pages (`src/app/(console)/ecommerce/products/`)
- `page.tsx` (목록), `[id]/page.tsx` (상세), `[id]/edit/page.tsx`, `new/page.tsx`. server component 우선, eligibility pre-flight(`getCatalog()` → `productKey='ecommerce'` available+tenants), degrade/not-eligible/forbidden 분기(기존 `/ecommerce/page.tsx` 패턴). 기존 `/ecommerce` 섹션 페이지에서 products 로 진입 링크 추가.

### D. Nav + Auth
- `src/shared/ui/ConsoleSidebarNav.tsx` — `GROUPS` 에 ecommerce `NavParent`(`/ecommerce`) + `products` `NavLeaf`(`/ecommerce/products`) 추가(도메인 운영 그룹).
- `ecommerce.operator` OIDC scope — console OIDC client scope 에 추가(다른 도메인 scope 패턴). 도메인-facing 토큰이 ecommerce 게이트에서 통하도록(BE-366 은 account_type=OPERATOR 만 보지만, scope 정합 유지).

## Out of scope (후속 facet)
- **product image (presigned upload, #11-14)** — XHR direct-to-S3 복잡 컴포넌트. PC-FE-082(또는 본 facet 후속).
- **orders 흡수** (계약 § 2.4.10 #15-17) — PC-FE-083.
- users/promotions/shippings/notifications — Phase 2~5(백엔드 tenant_id 선행).
- admin-dashboard 삭제 — Phase 6.

# Acceptance Criteria

- **AC-1**: `/ecommerce/products` 목록/상세 렌더(server component, eligibility+degrade 분기); 타 도메인 섹션과 동형 안전(일시 장애 시 섹션만 degrade).
- **AC-2**: 등록/수정/삭제 + variant inline + stock 조정이 Route Handler → ecommerce gateway `/api/admin/products/**` 직접 호출로 동작. 도메인-facing OIDC 토큰, `getOperatorToken()` 미사용, X-Tenant-Id 헤더 미부착(JWT claim 권위).
- **AC-3**: mutation confirm-gated; 409/422(producer state guard) actionable surface; Idempotency-Key 미부착(producer 미정의).
- **AC-4**: `ConsoleSidebarNav` ecommerce 그룹 + products leaf; `ecommerce.operator` scope 추가.
- **AC-5**: BFF 0-change(write leg 없음); ecommerce 백엔드 0-change.
- **AC-6 (빌드)**: console-web `pnpm test`(vitest) + `pnpm lint` + `pnpm build`(tsc) GREEN. 신규 컴포넌트/route/hook 단위 테스트(기존 wms-outbound 테스트 수준).
- **AC-7**: § 3 IAM-parity matrix 불변(additive domain scope).

# Related Specs / Code

- 계약: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10.
- spec: `projects/platform-console/specs/services/console-web/architecture.md` (ecommerce-ops phase 노트).
- 선례: `src/features/wms-outbound-ops/**`, `src/app/api/wms/outbound/[orderId]/ship/route.ts`, `src/app/(console)/ecommerce/page.tsx`(섹션 패턴), `src/app/(console)/scm/page.tsx`.
- 백엔드(소비, 0-change): ecommerce `AdminProductController` `/api/admin/products/**`(BE-366 operator-plane).
- nav: `src/shared/ui/ConsoleSidebarNav.tsx`. registry: `src/shared/api/registry-types.ts`(`ecommerce` productKey 존재).

# Related Contracts

- `console-integration-contract.md` § 2.4.10 (#1-9 product endpoints). 신규 백엔드 계약 0(기존 admin API 소비).

# Edge Cases / Failure Scenarios

- **credential 혼용**: `getOperatorToken()` 사용 시 ADR-017 D4 위반 → 반드시 `getDomainFacingToken()`.
- **BFF write leg 유혹**: CRUD 를 BFF 로 라우팅하면 ADR-017 D2.A 위반 → Route Handler 직접.
- **Idempotency 가짜 부착**: producer 가 무시하는 Idempotency-Key 부착 금지 → confirm-gate + state guard(409/422).
- **eligibility/degrade**: catalog/gateway 일시 장애 시 `/ecommerce/products` 가 콘솔 셸 깨면 안 됨 — 섹션만 degrade.
- **tenant 누출**: X-Tenant-Id 헤더 수동 부착 금지(JWT claim 권위) — product 는 tenant_id 격리(BE side) 이므로 토큰만 정확하면 자동 격리.
- **producer DTO 불일치**: register/update/variant/stock request body 형태가 `AdminProductController` DTO 와 어긋나면 422 — 실제 DTO(RegisterProductRequest/UpdateProductRequest/AddVariantRequest/AdjustStockRequest) 확인 후 Zod 정합.

# Notes

- 격리 worktree `monorepo-lab-ecom-pcfe081` (브랜치 `task/pc-fe-081-ecommerce-ops-products`, base=origin/main `234824002`). 메인 체크아웃(다른 세션 pc-fe-079 점유) + 타 worktree(pc-fe-080) 미접촉.
- ⚠️ 동시 세션 활발(pc-fe-079/080/081 동시). 머지 직전 root/platform-console `tasks/` PC-FE 최대값 재확인(현재 080 기준 081).
- image(presigned)/orders 후속 분리 — 본 PR 관리 크기 유지.
- 머지 후 close-chore: review→done Status 만, narrative 는 커밋. 3-dim 검증.
