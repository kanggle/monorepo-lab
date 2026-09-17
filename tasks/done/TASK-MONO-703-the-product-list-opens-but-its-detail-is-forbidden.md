# Task ID

TASK-MONO-703

# Title

🔴 콘솔 E-Commerce 에서 상품 **목록은 열리는데 상세·편집만 403** 이다 — 같은 운영자 · 같은 테넌트(`ecommerce`)

# Status

done (2026-09-17 UTC — 창 판정 2026-09-17T16:55Z)

# Owner

monorepo

# Task Tags

- console
- ecommerce
- authorization
- cross-project

---

# Goal

2026-09-17 데모 창(AMI `b54296645`)에서 `scripts/capture-portfolio.mjs` 를 테넌트 `ecommerce` 로 돌렸을 때 관측했다(기록: `tasks/in-progress/TASK-MONO-648-*` § «창 실측 — 2026-09-17»).

| 경로 (운영자 `demo@demo.com`, 테넌트 `ecommerce`) | 결과 |
|---|---|
| `/ecommerce/products` (목록) | 🟢 열림 — 동적 경로 해석이 목록에서 링크를 찾았다 |
| `/ecommerce/products/b0000000-0000-0000-0000-000000000002` (상세) | 🔴 화면 `product-forbidden` · `GET /api/ecommerce/products/{id}` → **403 `{"code":"FORBIDDEN","message":"not permitted"}`** |
| `/ecommerce/products/{id}/edit` | 🔴 `denied`(같은 상품) |
| 대조군: `/ecommerce/promotions/[id]` · `/ecommerce/sellers/[id]` · `/ecommerce/users/[id]` | 🟢 전부 열림(같은 세션) |

⇒ **목록 권한과 상세 권한이 어긋난다.** 방문자(운영자)는 목록에서 상품을 눌러 «권한 없음» 을 만난다.

🔴 **어느 층이 거절했는지는 아직 모른다.** 콘솔 게이트웨이 코어가 403 메시지를 `'not permitted'` 로 **덮어쓴다**(`TASK-PC-FE-282` 기록 · `tests/unit/sample-read-only-copy.test.tsx`) — 그래서 응답 본문의 `FORBIDDEN` 은 **원래 코드가 아닐 수 있다**. 후보: ① 콘솔 route handler 의 적격 판정(`app/(console)/ecommerce/products/[id]/page.tsx` 의 `product-forbidden`) ② ecommerce 게이트웨이의 경로별 권한 ③ product-service 의 상세 엔드포인트 권한 ④ 그 상품 id 의 테넌트/판매자 소유 검사. 🔴 **추론으로 고르지 마라** — AC-0 이 층을 관측으로 지목한다.

---

# Scope

## 포함

- 403 을 낸 **층**을 관측으로 지목(원래 에러 코드까지).
- 그 층의 요구 권한 ↔ 데모 운영자가 `ecommerce` 테넌트에서 실제로 가진 권한 대조.
- 결함이면 고친다(계약/스펙 먼저). 의도라면 목록이 상세 링크를 **그리지 않게** 하거나 사유를 표시하는 쪽을 고른다(🔴 소유자 결정).

## 제외

- 촬영 스크립트의 섹션 거부 판정(`TASK-MONO-702`).
- 다른 ecommerce 동적 경로(주문 · 알림 템플릿 — 목록이 비어 해석 불가였다, 권한 문제가 아니다).

---

# Acceptance Criteria

- [x] **AC-0 — 층을 지목한다.** 콘솔 `app/api/ecommerce/products/[id]/route.ts` → 게이트웨이 코어 → ecommerce 게이트웨이 → product-service 경로를 코드로 따라가고, **덮어쓰기 전의 원래 응답**(상태 · 코드)을 어디서 볼 수 있는지 찾는다. 로컬 재현(단위/슬라이스 테스트)으로 같은 403 을 만들 수 있으면 그것이 첫 판정이다. 🔴 창에서만 볼 수 있으면 그 사실과 술어를 적고 ⚪ + 갈 곳.
- [x] **AC-1 — 요구 ↔ 보유 대조.** 상세가 요구하는 권한 키(또는 소유 검사)와, 목록이 요구하는 것을 **나란히** 적는다. 🔴 화면 문구는 키가 아니다(`TASK-MONO-676` 이 배운 것).
- [x] **AC-2 — 결함/의도 판정 → (결함이면) 고친다, (의도면) 소유자에게 표시 방식을 묻는다.** bite: 고친 뒤 되돌리면 AC-0 의 재현 테스트가 403 으로 돌아간다.
- [x] **AC-3 — 창 판정.** 테넌트 `ecommerce` 로 `/ecommerce/products` → 상품 클릭 → 상세·편집이 열리는지(또는 의도된 표시인지) 이미지로 본다. 🔴 백엔드 변경이면 AMI 재굽기 뒤 창이어야 한다. 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).

---

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` (ecommerce 프록시 · 403 처리)
- `projects/ecommerce-microservices-platform/specs/contracts/http/` — product 상세 엔드포인트 계약(AC-0 에서 파일을 특정한다)
- `tasks/done/TASK-PC-FE-282-*` — 게이트웨이 코어의 403 메시지 덮어쓰기

# Related Contracts

- ecommerce product 상세 API · 콘솔 ecommerce 프록시. 🔴 바꿔야 하면 계약 먼저.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 테넌트 `demo-corp` 에서 같은 상세 | `demo-corp` 는 상품 목록이 비어 링크가 없었다 — 대조군으로 쓸 수 없음을 적는다 |
| 특정 상품(`b0…002`)만 거부 | 다른 상품 id 로도 재서 «상품 전반» 인지 «그 행» 인지 가른다 |
| 판매자 소유 검사(운영자가 판매자가 아님) | 의도일 수 있다 — AC-2 의 소유자 결정으로 |

# Failure Scenarios

1. **응답의 `FORBIDDEN` 을 원래 코드로 믿는다** → 코어가 덮어쓴 값일 수 있다(AC-0).
2. **콘솔 적격 판정만 풀어 화면을 연다** → 백엔드가 여전히 403 이면 화면은 «불러올 수 없음» 으로 바뀔 뿐이다.
3. **한 층을 고치고 창에서 안 본다** → 층이 둘 이상 겹쳐 있을 수 있다(AC-3).

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus 5** (층 지목 + 두 프로젝트 권한 대조. 원인이 콘솔 한 곳으로 확정되면 Sonnet 5)

---

# 구현 기록 — 2026-09-17 UTC (분석=Opus 5 조사 에이전트 + 조정자 코드 대조 / 구현=Opus 5 에이전트, diff 는 조정자가 대조)

## AC-0 — 층: **ecommerce 게이트웨이 `AccountTypeEnforcementFilter`** (코드 + 기존 단위 테스트로 재현)

| 층 | 자리 | 목록 | 상세 |
|---|---|---|---|
| 콘솔 API 클라이언트 | `console-web/src/features/ecommerce-ops/api/products-api.ts:111-160`(수정 전) | `ECOMMERCE_ADMIN_BASE_URL` `/products` = `/api/admin/products` | 🔴 `ECOMMERCE_PUBLIC_BASE_URL` `/products/{id}` = **`/api/products/{id}`** (주석: *"admin controller has no GET /{id}"*) |
| 콘솔 코어 | `shared/api/ecommerce-gateway.ts` · `proxy-factory.ts` | — | 403 의 **message 만** `'not permitted'` 로 덮고 **code 는 위쪽 본문 그대로** 넘긴다(조사 에이전트 보고: `:139-151` · `:79-83`) |
| ecommerce 게이트웨이 | `gateway-service/.../filter/AccountTypeEnforcementFilter.java:80-92` · `:157-161` · `:163-167` | `/api/admin/**` → `ECOMMERCE_OPERATOR` 면 통과 | 공개 트리 → **`CUSTOMER` 만**(운영자 예외는 `/api/promotions` · `/api/shippings` · `/api/notifications` 셋) → 거부 시 `ErrorResponse.of("FORBIDDEN", "This operation requires a consumer account")` |
| product-service | `ProductController.java:45-53` · `AdminProductController` | 역할 검사 없음(인가는 게이트웨이) | 역할 검사 없음 · 상태 필터 없음 |

- **원래 응답**: 403 `{"code":"FORBIDDEN","message":"This operation requires a consumer account"}` — 창에서 본 `FORBIDDEN` 은 **덮어쓴 값이 아니라 원래 code** 였다(콘솔이 message 만 덮는다). 다른 403 후보는 code 가 다르다(테넌트 `TENANT_FORBIDDEN` · audience `AUDIENCE_FORBIDDEN` · product-service `ACCESS_DENIED`).
- **로컬 재현 = 기존 단위 테스트**: `AccountTypeEnforcementFilterTest.nonOperatorPublicPath_adminRoleOnly_returns403`(`:289-301`, 수정 전 줄) 이 `roles=[ECOMMERCE_OPERATOR]` 로 `/api/products/1` 을 부르면 403 인 것을 **의도로 고정**해 두었다. 창의 요청과 같은 (역할, 경로) 조합이다 ⇒ 첫 판정.
- 편집 화면도 같은 `getProductDetailSectionState → getProduct` 를 부른다(`products/[id]/edit/page.tsx:50`) ⇒ 같은 층. 필터는 행을 보기 전에 경로·역할로 거부하므로 `b0…002` 한 행이 아니라 **상품 상세 전반**(Edge Case 2 — 추론, 창에서 확인).
- 🔴 창에서만 확인 가능한 것: 창 토큰의 실제 `roles` claim · 다른 상품 id. AC-3 에서 본다.

## AC-1 — 요구 ↔ 보유

| | 요구 | 데모 운영자(`ecommerce` 테넌트 assume 토큰) |
|---|---|---|
| 목록 `GET /api/admin/products` | `roles ∋ ECOMMERCE_OPERATOR` | `ECOMMERCE_OPERATOR` (`seed-ecommerce.sh:139-152` · `TenantClaimTokenCustomizer` → `OperatorRoleDerivation`) → 200 |
| 상세 `GET /api/products/{id}` | `roles ∋ CUSTOMER` (Bearer 가 붙었을 때) | `CUSTOMER` **없음** → 403 |
| 대조군 promotions 상세 | 공개 트리지만 운영자 예외 목록 안 | → 열림 |
| 대조군 sellers · users 상세 | `/api/admin/**` | → 열림 |

⇒ 상품 상세만 **«공개 트리 + 예외 목록 밖»** 이다. 🔴 소유 검사(판매자)는 아니다 — 시드 행에 seller 컬럼이 없고 필터가 행을 보기 전에 거부한다.

## AC-2 — 결함 판정 → 소유자 결정 → 고침

**결함**이다(운영자가 목록에서 상세를 못 연다, 상세 화면의 variant CRUD·재고 조정 진입까지 막힌다). 고치는 갈래를 2026-09-17 UTC 선택창으로 물었다(🔵 추천 표지는 내 것). 소유자 선택(라벨 원문): **「ⓐ admin 상세 신설 (Recommended)」**
— 선택지 설명(내가 쓴 것): *product-service 에 GET /api/admin/products/{id} 추가(계약 먼저) · 콘솔 getProduct 를 admin 경로로. 게이트웨이와 범위를 고정한 기존 테스트는 그대로. 상세 화면의 CRUD 도 이미 admin 경로라 한 트리로 맞춰진다. 백엔드 변경 → 창 판정 전 AMI 재굽기.*
안 고른 것: **ⓑ 게이트웨이 예외에 `/api/products` GET 추가** — 필터의 예외는 «Admin 엔드포인트가 사는 공개 트리» 용이라 근거와 어긋나고 범위 고정 테스트를 뒤집는다 · **ⓒ 의도로 두고 표시만** — 상세·CRUD 진입이 사라진다.

**변경** (커밋 `a1cb5d834` — 15 파일):
- 계약 먼저: `ecommerce-microservices-platform/specs/contracts/http/product-api.md` § `GET /api/admin/products/{productId}` 신설(공개 상세와 같은 응답 · `ECOMMERCE_OPERATOR` · 테넌트 범위 · 404) · `platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 #2 행을 admin 경로로.
- product-service `AdminProductController#detail` — 공개 상세와 같은 `QueryProductService#findById` + `ProductImageService#getImages` → `ProductDetailResponse`. 테넌트는 형제(목록·PATCH)와 같이 게이트웨이 `X-Tenant-Id` → `TenantContext` → 저장소 `WHERE tenant_id`(컨트롤러가 헤더를 직접 안 읽는다). `/summary` 는 리터럴이 템플릿보다 우선.
- 테스트: `AdminProductControllerSliceTest` (200 · 404) · `ProductApiContractTest` · `MultiTenantIsolationIntegrationTest.operatorPlaneDetail_crossTenant_returns404_sameTenantReturns200`(🔴 로컬 Docker 없음 → **미실행, 권위=CI integration**) · 게이트웨이 `AccountTypeEnforcementFilterTest.adminProductDetail_operatorRole_passesThrough`(양성 대조군 — 기존 403 고정 칸은 **그대로**).
- console-web: `getProduct` → `ECOMMERCE_ADMIN_BASE_URL` · 샘플 방문자 픽스처(`shared/sample/fixtures/ecommerce.ts`)의 상세 경로를 admin 으로 · 주석 셋(route · product-types · env — 조정자가 env.ts diff 가 주석뿐인 것 확인) · 단위 테스트 셋(ADMIN base 고정 + 공개 경로 아님 단언 · 프록시 기대 URL · 샘플 픽스처가 공개 경로엔 200 을 안 줌).

**검증** (에이전트 실행, rc 명시):
| 명령 | rc | 결과 |
|---|---|---|
| product-service `test` + gateway `AccountTypeEnforcementFilterTest` | 0 | 380 · 21 통과 |
| console vitest (products-api · products-proxy · sample-fixtures-schema-ecommerce) | 0 | 90 통과 |
| console vitest `tests/unit/ecommerce tests/unit/sample` | 0 | 70 파일 943 통과 |
| console `tsc --noEmit` | 0 | — |

**bite**: (a) 콘솔 `getProduct` 를 PUBLIC 으로(+ 샘플 경로 되돌림) → **rc=1, 90 중 6 실패**(3 파일) · 복원 943 통과 (b) 컨트롤러 매핑을 `/{productId}/bite-disabled` 로 → **rc=1, 37 중 3 실패**(슬라이스 200 · 404 · 계약) · 복원 통과. ⇒ AC-2 의 «되돌리면 403 재현» 은 이 저장소에서 **콘솔이 다시 공개 경로를 부르는 것**으로 물린다(그 경로의 403 은 위 게이트웨이 고정 칸이 계속 보장한다).

🔵 곁발견(범위 밖, 기록만): `product-api.md` 공개 상세 JSON 예시는 `productId`/`variantId`/`imageId` 인데 DTO·계약 테스트는 `id`(+ 이미지 `objectKey`/`uploadedAt`) — 새 절이 «공개 상세와 같다» 로 그 불일치를 물려받는다. `tenant_id="*"` SUPER_ADMIN 와일드카드 GET 에서 `findById` 동작은 안 쟀다(목록과 같은 상황).

## AC-3 — ⏳ AMI 재굽기 + 창

🔴 백엔드(product-service jar) 변경이라 **이 PR 이 들어간 SHA 로 AMI 를 다시 구운 창**이어야 한다. 콘솔 쪽은 Vercel 배포. 창 술어: 테넌트 `ecommerce` 로 `/ecommerce/products` → 상품 링크 → 상세 · 편집이 열리는지(`product-forbidden` 없음) 이미지로 보고, **다른 상품 id 하나** 로도 연다(Edge Case 2). 창이 없으면 ⚪ + `TASK-MONO-672`.

---

# 🔵 창 실측 — 2026-09-17 UTC 둘째 창(시작 2026-09-17T16:34:55Z · 종료 17:21:02Z · 46분) · AMI `ami-02613b0378621b124`(RepoCommit `af0018aa6`, 12차 — 구조된 굽기, provenance operator-record) · 인스턴스 `i-07ddb6b41233f2673` · 묶음 `console console-ecommerce console-wms console-scm store fan` · 소유자 승인 «af0018aa6, 상한 100분»

이 PR 의 SHA(`af0018aa6`)로 구운 AMI 이고 콘솔은 같은 커밋의 Vercel 배포(성공). 운영자 로그인 → `POST /api/tenant` **왕복**(`demo-corp` 200 → `ecommerce` 200, 셀렉트 값 `ecommerce` 확인) → `/ecommerce/products`(상세 링크 20개) → 첫째·마지막 상품의 상세·편집.

| 상품 | 화면 | HTTP | h1 | 거부 요소 | `GET /api/ecommerce/products/{id}` |
|---|---|---|---|---|---|
| p1 = `b0000000-…-000000000002` (**지난 창에서 403 이던 바로 그 행**) | 상세 | 200 | 상품 상세 | 0 | **200** |
| p1 | 편집 | 200 | 상품 수정 | 0 | 200 |
| p2 = 목록 마지막(다른 id) | 상세 | 200 | 상품 상세 | 0 | 200 |
| p2 | 편집 | 200 | 상품 수정 | 0 | 200 |

- 🟢 이미지를 열어 확인: p1 상세에 상품명·상태·가격·옵션(variant) 표·옵션 추가·이미지·재고 조정까지 렌더 · p2 편집 폼(상품명·설명·가격·썸네일·상태) 렌더.
- 🟢 Edge Case 2: 한 행이 아니라 **상품 상세 전반**이 열린다(둘째 id 로 확인).

---

# ✅ 닫음 — 2026-09-17 UTC (4차원 검증)

- (a) impl PR [#3904](https://github.com/kanggle/monorepo-lab/pull/3904) `state=MERGED` 2026-09-17T15:21:33Z (🔵 #3903 은 같은 내용 — #3902 머지 뒤 INDEX 충돌을 rebase 로 풀고 force-push 대신 새 ref 로 대체하며 닫았다)
- (b) squash `af0018aa6` 가 `origin/main` 조상(rc=0)
- (c) 머지된 PR `statusCheckRollup` — SUCCESS 22 · SKIPPED 42 · **FAILURE 0** (ecommerce Integration A·B·C — product-service `integrationTest` 포함 — · console-bff · 프런트 단위 · E2E smoke 통과)
- (d) AC-0~AC-3 본문을 열어 읽음 — 전부 `[x]`. AC-3 «AMI 재굽기 뒤 창에서 상세·편집이 열리는지 이미지로» 는 위 표 + 이미지로 닫힘.
