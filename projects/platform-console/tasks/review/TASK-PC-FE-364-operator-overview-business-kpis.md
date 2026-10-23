# Task ID

TASK-PC-FE-364

# Title

운영자 통합 개요(`/dashboards/overview`)의 카드 여섯을 «행 개수» 에서 **업무 지표**로 — 잠긴 계정 · 미출고 주문 · 제출 대기 발주 · 미해소 대사 차이 · 내 결재 대기 · 출고 대기 주문 (카드당 호출 1개 그대로, 1:1 교체)

# Status

review

# Owner

frontend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (계약 § 2.4.9.1 표 · 계약 시험 고정값 · 카드 스키마 · 샘플 · 권한별 저하가 함께 바뀜)
> 기안 2026-10-22 UTC — 소유자 질문 «전체개요 · 도메인 개요가 대시보드 느낌인가» 의 2단계. 후보 측정(읽기 전용 에이전트, 이 세션) 뒤 **소유자 결정 2026-10-22 KST**(선택창 — 넷은 추천 그대로, IAM · WMS 는 추천을 알리고 진행).

# Dependency Markers

- 선행 없음. 같은 날 1단계 = `TASK-PC-FE-363`(도메인 개요 모양 맞추기 — 다른 파일). 둘 다 `tasks/INDEX.md` 를 고치므로 **직렬 머지**.
- 🔴 «카드당 호출 1개» 는 기존 소유자 결정이다(계약 § 2.4.9.1 의 두 번째 호출 기각 기록 — `console-integration-contract.md` 3020-3044 근처). 이 티켓은 **교체만** 하고 호출을 늘리지 않는다.

# Goal

운영 익명 샘플 캡처(2026-10-22 UTC)에서 통합 개요의 카드 여섯은 «회원 계정 · 재고 행 · 스냅샷 행 · 잔액 있음 · 활성 부서 · 상품 수» — 운영자가 처음 볼 «할 일 · 쌓인 것 · 문제» 가 아니다. 소유자 결정:

| 도메인 | 지금 | 바꿀 지표 | 출처(기존 목록 API · `size=1` 의 `totalElements`) | 측정 메모(읽기 전용 에이전트 · 메인 세션 확인 포함) |
|---|---|---|---|---|
| IAM | 회원 계정 수 | **잠긴 계정 수** | `GET /api/admin/accounts?status=LOCKED&tenantId=…` (`searchAccounts`) | 같은 API · 같은 권한(`account.read`). 계정 수준 잠금만 센다(사이트 소속 잠금은 제외 — `AccountJpaRepository.java:119`) ⇒ 라벨에 «계정» |
| WMS | 재고 행 수 | **미출고 주문 수** | `GET /dashboard/orders?status=RECEIVED` (`listOrders`) | 읽기 모델이 상태를 셋으로 접지만 «받았고 아직 출하 · 취소 안 됨» 의 뜻으로는 참(`OrderSummaryEntity.java:123-135`) · 테넌트 격리 · 같은 권한 |
| SCM | 재고 스냅샷 행 수 | **제출 대기 발주(초안)** | `GET /api/v1/procurement/po?status=DRAFT` (`listPurchaseOrders`) | 쓰기 모델 상태 기계 · 🔴 테넌트가 호출자 기준인지 **추론** — AC-0 이 확인 · 권한 `SCM_PROCUREMENT_READ`(지금 카드와 다름). 보충 제안은 테넌트가 `"scm"` 고정(`SuggestionController.java:39`)이라 탈락 |
| Finance | 기본계좌 잔액 있음/없음 | **미해소 대사 차이** | `GET /api/finance/ledger/reconciliation/discrepancies?status=OPEN` (`listDiscrepancies`) | 테넌트 범위 · 진짜 `totalElements` · **기본계좌 선행 조건이 사라진다**(지금 카드는 기본계좌 없는 운영자에게 «권한 없음») · 권한 `FINANCE_LEDGER_READ`(다름) |
| ERP | 활성 부서 수 | **내 결재 대기** | `GET /api/erp/approval/inbox` (`listApprovalInbox`) | 호출 1개로 `SUBMITTED` + `IN_REVIEW` · 🔴 운영자 계정이 직원과 연결 안 돼 있으면 빈 결과에 `meta.actorEmployeeId` 없음(`ApprovalInboxController.java:45`) ⇒ **0 이 아니라 «직원 연결 없음»** 을 그린다 · 권한 `ERP_APPROVAL_READ`(다름) |
| E-commerce | 상품 수 | **출고 대기 주문**(결제 완료 · 배송 전 = `CONFIRMED`) | `GET /api/admin/orders?status=CONFIRMED` (`listOrders`) | 쓰기 모델 상태 · 테넌트 격리 · 권한 `ECOMMERCE_ORDER_READ`(다름). 🔴 `STUCK_RECOVERY_FAILED` 는 기록되는 곳이 없어 늘 0(`Order.java:341` 호출자 0) — 쓰지 않는다 |

# Scope

## In Scope

- **AC-0 재확인** (file:line — 측정 보고를 그대로 믿지 않는다): ① 표의 여섯 출처가 지금도 위와 같은가 · SCM 발주 목록의 테넌트 범위(호출자 기준인가 고정인가 — 고정이면 STOP 하고 소유자에게) ② 각 카드의 지금 자격 증명(IAM = 운영자 토큰 · 나머지 = 도메인용 IAM OIDC 토큰)으로 새 엔드포인트가 통과하는가 · 업무 권한 세트(ADR-MONO-083)별로 «권한 없음» 이 새로 생기는 운영자가 누구인가(표로) ③ 계약 § 2.4.9.1 의 생산자 표 · ok 데이터 모양 표 · `specs/contracts/fixtures/operator-overview-leg-bodies.json` · `leg-body-contract.test.tsx` · `console-composition.test.ts` · `shared/sample/fixtures/dashboards.ts` · 카드 스키마(`features/operator-overview`) 의 바꿀 자리 전수 ④ 샘플 고정값이 각 필터를 지키는가(측정: IAM · WMS · SCM · Finance · ERP 는 지킴, E-commerce 는 상태 필터는 지키나 행 구성 미확인).
- **계약 먼저**(별도 커밋): § 2.4.9.1 생산자 표 · 데이터 모양 표 · 고정값 JSON.
- 구현: 카드 여섯의 읽기 · 스키마 · 라벨 · 링크(카드를 누르면 그 할 일 화면 — 예 잠긴 계정 → `/accounts?status=LOCKED`, 미출고 → `/wms/outbound` 등, 있으면) · ERP «직원 연결 없음» 상태 · 샘플. 카드마다 독립 저하 규칙 그대로(403 → 권한 없음 · 그 밖 → 점검 필요 · 401 → 전체 재로그인).
- 시험: 카드마다 ok · 0 · 403 · 저하 · (ERP) 연결 없음 · 계약 시험 고정값 · 호출 수(카드당 1, 총합 불변) · 샘플.

## Out of Scope

- 카드에 두 번째 숫자 · 호출 추가(기존 결정 — 다시 열려면 소유자 결정).
- 서버 변경 · 새 생산자 엔드포인트(ADR-MONO-017).
- 도메인 개요들(1단계 `TASK-PC-FE-363`).

# Acceptance Criteria

- [x] **AC-0** — 위 ①~④. ① SCM 테넌트가 고정이면 STOP. → 아래 `## AC-0 측정` (SCM 은 호출자 기준 — STOP 아님)
- [x] **AC-1** — 계약 먼저 → 구현 + **사용자에게 보이는 새 문구(카드 제목 · 설명 · «직원 연결 없음» · 링크 글자) 목록과 «권한 없음» 이 새로 생기는 운영자 표를 소유자에게 보이고 승인** 받은 뒤 머지.
- [x] **AC-2** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`, 마지막 전체 실행은 최종 커밋에서) · `tsc --noEmit` · `next lint` · Client graph 가드 둘 · 큐 가드 셋(스테이지 뒤) rc=0. SSR 이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인 · 로그인 운영자 라이브(권한별 «권한 없음» 표가 맞는가) = 다음 데모 창.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` (생산자 `/summary` 기각 — 이 티켓은 해당 없음)
- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` (업무 권한 세트 — AC-0 ②)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 (갱신 대상)
- `projects/platform-console/specs/contracts/fixtures/operator-overview-leg-bodies.json` (갱신 대상)
- 각 도메인 계약(읽기만): iam admin-api · wms admin-service-api § 1.3 · scm procurement-api · finance ledger-api · erp approval-api · ecommerce order-api

# Edge Cases

- 업무 권한 세트가 좁은 운영자: 지금 보이던 카드가 «권한 없음» 으로 바뀔 수 있다 — AC-1 의 표로 소유자가 본다.
- ERP 직원 연결 없음 vs 결재 0 건 — 다른 상태로 그린다.
- WMS 테넌트 축 없는 운영자(네이티브 `wms` 평면) — 미출고 주문이 비어 보일 수 있음(계약 § 1.0) — 빈 값이 오류처럼 보이지 않게.

# Failure Scenarios

1. 측정 보고를 그대로 믿고 SCM 테넌트 고정을 놓친다 → AC-0 ① STOP.
2. ERP 연결 없음을 0 으로 그려 «할 일 없음» 으로 읽힌다 → 별도 상태 + 시험.
3. 계약 시험 고정값을 안 바꿔 CI 가 빨개지거나, 고정값만 바꾸고 계약 표를 안 바꿔 계약이 거짓이 된다 → AC-0 ③ 전수.

---

## AC-0 측정 (2026-10-22 UTC)

> 구현 에이전트(Opus 5.5)가 생산자 코드를 직접 읽어 다시 쟀다. 경로는 리포 루트 기준. 측정 보고와 다른 곳은 🔴 **정정**으로 적었다.

### ① 여섯 출처 — 지금도 표와 같은가

| 카드 | 확인한 곳 (file:line) | 결과 |
|---|---|---|
| IAM 잠긴 계정 | `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/presentation/AccountAdminController.java:72-79`(`status` 파라미터) · `:69-70`(허용값 `ACTIVE/LOCKED/DORMANT/DELETED`) · `:107`(목록 = `account.read`) · `:118-119`(`tenantId` 쿼리로 범위) · `:133`(`listAll(…, normalizedStatus)`); `projects/iam-platform/apps/account-service/src/main/java/com/example/account/infrastructure/persistence/AccountJpaRepository.java:118-125` | 표와 같다. 거르는 칸은 **`a.status`(계정)** 이고 사이트 소속 상태(`m.status IN (ACTIVE, LOCKED)`, `:124`)는 «소속이냐» 만 정한다 ⇒ 사이트에서만 잠긴 풀 계정은 안 센다. 라벨에 «계정» 유지 |
| WMS 미출고 주문 | `projects/wms-platform/apps/admin-service/src/main/java/com/wms/admin/api/dashboard/OrderDashboardController.java:28-29`(경로 · `hasRole('WMS_VIEWER')`) · `:57-61`(`readScopeProvider.current().tenantFilter()`); `…/readmodel/outbound/OrderSummaryEntity.java:123`(`RECEIVED`) · `:128`(`CANCELLED`) · `:133`(`SHIPPED`); 지금 카드 `…/InventoryDashboardController.java:20` 도 같은 `WMS_VIEWER` | 표와 같다. 읽기 모델 상태는 셋뿐이고 `RECEIVED` = 받았고 아직 출하·취소 안 됨. 권한 게이트가 지금 카드와 **글자 그대로 같다** |
| SCM 제출 대기 발주 | `projects/scm-platform/apps/procurement-service/src/main/java/com/example/scmplatform/procurement/presentation/controller/PurchaseOrderController.java:117-127` → `…/application/PurchaseOrderApplicationService.java:433-438`(`poRepository.search(actor.tenantId(), …)`) → `…/infrastructure/persistence/jpa/PurchaseOrderJpaRepository.java:23`(`WHERE p.tenantId = :tenantId`); `actor.tenantId()` 출처 = `…/infrastructure/security/SecurityConfig.java:86-87`(`ActorContextJwtAuthenticationConverter`) → `libs/java-security-servlet/src/main/java/com/example/security/servlet/actor/ActorClaims.java:71`(JWT `tenant_id` 클레임); 권한 `SecurityConfig.java:75-76`(GET `/api/procurement/**` = `SCM_PROCUREMENT_READ ∨ _WRITE`) | ✅ **테넌트는 호출자 기준(JWT `tenant_id`) — 고정 아님 ⇒ STOP 아님.** 측정 보고의 «추론» 을 코드로 확정. 보충 제안 고정값 `projects/scm-platform/apps/demand-planning-service/src/main/java/com/example/scmplatform/demandplanning/adapter/inbound/web/controller/SuggestionController.java:39`(`TENANT_ID = "scm"`)도 그대로 — 탈락 맞음 |
| Finance 미해소 대사 차이 | `projects/finance-platform/apps/ledger-service/src/main/java/com/example/finance/ledger/presentation/controller/ReconciliationController.java:100-118`(`status` · `actor.tenantId()` · `meta.totalElements` `:115`); `…/domain/reconciliation/DiscrepancyStatus.java:10`(`OPEN`); 권한 `…/infrastructure/security/SecurityConfig.java:68-70, 89-90, 115` | 표와 같다(테넌트 범위 · 진짜 `totalElements` · 기본계좌 불필요). 🔴 **정정(보탬)**: 이 읽기는 `FINANCE_BASE_URL`(account-service) 이 아니라 **`LEDGER_BASE_URL`** 로 간다 — 콘솔의 원장 화면 클라이언트와 같은 주소(`apps/console-web/src/shared/api/ledger-client.ts:106`, `src/shared/config/env.ts:253`). 읽기 권한은 `FINANCE_LEDGER_READ ∨ _WRITE ∨ _CONTROL ∨ finance 스코프 ∨ SUPERADMIN_READ` |
| ERP 내 결재 대기 | `projects/erp-platform/apps/approval-service/src/main/java/com/example/erp/approval/presentation/controller/ApprovalInboxController.java:31-48`(`:45` 연결됐을 때만 `meta.actorEmployeeId`); `…/application/ApprovalApplicationService.java:308-318`(미연결 → 빈 페이지 + `null`); `…/infrastructure/persistence/jpa/ApprovalRequestJpaRepository.java:61-64`(`SUBMITTED, IN_REVIEW`); `…/application/ActingEmployee.java:52-58`(직원 조회 불가 → 503 = **점검 필요**, «미연결» 아님); 권한 `…/application/ActorContext.java:68-72` | 표와 같다. 🔵 보탬: masterdata 가 답을 못 하면 503 이라 카드는 «연결 없음» 이 아니라 `degraded` 로 간다 — 두 상태가 섞이지 않는다 |
| E-commerce 출고 대기 | `projects/ecommerce-microservices-platform/apps/order-service/src/main/java/com/example/order/presentation/AdminOrderController.java:70-82`; `…/presentation/OrderOperatorAccess.java:21-34`(`ECOMMERCE_ORDER_READ ∨ _WRITE`, 또는 `X-Tenant-Id = '*'`); `…/infrastructure/persistence/OrderRepositoryImpl.java:247-252`(`TenantContext.currentTenant()`); `…/domain/model/OrderStatus.java:5`(`CONFIRMED`) · `:10`; `…/domain/model/Order.java:341`(`markStuckRecoveryFailed` — 호출하는 곳 0, 같은 디렉터리 grep) | 표와 같다. 🔵 보탬: 응답은 `AdminOrderListResponse.java:9-13` = `{content, page, size, totalElements}` — **`totalPages` 없음** |

🔴 **정정 — Edge Case «WMS 테넌트 축 없는 운영자» 의 방향이 반대.** `…/wms-platform/apps/admin-service/src/main/java/com/wms/admin/application/security/ReadScope.java:10-14` — **네이티브 wms 평면은 제한 없음(전 행)**, entitlement 로 들어온 **고객 테넌트만 자기 행**으로 좁혀진다. 그래서 «비어 보일 수 있는» 쪽은 자기 주문이 없는 고객 테넌트이고, 그 `0` 은 오류가 아닌 참값이다(카드는 `0` 을 그대로 그린다). 지금 카드(재고)는 창고 전역이라 이 차이가 없었다.

### ② 자격 증명 · 업무 권한 세트별 «권한 없음» 변화

자격 증명: 여섯 다리 모두 **지금과 같은 토큰**(IAM = 운영자 토큰 + `X-Tenant-Id` + `tenantId` 쿼리, 나머지 = 도메인용 IAM OIDC 토큰, `X-Tenant-Id` 없음) — `apps/console-web/src/shared/composition/console-composition.ts` 의 `domainHeaders` 그대로. 각 생산자의 테넌트 게이트도 지금 카드와 같은 서비스 경계(게이트웨이/리소스 서버)다.

권한 게이트 대조 (이전 → 이후):

| 카드 | 이전 다리가 요구한 것 | 새 다리가 요구하는 것 | 근거 |
|---|---|---|---|
| IAM | `account.read` | `account.read` (같은 엔드포인트) | `AccountAdminController.java:107` |
| WMS | `WMS_VIEWER` | `WMS_VIEWER` | `InventoryDashboardController.java:20` · `OrderDashboardController.java:29` |
| SCM | `SCM_INVENTORY_VISIBILITY_READ ∨ _WRITE` | `SCM_PROCUREMENT_READ ∨ _WRITE` | `projects/scm-platform/apps/inventory-visibility-service/src/main/java/com/example/scmplatform/inventoryvisibility/config/SecurityConfig.java:50-51, 77` · procurement `SecurityConfig.java:75-76` |
| Finance | `FINANCE_ACCOUNT_READ ∨ _WRITE ∨ 스코프 ∨ SUPERADMIN_READ` **+ 기본계좌**(없으면 호출 없이 `MISSING_PREREQUISITE`) | `FINANCE_LEDGER_READ ∨ _WRITE ∨ _CONTROL ∨ 스코프 ∨ SUPERADMIN_READ`, 선행 조건 없음 | `projects/finance-platform/apps/account-service/src/main/java/com/example/finance/account/infrastructure/security/SecurityConfig.java:64-65, 79-80` · ledger `SecurityConfig.java:89-90` |
| ERP | masterdata READ = `ERP_MASTERDATA_READ ∨ _WRITE ∨ erp.read ∨ erp.write ∨ 테넌트 '*'` | `ERP_APPROVAL_READ ∨ ERP_APPROVAL_WRITE ∨ erp.write ∨ erp.read` (`'*'` 갈래 없음) | `projects/erp-platform/apps/masterdata-service/src/main/java/com/example/erp/masterdata/domain/authorization/RequiredScope.java:9` · approval `ActorContext.java:58-72` · `…/infrastructure/authorization/JwtBackedAuthorizationAdapter.java:33` |
| E-commerce | `ECOMMERCE_PRODUCT_READ ∨ _WRITE ∨ '*'` | `ECOMMERCE_ORDER_READ ∨ _WRITE ∨ '*'` | `projects/ecommerce-microservices-platform/apps/product-service/src/main/java/com/example/product/presentation/controller/ProductOperatorAccess.java:23, 38-40` · `OrderOperatorAccess.java:30-34` |

세트별 결과 — 아래 `## 권한별 «권한 없음» 변화` 표(소유자에게 보일 표)에 적었다. 세트 정의: `projects/iam-platform/apps/admin-service/src/main/resources/db/migration/V0052__create_domain_permission_sets_and_backfill.sql:124-158` · `V0056__scm_fine_grained_domain_roles.sql:27-49, 54-69` · `V0057__erp_fine_grained_domain_roles.sql:28-41, 50-58` · `V0058__finance_fine_grained_domain_roles.sql:31-44, 53-62` · `V0059__ecommerce_fine_grained_domain_roles.sql:32-62, 71-88`(`*-full` · `all-domains-full` · `legacy-compat` 은 옛 `*_OPERATOR` 를 그 도메인 역할 전부로 바꿨다 ⇒ 변화 없음).

### ③ 바꿀 자리 전수

| 자리 | 바꾸는 것 |
|---|---|
| `specs/contracts/console-integration-contract.md` § 2.4.9.1 | 생산자 표 6행 · 응답 예시 · `forbidden` 사유 문단 · ok 데이터 모양 표 · «행 수» 문단(이력으로) · finance 다통화 문단(이력으로) · 관측 문단의 `MISSING_PREREQUISITE` · finance 선행 조건 절(이 경로에선 이력) · scm/ecommerce 다리 위상 주석 · § 2.4.9 주소 예시. 🔴 «카드당 호출 1개» 결정 문단(`TASK-PC-FE-296`)은 **글자 그대로 두고** 그 뒤에 364 주석만 붙였다 |
| `specs/contracts/fixtures/operator-overview-leg-bodies.json` | 여섯 다리 본문 전부 + `variants.erpNotLinked` |
| `apps/console-web/src/shared/composition/console-composition.ts` | `operatorOverviewLegs` 여섯 URL · `financeDefaultAccountId` 제거 · finance 는 `LEDGER_BASE_URL` |
| `apps/console-web/src/shared/composition/operator-overview-composition.ts` | `getFinanceDefaultAccountId()` 호출 제거 |
| `apps/console-web/src/features/operator-overview/api/operator-overview-types.ts` | `Scm/Finance/ErpDataSchema` 모양 |
| `apps/console-web/src/features/operator-overview/components/{DomainCard,DomainCardSummaries,DomainCardStates}.tsx` | 제목 · 라벨 · 설명 · 링크 · ERP «직원 연결 없음» · finance 기본계좌 힌트 제거 |
| `apps/console-web/src/shared/sample/fixtures/dashboards.ts` | 여섯 카드 본문을 같은 도메인 픽스처에 같은 질의로 물어 파생 |
| 시험 | `tests/unit/shared/console-composition.test.ts` · `tests/unit/dashboard-composition-routes.test.ts` · `tests/unit/dashboard-ssr-direct-composition.test.ts` · `tests/unit/features/operator-overview/{leg-body-contract,DomainCard,OperatorOverviewScreen}.test.tsx` · `finance-option-a-render.test.tsx`(전제 소멸 — 삭제) · `tests/unit/sample-fixtures-schema.test.ts` · `tests/unit/sample-overview-cards-match-lists.test.ts` · e2e `e2e-smoke/sample-visitor.spec.ts` · `tests/e2e/operators-profile.spec.ts` |

### ④ 샘플 고정값이 필터를 지키는가

| 카드 | 픽스처 (file:line) | 필터 | 샘플 값 |
|---|---|---|---|
| IAM | `apps/console-web/src/shared/sample/fixtures/iam.ts:157-160` | `status` 지킴 | `LOCKED` 2 |
| WMS | `…/fixtures/wms.ts:654-658`(`collapseStatus`) · `:677-690` | `status` 지킴(읽기 모델처럼 접은 뒤) | `RECEIVED` 2 |
| SCM | `…/fixtures/scm.ts:318-338` | `status` 지킴 | `DRAFT` 1 |
| Finance | `…/fixtures/ledger.ts:547-567` | `status` 지킴 | `OPEN` 1 |
| ERP | `…/fixtures/erp.ts:849-861` | 현재 결재자 = 나 ∧ `SUBMITTED/IN_REVIEW`, `actorEmployeeId` 있음 | 2 |
| E-commerce | `…/fixtures/ecommerce.ts:584-590` | 🔵 **확인됨**: `status` 지킴(측정 보고의 «행 구성 미확인» 해소) | `CONFIRMED` 2 (`order-sample-0002` · `-0005`) |

---

## 새 문구 (소유자 승인 2026-10-22 KST)

> 화면에 새로 보이는 한국어 문구 전부. 경로 접두 `apps/console-web/src/features/operator-overview/components/`. 새 `aria-label` 은 **없다**(링크는 보이는 글자가 곧 이름이고, IAM 제목의 기존 `sr-only` «상세 보기» 는 그대로). 🔴 **사라지는 문구**도 끝에 적었다.

| file:line | 문구 | 언제 보이나 |
|---|---|---|
| `DomainCard.tsx:35` | `WMS 출고` | WMS 카드 제목 (항상) — 이전 `WMS 재고` |
| `DomainCard.tsx:36` | `SCM 발주` | SCM 카드 제목 (항상) — 이전 `SCM 가시성` |
| `DomainCard.tsx:37` | `Finance 대사` | Finance 카드 제목 (항상) — 이전 `Finance 잔액` |
| `DomainCard.tsx:38` | `ERP 결재` | ERP 카드 제목 (항상) — 이전 `ERP 마스터` |
| `DomainCard.tsx:39` | `ecommerce 주문` | E-commerce 카드 제목 (항상) — 이전 `ecommerce 상품` |
| `DomainCard.tsx:34` | `IAM 계정` | (바뀌지 않음 — `/dashboards` 상세 링크의 접근 가능한 이름이라 유지) |
| `DomainCardSummaries.tsx:109` | `잠긴 계정 (이 테넌트)` | IAM 카드 `ok` — 숫자 라벨 (이전 `회원 계정 (이 테넌트)`) |
| `DomainCardSummaries.tsx:110` | `계정 자체가 잠긴 회원 계정입니다. 사이트에서만 잠긴 계정은 세지 않습니다.` | IAM 카드 `ok` — 숫자 아래 설명 |
| `DomainCardSummaries.tsx:115` | `계정 운영으로 이동` | IAM 카드 `ok` — `/accounts` 링크 |
| `DomainCardSummaries.tsx:134` | `미출고 주문` | WMS 카드 `ok` — 숫자 라벨 (이전 `재고 행 수`) |
| `DomainCardSummaries.tsx:135` | `접수됐고 아직 출하 · 취소되지 않은 출고 주문입니다.` | WMS 카드 `ok` — 설명 |
| `DomainCardSummaries.tsx:141` | `출고 화면으로 이동` | WMS 카드 `ok` — `/wms/outbound` 링크 |
| `DomainCardSummaries.tsx:159` | `저재고 재고 보기` | (바뀌지 않음 — `TASK-PC-FE-296` 링크 유지) |
| `DomainCardSummaries.tsx:178` | `제출 대기 발주 (초안)` | SCM 카드 `ok` — 숫자 라벨 (이전 `스냅샷 행 수`) |
| `DomainCardSummaries.tsx:179` | `초안 상태로 아직 공급사에 제출하지 않은 발주입니다.` | SCM 카드 `ok` — 설명 |
| `DomainCardSummaries.tsx:184` | `조달 화면으로 이동` | SCM 카드 `ok` — `/scm/procurement` 링크 |
| `DomainCardSummaries.tsx:202` | `미해소 대사 차이` | Finance 카드 `ok` — 숫자 라벨 (이전 `잔액 정보`) |
| `DomainCardSummaries.tsx:203` | `대사에서 발견됐고 아직 해소하지 않은 차이입니다.` | Finance 카드 `ok` — 설명 |
| `DomainCardSummaries.tsx:208` | `원장 화면으로 이동` | Finance 카드 `ok` — `/ledger` 링크 |
| `DomainCardSummaries.tsx:256` | `내 결재 대기` | ERP 카드 `ok` · 직원 연결됨 — 숫자 라벨 (이전 `활성 부서 수`) |
| `DomainCardSummaries.tsx:257` | `지금 내가 결재할 차례인 요청(상신 · 검토 중)입니다.` | ERP 카드 `ok` · 직원 연결됨 — 설명 |
| `DomainCardSummaries.tsx:234` | `내 결재 대기` | ERP 카드 `ok` · **직원 연결 없음** — 라벨 |
| `DomainCardSummaries.tsx:239` | `직원 연결 없음` | ERP 카드 `ok` · 응답에 `meta.actorEmployeeId` 없음 — 숫자 자리에 (숫자·`0` 은 그리지 않음) |
| `DomainCardSummaries.tsx:242` | `이 운영자 계정에 연결된 직원이 없어 결재 대기를 셀 수 없습니다.` | 같은 상태 — 설명 |
| `DomainCardSummaries.tsx:246` · `:262` | `결재함으로 이동` | ERP 카드 `ok`(연결 여부 무관) — `/erp/approval` 링크 |
| `DomainCardSummaries.tsx:280` | `출고 대기 주문 (결제 완료)` | E-commerce 카드 `ok` — 숫자 라벨 (이전 `상품 수`) |
| `DomainCardSummaries.tsx:281` | `결제가 확인됐고 아직 배송을 시작하지 않은 주문입니다.` | E-commerce 카드 `ok` — 설명 |
| `DomainCardSummaries.tsx:286` | `주문 화면으로 이동` | E-commerce 카드 `ok` — `/ecommerce/orders` 링크 |

🔴 사라지는 문구: `잔액 조회 가능` · `잔액 정보 없음` · 통화 칩(예 `KRW`) · SCM `meta.warning` 안내(S5 «Not for procurement decisions» — 발주 목록 응답엔 없다) · `운영자 프로필에서 기본 finance 계정을 설정하세요.`(`DomainCardStates.tsx` — 이 경로가 더 이상 `MISSING_PREREQUISITE` 를 만들지 않음; 사유 자체는 어휘에 남고 일반 문구 `조회에 필요한 사전 설정이 누락되었습니다.` 는 그대로).

🔵 링크에 필터 파라미터가 **없다**(예 `/accounts?status=LOCKED` 가 아니라 `/accounts`): 다섯 목적지 화면 모두 URL 에서 상태 필터를 읽지 않는다(`app/(console)/accounts/page.tsx` · `wms/outbound/page.tsx` · `scm/procurement/page.tsx` · `ecommerce/orders/page.tsx` 에 `searchParams` 없음, `ledger/page.tsx` 는 `entryId/accountCode/statementId` 만). 읽지 않는 파라미터를 붙이면 필터 안 된 목록에 조용히 떨어진다(`TASK-PC-FE-296` 교훈). 필터 링크는 목적지가 파라미터를 받는 변경이 먼저 — 별도 티켓 후보.

## 권한별 «권한 없음» 변화

> 카드 `forbidden` = 생산자 403(그 밖 오류는 «점검 필요», 401 은 전체 재로그인 — 규칙 그대로). 세트 = ADR-MONO-083 시스템 프리셋(`system_key`, 마이그레이션 file:line 은 `## AC-0 측정` ②). «이전/이후» 는 그 세트**만** 가진 운영자 기준. 테넌트 범위(구독 · 소속)는 바뀌지 않으므로 표에 없다.

| 업무 권한 세트 | 바뀌는 카드 | 이전 | 이후 | 비고 |
|---|---|---|---|---|
| `scm-inventory-visibility-operate` (재고 가시성 운영) | SCM | ok | 🔴 **권한 없음** | 새 다리는 `SCM_PROCUREMENT_*` |
| `scm-procurement-operate` (발주 운영) | SCM | 권한 없음 | ✅ ok | |
| `scm-demand-planning-operate` · `scm-logistics-operate` | SCM | 권한 없음 | 권한 없음 | 변화 없음 |
| `scm-read` · `scm-full` · `all-domains-full` · `legacy-compat` | SCM | ok | ok | 둘 다 가짐 |
| `finance-account-operate` (계좌 운영) | Finance | ok (기본계좌 있을 때) / 권한 없음 (`MISSING_PREREQUISITE`, 기본계좌 없을 때) | 🔴 **권한 없음** | 새 다리는 `FINANCE_LEDGER_*` |
| `finance-ledger-operate` (원장 운영) | Finance | 권한 없음 (계좌 읽기 권한 없음, 또는 기본계좌 없음) | ✅ ok | |
| `finance-read` · `finance-full` · `all-domains-full` · `legacy-compat` | Finance | ok (기본계좌 있을 때만) | ✅ ok (기본계좌 무관) | 🔵 기본계좌 없는 운영자에게 생기던 «권한 없음» 이 사라진다 |
| `erp-masterdata-operate` (기준정보 운영) | ERP | ok | 🔴 **권한 없음** | 새 다리는 `ERP_APPROVAL_*` |
| `erp-approval-operate` (결재 운영) | ERP | 권한 없음 | ✅ ok | 직원 미연결이면 «직원 연결 없음» |
| `erp-read` · `erp-full` · `all-domains-full` · `legacy-compat` | ERP | ok | ok | 직원 미연결이면 숫자 대신 «직원 연결 없음» |
| `ecommerce-product-operate` (상품 운영) | E-commerce | ok | 🔴 **권한 없음** | 새 다리는 `ECOMMERCE_ORDER_*` |
| `ecommerce-order-operate` (주문 운영) | E-commerce | 권한 없음 | ✅ ok | |
| `ecommerce-settlement-` · `-promotion-` · `-shipping-` · `-notification-operate` | E-commerce | 권한 없음 | 권한 없음 | 변화 없음 |
| `ecommerce-read` · `ecommerce-full` · `all-domains-full` · `legacy-compat` | E-commerce | ok | ok | |
| 모든 IAM 역할 · WMS 세트(`wms-*`) | IAM · WMS | — | — | 게이트가 글자 그대로 같다(`account.read` · `WMS_VIEWER`) — 변화 없음 |
| ⚪ 플랫폼 SUPER_ADMIN 와일드카드 토큰(`tenant_id='*'`, 업무 역할 없음) | ERP | ok (masterdata 가 `'*'` 허용 — `RequiredScope.java:9`) | 🔴 **권한 없음** (추정) | approval 읽기엔 `'*'` 갈래 없음(`ActorContext.java:68-72` · `JwtBackedAuthorizationAdapter.java:33`). 🔴 **추정**: TASK-PC-FE-338 이후 테넌트를 고르면 assume 토큰이 실리므로 이 토큰이 개요 다리에 실제로 실리는 경로가 있는지는 **안 쟀다** — AC-3 라이브에서 확인. SCM 은 이전·이후 모두 역할만 보므로 변화 없음, Finance · E-commerce 는 둘 다 `'*'`/SUPERADMIN_READ 허용 |

## 구현 (2026-10-22 UTC · Opus)

**SSR vs 클라이언트**: 바뀐 코드는 전부 **서버 쪽**이다 — 합성(`shared/composition/*`, 서버 전용)과 카드(`DomainCard` · `DomainCardSummaries` · `DomainCardStates`, 서버 컴포넌트). 새 `'use client'` 없음, 클라이언트 그래프에 새 import 없음(유일한 클라이언트 잎 `RetryButton` 그대로). 데이터는 SSR 에서 합성 함수를 프로세스 안에서 부르고(TASK-PC-FE-350), 브라우저는 «다시 시도» 때만 같은 경로를 부른다 — 둘 다 같은 `composeOperatorOverview()`.

**바꾼 파일**

| 파일 | 변경 |
|---|---|
| `specs/contracts/console-integration-contract.md` § 2.4.9 · § 2.4.9.1 | 계약 먼저(별도 커밋) — `## AC-0 측정` ③ 의 자리 전부 |
| `specs/contracts/fixtures/operator-overview-leg-bodies.json` | 여섯 본문 + `variants.erpNotLinked` |
| `apps/console-web/src/shared/composition/console-composition.ts` | `operatorOverviewLegs` 여섯 URL 1:1 교체 · `financeDefaultAccountId` 제거(정해진 다리 0) · finance = `LEDGER_BASE_URL` |
| `apps/console-web/src/shared/composition/operator-overview-composition.ts` | `getFinanceDefaultAccountId()` 호출 제거 |
| `apps/console-web/src/features/operator-overview/api/operator-overview-types.ts` | `Scm/Finance/ErpDataSchema` 를 새 생산자 본문으로(`Erp` 에 `actorEmployeeId`) |
| `apps/console-web/src/features/operator-overview/components/DomainCard.tsx` | 제목 5개 |
| `apps/console-web/src/features/operator-overview/components/DomainCardSummaries.tsx` | 카드마다 숫자 하나 · 설명 · 작업 화면 링크 · ERP «직원 연결 없음» · WMS 저재고 링크 유지 |
| `apps/console-web/src/features/operator-overview/components/DomainCardStates.tsx` | finance 기본계좌 힌트 제거 |
| `apps/console-web/src/shared/sample/fixtures/dashboards.ts` | 여섯 카드 = 같은 도메인 픽스처에 같은 질의로 물은 본문, 0 이면 모듈 로드 시 throw |
| `apps/console-web/tests/unit/shared/console-composition.test.ts` | 새 URL · **카드당 호출 1 · 총 6**(카드별 URL 정확 일치) · 정해진 다리 0 · finance 주소 · 403 per-card · 미연결 ERP 통과 |
| `apps/console-web/tests/unit/dashboard-composition-routes.test.ts` | 경로로 카드당 1회 · 기본계좌 없어도 finance ok/6회 · 403 per-card |
| `apps/console-web/tests/unit/dashboard-ssr-direct-composition.test.ts` | 새 경로 · 프로세스 안 throw 주입을 env 로 |
| `apps/console-web/tests/unit/features/operator-overview/leg-body-contract.test.tsx` | 고정값 본문 → **정확한 숫자** 단언(«— 아님» 보다 강함) · 미연결 ERP · F5 |
| `apps/console-web/tests/unit/features/operator-overview/DomainCard.test.tsx` | 카드마다 ok · 0 · 링크 · 읽을 수 없는 본문 · 저하 · 권한 없음 · ERP 연결 없음/null/연결+0/id 비노출 |
| `apps/console-web/tests/unit/features/operator-overview/OperatorOverviewScreen.test.tsx` | 고정값을 생산자 모양으로 |
| `apps/console-web/tests/unit/features/operator-overview/finance-option-a-render.test.tsx` | **삭제** — 전제(개요 finance 카드가 기본계좌를 읽음)가 사라짐 |
| `apps/console-web/tests/unit/sample-fixtures-schema.test.ts` · `sample-overview-cards-match-lists.test.ts` | 여섯 카드 = 목록을 카드의 술어로 걸러 센 값(목록에 술어가 **빼는** 행도 있어야 통과 — 필터가 무시되면 빨강) |
| `apps/console-web/e2e-smoke/sample-visitor.spec.ts` | 새 testid(`iam-locked` · `ecommerce-to-ship` · `erp-inbox`) |
| `apps/console-web/tests/e2e/operators-profile.spec.ts` | 기본계좌 확인을 `/finance` 개요로 옮김(개요 카드는 이제 기본계좌와 무관) |

**결과** (worktree, 마지막 전체 실행 = 최종 코드)

| 게이트 | 결과 |
|---|---|
| `npx vitest run --maxWorkers=4 --minWorkers=2` | rc=0 · **424 files · 5199 tests 통과** |
| `npx tsc --noEmit` | rc=0 |
| `npx next lint` | rc=0 (경고 1 — 기존 `src/app/api/onboarding/received-invitations/route.ts:26`, 이 변경과 무관) |
| bite — `ErpSummary` 가 `actorEmployeeId` 를 무시하게 바꿔 실행 | 정확히 «연결 없음» 칸 3개만 빨강(`DomainCard.test.tsx` 2 · `leg-body-contract.test.tsx` 1) → 원복 |

**e2e 영향 (grep)**: `e2e-smoke/sample-visitor.spec.ts`(옛 testid 둘 — 고침) · `tests/e2e/operators-profile.spec.ts`(개요 finance 카드로 기본계좌 확인 — `/finance` 로 고침) · `tests/e2e/overview-consolidation.spec.ts:133-157`(IAM 제목 링크 — 그대로 유효). 🔴 **고치지 않고 보고**: `tests/federation-hardening-e2e/specs/{entitlement-trust-crossdomain,tenant-switch-rescope}.spec.ts` 는 카드 `data-status` 로 테넌트 entitlement 를 판정한다. 새 다리는 다른 업무 역할을 요구하므로, 그 시드 운영자(`fixtures/seed.sql` — acme · multi, `permission_set_id NULL` = 상속)의 assume 토큰에 `SCM_PROCUREMENT_*` · `FINANCE_LEDGER_*` · `ERP_APPROVAL_*` 가 실리는지에 따라 «entitled 인데 forbidden» 으로 빨개질 수 있다 — 로컬에서 판정 불가(도커 스택). 또 `tenant-switch-rescope.spec.ts:135-152` 의 «scm = ok» 칸은 inventory-visibility `/snapshot` 422 회귀(MONO-171/SCM-BE-021)를 잡으려던 것인데, 개요가 그 엔드포인트를 더는 부르지 않으므로 **그 회귀를 더 이상 물지 않는다**. ⇒ 머지 전 브랜치에서 `nightly-e2e`(콘솔) **와** `federation-hardening-e2e` 를 돌려야 한다.

**남은 것**: AC-1(새 문구 · 권한 표 소유자 승인) · AC-2 의 브랜치 nightly · AC-3(배포 · 샘플 화면 · 로그인 운영자 라이브 — 특히 위 표의 ⚪ 행).

## federation-hardening e2e 빨강 진단 · 수정 (2026-10-22 UTC · Opus)

> 브랜치 런 **39766172565**(head `e915c7de4`) 빨강 2칸 — `entitlement-trust-crossdomain.spec.ts:100` · `tenant-switch-rescope.spec.ts:85`(:125 경유): acme-corp 의 scm 카드가 `forbidden` 이어야 하는데 `degraded`. 콘솔 nightly(39766167157)는 초록.

**원인 (증거)** — 생산자 문제가 **아니라** 시험 스택 문제다.
1. 그 런의 console-web 로그(«Dump docker compose logs on failure» 스텝): 같은 요청 `270f34aa…` 에서 **wms · scm · finance · erp · ecommerce 다섯 다리 모두 `TIMEOUT` 4000 ms**, iam 만 응답. 바뀌지 않은 wms 다리까지 시간 초과 ⇒ 개별 생산자의 답이 아니라 console-web 쪽 정체. 요청 `04a0a372…` 에선 erp 가 17 ms 에 답(`DOWNSTREAM_ERROR`)했는데 scm 은 그 뒤 ~4 s 에 `TIMEOUT` — 조회가 줄 서 있었다.
2. 정체의 원인: `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` 의 console-web 에 **`LEDGER_BASE_URL` 이 없었다** → 코드 기본값 `http://finance.local`(`src/shared/config/env.ts:253`) — 이 스택에서 해석 불가, 그리고 실패하지 않고 **4 s 넘게 걸린다**. 기존부터 `ECOMMERCE_ADMIN_BASE_URL` 도 같은 상태(`ecommerce.local`). getaddrinfo 는 취소 불가 · libuv 4-스레드 풀에서 돌므로 렌더당 두 개의 멈춘 조회가 풀을 막아 나머지 다리의 조회가 전부 줄을 섰다. 이 기전은 콘솔 e2e 스택에서 이미 측정 · 수정됐다(`projects/platform-console/docker-compose.e2e.yml:445-460`, TASK-PC-FE-341 · 351) — **federation 스택에는 적용된 적이 없고, 그 가드(`tests/unit/e2e-compose-composition-bases.test.ts`)의 모집단에도 없었다.**
3. 게다가 이 스택엔 **finance ledger-service 가 없었다**(boot jar 목록 · compose 모두) → `LEDGER_BASE_URL` 을 고쳐도 가리킬 생산자가 없었다.
4. (b) 생산자 게이트는 정상: scm 게이트웨이 `projects/scm-platform/apps/gateway-service/src/main/java/com/example/scmplatform/gateway/config/OAuth2ResourceServerConfig.java:98-101` 와 procurement `…/procurement-service/…/security/ServiceLevelOAuth2Config.java:66-68, 88-91` 가 inventory-visibility 와 같은 decode 시점 `TenantClaimValidator.forTenant(scm).trustEntitledDomains()` — 비구독 테넌트는 라우트와 무관하게 403. (c) ledger `…/ledger-service/…/ServiceLevelOAuth2Config.java:69-72, 90-94`, approval `…/approval-service/…/ServiceLevelOAuth2Config.java:69-71, 88-91` 도 같은 게이트. ⇒ STOP 조건(생산자가 비구독에 403 아닌 답) **아님**.
5. erp 는 단언이 scm 에서 먼저 멈춰서 판정되지 않았다(`NON_ENTITLED_DOMAINS = ['scm','erp']` 순서) — 로그상 erp 도 같은 `TIMEOUT` 이었다.

**수정 (시험 인프라만 — 단언은 그대로)**
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml`: `finance-ledger-service` 추가(MySQL `finance_ledger_db` · OIDC = auth-service · Kafka 죽은 주소 · outbox 끔) · console-web `LEDGER_BASE_URL: http://finance-ledger-service:8080` · 생산자 없는 `*.local` 기본값들(`ECOMMERCE_ADMIN/PUBLIC_BASE_URL` · `WMS_OUTBOUND_BASE_URL` · `FAN_GATEWAY_BASE_URL`)은 341/351 관례대로 `http://127.0.0.1:9`(즉시 연결 거부 → `degraded`, 조회 없음) · `CONSOLE_ONBOARDING_URL` 은 admin-service(콘솔 스택과 같은 값).
- `tests/federation-hardening-e2e/fixtures/seed.sql`: `finance_ledger_db` 생성 + `finance` 권한.
- `.github/workflows/federation-hardening-e2e.yml`: ledger-service bootJar · 업로드 · 경로 복원 · Phase 2 기동 목록 · 헬스 대기.
- `apps/console-web/tests/unit/e2e-compose-composition-bases.test.ts`: 같은 두 칸(«`*.local` 기본값 전부 설정» · «합성 base 전부 설정»)을 **federation compose 에도** 적용. bite: federation 의 `LEDGER_BASE_URL` 을 주석 처리 → 정확히 federation 두 칸만 빨강(`expected [ 'LEDGER_BASE_URL' ]`) → 원복.
- 진단용 주석만: `observability-trace-tree.spec.ts` 의 `KNOWN_PRODUCERS`(진단 출력용 — 판정에 안 씀) · `tenant-switch-rescope.spec.ts` 의 «scm = ok» 칸 주석(이제 procurement 다리를 문다; 단언 불변).

**남는 간극 (기록)**: 이 스택엔 erp 게이트웨이와 approval-service 가 없어 `ERP_BASE_URL` 이 masterdata 를 직접 가리킨다 — 개요 erp 다리(`/api/erp/approval/inbox`)는 masterdata 에 닿고, 같은 erp decode 게이트라 비구독 = 403 은 맞지만 구독 테넌트는 404 → `degraded`(«권한 없음 아님» 단언은 통과)이다. 진짜 inbox 를 재려면 erp 게이트웨이 + approval-service 추가가 필요 — 별도 변경 후보.

⚪ 로컬에선 도커 스택을 못 돌려 수정 효과는 **다음 브랜치 런이 판정**한다(`docker compose … config` rc=0 · 워크플로 YAML 파싱만 확인).

## 소유자 승인 · 머지 전 확인 (2026-10-22 UTC · 분석=Opus 5.5)

- **소유자 승인**(선택창, 2026-10-22 KST, 머지 전): 새 문구 표 · 권한별 «권한 없음» 변화 표(SUPER_ADMIN 와일드카드 ERP 행은 추정 — AC-3 라이브로) → AC-1 닫힘(계약 먼저 = `0f689809a`).
- `TASK-PC-FE-363` 머지(#4467 · #4468) 뒤 rebase — `INDEX.md` ready 칸 충돌 1곳(`_(없음)_`). 합친 트리 vitest 425 files · 5215 tests · tsc · lint · 가드 다섯 rc=0.
- 🔴 첫 브랜치 federation-hardening-e2e run `39766172565`(head `e915c7de4`) **빨강** — 비구독 테넌트의 SCM 카드가 `forbidden` 대신 `degraded`(main 의 같은 잡은 최근 5회 전부 success ⇒ 이 브랜치가 만든 것). 원인 = 시험 스택(§ federation-hardening e2e 빨강 진단 · 수정): console-web 에 `LEDGER_BASE_URL` 이 없어 `*.local` 조회가 4 s+ 걸리며 Node 4-스레드 풀을 막아 모든 다리가 TIMEOUT · 스택에 ledger-service 자체가 없음. 생산자 게이트는 비구독 테넌트에 403(확인). 수정 `cc862528d` = 시험 인프라만(compose · seed · 워크플로 · 단위 가드 확장) — **단언은 그대로**.
- 🔵 이 PR 은 공유 경로(`.github/workflows/federation-hardening-e2e.yml` · `tests/federation-hardening-e2e/`)도 바꾼다 — 이 변경 없이는 364 가 e2e 를 통과할 수 없어 같은 PR 에 묶었다(원자적 변경).
- 재실행(head `cc862528d`): 콘솔 nightly run `39771872661` **success** · federation-hardening-e2e run `39771878976` **success** · PR #4469 체크 pending 0 · fail 0 → AC-2 닫힘.
- 남는 간극(이 티켓 밖): federation 스택에 erp 게이트웨이 · approval-service 가 없어 ERP 다리는 masterdata 에 닿는다 — 비구독 403 은 맞지만 구독 테넌트의 진짜 결재 대기 응답은 검사하지 않는다.
- 열림: **AC-3**(Vercel Production · 익명 샘플 개요 · 로그인 운영자 라이브 — 권한 표 · SUPER_ADMIN 와일드카드 ERP 행).

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 백엔드: 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899`.
- **AC-3 ❌** — 로그인(`demo-corp`) 운영자 통합 개요: **«WMS 출고» · «SCM 발주» 카드가 «하위 서비스에서 오류가 발생했습니다 · 사유: DOWNSTREAM_ERROR»**(두 번 로드, 수 분 간격, 같음). 도메인 상태 요약 «WMS 점검 불가». 나머지 IAM 0 · Finance 0 · ERP 2 · ecommerce 0 은 표시.
- 해당 서비스 로그(wms-admin-service · scm procurement)에 그 요청의 오류 없음 → 요청이 서비스에 안 닿은 것으로 보인다. 같은 `WMS_ADMIN_BASE_URL` 을 쓰는 `/wms` 화면은 정상. 콘솔 서버(Vercel) 로그는 볼 수 없었다(CLI 없음).
- 권한 세트별 «권한 없음» 표는 이번 창에서 못 쟀다.
- 새 티켓: **`TASK-PC-FE-369`**(개요 WMS · SCM 카드 `DOWNSTREAM_ERROR` · «WMS 점검 불가»). 그것이 고쳐진 뒤 AC-3 을 권한 표까지 다시 잰다.
- ⇒ 이 티켓은 `review` 에 남는다(체크 칸은 동결 규칙으로 고치지 않음).
