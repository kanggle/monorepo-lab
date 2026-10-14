# ADR-MONO-083 — 테넌트별 **도메인 업무 권한 세트**: 운영자 · 그룹에 배정한 세트만 assume 토큰에 싣는다 (ADR-035 O1-B 실행)

**Status:** ACCEPTED
**Date:** 2026-10-14 (PROPOSED · 같은 날 ACCEPTED — 갈래 A)
**주관 티켓:** `TASK-MONO-790`
**Decision driver:** 소유자 요구(2026-10-13 대화) — «하나의 테넌트가 여러 도메인을 구독하더라도 운영자 · 그룹마다 담당 도메인과 세부 업무만 사용할 수 있게». 예: A 테넌트가 WMS · SCM 구독 → 재고팀은 WMS 재고만, 출고팀은 WMS 출고만, SCM팀은 SCM 만, 통합팀은 여러 세트, 같은 사람이 A 에선 재고 · B 에선 출고.
**Supersedes:** 없음 — `ADR-MONO-035` O1 의 **미뤄 둔 B행**(«deferred refinement», `ADR-MONO-035…md:90`)을 실행한다. O1-A(구독 파생)는 이 ADR 이 ACCEPT 되면 **대체**된다(035 본문 바이트 불변, 덧붙임만).
**Related:** [`ADR-MONO-035`](ADR-MONO-035-operator-auth-unification-model.md) (O1-A 구독 파생 · O1-B 미룸 · 🔴 O1-C «관리 역할 → 도메인 역할 매핑» 기각 = 이름 공간 분리) · [`ADR-MONO-082`](ADR-MONO-082-one-person-operator-in-many-companies.md) (D1 한 토큰 = 한 회사 · 🔴 D5 «도메인 평면 불변» · R4 `permission_set_id` 미사용) · [`ADR-MONO-020`](ADR-MONO-020-operator-multitenant-assignment.md) (D5 «배정마다 권한 세트» — ACCEPTED, 칸만 있음) · [`ADR-MONO-024`](ADR-MONO-024-tenant-admin-delegation.md) (D2 범위 = 행의 테넌트 · D6 관리자 ≠ 도메인 권한) · [`ADR-MONO-045`](ADR-MONO-045-cross-org-partner-delegation.md) (D3 위임 상한 · 관리 역할 위임 금지) · [`ADR-MONO-046`](ADR-MONO-046-operator-group-model.md) (D2 fan-out · 상속은 미룸) · [`ADR-MONO-047`](ADR-MONO-047-org-node-tenant-hierarchy.md) (D2 · D3 · D6 조직 노드 상한 = 도메인 단위 · 거부 전용) · [`ADR-MONO-080`](ADR-MONO-080-workforce-on-the-consumer-pool.md) (D5 퇴사 = 그 회사 측면만) · `TASK-MONO-773`(회사 생성자 · 두 번째 회사) · `TASK-MONO-784`(회사별 소속 · 역할 — review)

> 🟢 **ACCEPTED — 갈래 A** (2026-10-14 UTC, 소유자 정확형 `ADR-MONO-083 ACCEPTED — A`). 이름 · `ACCEPTED` · 갈래 letter 세 요건이 한 문장에 도착했다.
> 🔴 **OD 답은 공급되지 않았다** — OD-1~OD-6 은 기본값이 **구현자 선택**으로 남는다(§ OD 대조). 단계 티켓은 이 ACCEPT PR 안에서 기안했다(§ ACCEPT 가 만든 새 의무). 아래 PROPOSED 시점 본문은 **바이트 그대로**.
> (PROPOSED 시점 머리말) 🟡 **PROPOSED** — 소유자 ACCEPT 는 정확형 **`ADR-MONO-083 ACCEPTED — <A|B|C>`** 로만 넘어간다(§ 수락 형식). «진행» · «좋아» · 갈래 letter 만 있는 문장 · 추천과 같은 선택창 응답은 수락이 아니다(`platform/architecture-decision-rule.md` § The ACCEPTED Gate).
> 이 문서는 구현을 인가하지 않는다. 단계 티켓은 ACCEPT PR 안에서 기안한다(§ 단계).

---

## Context

### 무엇을 정하나

운영자가 테넌트를 고르면(assume-tenant) 받는 도메인 토큰의 `roles` 를 **무엇에서** 만드는가. 지금은 «그 테넌트가 구독한 도메인» 하나에서 만든다 — 그래서 같은 테넌트의 운영자는 **모두 같은 업무 권한**을 받는다. 바꾸려는 것은 «그 테넌트에서 **그 운영자에게 배정된 업무 권한 세트**» 에서 만드는 것이다. 세 축을 따로 둔다:

| 축 | 무엇이 정하나 | 이 ADR |
|---|---|---|
| 도메인 접근 | 테넌트의 활성 구독 ∩ 조직 노드 상한(ADR-047) | **그대로** — 세트가 이 밖으로 넓힐 수 없다(상한) |
| 업무 수행 권한 | 지금: 구독 파생(모두 같음) → **배정된 세트의 합** | **바꾼다** |
| 데이터 범위 | `org_scope`(배정 행) · 테넌트 격리 | **그대로** — 별도 적용 |

### 지금 어떻게 생겼나 (실측 — 코드, 2026-10-14 UTC, origin/main `2830130f6`)

경로 약어: `AUTH` = `projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure`, `ADM` = `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin`.

#### ① 도메인 역할을 만드는 곳은 한 자리다

- 운영자 assume 의 `roles` = `OperatorRoleDerivation.fromEntitledDomains(entitled)`(`AUTH/oauth2/TenantClaimTokenCustomizer.java:1029-1037`). 입력은 **테넌트의 entitled domains 하나** — 구독 ∩ 상한(`account-service …/TenantEntitledDomainsQueryUseCase.java:59-71`). 운영자 · 배정 · 역할 행은 입력이 아니다(«never admin_operator_roles», ADR-035 O1).
- 매핑은 하드코딩 `switch`(`AUTH/oauth2/OperatorRoleDerivation.java:53-58, 80-125`):

| 도메인 | 발급 역할 |
|---|---|
| `wms` | `WMS_OPERATOR, OUTBOUND_READ, OUTBOUND_WRITE, INBOUND_READ, INBOUND_WRITE, INVENTORY_READ, INVENTORY_WRITE, MASTER_READ` |
| `scm` · `erp` · `finance` · `ecommerce` · `mes` | `SCM_OPERATOR` · `ERP_OPERATOR` · `FINANCE_OPERATOR` · `ECOMMERCE_OPERATOR` · `MES_OPERATOR` |
| `fan` · `fan-platform` | `FAN_OPERATOR` |

  `*_ADMIN` · `MASTER_WRITE` 는 **일부러 발급하지 않는다**(`:35-51, 90-101`). 같은 목록이 admin-service `DelegatableRoleCatalog.java:47-61` 에 손으로 복제돼 있다(«keep in sync», `:24-29`).
- 예외 하나: **파트너십(교차 조직) assume** 은 파생을 건너뛰고 admin-service 가 준 `delegatedScope.roles` 를 **그대로** 싣는다(`TenantClaimTokenCustomizer.java:1005-1007, 1118-1138`). 상한 = `delegated ∩ participant ∩ host-holds`(`ADM/application/OperatorAssignmentCheckUseCase.java:282-294`).
- 그 밖의 발급 경로(전수): 기본 로그인/리프레시 = 소비자 역할만 · `client_credentials` = `WorkloadRoleCatalog`(wms 내부 클라이언트의 `MASTER_*` 만, `WorkloadRoleCatalog.java:147-149`) · 워크로드 assume = 역할 없음 · admin-service 토큰 교환 = `roles` 클레임 없음(`OperatorAccessTokenIssuer.java:55-60`). ⇒ **운영자 도메인 역할의 입구 = 일반 assume 파생 + 파트너십 위임, 둘뿐.**

#### ② 🔴 역할 말고 **범위(`scope`)** 로도 쓰기가 열린다

- assume 토큰의 `scope` = 콘솔 클라이언트의 **등록 범위 전체**(`AUTH/oauth2/AssumeTenantAuthenticationProvider.java:250` `authorizedScopes(registeredClient.getScopes())`). 등록 범위 = `openid, profile, email, tenant.read`(`auth-service …/V0015__seed_platform_console_oidc_client.sql:93`) + **`erp.write`**(`V0023__add_erp_write_scope_to_platform_console.sql:21`).
- ERP 쓰기 판정 = `erp.write` 범위 **∨** 운영자 역할(`erp-platform …/masterdata-service/…/RoleScopeAuthorizationAdapter.java:80-96`). ⇒ **ERP 를 구독한 테넌트로 assume 한 운영자는 역할과 무관하게 ERP 쓰기가 통한다.** 역할만 줄이면 ERP 는 세분되지 않는다 — 이 ADR 의 **우회 경로 1번**이다.
- finance 는 `SCOPE_finance.write` 를 쓰기 권한으로 받지만(`finance-platform …/account-service/…/SecurityConfig.java:116-124`), 콘솔 클라이언트에 `finance.write` 가 등록돼 있지 않다 — 지금은 우회 아님(등록이 바뀌면 같은 구멍).

#### ③ 도메인별 실제 서버 인가 (카탈로그의 원재료)

| 도메인 | 게이트웨이 | 서비스 인가 | 세분 | 출처 |
|---|---|---|---|---|
| **WMS** | 아무 역할 · 범위나 통과(`RoleAdmissions.roleOrScope`) · 테넌트 일치 | inventory `INVENTORY_READ` / `INVENTORY_WRITE`(조정 · 이동) / `INVENTORY_ADMIN`(폐기) / `INVENTORY_RESERVE`(보유자 0) · inbound `INBOUND_READ/WRITE/ADMIN` · outbound URL 단 GET=`OUTBOUND_READ∨WRITE∨ADMIN`, 변경=`OUTBOUND_WRITE∨ADMIN` · master `MASTER_READ/WRITE/ADMIN` + `org_scope` 창고 ABAC · admin-service `WMS_VIEWER`(대시보드, `entitled_domains∋wms` 면 합성) / `WMS_OPERATOR`(알림 확인 · 운영 조치) / `WMS_ADMIN`(사용자 · 설정) | ✅ 업무 · 조회/변경 둘 다 있음 | `inventory-service …/InventoryQueryController.java:37` · `AdjustmentController.java:70, 93, 113` · `outbound-service …/SecurityConfig.java:97-102` · `master-service …/WarehouseService.java:45, 81, 117` · `wms admin-service …/SecurityConfig.java:52-57, 110-141` |
| **SCM** | 같음 | 🔴 **4 서비스 모두 `authenticated()` 만** — demand-planning · logistics · inventory-visibility · procurement. 유일한 세분 = procurement 공급사 등록(`requireOperator()`)과 발주 확정(상태 기계 `ActorType`) | ❌ 없음 | `scm-platform …/demand-planning-service/…/SecurityConfig.java:45` · `logistics-service/…/SecurityConfig.java:39` · `procurement-service/…/SupplierApplicationService.java:57, 133-136` · `PoStatusMachine.java:14-20` |
| **ERP** | 같음 | `authenticated()` + 앱 단 `AuthorizationPort`: 읽기 = 범위 ∨ 운영자 ∨ `entitled_domains∋erp` ∨ `'*'`, 쓰기 = **`erp.write` ∨ 운영자**. 결재 자격은 도메인 가드(직무 분리) — 역할 아님 | ❌ 도메인 전체 하나 | `RoleScopeAuthorizationAdapter.java:80-96` · `approval-service …/JwtBackedAuthorizationAdapter.java:32-35` · `read-model-service …/ReadAuthorizationGate.java:58-80` |
| **Finance** | 같음 | 필터 체인 `hasAnyAuthority`: 쓰기 = `SCOPE_finance.write ∨ OPERATOR/ADMIN/SUPER_ADMIN/FINANCE_OPERATOR`, 읽기 + `FINANCE_VIEWER`(합성). account 만 KYC · 충전에 추가 운영자 검사 · 🔴 ledger 는 분개 · 환율 덮어쓰기 · 기간 마감 · 대사 해소가 **같은 하나의 쓰기 권한** | ❌ 도메인 전체 하나 | `account-service …/SecurityConfig.java:102-124` · `AccountApplicationService.java:140-142, 355-358` · `ledger-service …/SecurityConfig.java:118-126` |
| **E-commerce** | 🔴 **게이트웨이가 유일한 판정**: `/api/admin/**` = `roles ∋ ECOMMERCE_OPERATOR`(또는 GET 만 `'*'`) · 서비스는 대부분 헤더를 믿고 `permitAll` · 일부 서비스가 같은 술어를 다시 검사 | 주문 · 상품 · 회원 · 정산 · 프로모션 · 배송 · 알림 · 검색 모두 `ECOMMERCE_OPERATOR` 하나 | ❌ 도메인 전체 하나 | `ecommerce …/gateway-service/…/AccountTypeEnforcementFilter.java:99-108, 244-248` · `order-service …/OrderSecurityConfig.java:146-157` · `settlement-service …/OperatorRoleGuard.java:12-33` |
| fan | — | 이번 조사에서 열지 않았다(⚪) | ⚪ | — |

⇒ **세분 인가가 있는 도메인은 WMS 하나다.** 나머지 넷에서 «도메인 진입 역할 하나 = 모든 업무 API» 다. 이 상태에서 세트를 쪼개 배정해도 **서버가 구분하지 않으므로** 세트 경계는 화면에만 있다 — 소유자 요구(«세부 업무 권한이 없는 도메인은 서버 인가를 추가»)가 바로 이 칸이다.

#### ④ admin-service 의 저장 · 그룹 · 출처

- 관리 역할 = `admin_roles` + `admin_role_permissions(role_id, permission_key)` — 키는 전부 관리 평면(`account.*` · `audit.read` · `operator.manage` · `group.manage` …). **도메인 역할 행은 없다.** 역할 종류 칸도 없다(`V0004__create_admin_rbac_tables.sql:24-41`). 콘솔 «권한 세트» 화면(`/permission-sets`)은 **별도 엔티티가 아니라 `GET /api/admin/roles` 를 다시 보여 주는 것**이다(`PermissionSetsScreen.tsx:1-20` · `admin-api.md` «`GET /api/admin/permission-sets` 는 일부러 없다»).
- `operator_tenant_assignment.permission_set_id` = `admin_roles.id` **단일 FK**(`V0030…sql:26-37`), 평가에서 읽는 곳 0 · ADR-082 R4 · 784 D-15 가 «계속 미사용» 으로 고정.
- 🔴 **그룹 fan-out 의 출처 표시는 한 칸이다**: 역할 행 PK `(operator_id, role_id, tenant_id)` 에 `group_origin` 하나(`V0044` · `V0051:64`). 두 그룹이 같은 역할을 주면 **먼저 만든 쪽이 행을 갖고**(«first writer owns», `ADM/application/GroupFanOutService.java:131-145`), 그 그룹을 회수하면 **다른 그룹이 여전히 주려는 권한이 사라진다** — 스펙도 v1 성질로 인정(`data-model.md:243`). 직접 부여가 먼저면 그룹 회수가 직접 행을 지우지 않는다(옳다). 🔵 같은 테넌트에서 그룹 행 위에 직접 역할 PATCH 를 하면 `saveAll` 의 merge 로 그룹 행이 감사 없이 직접 행으로 바뀌는 것으로 **보인다**(코드 추론 · 시험 없음 — ⚪, § Outstanding).
- 권한 회수 장치(지금): assume 토큰은 **30분**(콘솔 클라이언트 `access-token-time-to-live` 1800s, `V0015:95`) · **리프레시 없음**(`AssumeTenantAuthenticationProvider.java:268-270`) · 저장 안 함 ⇒ `/oauth2/revoke` 불가 · 도메인 게이트웨이는 JWKS 로컬 검증만(차단 목록 · 권한 버전 없음). 콘솔은 기본 토큰을 돌릴 때마다 다시 교환하므로 **다음 교환에서** 배정 · 상태 · 구독이 다시 판정된다. ⇒ 지금 «운영자 정지 · 배정 회수 · 구독 해지» 의 기존 토큰 회수 지연 = **최대 30분**.
- assume 판정 내부 호출 `GET /internal/operator-assignments/check` 는 **fail-closed**(오류 · `assigned=false` → 거절, `AUTH/client/AdminAssignmentClient.java:121-139`). entitled-domains 조회는 **fail-soft 로 좁힌다**(오류 → `roles` 없음, `TenantClaimTokenCustomizer.java:1078-1093`). 응답에 이미 `delegatedScope{domains, roles}` 를 싣는 선례가 있다(`auth-to-admin.md:16-77`).

#### ⑤ 콘솔

- 메뉴: 관리 메뉴는 `/me` 관리 역할로 **숨기고**, 도메인 메뉴는 구독이 없으면 «구독 필요» **배지만** 붙인다 — 업무 권한으로 숨기는 입력이 없다(`console-nav-exposure.ts:69-86, 108-137` · `permission-map.ts:186-196`).
- 화면 · 버튼: 도메인 화면은 역할을 읽어 버튼을 숨기지 않는다 — 시도하고 403 을 받아 화면 안 «권한 없음» 으로 보인다(계약 `console-integration-contract.md:599, 656` «does NOT pre-gate on role»). 직접 URL 진입도 같은 403 경로(`(console)/layout.tsx:145-267` 은 인증만 본다).
- 배포: console-web = Vercel(`apps/console-web/vercel.json` · `VERCEL.md` — `main` 직접 배포 꺼짐) · 백엔드 = EC2 AMI 위 Docker Compose 데모 스택(`infra/demo/`). **`main` 머지 ≠ 백엔드 운영 반영** — AMI 재굽기 · 데모 창이 따로다.

---

## Decision

D1~D9 는 갈래와 무관하게 참이다. 갈래(A/B/C)는 **세트를 어디에 저장하는가**만 가른다.

### D1 — 세 축은 섞지 않는다 · 도메인 업무 권한은 관리 역할과 다른 이름 공간이다

- 관리 역할(`admin_roles`)이 있다는 이유로 도메인 업무 권한이 생기지 않고, 그 반대도 없다(ADR-024 D6 · ADR-035 O1-C 그대로).
- 실효 업무 권한 = **(직접 배정 세트 ∪ 그룹 배정 세트)의 역할 합** **∩ 카탈로그(테넌트의 entitled domains = 구독 ∩ 조직 노드 상한)**. 세트는 상한을 넓히지 못한다 — 구독하지 않은 도메인의 세트는 배정돼 있어도 토큰에 아무것도 싣지 않는다.
- 데이터 범위(`org_scope` · 테넌트 격리)는 이 계산과 **따로** 그대로 적용된다.

### D2 — 카탈로그는 서버가 낸다 · 실제로 검사되는 역할만 · 관리(ADMIN) 등급은 넣지 않는다

- 배정 가능한 업무 역할 목록(카탈로그)은 admin-service 한 곳이 소유하고 API 로 낸다. `OperatorRoleDerivation` 과 `DelegatableRoleCatalog` 의 두 손 복제본은 이 카탈로그로 **합친다**(한 사실 한 자리).
- 카탈로그에 들어가는 조건: **그 도메인의 서버가 실제로 검사하는 역할**. 지금 발급하지 않는 `*_ADMIN` · `MASTER_WRITE` · `INVENTORY_RESERVE` · `WMS_ADMIN` 등은 **넣지 않는다** — 넣으려면 별도 ADR(설계 근거 · 검증).
- 1차 카탈로그(WMS — 지금 코드로 확인됨): `INVENTORY_READ` · `INVENTORY_WRITE` · `INBOUND_READ` · `INBOUND_WRITE` · `OUTBOUND_READ` · `OUTBOUND_WRITE` · `MASTER_READ` · `WMS_OPERATOR`(admin-service 알림 확인 · 운영 조치 — 업무 API 를 열지 않는다, ③).

### D3 — 세분 인가가 없는 도메인은 서버 인가를 먼저 넣는다 · 그 전에는 «도메인 전체» 세트 하나만 배정 가능

- SCM · ERP · Finance · E-commerce 는 지금 «진입 역할 하나 = 모든 업무 API» 다(③). 이 도메인들의 세분 세트는 **그 도메인 서버가 세분 역할을 검사한 뒤에만** 카탈로그에 들어간다. 그 전까지 이 도메인의 카탈로그 항목은 지금 역할 하나(`SCM_OPERATOR` 등 = «이 도메인 전체 업무») 뿐이고, 화면은 그것을 «전체 업무(세분 미지원)» 로 표시한다 — 세분된 척하지 않는다.
- 세분 역할을 넣는 순간 그 도메인의 **진입 역할은 업무 API 를 열지 않게** 바뀐다(진입 = 게이트웨이 통과 · 화면 진입용). 진입 역할은 배정된 업무 역할이 하나라도 있을 때 **서버가 파생**하고, 그 자체로 업무 권한을 넓히지 않는다. 도메인별 업무 구분(제안)은 § 도메인 업무 구분 제안 — 각 도메인의 계약 · 서비스 명세를 먼저 고친다(CLAUDE.md § Contracts).
- 🔴 **우회 경로 1번(②)을 같은 단계에서 닫는다**: 운영자 assume 토큰의 `scope` 에서 도메인 쓰기 범위(`erp.write`)를 뺀다(워크로드 클라이언트 `erp-platform-internal-services-client` 의 `erp.write` 는 그대로). ERP 세분 역할이 들어가기 전에 빼면 ERP 쓰기가 «운영자 역할» 로만 열리고, `ERP_OPERATOR` 를 «ERP 전체 업무» 세트로 배정받은 사람만 쓴다 — 그것도 지금보다 좁다(배정 없는 사람은 0).

### D4 — 배정은 테넌트별 · 출처별 행이다 (그룹은 fan-out 그대로, 출처 표시만 넓힌다)

- 배정 단위 = `(운영자, 테넌트, 세트, 출처)` 한 행. 출처 = `DIRECT` 또는 `GROUP(group_id)`. 같은 운영자 · 테넌트 · 세트를 직접 + 그룹 G1 + 그룹 G2 가 동시에 주면 **행이 셋**이다.
- 회수는 **자기 출처의 행만** 지운다: 직접 회수 → `DIRECT` 행만 · G1 탈퇴/삭제/세트 회수 → `GROUP(G1)` 행만. 그래서 «직접 · 그룹 중복» · «여러 그룹 중복» 에서 한쪽 회수가 다른 쪽을 지우지 않는다(④ 의 단일 `group_origin` 결함을 **반복하지 않는다**).
- 그룹 평가 방식은 ADR-046 D2 그대로 **fan-out**(멤버 가입 · 세트 배정 시 행 생성, 탈퇴 · 회수 · 그룹 삭제 시 그 그룹 출처 행 삭제). 상속(조회 시 평가)으로 바꾸지 않는다.
- 그룹의 세트 배정은 **그룹 테넌트에만** 행을 만든다(784 D-11 의 역할 fan-out 과 같은 축). 다른 테넌트에 세트를 주는 그룹 기능은 만들지 않는다.
- 784 의 «이 회사에서 내보내기»(D-9)는 그 회사의 세트 배정 행(모든 출처)도 함께 지운다.
- 🔵 기존 관리 역할 fan-out 의 단일 출처 결함(④)은 이 ADR 이 고치지 않는다 — 같은 설계(출처별 행)로 고칠 수 있다는 것만 적고 별도 티켓으로 둔다(§ Outstanding).

### D5 — 누가 배정하나 · 권한 상승 금지

- 세트 배정 · 회수 · 세트 정의 변경은 **관리 평면 권한**이다: 새 권한 키 `domain_access.manage`(가칭)를 `TENANT_ADMIN` · `SUPER_ADMIN` 에 둔다. 판정은 784 의 행동 테넌트 규칙 그대로 — 그 테넌트의 grant scope 에 이 키가 있어야 한다(A 의 관리자가 B 의 배정을 바꿀 수 없다).
- 이 키는 **도메인 업무 권한을 주지 않는다**(D1). 관리자가 자기 자신에게 세트를 주는 것을 허용할지는 OD-4.
- 배정 가능한 세트 = 그 테넌트의 세트(+ 시스템 프리셋) 중 **그 테넌트가 구독한 도메인의 것** — 구독 밖 도메인의 세트 배정은 서버가 거절한다(조직 노드 상한 밖도 거절).
- 변경과 거절은 기존 감사 규칙(`AdminActionAuditWriter` · `AdminActionDenyWriter` — DENIED 행 fail-closed)을 따른다. 새 action code 는 계약에서 정한다.

### D6 — 토큰 발급: 배정된 역할만 · 조회 실패는 «권한 0» 이다

- assume 판정 내부 응답(`/internal/operator-assignments/check`)에 **`domainRoles`**(그 테넌트에서의 실효 업무 역할 = D1 의 합, 카탈로그 교집합 전)를 더한다. auth-service 는 그것을 **entitled domains(구독 ∩ 상한)로 다시 거른 뒤**(`derive(entitled)` 교집합 — 파트너십 `delegatedScope` 와 달리 **그대로 싣지 않는다**) `roles` 에 싣는다. 구독 파생(`OperatorRoleDerivation`)은 운영자 경로에서 **제거**한다.
- 실패 처리: 판정 호출 실패 = 지금처럼 **assume 거절**(fail-closed). `domainRoles` 가 비었거나 없음 = **도메인 역할 0**(토큰은 발급되고 도메인 API 는 403). 어느 실패도 «구독 전체» 로 떨어지지 않는다 — 구독 파생 코드가 남아 있지 않으므로 떨어질 곳이 없다.
- 플랫폼 운영자(`'*'`)는 지금처럼 assume 이 통하되, 업무 역할은 **그 테넌트에서 배정된 세트**로만 받는다(OD-3 이 «`'*'` 는 전체» 를 고르지 않는 한).
- 파트너십 경로는 그대로(`delegated ∩ participant`). 🔴 파트너 운영자의 **호스트 테넌트 세트 배정은 이 경로에서 읽지 않는다** — 새 배정이 위임 상한을 넓히는 길이 생기지 않는다.
- 다른 발급 경로(①)에는 운영자 도메인 역할이 없으므로 바꿀 것이 없다. 콘솔의 매 재교환이 같은 판정을 탄다(리프레시 없음).

### D7 — 회수 지연은 숫자로 말한다 (기본값 = 지금 구조 유지 · OD-2)

- 지금 구조에서 «권한 회수 · 그룹 탈퇴 · 운영자 정지 · 구독 해지 · 세트 구성 변경» 은 **다음 재교환부터** 반영되고, **이미 발급된 assume 토큰은 `exp` 까지(최대 30분) 그대로 쓰인다.** 이 문장을 계약 · 콘솔 안내에 그대로 적는다 — «즉시 회수» 라고 쓰지 않는다.
- 단축 방법(OD-2): (가) 그대로 30분 (나) **assume 토큰만** 짧게(예: 5분 — 콘솔 재교환 빈도가 오른다, 기본 토큰 TTL 무변경) (다) 권한 버전 클레임 + 6 게이트웨이 조회(Redis) — 즉시에 가깝지만 모든 도메인 게이트웨이가 새 의존을 갖는다.
- 어느 쪽이든 단계 티켓은 **실제 지연을 시험으로 잰다**(회수 직후 기존 토큰으로 도메인 API 호출 → 언제 403 이 되는가).

### D8 — 기존 운영자 이전: 데이터로 보존하고 코드 우회는 남기지 않는다 (기본값 · OD-1)

- 이전 시점에 **이미 소속(배정 행)이 있는 비-`'*'` 운영자 × 그 테넌트**마다, 그 테넌트의 entitled domains 에 해당하는 «이전 호환» 시스템 세트(= 지금 파생과 **같은 역할 집합**)를 `MIGRATION` 출처로 한 번 배정한다(멱등 — 이미 있으면 0행). ⇒ 배포 직후 기존 사용자의 토큰 `roles` 는 **바이트 동일**하다(대조군).
- 이전 **뒤** 생긴 소속(초대 수락 · 배정 · 그룹 · 셀프 온보딩)은 세트 **0** 에서 시작한다 — 소속됐다는 이유만으로 업무 권한을 받지 않는다. 회사 생성자의 기본값은 OD-5(773).
- «이전 호환» 은 **코드 분기가 아니라 행**이다: 관리자가 회수하면 사라지고, 새 운영자에게는 생기지 않는다. 영구 우회가 되지 않는다.
- 배포 순서: ① admin 스키마 · 카탈로그 · 배정 API · 백필(토큰 무변경) → ② auth 발급 전환 → ③ 콘솔. 완료 판정 = 「이전 시점 소속 중 세트 행 0 인 것」= 0(라이브 SQL 술어를 단계 티켓 AC 에) · 복구 = ② 를 되돌리는 배포(파생 코드 복귀) — **전환 플래그로 두 경로를 계속 들고 가지 않는다**(플래그가 곧 영구 우회가 된다). 🔴 ② 의 롤백 창은 ② 머지부터 다음 AMI 굽기까지로 한정하고 그 뒤 파생 코드를 지운다.

### D9 — 콘솔: 메뉴 숨김은 편의다 · 최종 판정은 서버다

- IAM 관리 역할 화면과 도메인 업무 세트 화면을 **이름부터 나눈다**: 지금 «권한 세트»(`/permission-sets`, 실은 관리 역할 목록)는 «IAM 관리 역할» 로 바꾸고, «업무 권한 세트» 를 새로 둔다.
- 도메인 메뉴 · 화면 · 버튼은 **두 신호를 구분해 보인다**: 구독 없음(지금 배지) / 구독은 있으나 **업무 권한 없음**(새 — 서버의 실효 권한 API 로, 토큰 디코드 아님). 조회 전용 세트면 변경 버튼을 숨기되 직접 호출은 서버가 403 으로 거절한다.
- 운영자 · 그룹 화면: 테넌트별 직접 배정 · 그룹에서 받은 세트와 출처 · 합산 실효 권한 표시 · **직접 배정만 회수 버튼**.

---

## 갈래 — ACCEPT 단위 (세트를 어디에 저장하나)

| 갈래 | 저장 | 근거 · 대가 |
|---|---|---|
| **A** | **별도 모델** — `domain_permission_set`(세트) · `domain_permission_set_role`(세트 → 업무 역할) · `operator_domain_set_grant`(운영자 × 테넌트 × 세트 × 출처). `admin_roles` · `permission_set_id` 무접촉 | 관리 역할과 이름 공간이 **스키마에서** 갈린다(035 O1-C · D1). 출처별 행을 처음부터 갖는다(D4). 대가: 표 셋 · API 묶음이 새로 생긴다 |
| **B** | `admin_roles` 를 확장 — `kind`(ADMIN/DOMAIN) 칸 · 도메인 세트를 역할 행으로 · 배정은 `admin_operator_roles` | 표가 늘지 않는다. 🔴 **ADR-035 O1-C(기각)를 뒤집는다** — 관리 역할 평가 · 감사 · grant scope · ≤-own(784)이 도메인 행을 걸러야 하는 자리가 전부 새 실패 표면이 된다. 단일 `group_origin` 결함(④)을 그대로 물려받는다(PK 에 출처가 없다) |
| **C** | `operator_tenant_assignment.permission_set_id` 를 세트 표 FK 로 넓힌다(ADR-020 D5 글자 그대로) | 🔴 **ADR-082 R4 를 뒤집는다**. 배정 행 하나에 세트 **하나** — «통합팀은 여러 세트» 를 못 담는다(세트를 합친 세트를 또 만들어야). 그룹 출처도 한 칸 |

## 추천 — 🔴 **구현자의 선호**다. 소유자 결정이 아니다

**A.** ① 소유자 요구의 «여러 세트 · 여러 그룹 · 안전한 회수» 를 PK 수준에서 담는 것은 A 뿐이다(B 는 단일 출처, C 는 단일 세트). ② 이미 ACCEPTED 된 두 결정(035 O1-C · 082 R4)을 뒤집지 않는다. ③ 표 셋은 비용이지만, B 가 아끼는 표 대신 관리 평면 코드 전부가 «이 행이 관리 역할인가» 를 묻게 되는 비용이 더 크고 조용하다.

## 소유자 결정 (OD) — 갈래와 함께 받는다 (없으면 아래 기본값이 **구현자 선택**으로 남는다)

| OD | 질문 | 기본값 (구현자 선호) | 대안 |
|---|---|---|---|
| **OD-1** | 기존 운영자 이전 | **«이전 호환» 세트 백필**(D8) — 배포 직후 업무 중단 0 · 바이트 동일 | 백필 없음 — 배포 순간 기존 운영자 전원 업무 권한 0(관리자가 다시 배정) |
| **OD-2** | 회수 지연 | **(나) assume 토큰만 5분** — 콘솔이 이미 교환을 반복하므로 구조 변경이 가장 작다 | (가) 30분 유지 · (다) 권한 버전 + 게이트웨이 조회 |
| **OD-3** | 플랫폼 운영자(`'*'`)의 업무 권한 | **배정된 세트만**(D6) — `'*'` 도 예외 없음 | «`'*'` 는 구독 전체»(지금 동작 유지 — 단 그 경로가 남는다) |
| **OD-4** | 관리자의 **자기 자신** 세트 배정 | **허용 · 감사 행에 자기 배정 표시** — 관리자 하나뿐인 회사가 막히지 않는다 | 금지(다른 관리자 필요) |
| **OD-5** | 회사 생성자(셀프 온보딩 · 773) 기본 세트 | **새 테넌트의 entitled 도메인 «전체 업무» 세트를 생성자에게**(ADR-044 D2 — 새 테넌트에만) | 0 에서 시작(생성자가 스스로 배정 — OD-4 허용 필요) |
| **OD-6** | 세분 미지원 4도메인의 업무 구분 | **§ 도메인 업무 구분 제안** 그대로, 도메인별 단계 티켓에서 계약 먼저 | 도메인별로 다시 정한다 |

## 도메인 업무 구분 제안 (OD-6 — 각 도메인 서비스가 실제로 나뉘는 축)

| 도메인 | 업무 구분 (조회 / 변경) | 근거 — 지금 서비스 경계 |
|---|---|---|
| WMS | 재고 · 입고 · 출고 · 기준정보(조회만) — **이미 있음** | ③ |
| SCM | 발주(procurement) · 수요계획(demand-planning) · 물류(logistics) · 재고 가시성(조회만) | 서비스 4개 |
| ERP | 기준정보(masterdata) · 결재(approval) — 조회 모델 · 알림은 조회 | 서비스 4개 · 결재 자격(직무 분리)은 역할과 별개로 유지 |
| Finance | 계좌(account) · 원장(ledger) | 서비스 2개 · 🔴 원장의 기간 마감 · 환율 덮어쓰기를 «원장 변경» 에서 떼어 관리 등급으로 둘지는 Finance 단계 티켓이 정한다(지금 발급 안 하는 등급은 D2 에 따라 자동 부여 안 함) |
| E-commerce | 주문 · 상품(+판매자) · 회원 · 정산 · 프로모션 · 배송 · 알림 · 검색 | 관리 컨트롤러 단위 · 판매자 범위(`X-Seller-Scope`)는 아직 주입되지 않는다(ADR-030 Step 4) — 데이터 범위라 이 ADR 밖 |

## 단계 (ACCEPT 뒤 티켓 — ACCEPT PR 안에서 기안)

| 단계 | 무엇 | 서비스 | 배포 |
|---|---|---|---|
| P1 | 계약 · 명세 먼저(admin-api · auth-to-admin · rbac · data-model · 콘솔 계약) | 문서 | — |
| P2 | admin: 세트 스키마 · 카탈로그 API · 세트 CRUD · 직접/그룹 배정(출처별 행) · 실효 권한 · 출처 조회 · 감사 · 내보내기 연동 · 백필(OD-1) | admin | 백엔드(AMI) |
| P3 | auth: assume `roles` = 배정 ∩ entitled · 파생 제거 · `erp.write` 운영자 토큰에서 제거 · (OD-2) TTL | auth | 백엔드(AMI) — P2 뒤 |
| P4 | 콘솔: 화면 이름 분리 · 세트 · 운영자 · 그룹 화면 · 메뉴/버튼 두 신호 | console-web | Vercel |
| P5~P8 | 도메인별 세분 인가(계약 먼저): SCM · ERP · Finance · E-commerce — 각 도메인 프로젝트 티켓 · 그 도메인 카탈로그 확장 | 각 도메인 | 백엔드(AMI) |
| P9 | 773 연동(OD-5) — 773 착수 전 이 ADR ACCEPT 필요 | admin · console | — |

## Alternatives Considered

- **구독 파생 유지 + 콘솔에서만 메뉴 숨김** — 기각: 직접 URL · API 호출이 그대로 통한다(소유자 요구 «최종 인가는 서버»).
- **부서마다 테넌트 분리** — 기각: 테넌트는 데이터 격리 경계다(소유자 원칙). 재고팀과 출고팀은 같은 재고 데이터를 본다.
- **개별 업무 권한 직접 배정** — 범위 밖(소유자 원칙: 운영자 · 그룹에는 세트만).
- **그룹을 상속(조회 시 평가)으로 전환** — 기각: ADR-046 D2(fan-out) 존중. 출처별 행으로 같은 안전성을 얻는다(D4).
- **세트 경계를 토큰의 `scope` 로 표현** — 기각: 도메인 서비스는 역할로 판정하고(③), `scope` 는 이미 우회 경로다(②).

## Consequences

### ACCEPT 가 인가하는 것

- 위 단계 P1~P9 의 티켓 기안. 갈래에 따른 스키마. `OperatorRoleDerivation` 의 운영자 경로 제거와 `DelegatableRoleCatalog` 통합. 운영자 assume 토큰에서 `erp.write` 제거. 콘솔 «권한 세트» 화면 이름 변경.
- ADR-035 · ADR-082 · ADR-020 에 «083 이 O1-A 를 대체 · D5 의 도메인 평면을 연다 · `permission_set_id` 는 (A 면) 계속 미사용» 덧붙임(본문 바이트 그대로).

### 건드리지 않는 것

- 토큰 모양(한 토큰 = 한 테넌트, ADR-082 D1) · 조직 노드 상한 모델(ADR-047 — 도메인 단위 · 거부 전용) · 파트너십 위임 계산 · `org_scope` · 관리 역할 모델 · 그룹 fan-out 방식.

### 새로 생기는 위험

- 🔴 **세분 미지원 도메인에서 «세트를 나눴다» 가 화면에만 있다** — D3 의 «전체 업무(세분 미지원)» 표시가 없으면 관리자가 SCM 을 «조회만» 준 줄 알고 실제로는 전체 업무를 준다. 카탈로그에 없는 세분은 만들 수 없게 막는다.
- 🔴 **P3 전환 순간의 대조군**: 이전 호환 백필이 빠진 운영자가 있으면 그 사람은 업무 0 이 된다 — 완료 판정 SQL 이 0 이 아니면 P3 을 배포하지 않는다.
- E-commerce 는 게이트웨이가 유일한 판정이고 서비스 다수가 `permitAll` 이다 — 세분 역할을 게이트웨이 경로 판정에만 넣으면 서비스 간 직접 호출이 우회한다. P8 은 서비스 단 재검사를 함께 넣는다.
- 회수 지연(D7)은 OD-2 를 어떻게 고르든 0 이 아니다(다 제외).

## Verification (구현 단계에서)

- 🔴 대조군 — A 가 WMS · SCM 구독: 재고 세트 운영자 → WMS 재고 API 200 · **출고 API 403**(실패 쪽 먼저) / 출고 세트 → 반대 / SCM 세트 → SCM 200 · **WMS 직접 API 403**.
- 조회 세트 → GET 200 · 변경 API 403. 여러 세트 · 여러 그룹 = 합집합.
- 직접 + 그룹 중복 → 직접 회수 뒤에도 그룹분 유지 · G1 + G2 중복 → G1 탈퇴 뒤에도 G2 분 유지(현재 관리 역할에서 빨간 바로 그 시험의 세트 판).
- 같은 운영자 A · B 분리(A 변경이 B 토큰 `roles` 불변).
- 구독하지 않은 도메인의 세트 → 배정 거절 · 이미 배정된 뒤 구독 해지 → 다음 교환에서 그 역할 없음.
- 회수 · 그룹 탈퇴 · 운영자 정지 · 구독 해지 · 세트 구성 변경 → 기존 토큰이 403 이 되는 시각을 잰다(D7).
- 권한 상승: `domain_access.manage` 없는 운영자의 배정 변경 403 · 다른 테넌트 배정 403 · 파트너 운영자의 호스트 세트 배정이 위임 상한을 넓히지 않음.
- 판정 호출 실패 → assume 거절 · `domainRoles` 없음 → `roles` 없음(구독 전체 아님).
- 이전: 백필 직후 기존 운영자의 assume `roles` 바이트 동일 · 백필 뒤 새로 초대된 운영자의 `roles` 없음.
- 🔴 `erp.write` 제거 뒤 ERP 세트 없는 운영자의 ERP 쓰기 403(지금은 200 — 우회 경로 1번의 «전» 상태).

## Outstanding follow-ups

- ⏸️ ACCEPT 뒤 단계 티켓 기안 — ACCEPT PR 안에서.
- ⏸️ 관리 역할 그룹 fan-out 의 단일 출처 결함(④) — 별도 티켓(이 ADR 의 출처별 행 설계를 관리 역할에도 적용할지).
- ⏸️ ⚪ 그룹 행 위 직접 역할 PATCH 가 그룹 행을 감사 없이 직접 행으로 바꾸는지 — 시험으로 확인(추론만 있음).
- ⏸️ ⚪ fan 도메인의 운영자 인가 — 이번 조사에서 열지 않았다.
- ⏸️ ⚪ 라이브 모집단 — «이전 시점 소속 수 × 그 테넌트 entitled 도메인 수» = 백필 행 수. 데모 창 필요.

## 수락 형식

```
ADR-MONO-083 ACCEPTED — <A|B|C>
```

- 이름 · `ACCEPTED` · **갈래 letter** 세 요건이 한 문장에 있어야 한다. letter 만 · «진행» · 추천과 같은 선택창 응답은 수락이 아니다.
- 선택 — OD-1~OD-6 의 답을 같은 문장 또는 같은 메시지에 덧붙인다(예: `OD-2 가`). 없으면 위 기본값이 **구현자 선택**으로 남고 ACCEPT 시점에 그렇게 기록한다.

## History

| 날짜 (UTC) | 상태 | 내용 | 근거 |
|---|---|---|---|
| 2026-10-14 | PROPOSED | 실측 ①~⑤(발급 경로 전수 · `scope` 우회 · 도메인별 서버 인가 · 그룹 단일 출처 · 콘솔 · 배포). D1~D9 · 갈래 A/B/C · 추천 A(구현자 선호) · OD-1~OD-6 · 단계 P1~P9 | `TASK-MONO-790` AC-0 · PR #4335 |
| 2026-10-14 | PROPOSED → **ACCEPTED (A)** | 소유자 정확형 `ADR-MONO-083 ACCEPTED — A`. D1~D9 본문은 **바이트 그대로**. OD 답 미공급 → OD-1~OD-6 기본값(구현자 선택). 단계 티켓 P1~P8 + 후속 1건 기안 · P9 = `TASK-MONO-773` 개정 | 소유자(이 대화) · § ACCEPT 게이트 기록 |

## ACCEPT 게이트 기록 (2026-10-14 UTC)

- PROPOSED PR(#4335, `2f0577f49`) 머지 뒤 번호가 `main` 에서 확정된 상태에서, 다음 메시지로 *«ADR-MONO-083 ACCEPTED — A»* 가 도착했다. 선택창은 쓰지 않았다 — 소유자가 문장을 직접 보냈다. ACCEPT 는 그 메시지에 귀속된다.
- 갈래 A 는 § 추천(구현자 선호)과 같다. 추천이 결정으로 바뀐 근거는 그 문장이지 추천이 아니다.
- 같은 메시지에 OD 답은 없었다 — 아래 대조.

## OD 대조 (ACCEPT 시점)

| OD | 기록 | 걸리는 단계 |
|---|---|---|
| OD-1 기존 운영자 이전 | 기본값 = «이전 호환» 세트 백필(D8) — **구현자 선택** | P2 |
| OD-2 회수 지연 | 기본값 = (나) assume 토큰만 5분 — **구현자 선택** | P3 |
| OD-3 플랫폼 운영자 | 기본값 = 배정된 세트만 — **구현자 선택** | P2 · P3 |
| OD-4 자기 배정 | 기본값 = 허용 · 감사 표시 — **구현자 선택** | P2 |
| OD-5 회사 생성자 | 기본값 = 새 테넌트 «전체 업무» 세트 — **구현자 선택** | P9(773) |
| OD-6 4도메인 업무 구분 | 기본값 = § 도메인 업무 구분 제안 — **구현자 선택** | P5~P8 |

🔵 기본값은 «소유자가 고른 값» 이 아니다. 각 단계 티켓이 해당 OD 를 AC 로 싣고, 소유자는 그 단계 착수 전에 한 줄로 뒤집을 수 있다(예: `OD-2 가`).

## OD 소유자 결정 (ACCEPT 뒤 — 덧붙임, 위 표는 ACCEPT 시점 기록이라 그대로 둔다)

| 날짜 (UTC) | OD | 결정 | 근거 |
|---|---|---|---|
| 2026-10-14 | **OD-3** | **«세트 배정 허용»** — `SUPER_ADMIN`(`'*'` grant scope 의 `domain_access.manage`)만 `'*'` 운영자에게 테넌트별 세트(또는 테넌트 `'*'` = 모든 테넌트)를 배정한다. 회사 소속(배정 행)은 만들지 않는다 — ADR-082 D2 유지. 이전 호환 백필은 `'*'` 운영자에게도(`'*'` × `legacy-compat`). D6 의 «`'*'` 도 배정된 세트만» 과 모순 없음 — 배정할 **길**을 더한 것 | `TASK-MONO-791` AC-0 이 찾은 충돌: 기본값 그대로면 `'*'` 는 소속을 못 가져 세트를 받을 수 없고, `fan-platform`(플랫폼 운영자 전용 테넌트)의 `FAN_OPERATOR` 를 포함해 도메인 역할이 0 이 된다. 소유자 선택창 응답(내 추천과 같음 — ADR ACCEPT 가 아닌 OD 결정이라 선택창으로 받았다) |
| 2026-10-15 | **OD-5** (P9 — `TASK-MONO-773` OD-4 · OD-5) | **«회사 생성자의 전체 업무» = 프리셋 `all-domains-full` 한 행, 새 출처 `ORIGIN`(시스템 전용 — `MIGRATION` 과 같은 종류: API 배정 불가 · 개인 회수가 지운다 · 내보내기가 지운다)**, 새 테넌트에만(ADR-044 D2). 셀프 온보딩 생성자 · `SUPER_ADMIN` 경로의 첫 관리자(첫 관리자 초대 수락 — 첫 관리자 초대에만) 두 자리에서만 쓴다. 🔴 **D5 «구독 밖 도메인의 세트 배정은 서버가 거절» 의 예외 — 이 한 자리**: `ORIGIN` 행은 구독 검사 없이 쓴다(새 테넌트는 구독 0 · `all-domains-full` 은 고객 테넌트가 구독할 수 없는 `fan` 을 담는다). 상한은 쓰기 게이트가 아니라 D6 의 `∩ derive(entitled)`(P3) — 권한이 entitled 밖으로 넓어지지 않는다. 그 밖의 배정 경로의 D5 거절은 그대로. D1 ~ D9 본문은 바이트 그대로 | `TASK-MONO-773` AC-0 § 8-2: OD-5 기본값을 배정 API 규칙 그대로 실행하면 생성 직후 entitled = ∅ 라 어떤 세트도 줄 수 없고 `all-domains-full` 은 영원히 `422` — AC-2 의 단언이 «∅ = ∅» 로 공허해진다. 소유자 선택창 응답(2026-10-15 UTC — 773 § 12, 추천안 그대로). 계약: `admin-api.md` § 출처와 회수 규칙 · `onboarding-api.md` «회사 생성자의 업무 권한 세트» · `auth-to-admin.md` § accept «첫 관리자 초대일 때» · `data-model.md` § operator_domain_set_grant |

## ACCEPT 가 만든 새 의무 — 티켓으로 기안했다

| 단계 | 티켓 | 큐 |
|---|---|---|
| P1 계약 · 명세 | `TASK-MONO-791` | 루트 `tasks/ready/` |
| P2 admin — 세트 · 배정 · 백필 · `domainRoles` | `TASK-BE-631` | `projects/iam-platform/tasks/ready/` |
| P3 auth — 발급 전환 · `erp.write` 제거 · TTL | `TASK-BE-632` | `projects/iam-platform/tasks/ready/` |
| P4 콘솔 | `TASK-PC-FE-338` | `projects/platform-console/tasks/ready/` |
| P5 SCM 세분 인가 | `TASK-MONO-792` | 루트 (scm + iam 카탈로그) |
| P6 ERP 세분 인가 | `TASK-MONO-793` | 루트 (erp + iam 카탈로그) |
| P7 Finance 세분 인가 | `TASK-MONO-794` | 루트 (finance + iam 카탈로그) |
| P8 E-commerce 세분 인가 | `TASK-MONO-795` | 루트 (ecommerce + iam 카탈로그) |
| P9 773 연동(OD-5) | `TASK-MONO-773` 개정(선행 · AC) | 루트 `tasks/ready/` |
| § Outstanding — 관리 역할 그룹 fan-out 단일 출처 | `TASK-BE-633` | `projects/iam-platform/tasks/ready/` |
