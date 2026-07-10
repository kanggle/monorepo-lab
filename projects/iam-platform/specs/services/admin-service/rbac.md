# admin-service — RBAC Model

## Purpose

admin-service가 하드코딩된 `SUPER_ADMIN` 전제를 대체하여 **역할 기반 권한 평가(RBAC)**를 적용하기 위한 스펙. 운영자(`admin_operators`)는 하나 이상의 역할(`admin_roles`)을 부여받고, 역할은 권한 키(`admin_role_permissions`)의 집합을 보유한다. HTTP 엔드포인트는 필요한 권한 키를 선언하며, 요청 처리 전에 operator의 권한 합집합과 대조하여 통과/거부를 결정한다.

이 문서는 [specs/services/admin-service/architecture.md](./architecture.md)의 하위 컴포넌트이며, 테이블 DDL은 [specs/services/admin-service/data-model.md](./data-model.md)에서 선언된다. 본 문서는 **행동 규칙**(매트릭스, 평가 알고리즘, seed 데이터, 기록 정책, JWT claim 규약)만 다룬다.

구현은 TASK-BE-028에서 수행한다. 본 태스크(TASK-BE-027)는 specs-only.

---

## Design Decisions

### D1. Permission은 키 문자열, Role은 키의 집합

권한 키는 UPPER dotted 형식(`account.lock`, `audit.read`). role은 해당 키들의 **명시적 집합**(bitmask/계층 없음). 복수 role은 **합집합**으로 평가한다 (task Edge Cases 명시).

### D2. Missing-annotation은 deny default

컨트롤러 메서드에 `@RequiresPermission`이 선언되지 않은 경우 — **deny** (fail-closed). 실수로 권한 없이 배포된 신규 endpoint를 자동 차단하기 위한 guardrail. 의도적 public endpoint는 `@RequiresPermission(PUBLIC)` 같은 명시적 annotation(TASK-BE-028 구현 세부)으로 예외 처리한다.

`admin_actions.permission_used`에는 sentinel `"<missing>"`을 기록하여 배포 후 즉시 감지 가능하도록 한다.

### D3. DENIED도 감사 row 1건으로 기록

권한 거부는 `admin_actions`에 `outcome = DENIED` row로 기록한다. 공격자가 권한 탐색(probe)을 수행해도 모두 감사 로그에 남는다 ([rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A1·A5).

**기록 규칙 — 1 request = 1 row (no dedup)**:
- 동일 operator가 같은 endpoint를 짧은 시간에 반복 호출(spray)해도 **요청 단위로 N row 기록**한다.
- 중복 제거(dedup)·샘플링·집계 기록을 수행하지 않는다. 감사 완전성 우선.
- rate limiting은 별도 레이어(Redis 기반 operator quota, [architecture.md](./architecture.md) Integration Rules)가 담당하며, rate limit으로 차단된 요청도 차단 시점 기준 1건씩 DENIED 기록을 남긴다.

### D4. Operator 식별자는 JWT `sub` 클레임

admin-service의 operator JWT는 일반 사용자 JWT와 **분리된 발급 경로/서명 키**를 사용한다. 운영자 식별자는 JWT **`sub` 클레임**에 `admin_operators.operator_id` (UUID v7)를 실어 전달한다.

| 클레임 | 값 | 비고 |
|---|---|---|
| `sub` | `admin_operators.operator_id` (UUID v7) | 운영자 식별자 (canonical) |
| `token_type` | `"admin"` | 일반 user token과 구분. 다른 값은 admin-service에서 전부 401 |
| `iss` | `"admin-service"` | admin-service 자체 IdP가 발급한 토큰임을 식별. 다른 issuer 값은 401 ([architecture.md](./architecture.md) Admin IdP Boundary 참조). |
| `exp` / `iat` / `jti` | 표준 | `jti`는 `admin.action.performed` outbox envelope의 `actor.sessionId` 값으로 사용된다 ([data-model.md](./data-model.md) `admin.action.performed` Event Envelope 절). admin-service는 자체 세션 저장소를 두지 않으므로 `jti`가 session 식별 용도로 재활용된다. |

**선택 이유 (vs. 별도 커스텀 클레임)**:
- `sub`가 표준 JWT 클레임이며 Spring Security `Authentication.getName()`이 기본으로 참조하여 코드가 단순.
- `token_type = "admin"` 분리로 user token과의 혼용은 이미 차단되므로 `sub`를 재사용해도 충돌 없음.
- `roles` / `permissions`는 JWT에 싣지 **않는다**. role 변경 즉시 반영을 위해 매 요청마다 DB 조회(또는 짧은 TTL 캐시)로 해석한다 (D5).

### D5. Role·Permission 해석은 request-time DB 조회

JWT에 role/permission을 embed하면 role revoke 즉시성이 깨진다. 매 요청마다 `operator_id → admin_operator_roles → admin_role_permissions`를 조인하여 permission 집합을 계산한다. 성능 필요 시 Redis에 **10초 TTL** 단기 캐시를 두되, role 변경 API는 해당 캐시 키를 명시적 invalidate해야 한다 (TASK-BE-028 구현 결정).

---

## Permission Keys

본 태스크에서 정의되는 권한 키 catalog. 다른 키는 본 문서 업데이트 없이 도입 금지.

| 키 | 설명 | 대상 endpoint |
|---|---|---|
| `account.read` | 전체 계정 목록 페이지네이션 조회 | `GET /api/admin/accounts` (email 파라미터 없음) |
| `account.lock` | 계정 강제 잠금 | `POST /api/admin/accounts/{accountId}/lock` |
| `account.unlock` | 계정 잠금 해제 | `POST /api/admin/accounts/{accountId}/unlock` |
| `account.force_logout` | 특정 계정의 모든 세션 강제 종료 | `POST /api/admin/sessions/{accountId}/revoke` |
| `audit.read` | 통합 감사 로그 조회 | `GET /api/admin/audit` |
| `security.event.read` | security-service의 로그인 이력·의심 이벤트 조회 | `GET /api/admin/audit` (source=login_history|suspicious 필터 사용 시 추가 요구) |
| `operator.manage` | 운영자 계정 생성·역할 부여·상태 변경 + **RBAC role/permission 카탈로그 읽기**(TASK-BE-486) | `GET /api/admin/operators`, `POST /api/admin/operators`, `PATCH /api/admin/operators/{operatorId}/roles`, `PATCH /api/admin/operators/{operatorId}/status`, `GET /api/admin/operators/grantable-roles`, **`GET /api/admin/roles`**, **`GET /api/admin/permissions`** |
| `tenant.manage` | 테넌트 생명주기 관리(조회·생성·상태/표시명 변경). SUPER_ADMIN(플랫폼 스코프)만 보유 — read 도 동일 권한으로 일관 강제(`@RequiresPermission` 1차 게이트) 위에 inline platform-scope/tenant-allowed 2차 방어를 둔다. `V0024__seed_tenant_manage_permission.sql` 가 SUPER_ADMIN 에 seed | `GET /api/admin/tenants`, `GET /api/admin/tenants/{tenantId}`, `POST /api/admin/tenants`, `PATCH /api/admin/tenants/{tenantId}` |
| `subscription.manage` | 테넌트↔도메인 구독 생명주기 관리 (entitlement 평면; `operator.manage`와 분리 — ADR-MONO-023 D2/D3). 실제 쓰기는 account-service 로 위임 | `POST /api/admin/subscriptions`, `PATCH /api/admin/subscriptions/{tenantId}/{domainKey}/status` |
| `tenant.admin.delegate` | in-tenant sub-delegation 권한 (ADR-MONO-024 D4-B). `TENANT_ADMIN` 만 보유 — 보유자는 **자기 테넌트 한정** 으로 추가 `TENANT_ADMIN` 임명 가능 (D2 confinement 이 cross-tenant 을 구조적으로 차단). SUPER_ADMIN 은 미보유 (플랫폼-unconstrained 권한으로 위임). grant-menu 발효는 step 2b | grant-menu (`PATCH /api/admin/operators/{operatorId}/roles`, `POST /api/admin/operators`) — step 2b |
| `partnership.manage` | cross-org 파트너십 관리 권한 (ADR-MONO-045 D2/D4). `TENANT_ADMIN` 만 보유 — 보유자는 **자기 테넌트 한정**(D2 confinement 이 대상 테넌트 = acting-side 테넌트로 게이트)으로 (a) host 로서 partner 를 invite/terminate, (b) partner 로서 accept, (c) partner 로서 자기 operator 를 participant 로 배정/해제 가능. SUPER_ADMIN 은 미보유 — 파트너십은 **두 실제 고객 테넌트** 사이 관계이며 플랫폼은 당사자가 아니다(D2-C broker gate 는 deferred, `tenant.admin.delegate` 가 SUPER_ADMIN 에 ❌ 인 것과 동형). 이 키는 파트너십 **관리 표면**만 게이트하며, 그로부터 B-operator 가 A 에서 얻는 **파생 도메인-운영 권한**은 별개 축(아래 Cross-Org Partner Delegation Confinement)이 강제 | `POST /api/admin/partnerships`, `POST .../{id}:accept`, `POST .../{id}:suspend\|:reactivate\|:terminate`, `GET /api/admin/partnerships`, `POST\|DELETE .../{id}/participants/{operatorId}` |
| `org.manage` | org-node 트리 CRUD + ceiling 편집 + `ORG_ADMIN` grant/revoke (ADR-MONO-047 § D5). org-node 는 company 위의 **data-less 그룹핑 노드**로 tenant 를 격리하지 않고 grouping 한다(D1) — admin-service 는 트리를 저장하지 않고 account-service 로 위임하는 **thin command gateway** 다(subtree tenant 집합·effective ceiling 은 `/internal/org-nodes/{id}/{tenants,effective-ceiling}` 로 읽음). `SUPER_ADMIN`(ROOT 노드 생성 유일 주체)과 신규 `ORG_ADMIN`(노드에서 부여되는 company-wide 위임관리자)만 보유. `V0041__seed_org_manage_permission_and_org_admin_role.sql` 가 seed(inert/net-zero — 어떤 operator 에도 미배정). 노드 변이·ceiling·grant 의 confinement 은 아래 Org-Node Scope Confinement 가 강제 | `POST\|GET /api/admin/org-nodes`, `GET\|PATCH\|DELETE /api/admin/org-nodes/{orgNodeId}`, `PUT /api/admin/org-nodes/{orgNodeId}/ceiling`, `GET /api/admin/org-nodes/{orgNodeId}/tenants`, `GET\|POST /api/admin/org-nodes/{orgNodeId}/admins`, `DELETE /api/admin/org-nodes/{orgNodeId}/admins/{operatorId}` |

**Annotation**: 각 endpoint에는 `@RequiresPermission("account.lock")` 형식으로 단일 키를 선언한다. 복수 키가 필요한 경우(예: `GET /api/admin/audit`가 source에 따라 `audit.read` 또는 `security.event.read`를 요구)는 컨트롤러 레벨에서 최소 공통 권한을 선언하고, 내부에서 파라미터별 추가 권한을 재검사한다 (TASK-BE-028 구현).

Permission 키 오탈자 방지를 위해 문자열 상수 클래스를 TASK-BE-028에서 도입한다 (task Failure Scenarios).

> **TASK-BE-486 (role/permission 읽기 API 권한 키 결정)**: 콘솔 「권한」/「권한 세트」 화면용 read-only 조회 API(`GET /api/admin/roles`, `GET /api/admin/permissions`)는 **신규 키를 도입하지 않고 기존 `operator.manage` 를 재사용**한다. 근거: (1) 가장 가까운 형제 `GET /api/admin/operators/grantable-roles`(role 카탈로그 read)가 이미 `operator.manage` 게이트, (2) role/permission 카탈로그는 운영자 관리(역할 부여)의 참조 데이터로 동일 독자층이 소비, (3) `operator.manage` 는 이미 `SUPER_ADMIN`(V0022)·`TENANT_ADMIN`(V0033)에 seed 되어 **seed 매트릭스 변경 0** — read-only 이므로 role 정의 자체가 자주 바뀌지 않아 별도 캐시·별도 키 불필요. `GET /api/admin/permissions` 의 catalog 는 코드 canonical(`Permission.catalog()`)에서 노출하며 `<missing>` sentinel 은 제외한다.

---

## Seed Roles

마이그레이션 시점에 투입되는 기본 역할. role 이름은 `admin_roles.name`의 UNIQUE 값.

| Role | 보유 권한 | 의도 |
|---|---|---|
| `SUPER_ADMIN` | `account.read`, `account.lock`, `account.unlock`, `account.force_logout`, `audit.read`, `security.event.read`, `operator.manage`, `tenant.manage`, `subscription.manage`, `org.manage` | 전체 권한. `org.manage` 로 ROOT org-node 생성 유일 주체 (ADR-MONO-047 D5) |
| `SUPPORT_READONLY` | `account.read`, `audit.read`, `security.event.read` | CS L1. 조회 전용 |
| `SUPPORT_LOCK` | `account.lock`, `account.unlock`, `account.force_logout`, `audit.read` | CS L2. 계정 제어 + 감사 조회. security 이벤트는 열람 불가 |
| `SECURITY_ANALYST` | `audit.read`, `security.event.read`, `account.force_logout` | 보안팀. 의심 세션 긴급 종료 가능, 계정 lock/unlock은 CS 경유 |
| `TENANT_ADMIN` | `operator.manage`, `tenant.admin.delegate`, `partnership.manage` | **테넌트-scoped 위임관리자** (ADR-MONO-024 D1/D4-B, ADR-MONO-045 D2/D4). grant row 의 `admin_operator_roles.tenant_id` 로 스코프 — 자기 테넌트의 운영자 관리 + 자기 테넌트 한정 sub-delegation + 자기 테넌트를 당사자로 하는 cross-org 파트너십 관리(invite/accept/participant). IAM 평면 전용 (`subscription.manage` 미포함). SUPER_ADMIN (`'*'`) 과 달리 D2 confinement 으로 자기 테넌트에 한정됨 (TASK-BE-345). `partnership.manage` 는 파트너십 **관리 표면**만 부여하며, 그로 인한 cross-org **파생** 권한은 `delegated_scope` cap 이 별도 강제(TASK-BE-476) |
| `TENANT_BILLING_ADMIN` | `subscription.manage` | **테넌트-scoped entitlement 관리자** (ADR-MONO-024 D5-C). grant row 의 `tenant_id` 로 스코프 — 자기 테넌트의 도메인 구독 관리. `TENANT_ADMIN` 과 **별도 role** (IAM↔entitlement 위임 평면 분리, ADR-023 separability 실현) |
| `ORG_ADMIN` | `org.manage`, `operator.manage`, `tenant.admin.delegate` | **org-node-scoped company-wide 위임관리자** (ADR-MONO-047 D5) — flat `TENANT_ADMIN` 의 company-wide 아날로그, **노드에서 부여**된다. grant row 의 `admin_operator_roles.org_node_id` 로 스코프(≠ `tenant_id` — 아래 Org-Node Scope Confinement) → 노드 subtree 의 모든 서비스-tenant 에 대한 operator 관리 + subtree 한정 sub-delegation + org-node 트리/ceiling 관리. `subscription.manage` **미포함** (v1 — entitlement 는 `TENANT_BILLING_ADMIN` 유지; additive follow-up 으로만 부여 가능, 본 태스크는 부여하지 않음). SUPER_ADMIN(`'*'`)과 달리 D5 org-node confinement 으로 자기 노드 subtree 에 한정 |

### Seed Matrix (role × permission)

| permission \ role | SUPER_ADMIN | SUPPORT_READONLY | SUPPORT_LOCK | SECURITY_ANALYST | TENANT_ADMIN | TENANT_BILLING_ADMIN | ORG_ADMIN |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `account.read` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `account.lock` | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `account.unlock` | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `account.force_logout` | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `audit.read` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `security.event.read` | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `operator.manage` | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| `tenant.manage` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `subscription.manage` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `tenant.admin.delegate` | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| `partnership.manage` | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| `org.manage` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

이 매트릭스가 `admin_role_permissions` seed 데이터의 canonical source이다. TASK-BE-028의 Flyway seed script는 본 표를 기계적으로 반영해야 한다.

> **ADR-MONO-024 step 2a (TASK-BE-346)**: `TENANT_ADMIN` / `TENANT_BILLING_ADMIN` 행·열은 `V0033__seed_tenant_admin_roles.sql` 로 시드된다. **inert/net-zero** — role 정의 + 매핑만 추가하며 어떤 operator 에도 배정하지 않는다 (`admin_operator_roles` 무변경). 두 role 은 grant 된 이후에만 효력이 있고, 보유 operator 의 admin 권한은 step-1 (TASK-BE-345) D2 confinement 으로 grant row 의 `tenant_id` 에 한정된다. `tenant.admin.delegate` 가 SUPER_ADMIN 에 ❌ 인 것은 의도적 — SUPER_ADMIN 은 플랫폼-unconstrained 권한으로 위임하며 이 키를 거치지 않는다 (이 키는 *in-tenant* sub-delegation 을 게이트, grant-menu = step 2b).

> **ADR-MONO-045 step 1→2 (TASK-BE-476 spec / step-2 impl)**: `partnership.manage` 행은 `V00NN__seed_partnership_manage_permission.sql` 로 `TENANT_ADMIN` 에 시드된다. **inert/net-zero** — 기존 `TENANT_ADMIN` 보유자는 파트너십 **관리 표면**(invite/accept/participant)을 얻지만, D2 confinement 이 대상 = acting-side 테넌트로 게이트하므로 자기 테넌트가 당사자인 파트너십만 다룰 수 있고, 파트너십이 하나도 없으면 아무 파생도 없다. `partnership.manage` 가 SUPER_ADMIN 에 ❌ 인 것은 의도적 — 파트너십은 **두 실제 고객 테넌트** 사이 관계이며 플랫폼은 당사자가 아니다(D2-C SUPER_ADMIN broker gate 는 deferred; `tenant.admin.delegate` ❌ 와 동형). `TENANT_ADMIN` 이 이 키를 얻어도 cross-org **파생 도메인-운영 권한**은 `delegated_scope` cap(아래 Cross-Org Partner Delegation Confinement)이 독립적으로 강제하므로 admin 권한은 조직 경계를 넘지 않는다.

> **ADR-MONO-047 step 1→2 (TASK-BE-490 spec / TASK-BE-492 impl)**: `org.manage` 행 + `ORG_ADMIN` 열은 `V0041__seed_org_manage_permission_and_org_admin_role.sql` 로 시드된다. **inert/net-zero** — `ORG_ADMIN` 을 **어떤 operator 에도 배정하지 않으며**(`admin_operator_roles` 무변경 — `V0033__seed_tenant_admin_roles.sql` 와 동일 규율), role 정의 + 매핑만 추가한다. `ORG_ADMIN` 은 grant 된 이후에만 효력이 있고, 보유 operator 의 admin 스코프는 grant row 의 `admin_operator_roles.org_node_id`(V0042)가 구동하는 노드 subtree 로 D5 confinement 이 한정한다(아래 Org-Node Scope Confinement). `SUPER_ADMIN` 은 `org.manage` 를 추가로 얻어 ROOT 노드 생성 유일 주체가 된다. `ORG_ADMIN` 의 권한집합에 `subscription.manage` 가 ❌ 인 것은 의도적(v1 IAM 평면 전용; entitlement 는 `TENANT_BILLING_ADMIN` 유지 — additive follow-up).

### Seeding Strategy

- `V00NN+2__seed_admin_roles_and_permissions.sql`에서 `INSERT ... ON CONFLICT DO NOTHING`(MySQL은 `INSERT IGNORE`)으로 idempotent 투입.
- operator seed는 환경별로 분기: dev/staging은 고정 seed operator 1건, production은 수동 provisioning (seed script는 role/permission만 투입).
- role 이름 변경은 금지. 신규 role 추가는 본 rbac.md 업데이트 + 신규 Flyway migration 쌍으로만.

---

## Permission Evaluation Algorithm

요청 흐름에서 권한 검사는 `OperatorAuthenticationFilter` 이후, `AdminActionAuditor.begin(...)` 이전에 수행된다.

```
function evaluate(request, annotationPermission):
    # 1. operator 해석
    jwt = extractBearerToken(request)
    if jwt is null or invalid:
        respond 401 TOKEN_INVALID
        return  # audit row 없음 — 인증 실패는 upstream 필터가 처리

    operatorExternalId = jwt.claim("sub")
    tokenType = jwt.claim("token_type")
    if tokenType != "admin":
        respond 401 TOKEN_INVALID

    operator = admin_operators.findByOperatorId(operatorExternalId)
    if operator is null or operator.status != ACTIVE:
        respond 401 TOKEN_INVALID
        return

    # 2. 권한 집합 계산 (union across roles)
    roles  = admin_operator_roles.findByOperatorPk(operator.id)          # List<role_id>
    perms  = admin_role_permissions.findByRoleIds(roles).map(p -> p.permission_key)
    permissionSet = perms.toSet()   # union

    # 3. annotation 확인
    if annotationPermission is null:
        # D2: missing-annotation = deny
        recordDenied(operator, request, permissionUsed="<missing>")
        respond 403 PERMISSION_DENIED
        return

    # 4. 대조
    if annotationPermission not in permissionSet:
        recordDenied(operator, request, permissionUsed=annotationPermission)
        respond 403 PERMISSION_DENIED
        return

    # 5. 통과 — downstream command 실행
    proceed(operator, annotationPermission)
```

`recordDenied(...)`는 `admin_actions`에 다음 값을 가진 row 1건을 INSERT한다 (D3):

| 컬럼 | 값 |
|---|---|
| `action_code` | endpoint 매핑 (`ACCOUNT_LOCK`, `AUDIT_QUERY` 등). 미매핑 시 `UNKNOWN` |
| `operator_id` | `admin_operators.id` (내부 BIGINT PK) |
| `permission_used` | annotation 값 또는 `"<missing>"` |
| `target_type` / `target_id` | path variable에서 추출 가능하면 기록 (예: ACCOUNT / `{accountId}`) |
| `reason` | `X-Operator-Reason` 헤더. 없으면 공백이 아니라 `"<not_provided>"` |
| `request_id` | 요청 상관관계 ID (header 또는 생성) |
| `outcome` | `DENIED` |
| `detail` | 거부 사유 코드 (예: `MISSING_ANNOTATION`, `PERMISSION_NOT_GRANTED`) |
| `started_at` / `completed_at` | NOW() (DENIED row는 start=end 동일 값) |

DENIED row 기록과 403 응답은 **단일 트랜잭션**이어야 한다 ([rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A10 fail-closed). 감사 기록 실패 시 응답도 실패(500)하여 로그 누락을 방지한다.

### Source-IP Access Condition (ADR-MONO-026, axis ② 2단계)

권한 union 통과(step 4) **이후**, **변이(mutation) 엔드포인트**(`@RequiresPermission` 가 붙은 POST/PUT/PATCH/DELETE)는 추가로 **SOURCE_IP access condition** 게이트를 거친다 ([ADR-MONO-026](../../../../../docs/adr/ADR-MONO-026-role-grant-access-conditions.md), [platform/access-conditions.md](../../../../../platform/access-conditions.md)). 이는 RBAC → tenant-scope → data-scope 다음의 **4번째 직교 게이트**로, 닫힌 조건 enum 중 `SOURCE_IP` 타입이다.

- **carrier = 도메인 가드설정(D3-B)**: 허용 CIDR 목록은 `admin.access.source-ip-allowed-cidrs` (env `ADMIN_ACCESS_SOURCE_IP_CIDRS`) 로 admin-service 가 보유 — JWT claim·producer·IAM 무변경(consumer-side).
- **restriction-only**: step 4 통과 후에만 평가하며 권한을 **부여하지 않고 게이트만** 한다. 요청 source IP(게이트웨이 `X-Forwarded-For` 첫 홉, 없으면 remote addr)가 허용 CIDR 밖이면 `recordDenied(...)` 후 **403 `ACCESS_CONDITION_UNMET`**.
- **fail-safe**: source IP 미해석/공백 → deny.
- **net-zero / opt-in**: CIDR 미설정(기본 빈 값)이면 게이트 비활성 — 기존 동작과 byte-identical. **읽기(GET)는 게이트되지 않는다.**
- 평가는 공유 `com.example.security.access.SourceIpCondition` (`libs/java-security`) 이 수행하며, `RequiresPermissionAspect` 단일 결정 지점에서 합성된다.

> **다중 조건 AND 합성.** 4번째 게이트는 닫힌 조건 enum 을 **AND-only** 로 합성한다 — `SOURCE_IP`(위) **AND** `TIME_WINDOW`(ADR-MONO-028, `admin.access.time-window.*`) **AND** `RESOURCE_TAG`(ADR-MONO-029). 설정된 조건 중 **하나라도** 불충족이면 `403 ACCESS_CONDITION_UNMET`. 미설정 조건은 skip(net-zero) 이므로 합성은 "설정된 조건만"으로 깔끔히 degrade 한다.

### Resource-Tag Access Condition modes + resources (ADR-MONO-029, TASK-BE-353/BE-354/BE-355)

`RESOURCE_TAG` 은 **대상 리소스의 태그**(요청 아님 — **신뢰 데이터**에서만 해석, anti-spoof § D2-C)로 변이를 게이트하며 **두 모드**를 지원한다. 둘 다 빈 값(기본)이면 net-zero:

- **forbidden / deny-if-present** (`admin.access.resource-tag.forbidden`, env `ADMIN_ACCESS_RESOURCE_TAG_FORBIDDEN`) — 대상이 금지 태그(예: `protected`) 중 하나라도 보유하면 변이 거부 (TASK-BE-353).
- **required / deny-if-absent** (`admin.access.resource-tag.required`, env `ADMIN_ACCESS_RESOURCE_TAG_REQUIRED`) — 대상이 요구 태그(예: `certified`)를 **전부** 보유할 때만 변이 허용 (TASK-BE-354).

두 모드는 각각 별도의 `ResourceTagCondition` 빈(`forbidden(...)` / `required(...)`)으로, `RequiresPermissionAspect` 가 `ObjectProvider.orderedStream()` 로 **모든** 설정된 `ResourceTagCondition` 을 **AND-only** 평가한다. fail-safe: 미해석 태그(`null`)=deny; known-empty 태그셋=forbidden 하 허용·require 하 거부.

**리소스 (resolver) — 세 종류 (TASK-BE-355).** aspect 는 **모든** `ResourceTagResolver` 빈을 `orderedStream()` 로 조회한다(경로가 서로소라 요청당 최대 1개 적용; resolver 는 결정 지점에서만 호출). 태그는 전부 **admin-service 로컬 신뢰 컬럼**에서 읽으며(authz 핫패스에 cross-service 호출 없음), seed/admin-SQL 로만 설정한다(태그 set API 없음):

| 리소스 | resolver | 적용 경로 | 태그 출처 |
|---|---|---|---|
| operator | `OperatorResourceTagResolver` | `PATCH/POST /api/admin/operators/{id}/{roles\|status\|profile}` | `admin_operators.tags` |
| tenant | `TenantResourceTagResolver` | `PATCH /api/admin/tenants/{id}` | `admin_resource_tags(TENANT, id)` |
| account | `AccountResourceTagResolver` | `POST /api/admin/accounts/{id}/{lock\|unlock}` | `admin_resource_tags(ACCOUNT, id)` |

tenant/account 는 account-service 가 비즈니스 데이터를 소유하므로, anti-spoof 신뢰컬럼 불변식을 지키면서 cross-service 동기 호출을 피하기 위해 **admin-로컬 거버넌스 태그 테이블** `admin_resource_tags(resource_type, resource_id, tags)`(V0035) 를 둔다(operator 가 `admin_operators.tags` 를 로컬 보유하는 것과 동형). 컬렉션 변이(`POST /tenants`, `POST /accounts/bulk-lock`)는 단일 id 가 없어 미적용(skip).

### Target-Tenant Scope Confinement (ADR-MONO-024 D2)

권한 union 통과(step 4) 이후, **administration 표면의 변이**(operator/assignment/subscription 관리)는 추가로 **대상 테넌트 confinement**를 거친다 ([ADR-MONO-024](../../../../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md) D2). 한 operator 가 *어떤 테넌트를 관리(administer)* 할 수 있는지는 그 permission 을 부여하는 **role-grant 의 테넌트 스코프**로 결정된다 — assume-tenant 운영 스코프(`TenantScopeResolver` = home ∪ `operator_tenant_assignment`)와는 **별개의 축**이다.

**effective admin-grant scope** 정의:

```
function effectiveAdminScope(operator, permission):
    # operator 의 admin_operator_roles row 중 permission 을 부여하는 row 들을 union 으로 해소한다.
    # (ADR-MONO-047 § D5 amendment): '*' 사전 스캔 → org_node subtree → tenant.
    roleRows = admin_operator_roles.findByOperator(operator.id)   # 각 row: tenant_id (V0025/26) + nullable org_node_id (V0042)
    grantingRows = { row in roleRows if row.roleId in admin_role_permissions.roleIdsGranting(permission) }

    # (1) 플랫폼 스코프 (SUPER_ADMIN) — 어떤 subtree round-trip 보다 FIRST.
    #     루프 안의 단락으로 쓰면 안 된다: row 반복 순서에 따라 '*' row 에 닿기 전에
    #     subtreeTenantIds() 를 호출하게 되고, account-service 장애 시 그 호출이 실패하여
    #     SUPER_ADMIN 이 플랫폼 도달을 상실한다. 사전 스캔이라 순서에 무관하다.
    if any(row.tenant_id == '*' for row in grantingRows):
        return {'*'}

    scope = {}
    for row in grantingRows:
        if row.org_node_id != null:                 # (2) org-node-scoped grant (ORG_ADMIN) — subtree driver (D5)
            try:
                scope = scope ∪ subtreeTenantIds(row.org_node_id)   # account GET /internal/org-nodes/{id}/tenants
            except DownstreamFailure:               #     fail-closed: 이 row 는 공집합 기여. 절대 '*'·all-tenants 아님.
                pass                                #     (실패를 permissive 로 캐시하지 말 것)
        else:                                       # (3) tenant-scoped grant (TENANT_ADMIN) — byte-unchanged
            scope = scope ∪ { row.tenant_id }
    return scope
```

- **`'*'` short-circuit 은 어떤 subtree 분기보다 FIRST 로 평가**되어야 한다(순서 불변식): 그렇지 않으면 `SUPER_ADMIN` 이 account-service round-trip 을 지불하고, account-service 장애 시 **플랫폼 도달을 상실**한다(fail-closed 가 오히려 특권을 깎음). `'*'` 는 subtree 이전에 `{'*'}` 로 반환하므로 `requireTenantInScope` 의 `'*' in scope` 게이트는 **byte-unchanged**.
- **`subtreeTenantIds(...)` 는 fail-closed**: subtree 해소 실패(account-service down / CB open / timeout)는 해당 row 를 **공집합**으로 기여한다 — 절대 `'*'`·"모든 tenant" 아님. 캐시된 실패도 절대 permissive 취급 금지. company-wide admin 이 조용히 platform-wide 로 승격되는 것을 방지.

**confinement gate** (단일 결정 지점 — `TenantScopeGuard`):

```
function requireTenantInScope(actor, permission, targetTenantId, actionCode):
    scope = effectiveAdminScope(actor, permission)
    if '*' in scope:                          # 플랫폼 스코프 (SUPER_ADMIN) → 모든 테넌트 통과 (net-zero)
        return                                # 통과
    if targetTenantId != null and targetTenantId in scope:
        return                                # 통과
    recordCrossTenantDenied(actor, actionCode, permission, targetTenantId)   # best-effort DENIED row
    respond 403 TENANT_SCOPE_DENIED
```

- **대상 테넌트 해소**는 엔드포인트별이다(생성/관리 대상 operator 의 home `tenant_id`, assignment 의 `tenant_id`, 또는 요청 path 의 `tenantId`). 각 변이 use-case 는 *대상 테넌트만* 해소하여 gate 를 호출하며, **규칙 자체를 재구현하지 않는다**(D2-A 중앙 규칙; per-endpoint 재구현 D2-B 는 거부됨).
- `null` 대상 → **거부**(fail-closed). 모든 gated 변이는 구체적 대상 테넌트를 갖는다.
- **DENIED row 는 best-effort**(architecture.md A10 override, BE-249/262 cross-tenant deny 와 동일): 감사 실패는 `admin.audit.cross_tenant_deny_failure` 카운터만 증가시키고 403 은 항상 성립한다.
- **NET-ZERO**: 현재 `operator.manage` / `subscription.manage` 보유 role 은 `SUPER_ADMIN`(grant row `tenant_id='*'`) 뿐이라 gate 는 아무도 거부하지 않는다 — confinement 은 ADR-024 step 2 가 비-플랫폼 admin role(`TENANT_ADMIN`/`TENANT_BILLING_ADMIN`)을 seed 한 이후에만 발효한다.
- **NET-ZERO (org-node, ADR-MONO-047 D5)**: 위 (2) org-node subtree 분기는 seed 가 `ORG_ADMIN` 을 **어떤 operator 에도 배정하지 않으므로** 어떤 operator 가 `ORG_ADMIN @ node`(`org_node_id != null` 인 grant row)를 부여받기 전엔 **unreachable** — `effectiveAdminScope` 는 기존 동작과 byte-identical 하다.

대상 표면: `POST /api/admin/operators`, `PATCH .../operators/{id}/roles`, `PATCH .../operators/{id}/status`, `PUT .../operators/{id}/assignments/{tenantId}/org-scope`, `POST .../operators/{id}/assignments/{tenantId}` (assign, step 2b), `DELETE .../operators/{id}/assignments/{tenantId}` (unassign, step 2b), `POST /api/admin/subscriptions`, `PATCH /api/admin/subscriptions/{tenantId}/{domainKey}/status`.

> **org-node subtree driver (ADR-MONO-047 D5)**: 위 대상 표면들은 gate 규칙(`requireTenantInScope`) 자체는 불변이나, `effectiveAdminScope` 가 이제 node-scoped grant 를 subtree tenant 집합으로 해소하므로(위 (2) 분기) — `ORG_ADMIN @ node` 는 **동일한** 이 표면들을 통해 노드 subtree 안의 모든 tenant 의 operator/assignment 를 관리하게 된다(별도 표면 아님). org-node **트리 자체의 변이**(노드 CRUD·ceiling·`ORG_ADMIN` grant/revoke)는 이 D2 축이 아니라 아래 **Org-Node Scope Confinement** 가 게이트한다.

### Account-Data Mutation Confinement (TASK-BE-467)

위 D2 `TenantScopeGuard` 가 **administration 표면**(operator/assignment/subscription 관리)을 confine 하는 것과 **별개의 축**으로, **account 데이터 표면의 변이**(`POST /api/admin/accounts/{id}/{lock\|unlock}`, `bulk-lock`, gdpr-delete, export)는 **읽기 경로와 동일한 게이트**로 confine 된다. 읽기(`GET /api/admin/accounts`)가 이미 `QueryTenantScopeGate`(TASK-BE-357)로 활성 테넌트에 confine 되므로, 변이도 같은 게이트로 parity 를 맞춘다(TASK-BE-467 — 이전에는 account-service 가 `TenantId.FAN_PLATFORM` 리터럴에 하드핀되어 비-FAN 계정은 목록엔 보이나 변이는 실패했다).

- **게이트 = `QueryTenantScopeGate`** (D2 `TenantScopeGuard` 가 **아님**): 각 변이 컨트롤러가 `X-Tenant-Id`(활성 테넌트)를 읽어 `queryTenantScopeGate.resolve(op, header, <ActionCode>, <Permission>)` 로 해소·검증한다. 생략 → 운영자 자신의 테넌트. out-of-scope → **403 `TENANT_SCOPE_DENIED`** + best-effort DENIED row (목록 읽기와 동일).
- 해소된 테넌트는 `X-Tenant-Id` 로 account-service 에 스탬프된다(admin `AccountServiceClient` 의 mutation 호출이 `tenantId` 헤더 운반). account-service 의 admin-reachable 변이 use-case(`AccountStatusUseCase`/`GdprDeleteUseCase`/`DataExportUseCase`)는 `findById(TenantId.of(header), id)` 로 조회한다.
- **cross-tenant 대상 → `404 ACCOUNT_NOT_FOUND`** (enumeration-safe): tenant-scoped `findById` 가 빈 결과 → 기존 not-found 경로. 타 테넌트 존재를 확인해 주는 403 이 아니다. 새 cross-tenant finder 를 추가하지 않는다(격리 우회 금지).
- **NET-ZERO**: SUPER_ADMIN(`'*'`)/헤더 부재 → account-service 는 `fan-platform` 기본값 폴백 = BE-467 이전과 byte-identical. `X-Tenant-Id` 를 실제로 지정하는 현재 유일 경로는 assume-tenant 스위치뿐이라 기존 동작 무변.
- **bulk-lock**: 배치 진입 시 1회 해소된 테넌트를 모든 per-row `LockAccountCommand` 이 상속 — cross-tenant row 는 해당 row 만 `ACCOUNT_NOT_FOUND` outcome, 배치는 200 유지.
- **session-revoke** 는 admin-service 가 동일하게 해소·스탬프하나(BE-467 propagation), **실제 enforce 는 auth-service** 가 수행한다(TASK-BE-468) — credential-tenant 게이트로 cross-tenant force-logout 을 **no-op**(`200 count=0`, DB revoke·Redis 무효화 미수행) 처리. 상세: [auth-service/architecture.md](../auth-service/architecture.md) · [admin-api.md](../../contracts/http/admin-api.md#tenant-confinement--x-tenant-id-task-be-467).

> **세 confinement 축 구분.** D2 `TenantScopeGuard` = *operator-administration* 표면(대상 = 관리 대상 operator/assignment/subscription 의 테넌트, 위반 → **403 TENANT_SCOPE_DENIED**). BE-467 `QueryTenantScopeGate` = *account-data* 표면(대상 = 계정의 `tenant_id`, 위반 → **404**, 읽기 경로와 동형). ADR-045 Cross-Org Partner Delegation Confinement(아래) = *cross-org 파생 도메인-운영* 축(대상 = 파트너십에서 파생한 host 테넌트 reach, 캡 = `delegated_scope ∩ participant ∩ host-holds`) — 앞의 두 축이 admin/account 표면을 confine 하는 것과 달리, 이 축은 **assume-tenant 발급 시 파생되는 도메인 권한**을 attenuate 하며 **admin 권한은 절대 확장하지 않는다**. 세 문장: "누가 어느 테넌트를 관리(administer)할 수 있나"(D2), "이 계정이 활성 테넌트에 속하나"(BE-467), "partner 의 operator 가 host 를 어느 범위까지 operate 할 수 있나"(ADR-045).

### Grant-Menu No-Escalation (ADR-MONO-024 D3, step 2b)

역할을 *부여*하는 표면(`POST /api/admin/operators`, `PATCH .../operators/{id}/roles`)은 target-tenant confinement 에 더해 **grant-menu no-escalation** 규칙을 거친다 ([ADR-MONO-024](../../../../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md) D3, 단일 결정 지점 `RoleGrantGuard`):

```
function requireGrantable(actor, rolesToGrant, actionCode):
    if '*' in effectiveAdminScope(actor, operator.manage):   # 플랫폼 스코프 (SUPER_ADMIN)
        return                                                # unconstrained (net-zero)
    for role in rolesToGrant:
        if role.name == 'SUPER_ADMIN':                        # (a) 플랫폼/특권 role 금지
            recordRoleGrantForbidden(...); respond 403 ROLE_GRANT_FORBIDDEN
        rolePerms = admin_role_permissions(role)
        if rolePerms nonempty and not actor.permissions ⊇ rolePerms:   # (b) ≤-own
            recordRoleGrantForbidden(...); respond 403 ROLE_GRANT_FORBIDDEN
```

- **≤-own** (보유하지 않은 권한을 부여 불가)이 **위임 role 승인 규칙을 기계적으로 인코딩**한다: `TENANT_ADMIN` 의 권한집합은 `tenant.admin.delegate` 를 포함하므로 *그것을 보유한* actor 만 `TENANT_ADMIN` 을 부여(in-tenant sub-delegation, D4-B)할 수 있고, `subscription.manage` 보유자만 `TENANT_BILLING_ADMIN` 을 부여(D5-C)할 수 있다. 별도의 명시적 "이 role 은 이 권한 필요" 분기는 불필요.
- **빈 권한집합 role** 은 누구나 부여 가능(≤-own 자명).
- **플랫폼 스코프(SUPER_ADMIN `'*'`) 는 unconstrained** — 모든 role 부여 가능(플랫폼 온보딩 불변, net-zero).
- 위반 → 403 `ROLE_GRANT_FORBIDDEN` + best-effort DENIED row(`admin.audit.role_grant_forbidden_failure` 카운터). **테넌트 confinement(어느 테넌트)** 와 **grant-menu(어느 role)** 는 별개 — 전자는 step-1 `TenantScopeGuard`, 후자는 `RoleGrantGuard`.

### Assign/Unassign Surface (ADR-MONO-024 D3-i, step 2b)

`POST/DELETE /api/admin/operators/{operatorId}/assignments/{tenantId}` — operator↔tenant `operator_tenant_assignment` row 생성/삭제("내 직원에게 내 테넌트 접근 부여"). `operator.manage` + step-1 target-tenant confinement(target=path `tenantId`) + reason-gated + audited(`OPERATOR_ASSIGNMENT_CREATE`/`OPERATOR_ASSIGNMENT_DELETE`). 생성 시 whole-tenant(`org_scope=null` ⟺ `["*"]`, `permission_set_id=null` = operator-level role 상속); 중복 → 409 `ASSIGNMENT_ALREADY_EXISTS`; 삭제 시 미존재 → 404 `ASSIGNMENT_NOT_FOUND`. SUPER_ADMIN(`'*'`) net-zero.

### Cross-Org Partner Delegation Confinement (ADR-MONO-045 D3/D5)

앞의 두 축(D2 administration, BE-467 account-data)과 **별개의 세 번째 축**으로, **partner 테넌트 B 의 operator 가 host 테넌트 A 를 운영(operate)할 때의 파생 도메인 권한**을 attenuate 한다. 이는 ADR-024 within-tenant 위임을 **조직 경계 너머로** 확장한 유일한 경로이며(ADR-MONO-045 D3), 위임의 origination 만 신규이고 다운스트림(operator·role·assume-tenant)은 ADR-020/024 를 재사용한다 — 파트너십은 **관리된 envelope**이지 격리 우회가 아니다(D5).

**핵심 불변식 — 파트너십은 admin scope 를 확장하지 않는다.** cross-org actor(B-operator)가 A 에서 갖는 것은 **오직 도메인-운영 권한**(assume-tenant 도메인 토큰의 `entitled_domains` + 도메인 role)이며, A 에 대한 `effectiveAdminScope`(D2, 누가 `/api/admin/**` 를 호출 가능한가)는 **여전히 공집합**이다. 따라서:

- `effectiveAdminScope(operator, permission)`(D2) 정의는 **byte-unchanged** — `admin_operator_roles` 만 읽고 파트너십을 보지 않는다. B-operator 가 A 에서 `/api/admin/operators` 등 관리 표면을 시도하면 admin scope 공집합 → **403**(파트너십과 무관). partner 는 *scoped guest*, 절대 co-admin 이 아니다.
- 파트너십 **관리 표면**(`/api/admin/partnerships/**`) 자체는 `partnership.manage` + D2 `TenantScopeGuard`(대상 = acting-side 테넌트: invite/host-terminate → host, accept/participant/partner-terminate → partner)로 게이트된다 — 이 축이 아니라 **기존 D2 축**을 재사용.

**파생 스코프 계산** — assume-tenant 발급기(`/internal/operator-assignments/check`, ADR-020 D2)가 읽는 **effective tenant scope** 가 파트너십-파생 host 테넌트를 additive 하게 포함하고, 그 host 에서의 도메인 권한은 아래 교집합으로 캡된다:

```
function crossOrgPartnershipScope(operator, hostTenant):
    # operator(home = partner_tenant_id) 가 hostTenant 를 reach 할 수 있는지 + 그 범위
    p = tenant_partnership.findActive(host=hostTenant, partner=operator.tenant_id)   # status=ACTIVE 만
    if p is null:
        return EMPTY                       # 파트너십 없음/PENDING/SUSPENDED/TERMINATED → reach 0
    part = tenant_partnership_participant.find(p.id, operator.id)
    if part is null:
        return EMPTY                       # 이 operator 는 참여자가 아님 → reach 0
    # 삼중 교집합 (D3): host 위임 ∩ partner 좁힘 ∩ host 실제 보유
    scope = p.delegated_scope
    if part.participant_scope is not null:
        scope = scope ∩ part.participant_scope        # participant_scope ⊆ delegated_scope
    scope = scope ∩ hostEntitledScope(hostTenant)     # host-holds: request-time 은 IDENTITY(unbounded) — 아래 참고
    return scope    # {domains, roles} — 도메인 토큰의 entitled_domains + role 로 주입, admin 권한 0
```

- **single-`tenant_id` 토큰 (M1 보존)**: assume-tenant 는 여전히 선택된 host 하나의 `tenant_id` 토큰을 발급하며, `entitled_domains`/role 이 위 `scope` 로 캡될 뿐이다. cross-org multi-tenant 토큰은 발급하지 않는다(D5-B 거부).
- **≤-own across org — enforcement 는 invite-time (TASK-BE-479)**: "host 는 자신이 가진 것 이상을 위임할 수 없다"는 **invite 시점에 구체적으로 강제**된다: (a) `delegated_scope` 의 각 **도메인 ∈ host 의 ACTIVE 도메인 구독**(account-service = D2 entitlement authority; command 경로라 cross-service 조회 허용) — 미보유 도메인 → `422 PARTNERSHIP_SCOPE_INVALID`; (b) 각 **role ∈ `DelegatableRoleCatalog`**(operator-tier 도메인 role 만 = auth-service `OperatorRoleDerivation` 값집합의 미러; admin-tier `*_ADMIN`·`WMS_ADMIN` 및 tenant-admin 3종 제외) → 위반 시 422. account-service 장애 시 **fail-CLOSED**(위임 미발급). — **request-time 의 `∩ hostEntitledScope` 는 의도적으로 IDENTITY(unbounded)** 다(`UnboundedHostEntitledScopeResolver`): (i) ADR-020 §3.1 이 assume-tenant hot-path 의 cross-service 콜백을 금지하고, (ii) step 2b(BE-478)가 mint 시 `entitled_domains = host-ACTIVE ∩ delegated.domains` 로 도메인을 이미 클립하므로 host 가 도메인을 잃으면 그 도메인 role 은 게이트에서 inert. 삼중교집합 항은 알고리즘 형태 보존 + 미래 로컬 host-holds 미러를 위한 seam 으로 유지. admin role 은 `delegated_scope` 에 애초에 들어갈 수 없다(data-model 불변식 + invite containsAdminRole).
- **no transitive re-delegation (confused-deputy default deny)**: participant 는 자신이 파생한 A-scope 를 제3 조직 C 에게 재위임할 수 없다. participant 는 B 소유 operator 로 한정되고 스스로 origination 지점이 될 수 없으므로 B→C-into-A 는 구조적으로 표현 불가 — 향후 전이 위임은 별도 결정(ADR-024 within-tenant sub-delegation confinement 의 cross-org 미러).
- **cascade-revoke (D6, request-time fail-closed)**: 파트너십이 `SUSPENDED`/`TERMINATED` 로 전이하면 `findActive` 가 null → 그 파트너십에서 파생한 **모든** participant 의 reach 가 다음 요청(발급/갱신)에서 즉시 0 이 된다(per-operator sweep 불필요 — 파생이 사라짐, perm-cache TTL ~10초로 bounded). 개별 participant 제거(B 가 자기 직원 offboard) 시에도 그 operator 의 `find(p.id, operator.id)` 가 null → 동일하게 즉시 소멸. 종료 시 **one-shot** `partnership.terminated` 감사 이벤트 1건([partnership-events.md](../../contracts/events/partnership-events.md)) — operator 당 N 이벤트 아님.
- **half-state**: `PENDING`(invite 미수락) 파트너십은 `findActive` 에서 제외 → 파생 0. B-operator 는 accept(ACTIVE) 전엔 A 를 assume 할 수 없다.
- **NET-ZERO**: 파트너십이 하나도 생성되지 않으면 `findActive` 는 항상 null → 아무도 아무 것도 파생하지 않는다. 이 축은 ADR-045 §3.4 step 2 가 첫 파트너십을 ACTIVE 로 만든 이후에만 발효 — 기존 assume-tenant 동작과 byte-identical.

**대상 표면**: assume-tenant 발급 경로(`GET /internal/operator-assignments/check` 의 effective-scope 파생 + 도메인 토큰 `entitled_domains`/role customizer). 파트너십 **관리** 엔드포인트(`/api/admin/partnerships/**`)는 이 축이 아니라 D2 `TenantScopeGuard`(대상=acting-side 테넌트)로 게이트되며, cross-org actor 의 admin 표면 시도는 공집합 admin scope 로 403 된다(위 핵심 불변식).

### Org-Node Scope Confinement (ADR-MONO-047 D5)

앞의 세 축(D2 administration, BE-467 account-data, ADR-045 cross-org)과 **별개의 네 번째 축**으로, **org-node 트리 자체의 변이**(노드 CRUD·ceiling 편집·`ORG_ADMIN` grant/revoke)를 confine 한다. org-node 는 company 위의 **data-less 그룹핑 노드**로 tenant 를 격리하지 않고 grouping 한다(ADR-MONO-047 § D1) — admin-service 는 트리를 저장하지 않고 account-service 로 위임하는 **thin command gateway** 다(subtree tenant 집합·effective ceiling 은 `GET /internal/org-nodes/{id}/tenants`·`/effective-ceiling` 로 읽음). 이 축은 D2 + D5 에서 파생하며 AWS Organizations SCP-attach parity 다.

**ceiling 모델** (no-escalation cap 의 근거; 트리 자체는 account-service 소유):

```
Ceiling := UNBOUNDED | BOUNDED(set of domainKey)
UNBOUNDED = ceiling 없음 = 교집합 항등원 ("모든 known 도메인"이 아님)
BOUNDED({}) = 아무것도 불허. UNBOUNDED 와 BOUNDED({}) 는 정반대.
permits(c, d)          = (c == UNBOUNDED) || d ∈ c.domains
effectiveCeiling(node) = root..node chain 의 교집합
```

**administers 관계 정의**:

```
administers(actor, N)         = actor 가 SUPER_ADMIN, 또는 ancestors(N) ∪ {N} 중 어느 노드 A 에서 ORG_ADMIN 보유
strictlyAdministers(actor, N) = actor 가 SUPER_ADMIN, 또는 N 의 STRICT ancestor 에서 ORG_ADMIN 보유
```

**변이 규칙** (단일 결정 지점 — org-node command gateway):

| 변이 | 요구 |
|---|---|
| ROOT 노드 생성 (`parentId = null`) | `SUPER_ADMIN` 전용 |
| N 의 자식 생성 / N 개명 | `administers(actor, N)` |
| `PUT /{N}/ceiling` | **`strictlyAdministers(actor, N)`** — `ORG_ADMIN @ N` 은 N 자신의 ceiling 을 편집할 수 없다(그 ceiling 이 자기 bound 이므로 self-escalation; AWS 에서 자기 OU 에 attach 된 SCP 를 그 안에서 detach 불가와 정확히 동형). 위반 → 403 `ORG_NODE_SELF_CEILING_DENIED` |
| N 재부모화(re-parent) | `strictlyAdministers(actor, N)` **AND** `administers(actor, newParent)` |
| N 삭제 | `strictlyAdministers(actor, N)` (+ invariant I4 — 자식 없음·tenant 없음) |
| `ORG_ADMIN @ N` grant | `administers(actor, N)` **AND** granted role ⊆ actor 보유 **AND** granted domains ⊆ `effectiveCeiling(N)` **AND** role ≠ `SUPER_ADMIN` (no-escalation 은 ADR-024 D2/D3 재사용 — 재발명 아님) |

**핵심 불변식 — ceiling 은 좁히기만, org-node 는 admin scope 를 확장하지 않는다.**

- **ceiling narrows-only** — 노드 ceiling 은 tenant 의 reach 를 **좁히기만** 하고 절대 grant 하지 않는다(ADR-MONO-047 D2). `UNBOUNDED`(= 교집합 항등원, "모든 known 도메인"이 **아님**)와 `BOUNDED({})`(아무것도 불허)는 **정반대**다.
- **`ORG_ADMIN` 은 자기 노드 ceiling 편집 불가** — `ORG_ADMIN @ N` 은 N 자신의 ceiling 을 편집할 수 없고(self-escalation), `strictlyAdministers(actor, N)`(STRICT ancestor 또는 SUPER_ADMIN)만 통과한다.
- **subtree 실패 = fail-closed** — subtree 해소 실패(account-service down / CB open / timeout)는 해당 grant 를 **공집합**으로 기여한다. 절대 `'*'`·"모든 tenant" 아님. 캐시된 실패도 절대 permissive 취급 금지(위 `effectiveAdminScope` fail-closed 와 동일).
- **`SUPER_ADMIN` 은 절대 mintable 아님** — `ORG_ADMIN` grant 은 `role ≠ SUPER_ADMIN` 을 강제한다(ADR-024 D3 재사용).
- **cross-scope 는 404** — actor 스코프 밖의 노드/tenant 대상은 **404 `ORG_NODE_NOT_FOUND`**(403 아님 — 403 은 subtree 밖 노드/tenant 의 존재를 누설; 기존 cross-scope confinement 규약과 동형) + best-effort DENIED `admin_actions` row.

**감사**: `admin_actions` action code = `ORG_NODE_CREATE` / `ORG_NODE_UPDATE` / `ORG_NODE_DELETE` / `ORG_NODE_CEILING_SET` / `ORG_ADMIN_GRANT` / `ORG_ADMIN_REVOKE`, `target_type='ORG_NODE'`, `target_id=<orgNodeId>`. GET 성공은 감사 row 미기록(`grantable-roles` / BE-486 read-path 규약); 403 은 best-effort DENIED row.

**에러 코드**: 403 `PERMISSION_DENIED`, 403 `TENANT_SCOPE_DENIED`, 403 `ORG_NODE_SELF_CEILING_DENIED`, 404 `ORG_NODE_NOT_FOUND`(cross-scope 포함), 422 `ORG_ADMIN_GRANT_OUT_OF_CEILING`, 503 `INTEGRATION_UNAVAILABLE`.

**NET-ZERO**: seed 는 `ORG_ADMIN` 을 **어떤 operator 에도** 배정하지 않으므로(V0041 inert — `V0033__seed_tenant_admin_roles.sql` 와 동일 규율) 이 축은 어떤 operator 가 `ORG_ADMIN @ node` 를 부여받기 전엔 발효하지 않는다 — `effectiveAdminScope` 의 subtree 분기도 그때까지 unreachable, 기존 동작과 byte-identical. ROOT 노드 생성 유일 주체는 `SUPER_ADMIN`(`org.manage` 추가 보유).

**대상 표면**: `POST|GET /api/admin/org-nodes`, `GET|PATCH|DELETE /api/admin/org-nodes/{orgNodeId}`, `PUT /api/admin/org-nodes/{orgNodeId}/ceiling`, `GET /api/admin/org-nodes/{orgNodeId}/tenants`, `GET|POST /api/admin/org-nodes/{orgNodeId}/admins`, `DELETE /api/admin/org-nodes/{orgNodeId}/admins/{operatorId}`.

### Conditional Cross-Permission: `GET /api/admin/audit`

이 엔드포인트는 `source` query parameter 값에 따라 요구 권한이 달라진다. 컨트롤러 annotation은 최소 공통 권한(`audit.read`)만 선언하고, 내부에서 아래 알고리즘으로 추가 권한을 재검사한다.

```
function evaluateAuditRead(operator, permissionSet, request):
    source = request.queryParam("source")                # "admin_actions" | "login_history" | "suspicious" | null

    # 기본 요구 권한
    required = {"audit.read"}

    # source에 따라 security.event.read 추가 요구 (교집합이 아닌 조건부 확장 = AND)
    if source == "login_history" or source == "suspicious":
        required.add("security.event.read")
    # source == "admin_actions" 또는 null(전체 통합 조회이되 admin_actions 기본 소스로 제한):
    #   → audit.read만 요구

    # 평가
    missing = required - permissionSet
    if missing is empty:
        proceed(operator, action="AUDIT_QUERY",
                permissionUsed = joinKeys(required))     # "audit.read" 또는 "audit.read+security.event.read"
        return

    # DENIED
    if required.size == 2:
        permissionUsed = "audit.read+security.event.read"   # 복합 키 — 어떤 권한이 부족했는지가 아니라 요구된 전체 집합을 기록
    else:
        permissionUsed = "audit.read"
    recordDenied(operator, request,
                 actionCode = "AUDIT_QUERY",
                 permissionUsed = permissionUsed,
                 detail = "MISSING_PERMISSIONS=" + join(missing, ","))
    respond 403 PERMISSION_DENIED
```

**규칙 요약**:

- `source=login_history` 또는 `source=suspicious` → **`audit.read` AND `security.event.read`** 모두 필요
- `source=admin_actions` 또는 `source` 미지정 → **`audit.read`만** 필요
- DENIED 시 `admin_actions.permission_used`:
  - 단일 요구였던 경우: `"audit.read"`
  - 복수 요구였던 경우: 복합 키 `"audit.read+security.event.read"` (어떤 쪽이 부족했는지는 `detail` 필드에 `MISSING_PERMISSIONS=...`로 기록)
- 응답 본문에는 어느 permission이 누락되었는지 노출하지 않는다 (공격자에게 권한 구조 누설 방지, [admin-api.md](../../contracts/http/admin-api.md) 403 Response Shape 절과 일치).

---

## Caching & Invalidation (구현 가이드)

- Redis 키: `admin:operator:perm:{operator_id}` → permission key set, TTL 10초
- `admin_operator_roles` / `admin_role_permissions` 변경 API는 해당 key를 즉시 DELETE
- 캐시 miss 시 DB fallback, 동시 다발 miss는 mutex 불필요 (TTL 10초로 thundering herd 무시 가능)

세부 구현은 TASK-BE-028에서 확정.

---

## Testing Expectations (specs-level)

| 레이어 | 시나리오 |
|---|---|
| Unit | permission union across multiple roles, missing-annotation → deny, annotation mismatch → deny, operator.status != ACTIVE → 401 |
| Integration | seed role × endpoint 매트릭스를 table-driven으로 검증 (4 role × 6 endpoint ≈ 24 조합) |
| Audit | DENIED 시 `admin_actions` row 1건 기록, spray 10회 → 10 row (no dedup, D3) |
| Fail-closed | `admin_actions` INSERT 실패 주입 → 403이 아닌 500 반환, downstream 호출 없음 |
| Unit (org-node, ADR-047 D5) | `org.manage` union; `effectiveAdminScope` 3-way 해소 — `'*'` short-circuit **FIRST**(subtree round-trip 이전 반환), `org_node_id != null` → subtree 분기, else tenant 분기; `administers`/`strictlyAdministers` 판정; no-escalation 매트릭스(role ⊆ 보유 ∧ domains ⊆ effectiveCeiling ∧ role ≠ SUPER_ADMIN); `subtreeTenantIds` 실패 → **공집합**(절대 `'*'`/all) |
| Integration (org-node) | `ORG_ADMIN @ node` → subtree 전체 tenant reach; subtree 밖 tenant/노드 → **404**(403 아님) + DENIED row; `SUPER_ADMIN` byte-unchanged(round-trip 없음); over-ceiling grant → 422 `ORG_ADMIN_GRANT_OUT_OF_CEILING`; `admin_actions`(`ORG_NODE_*`/`ORG_ADMIN_*`) row 기록; GET 성공은 감사 row 미기록 |
| Security (org-node) | 모든 org-node 엔드포인트 deny-default(non-`org.manage` → 403 + DENIED row); `ORG_ADMIN @ N` 의 자기 노드 ceiling 편집 → 403 `ORG_NODE_SELF_CEILING_DENIED`; `SUPER_ADMIN` grant 시도 → 403(never mintable) |
| Fail-closed (org-node) | account-service down/CB/timeout → subtree **공집합**(admin 이 reach 를 잃음 — 절대 `'*'`/all tenants); 캐시된 실패 permissive 취급 안 함 |

---

## Assume-Tenant Assignment Check — `/internal/operator-assignments/check` (TASK-BE-327 / ADR-MONO-020 D2)

auth-service 의 assume-tenant 발급기가 issuance-time **one-shot** 으로 호출하는
read-only 인가 표면. 운영자(IAM OIDC subject)의 **effective tenant scope**(BE-326
D1 dual-read: `operator_tenant_assignment` rows ∪ {legacy `admin_operators.tenant_id`})
가 선택된 customer tenant 를 포함하는지 boolean 으로 응답한다.

- **판정**: `findByOidcSubject` fail-closed (row 없음/비-ACTIVE → `assigned=false`);
  `'*'` platform-scope (`isPlatformScope()`) → 비-blank tenant 모두 `true`
  (sentinel 이 모든 tenant 부여 — 단 assumed 토큰은 선택된 구체 tenant_id 를
  운반, `'*'` 토큰은 도메인-facing 토큰에 절대 발급되지 않는다);
  그 외 → `TenantScopeResolver.resolveEffectiveTenantScope(...).contains(tenantId)`.
- **권한 평가(`RequiresPermissionAspect`) 비경유**: 이 엔드포인트는 `/internal/**`
  체인에 속하며 operator 권한이 아니라 IAM `client_credentials` 워크로드 JWT 로
  인가된다. 따라서 `@RequiresPermission` annotation 도, `admin_actions` row 도
  적용되지 않는다 (read-only — ADR-014 token-exchange "not audited" 규칙 동일).
- **`/internal/**` 체인**: `SecurityFilterChain @Order(0)` + `securityMatcher("/internal/**")`,
  IAM JWKS resource-server, account-service 미러링. 미인증 → 401 `UNAUTHORIZED`.
  기존 operator 체인(`@Order(2)`, `/api/admin/**`)은 byte-unchanged.
- D1 effective-scope 가 이제 auth-service 에 cross-service 로 읽힌다. 스키마 변경 없음.

상세: [architecture.md](./architecture.md) "Assume-Tenant Assignment Check", [specs/contracts/http/internal/auth-to-admin.md](../../contracts/http/internal/auth-to-admin.md).

---

## Open Issues (to TASK-BE-028)

- `@RequiresPermission` annotation + `AspectJ` 또는 `HandlerInterceptor` 중 선택
- Permission 상수 클래스 배치 (application 레이어 vs infrastructure/security)

> TASK-BE-028c에서 확정됨: 다중 인스턴스 간 캐시 무효화 전파는 **TTL 10초 단독 의존**으로 결정. Pub/Sub 기반 push invalidation은 추후 backlog 항목(미지정 태스크)으로 이월. 프로세스-로컬 `invalidate(operatorId)`는 공유 Redis 인스턴스의 키를 즉시 DELETE하므로 같은 Redis를 바라보는 모든 admin-service 인스턴스에 반영된다. TTL 단독 의존의 한계(최대 10초 stale window)는 별도 backlog에서 다룬다.

---

## Reserved for TASK-BE-029 (2FA)

- `admin_operators.totp_secret_encrypted`, `admin_operators.totp_enrolled_at` 컬럼은 본 태스크에서 NULL 허용 placeholder로만 생성
- `admin_roles.require_2fa` 컬럼은 생성하되 권한 평가에서 참조하지 않음 (TASK-BE-029에서 `OperatorAuthenticationFilter`에 `totp_verified_at` 클레임 검사 추가 예정)
- 본 문서에서 2FA 정책은 정의하지 않는다 — Out of Scope

### Recovery Codes Hashing Policy (TASK-BE-029a 결정)

2FA enrollment 시 발급되는 **recovery codes**(10개, one-time-use)의 저장 정책:

- **Canonical hasher**: `libs/java-security.PasswordHasher` (Argon2id). 서비스 단위 우회 금지.
- **이유**: [rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R2 — 전 플랫폼 canonical 비밀 값 hashing은 Argon2id로 통일되어 있으며, BCrypt/PBKDF2/sha 등 서비스별 변종은 금지된다. recovery code는 로그인 크리티컬 경로는 아니지만 **동일한 canonical 정책**을 적용한다.
- **Parameter**: `libs/java-security`에 선언된 기본 프로파일을 그대로 사용(별도 tuning 금지). 검증 시 `PasswordHasher.matches(code, hash)` 사용.
- **저장 컬럼**: `admin_operator_totp.recovery_codes_hashed` (JSON array of Argon2 hash strings) — 실제 스키마는 [data-model.md](./data-model.md) V0012 마이그레이션에서 확정.
- **사용 정책**: 1회 사용 후 해당 entry 삭제 또는 `used_at` 마킹(구현 선택, 029에서 결정). 재사용 시 401.
- TASK-BE-029의 "BCrypt" 류 문구는 본 섹션을 canonical source로 정정된다.
