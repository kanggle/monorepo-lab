# Task ID

TASK-MONO-252

# Title

ADR-MONO-031 Phase 0 — ecommerce 운영자 UI 콘솔 일원화 진입: **ADR-031 ACCEPTED 기록** + **console-integration-contract `ecommerce-ops` (product/order CRUD) 바인딩 계약 선행**(contract-first) + console-web architecture phase 노트. admin-dashboard sunset 로드맵의 doc/contract 기반을 깔고, Phase 1(product/order 실제 콘솔 CRUD 흡수)의 계약 근거를 확정한다. **cross-project doc/contract only — 구현 코드 0** (console-web/route-handler 흡수는 Phase 1 후속 task).

# Status

done

# Owner

backend

# Task Tags

- monorepo
- cross-project
- console-integration
- adr-mono-031
- adr-mono-030
- platform-console
- contract-first

---

# Dependency Markers

- **선행 (prerequisite, 전부 done)**:
  - `TASK-MONO-240` — facet (a): ecommerce 카탈로그 6번째 타일 (done).
  - `TASK-MONO-241` — facet (a-후속): ecommerce 도메인-health 카드 + `/ecommerce` 드릴인 페이지 (done). 본 태스크는 그 드릴인 페이지에 운영 표면(product/order CRUD)을 얹는 facet (a-후속-2) 의 **계약 선행**이다.
- **근거 (ADR)**: [ADR-MONO-031](../../docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md) (본 태스크가 ACCEPTED 로 전환·기록) — § 4 Phase 0; [ADR-MONO-030](../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) § 3.4 Step 4 facet a-후속-2 "rich operations surface (product/order/seller management)"; ADR-MONO-017 D2.A (console-web 단일 도메인 직접 호출, BFF write leg 없음); ADR-MONO-013 § 6 (admin-web retirement gate 선례).
- **scope 결정 (user, 2026-06-13)**: admin-dashboard **완전 삭제 + 콘솔 일원화** / **전체 6영역 풀 CRUD** / **product·order 먼저, 나머지 4영역은 백엔드 멀티테넌트와 함께 단계적** (AskUserQuestion 3회). 본 태스크 = Phase 0 (doc/contract). product/order 실제 흡수 = Phase 1 (별도 PC-FE task).
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (ADR ACCEPTED 정합 + 계약 바인딩 설계 — credential/tenant-scope/error-envelope/CRUD 엔드포인트 매핑은 Phase 1 전체의 계약 골격이라 정밀 설계 필요).

---

# Goal

ADR-MONO-031 을 ACCEPTED 로 전환·기록하고, ecommerce 운영자 CRUD 표면을 platform-console 로 흡수하기 위한 **계약 기반**을 contract-first 로 확정한다. 이번 슬라이스는 **product-service + order-service** (ADR-030 Step 2/3 로 이미 `tenant_id` 격리 완료된 2영역) 의 운영 CRUD 를 console-web 이 ecommerce 게이트웨이로 직접 호출(ADR-017 D2.A)하는 바인딩을 `console-integration-contract.md` 에 선언한다. **실제 console-web feature/route-handler/page 구현은 Phase 1 (후속 PC-FE task)** 이 본 계약을 근거로 진행한다. users/promotions/shippings/notifications 4영역은 각자의 백엔드 `tenant_id` 마이그레이션(ADR-030 Step 4) 이 선행돼야 안전하므로 본 계약에 **미포함**(Phase 2~5 에서 영역별 계약 추가).

# Scope

**platform-console 계약 + spec + 루트 ADR 만** 변경. 구현 코드(Java/TS) 0. ecommerce 도메인 코드 0.

## In scope

### A. ADR (루트, shared)

1. **`docs/adr/ADR-MONO-031-...md`**: Status PROPOSED → **ACCEPTED** (Date 라인 + 상단 블록쿼트에 ACCEPTED 기록; user-explicit "ACCEPTED 진행" intent, NOT self-ACCEPT). ※ 본 태스크 작업 중 이미 적용 — 커밋에 포함.

### B. 계약 (contract-first — Phase 1 구현 전 확정)

2. **`projects/platform-console/specs/contracts/console-integration-contract.md`** 에 **§ 2.4.9.3 (ecommerce-ops — product/order operator CRUD)** 신규 절 추가:
   - **바인딩 모델**: console-web Next.js Route Handler → ecommerce `gateway-service` admin API 직접 호출 (ADR-017 D2.A; BFF write leg 없음 — wms-outbound-ops/erp-approval 선례와 동형).
   - **credential**: 도메인-facing IAM OIDC access token (`getDomainFacingToken()`), `tenant_id ∈ {ecommerce,*}` JWT claim. `getOperatorToken()` 금지. `X-Tenant-Id` 헤더 단독 신뢰 금지 — tenant scope 는 서명된 JWT claim 이 권위(ADR-019 D5).
   - **product CRUD 엔드포인트 매핑** (백엔드 `AdminProductController` `/api/admin/products` + `AdminProductImageController`):
     - `GET /api/admin/products?categoryId&status&page&size` (목록) · `GET .../{id}` (상세, public read 경로 포함)
     - `POST /api/admin/products` (등록) · `PATCH .../{id}` · `DELETE .../{id}`
     - variants: `POST/PATCH/DELETE .../{id}/variants[/{variantId}]`
     - stock: `PATCH .../{id}/stock`
     - images: `GET/POST .../{id}/images` · `POST .../{id}/images/upload-url` (presigned S3) · `PATCH/DELETE .../{id}/images/{imageId}`
   - **order 엔드포인트 매핑** (백엔드 `AdminOrderController` `/api/admin/orders`):
     - `GET /api/admin/orders?status&page&size` · `GET .../{id}` · `POST .../{id}/status` (상태 전이)
   - **mutation 규율**: write Route Handler 는 `export const runtime='nodejs'`, Zod body parse, `makeProxyErrorMapper('ecommerce', ...)` 에러 매핑(401/403/404/409/422/503), 적절 시 idempotency/낙관적 동시성(order status). wms-outbound `ship/route.ts` 패턴 미러.
   - **error envelope**: ecommerce 게이트 에러 스키마(상품/주문 컨트롤러의 `GlobalExceptionHandler` 형태)를 명시 — 전용 파서.
   - **tenant 격리 전제**: product/order 는 `tenant_id` 격리 완료(ADR-030 Step 2/3) — 본 영역만 멀티테넌트 콘솔에 안전. 나머지 4영역은 § 2.4.9.4~ 에서 각자 백엔드 격리 후 추가(명시).
   - **eligibility**: 기존 `productKey='ecommerce'` 카탈로그 구독(available + tenants) 재사용(MONO-240) — 신규 productKey/enum 없음.
3. **§ 2.4.5/6 per-domain credential 표**: `ECOMMERCE → IAM OIDC access token` 행이 MONO-241 에서 이미 있으면 확인만; CRUD(write) 도 동일 credential 임을 1줄 명시.

### C. console-web architecture spec phase 노트

4. **`projects/platform-console/specs/services/console-web/architecture.md`** 상단 phase 노트 블록에 **ecommerce-ops (product/order) 흡수** 항목 추가 (wms-ops/scm-ops/finance-ops phase 노트와 동형 서술): facet a-후속-2, `features/ecommerce-ops`(예정), 도메인-facing 토큰, product/order CRUD, 나머지 4영역 staged 명시. § 3 IAM-parity matrix 불변(additive domain scope).

### D. admin-dashboard spec — RETIRED 마커는 Phase 6 으로 (본 태스크 미적용)

5. ecommerce `specs/services/admin-dashboard/architecture.md` RETIRED 마커 + 앱 삭제는 **D7 게이트(6영역 전부 흡수·parity 검증 후)** 라 본 태스크 범위 밖. 본 태스크는 계약 § 2.4.9.3 에 "admin-dashboard product/order 표면의 콘솔 등가물"임을 1줄 교차참조만.

## Out of scope (명시적 후속)

- **Phase 1**: product/order 실제 console-web 흡수 (`features/ecommerce-ops`, `app/api/ecommerce/products|orders/**` route handlers, `app/(console)/ecommerce/products|orders` pages, `ConsoleSidebarNav` ecommerce 그룹, `ecommerce.operator` scope) — 별 PC-FE task, 본 계약 근거.
- **Phase 2~5**: users → promotions → shippings → notifications. 각 = 백엔드 `tenant_id` 마이그레이션(ADR-030 Step 4) + 콘솔 흡수 + 계약 영역 추가. notifications phase 에서 `TemplateController` `GET /templates/{id}` 갭 처리.
- **Phase 6**: admin-dashboard 앱 삭제 + OIDC client 회수 + spec RETIRED 마커 + portfolio sync 제외 + README 노트(ADR-031 D5).

# Acceptance Criteria

- **AC-1 (ADR ACCEPTED)**: `ADR-MONO-031` Status=ACCEPTED, Date/블록쿼트에 2026-06-13 user-explicit 기록(NOT self-ACCEPT). § 4 roadmap 불변.
- **AC-2 (계약 product/order 바인딩)**: `console-integration-contract.md` § 2.4.9.3 이 product/order 운영 CRUD 를 console-web→ecommerce-gateway 직접 호출 모델로 선언; credential(도메인-facing OIDC, `getOperatorToken()` 금지), tenant-scope(JWT claim 권위), 전체 product/order 엔드포인트 매핑, mutation 규율, error envelope 명시.
- **AC-3 (격리 전제 명시)**: 계약이 "product/order 만 `tenant_id` 격리 완료 → 본 영역만 멀티테넌트 콘솔 안전; 나머지 4영역은 백엔드 격리 후 추가" 를 명시(ADR-030 M1/M6 누출 방지 근거).
- **AC-4 (console-web phase 노트)**: console-web architecture.md 에 ecommerce-ops 흡수 phase 노트 추가(타 도메인 동형); § 3 parity matrix 마커 수 불변.
- **AC-5 (구현 0 / BFF 불변)**: Java/TS 구현 코드 0; console-bff write leg 0(ADR-017 D2.A 준수 명시). 본 PR 은 doc/contract only.
- **AC-6 (Phase 경계 정직)**: 계약·task 가 product/order(now) vs 4영역(staged) vs 앱삭제(Phase 6 D7 게이트) 를 명확히 구분 — silent truncation 0.

# Related Specs / Code

- ADR: `docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md` (본 태스크가 ACCEPTED), `docs/adr/ADR-MONO-030-...md` § 3.4 Step 4.
- 계약: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5/6/9.1/9.2 → 신규 § 2.4.9.3.
- spec: `projects/platform-console/specs/services/console-web/architecture.md` (phase 노트).
- 흡수 대상 백엔드(계약 매핑 출처, 코드 불변): `projects/ecommerce-microservices-platform/apps/product-service/.../AdminProductController.java` · `AdminProductImageController.java` · `apps/order-service/.../AdminOrderController.java`.
- 흡수 패턴 선례(Phase 1 용): console-web `src/features/wms-outbound-ops/` + `src/app/api/wms/outbound/[orderId]/ship/route.ts`.
- 폐기 대상(Phase 6): `projects/ecommerce-microservices-platform/apps/admin-dashboard/`.

# Related Contracts

- `console-integration-contract.md` § 2.4.9.3 (신규, ecommerce-ops product/order CRUD 바인딩). 이벤트 계약 없음(동기 read/write 경로). API 계약은 ecommerce 백엔드 admin API(기존) 를 console-web 이 소비하는 형태 — 신규 백엔드 엔드포인트 0.

# Edge Cases / Failure Scenarios

- **tenant 누출 함정(가장 중요)**: 격리 안 된 4영역(users/promotions/shippings/notifications)을 본 계약에 넣으면 멀티테넌트 콘솔에서 cross-tenant 노출(ADR-030 M1/M6 위반). → 본 계약은 product/order(격리 완료) 만. 4영역은 백엔드 격리 PR 선행 후 영역별 계약 추가(AC-3).
- **credential 혼용 함정**: ecommerce CRUD 를 `getOperatorToken()`(IAM 전용) 로 호출하면 ADR-017 D4 HARD INVARIANT 위반. 도메인-facing OIDC token 만(계약 명시).
- **BFF write leg 유혹**: CRUD 라고 BFF 에 POST/PATCH leg 를 추가하면 ADR-017 D2.A 위반(BFF=cross-domain read 집계 전용). console-web Route Handler 직접 호출이 정합(AC-5).
- **contract-first 역전**: Phase 1 구현이 본 계약 머지 전에 시작되면 spec-first 위반. 본 태스크(계약) 머지 후 Phase 1 착수.
- **admin-dashboard 조기 삭제**: D7 parity 게이트 전 앱 삭제 시 운영자 표면 공백(저-가역성). 본 태스크는 계약만 — 삭제 0.

# Notes

- 구현 격리 worktree `monorepo-lab-ecom-console` (브랜치 `task/mono-252-ecommerce-console-consolidation`, base=origin/main `a35b9ec6c`). 메인 체크아웃 + 동시 세션 worktree(scm-repl/pc-fe-077) 미접촉.
- ⚠️ 동시 세션 TASK 카운터: 작업 중 다른 세션이 PC-FE-076 머지(#1493) 확인됨 — 머지 직전 root `tasks/` MONO 최대값 재확인(현재 251 done 기준 252).
- 머지 후 close-chore: review→done 은 Status 만, closure narrative 는 커밋 메시지에. 3-dim 검증(state=MERGED / origin tip 일치 / pre-merge 0 failing required).
- contract-first PR — doc/contract only 라 path-filter 상 code-changed 미해당 가능(경량 CI). 단 ADR/계약/spec 정합 lint 확인.
