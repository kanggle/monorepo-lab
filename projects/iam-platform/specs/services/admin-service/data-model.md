# admin-service — Data Model

## Design Decision

admin-service는 도메인 상태를 거의 소유하지 않는 **command gateway**이다 ([architecture.md](./architecture.md)). 자체 DB에는 다음만 보관한다:

1. **RBAC 메타데이터** — operator / role / permission 바인딩 (본 서비스 내부 폐쇄 모델)
2. **감사 원장** — `admin_actions` append-only
3. **Outbox** — `admin.action.performed` 발행 버퍼

운영자 계정은 일반 사용자(`account-service.accounts`)와 **물리적으로 분리된 테이블**(`admin_operators`)에 저장한다. operator 인증 경계는 일반 사용자 인증 경계와 완전히 다른 JWT 발급 경로를 사용하며([rbac.md](./rbac.md) JWT claim 절), 두 식별자 공간은 절대 혼용되지 않는다.

RBAC의 의사결정(권한 평가 알고리즘, seed role 매트릭스, missing-annotation 정책, DENIED 기록 규칙)은 [rbac.md](./rbac.md)에서 정의한다. 본 문서는 **테이블 DDL과 데이터 분류만** 담당한다 (단일 책임 분리).

---

## Tables

### `admin_operators`

운영자 계정. 일반 사용자 `accounts`와 완전히 분리된 네임스페이스.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | 내부 PK |
| `operator_id` | VARCHAR(36) | UNIQUE, NOT NULL | internal | UUID v7. JWT `sub` 클레임에 실리는 외부 식별자 ([rbac.md](./rbac.md) JWT claim 절) |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | **confidential** | 로그인 식별자. PII ([rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R1). 응답에서 기본 마스킹 |
| `password_hash` | VARCHAR(255) | NULL | **restricted** | argon2id 해시. 평문 저장 금지 (R2). **TASK-BE-377 / ADR-MONO-035 4c**: `NOT NULL` → **NULL** 로 강등(break-glass). 운영자 PRIMARY 로그인은 통합 IAM OIDC credential(`token-exchange` 경유, ADR-MONO-014)이며, 이 로컬 비밀번호는 IdP/OIDC 불가용 시의 **비상(break-glass)** 로그인으로만 잔존. `NULL` = OIDC-only 운영자(로컬 비밀번호 없음 — `AdminLoginService` 가 fail-closed: 비밀번호 로그인 거부, OIDC 로만 인증). 완전 제거는 OIDC-only 입증 후 후속(deferred) |
| `display_name` | VARCHAR(120) | NOT NULL | **confidential** | 감사 UI에 표시되는 이름. 개인 식별 가능 (R1) |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | internal | `ACTIVE` / `DISABLED` / `LOCKED`. 소프트 비활성 플래그 |
| `totp_secret_encrypted` | VARBINARY(255) | NULL | **restricted** | TOTP 시크릿 (envelope encryption). **TASK-BE-029 예약 컬럼** — 본 태스크에서는 NULL만 허용 |
| `totp_enrolled_at` | DATETIME(6) | NULL | internal | TOTP 등록 완료 시각. **TASK-BE-029 예약 컬럼** — 본 태스크에서는 NULL만 허용 |
| `oidc_subject` | VARCHAR(255) | NULL, UNIQUE | internal | **신규 (TASK-BE-298, Flyway V0027)** — IAM OIDC `platform-console-web` access token 의 `sub`(account_id UUID). `POST /api/admin/auth/token-exchange` 의 OIDC↔operator **링크 키**. `NULL` = console-token-exchange 미허용 운영자 (fail-closed 기본값 — 명시적 provisioning 으로만 활성화). 플랫폼-전역 UNIQUE (OIDC subject 공간은 테넌트 무관 — 아래 §OIDC Subject ↔ Operator Link Key). 스코프 상승 비-소스: 이 컬럼은 **링크에만** 쓰이고, tenant 스코프는 `tenant_id` 가 단일 진실 소스 |
| `finance_default_account_id` | VARCHAR(36) | NULL | internal | **신규 (TASK-BE-304, Flyway V0028)** — 운영자가 platform-console `Operator Overview` (ADR-MONO-017 / `console-integration-contract.md § 2.4.9.1`) 의 finance 카드에서 기본으로 조회할 finance-platform 계정 UUID. `NULL` = 미설정 — platform-console 가 finance 카드를 `forbidden / MISSING_PREREQUISITE` 로 렌더링 (MVP option (b) 잔존 path). 비-NULL = `console-registry-api.md` 응답의 finance product item 에 `operatorContext.defaultAccountId` 로 emit (option (a) 활성화). **불투명 식별자** — IAM 는 finance-platform 에 verify 하지 않음 (cross-service decoupling); stale 값은 BFF 호출 시 finance `404 ACCOUNT_NOT_FOUND` 로 자연 노출. 인덱스 부재 (조회는 항상 operator row PK 단건). 분류 = **internal** (운영자가 선택한 외부 시스템 식별자; PII 아님, credential 아님). |
| `last_login_at` | DATETIME(6) | NULL | internal | 마지막 operator JWT 발급 시각 |
| `created_at` | DATETIME(6) | NOT NULL | internal | — |
| `updated_at` | DATETIME(6) | NOT NULL | internal | — |
| `version` | INT | NOT NULL, DEFAULT 0 | internal | 낙관적 락 (T5) |

**인덱스**:
- `uk_admin_operators_operator_id` UNIQUE (`operator_id`)
- `uk_admin_operators_email` UNIQUE (`email`) — V0025에서 복합
  `uk_admin_operators_tenant_email (tenant_id, email)` 로 대체됨 (TASK-BE-249)
- `idx_admin_operators_status` (`status`) — 활성 운영자 조회
- `uk_admin_operators_oidc_subject` UNIQUE (`oidc_subject`) — **신규
  (TASK-BE-298, V0027)**. NULL 다수 허용(MySQL UNIQUE 는 다중 NULL 허용),
  non-NULL 값은 플랫폼-전역 유일. `token-exchange` 의 OIDC subject → operator
  결정적 단건 조회 인덱스

> **OIDC Subject ↔ Operator Link Key (TASK-BE-298 / ADR-MONO-014 § D3 sub-decision)**
>
> ADR-MONO-014 D3 은 OIDC subject ↔ operator 매핑이 **명시적·결정적·
> operator-row-authoritative**(OIDC token 이 스코프를 상승시키지 않음)여야
> 한다고만 고정하고, 링크 키 선택을 IAM 구현 태스크의 sub-decision 으로
> 위임했다. **선택: provisioned `admin_operators.oidc_subject VARCHAR(255)
> NULL UNIQUE` 컬럼** (vs. verified-email 매칭).
>
> **근거 (oidc_subject 선택, email 기각)**:
> - **결정성/안정성**: OIDC `sub`(auth-service account_id UUID)는 불변·전역
>   유일 식별자. email 은 변경 가능하며 `admin_operators` 에서 `(tenant_id,
>   email)` 복합 UNIQUE 라 **테넌트 무관한 OIDC `sub` 단독으로는 단건
>   해석이 모호**(같은 email 이 다중 테넌트에 존재 가능, OIDC token 은
>   tenant claim 을 신뢰 소스로 쓰지 않음 → email+tenant 조인 불가).
> - **PII 결합 회피**: email 은 `confidential` 등급 PII (R1). operator
>   인증 경계의 링크 키를 PII 에 결합하면 R1/R4 노출면이 커진다.
>   `oidc_subject`(불투명 UUID, `internal` 등급)는 비-PII 키.
> - **명시적 fail-closed 기본값**: `oidc_subject` 는 NULL 기본. 운영자는
>   명시적 provisioning 으로만 console-exchange 가 활성화된다 — 대다수
>   운영자는 NULL 이라 exchange 시 fail-closed `401`. email 매칭이면 모든
>   기존 운영자가 암묵적으로 exchange 가능해져 fail-closed 원칙 위배.
> - **스코프 상승 비-소스**: 이 컬럼은 **링크에만** 사용. tenant 스코프는
>   `admin_operators.tenant_id`(ADR-002 sentinel 포함)가 단일 진실 소스이며
>   OIDC token 의 어떤 claim 도 스코프 결정에 쓰이지 않는다.
>
> **Provisioning**: `oidc_subject` 채움은 별도 운영 경로(operator
> provisioning / 향후 admin API)의 책임 — 본 태스크는 컬럼+제약+링크 해석만
> 도입한다. dev/test seed 는 Flyway dev migration 또는 IT seed 에서 직접
> 설정한다.
>
> **TASK-MONO-299 (ADR-MONO-040 Phase 3 part B) — account_id 단독으로 정착**: 위
> 근거대로 `oidc_subject` 는 account_id UUID 이다. 과거 시드/프로비저닝은 운영자
> email 을 채웠으나(federation `seed.sql`, dev V0028 일부), part A(TASK-MONO-298)가
> email → account_id backfill 메커니즘 + 시드 마이그레이션을 제공했고, part B
> (TASK-MONO-299)가 Phase-2 의 임시 **DUAL-KEY** email fallback 을 제거했다 —
> `assignment-check`(`OperatorAssignmentCheckUseCase`)·login-time exchange
> (`TokenExchangeService`) 모두 운영자를 account_id(`sub`) **단독**으로 조회한다.
> 서버사이드 `account_id → email` 해석(`X-Subject-Email` 헤더 /
> `GET /internal/auth/credentials/{accountId}/email`)은 함께 제거되었다.
> (실배포 전제: part-A backfill 선행 필수 — 미migrate 운영자는 fail-closed 된다.
> demo/e2e 시드는 account_id 이므로 stranded 없음.) **어떤 access token 에도
> email 은 탑재되지 않는다.**

### `admin_roles`

역할 카탈로그. Seed 값은 [rbac.md](./rbac.md) "Seed Roles" 절에서 정의한다.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `name` | VARCHAR(40) | UNIQUE, NOT NULL | internal | `SUPER_ADMIN` / `SUPPORT_READONLY` / `SUPPORT_LOCK` / `SECURITY_ANALYST`. UPPER_SNAKE_CASE |
| `description` | VARCHAR(255) | NOT NULL | internal | 운영자 관리 UI용 설명 |
| `require_2fa` | BOOLEAN | NOT NULL, DEFAULT FALSE | internal | 역할 보유 시 2FA 강제 플래그. **TASK-BE-029 예약 컬럼** — 본 태스크에서는 읽기만 하고 강제 평가는 TASK-BE-029에서 활성화 |
| `created_at` | DATETIME(6) | NOT NULL | internal | — |

**인덱스**:
- `uk_admin_roles_name` UNIQUE (`name`)

### `admin_role_permissions`

역할 ↔ 권한 키 바인딩. 권한 키 카탈로그는 [rbac.md](./rbac.md) "Permission Keys" 절이 canonical.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `role_id` | BIGINT | NOT NULL, FK → `admin_roles.id` ON DELETE CASCADE | internal | — |
| `permission_key` | VARCHAR(80) | NOT NULL | internal | 예: `account.lock`, `audit.read`. 값 검증은 애플리케이션 레벨 상수와 대조 |

**Primary Key**: (`role_id`, `permission_key`) 복합 PK
**인덱스**:
- `idx_admin_role_permissions_permission` (`permission_key`) — 특정 권한을 가진 role 역추적

### `admin_operator_roles`

운영자 ↔ 역할 바인딩. 다대다.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `operator_id` | BIGINT | NOT NULL, FK → `admin_operators.id` ON DELETE CASCADE | internal | **`admin_operators.id` (BIGINT PK) 참조**. 외부 UUID `operator_id` 컬럼이 아님 |
| `role_id` | BIGINT | NOT NULL, FK → `admin_roles.id` ON DELETE RESTRICT | internal | role 삭제는 해당 role의 운영자 바인딩이 모두 제거된 뒤에만 허용 |
| `granted_at` | DATETIME(6) | NOT NULL | internal | 역할 부여 시각 |
| `granted_by` | BIGINT | NULL, FK → `admin_operators.id` | internal | 부여자 operator. seed 투입 시 NULL |
| `tenant_id` | VARCHAR(32) | NOT NULL | internal | **TASK-BE-249 (Flyway V0025)** — 역할 바인딩의 테넌트 스코프. **canonical 규칙: 바인딩된 operator(`operator_id` → `admin_operators.id`)의 `tenant_id` 값을 그대로 미러링한다.** SUPER_ADMIN(플랫폼 스코프) operator 의 바인딩은 sentinel `'*'`. 멀티테넌트 행 레벨 격리 + 감사 라우팅의 근거. 전체 테넌트 스코프 모델·sentinel·backfill 정책은 [docs/adr/ADR-002-admin-tenant-scope-sentinel.md](../../../docs/adr/ADR-002-admin-tenant-scope-sentinel.md)가 canonical (`admin_operators.tenant_id` / `admin_actions.tenant_id`·`target_tenant_id` 포함) |
| `org_node_id` | VARCHAR(36) | NULL, `CHECK (org_node_id IS NULL OR tenant_id <> '*')` | internal | **신규 (TASK-BE-490 spec / TASK-BE-492 impl, Flyway V0042, ADR-MONO-047 § D5)** — org-node-scoped grant(`ORG_ADMIN`)의 **스코프 드라이버**. 비-NULL 이면 이 grant 의 effective admin-scope = 노드 subtree 의 tenant 집합(account-service `GET /internal/org-nodes/{id}/tenants` 로 해소; [rbac.md](./rbac.md) `effectiveAdminScope` 3-way 해소). `NULL` = 종전 tenant-scoped grant(byte-unchanged). **CHECK**: platform-scoped grant(`tenant_id='*'`)은 노드를 동시에 가질 수 없다 — 플랫폼은 이미 전체 도달이라 조합이 무의미하며 `'*'` short-circuit 이 subtree 를 무력화하므로 DB + 애플리케이션 양쪽에서 거부. **FK 부재** — account-service 소유 `org_node.id` 를 참조하는 **불투명 식별자**(cross-service decoupling; `admin_operators.oidc_subject`/`finance_default_account_id` 와 동형). `org_node` 트리·cycle/depth·ceiling 수학은 admin-service 가 저장하지 않는다(account-service = TASK-BE-491) |
| `group_origin` | BIGINT | NULL, DEFAULT NULL, FK → `operator_group.id` ON DELETE CASCADE | internal | **신규 (TASK-BE-519 spec / TASK-BE-520 impl, Flyway V00NN, ADR-MONO-046 § D2-A/D5)** — fan-out 마커. `NULL`(기본) = **직접 grant**(운영자에게 직접 부여된 역할 바인딩, 종전 동작 byte-unchanged). 비-NULL = 이 역할 바인딩이 해당 `operator_group` 에 대한 group-grant 의 fan-out 으로 **materialise** 되었음을 기록(ADR-MONO-045 cascade trail 의 형제). **nullable + DEFAULT NULL 이라 기존 모든 직접 grant row 는 마이그레이션 후에도 byte-identical**(backward-compatible). cascade-revoke(remove-member/delete-group)는 `group_origin = <groupId>` 로 **엄격 필터**하여 직접 grant(`group_origin IS NULL`)를 절대 파괴하지 않는다(아래 `group_origin` 마커 절 idempotence 불변식). 평가는 이 컬럼을 **보지 않는다** — 마커는 lifecycle 부기 전용, `PermissionEvaluator`/perm-cache byte-unchanged([rbac.md](./rbac.md) Operator Group Fan-Out). 같은 서비스 소유 `operator_group.id` 참조라 `org_node_id`(cross-service 불투명 식별자)와 달리 **실제 FK**(ON DELETE CASCADE — delete-group 시 fan-out row 자동 제거, 단 감사·outbox 원자성을 위해 애플리케이션이 명시 revoke 도 수행) |

> **Per-tenant 바인딩 불변식 (TASK-BE-289 WI-2)**: 모든 `admin_operator_roles` 행 생성 경로(`CreateOperatorUseCase`, `PatchOperatorRoleUseCase`)는 `tenant_id`를 대상 operator 의 `tenant_id`와 **반드시 일치**시켜야 한다. 하드코딩 기본값(`"fan-platform"`) 금지 — `PatchOperatorRoleUseCase`가 V0025 이전 레거시 4-arg 팩토리로 인해 이 불변식을 위반했던 회귀(TASK-BE-288 review Finding 1)가 V0026 backfill + 격리 회귀 테스트로 종결됨.

> **Org-node scope-driver 불변식 (TASK-BE-490 / ADR-MONO-047 D5)**: scope 해소 순서는 **(1) `tenant_id='*'` → PLATFORM**(전체 union 을 FIRST 로 단락, account-service round-trip 이전) → **(2) `org_node_id IS NOT NULL` → subtree tenant 집합** → **(3) else `{tenant_id}`**([rbac.md](./rbac.md) `effectiveAdminScope`). **`tenant_id` 는 재활용하지 않는다** — node-scoped grant 에서도 `tenant_id` 는 여전히 바인딩된 operator 자신의 `tenant_id` 를 미러링하는 **감사 라우팅/행-격리 컬럼**(위 WI-2 불변식)이며, `TENANT_ADMIN` 의 경우 스코프와 **우연히 일치**할 뿐이다. 스코프는 별개의 `org_node_id` 컬럼이 구동한다. subtree 해소 실패(account-service down/CB/timeout)는 **fail-closed**(해당 row 공집합 기여 — 절대 `'*'`/all). `org_node_id IS NOT NULL ∧ tenant_id='*'` 는 CHECK + 애플리케이션 양쪽에서 거부(hand-written seed/ops SQL 도 차단).

**Primary Key**: (`operator_id`, `role_id`) 복합 PK
**인덱스**:
- `idx_admin_operator_roles_role` (`role_id`) — 역할별 운영자 역검색

### `operator_group`

**신규 (TASK-BE-519 spec / TASK-BE-520 impl, Flyway `V00NN__create_operator_group_tables.sql`, ADR-MONO-046 D1/D3)** — `admin_operators` 를 **named unit** 으로 묶는 admin-service 소유 **tenant-scoped aggregate**. 역할/tenant-assignment 를 **여러 operator 에 한 번에** 부여(fan-out, D2-A)하는 **grant 의 단위이자 cascade-revoke 의 단위**(D5)다(AWS IAM User Group / Google Group 의 workforce-grouping facet). operator 는 admin-service 소유이므로 그 grouping 도 admin-service 에 속한다(D1). group 자체는 **평가 경로가 아니다** — grant 는 각 멤버의 flat `operator_tenant_assignment`/`admin_operator_roles` row 로 materialise 된다([rbac.md](./rbac.md) Operator Group Fan-Out).

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | 내부 PK. 외부 노출 식별자는 `group_id`(아래). fan-out row 의 `group_origin` 마커가 참조하는 값 |
| `group_id` | VARCHAR(36) | UNIQUE, NOT NULL | internal | UUID v7. HTTP path(`/api/admin/groups/{groupId}`)·이벤트 partitionKey 에 실리는 외부 식별자(`admin_operators.operator_id`/`tenant_partnership.partnership_id` 와 동형) |
| `tenant_id` | VARCHAR(32) | NOT NULL, `CHECK (tenant_id <> '*')` | internal | group 의 소유 테넌트(D3). `TenantScopeGuard` 대상 = 이 값 — `TENANT_ADMIN @ acme` 는 acme 그룹만, `SUPER_ADMIN`(`'*'`) net-zero(모든 group). **sentinel `'*'` 금지**(CHECK + 애플리케이션): group 은 **한 실제 테넌트 안** operator 의 unit 이며 플랫폼-전역 그룹은 v1 스코프 밖(`tenant_partnership` 이 `'*'` 를 금지하는 것과 동형). 멤버는 이 테넌트 소속 operator 만(`operator_group_member` 불변식) |
| `name` | VARCHAR(120) | NOT NULL | internal | 그룹 표시명. `(tenant_id, name)` 테넌트 내 UNIQUE(중복 → `409 GROUP_NAME_CONFLICT`) |
| `description` | VARCHAR(255) | NULL | internal | 운영자 관리 UI용 설명 |
| `created_by` | BIGINT | NULL, FK → `admin_operators.id` ON DELETE SET NULL | internal | 그룹을 생성한 operator. seed/시스템 경로는 NULL |
| `created_at` | DATETIME(6) | NOT NULL | internal | — |
| `updated_at` | DATETIME(6) | NOT NULL | internal | — |
| `version` | INT | NOT NULL, DEFAULT 0 | internal | 낙관적 락 (T5) — 동시 rename/grant/member 경합 방지 |

**인덱스**:
- `uk_operator_group_group_id` UNIQUE (`group_id`)
- `uk_operator_group_tenant_name` UNIQUE (`tenant_id`, `name`) — 테넌트 내 그룹명 유일(중복 invite → `409 GROUP_NAME_CONFLICT`)
- `idx_operator_group_tenant` (`tenant_id`) — 테넌트별 그룹 목록(`GET /api/admin/groups`, D3 read confine)

> **그룹 불변식 (TASK-BE-519 / ADR-MONO-046 D3)**:
> - `tenant_id != '*'` — 그룹은 한 실제 테넌트 안 operator 의 unit. 플랫폼-전역 그룹은 v1 스코프 밖(CHECK + 애플리케이션 양쪽 거부; hand-written seed/ops SQL 도 차단).
> - `(tenant_id, name)` 테넌트-scoped UNIQUE — 서로 다른 테넌트는 같은 그룹명을 독립적으로 가질 수 있다(M1 row isolation).

### `operator_group_member`

**신규 (TASK-BE-519 spec / TASK-BE-520 impl, Flyway `V00NN__create_operator_group_tables.sql`, ADR-MONO-046 D1/D5)** — 그룹 ↔ operator 멤버십 edge. 멤버 추가 시 그룹의 현행 grant 가 그 멤버로 fan-out 되고(D5), 멤버 제거 시 그 멤버의 `group_origin=<groupId>` row 가 revoke 된다(직접 grant 불변, D5). 이 멤버십 테이블은 **평가-시점에 조회되지 않는다**(fan-out, 평가는 flat row 만 읽음 — [rbac.md](./rbac.md) Operator Group Fan-Out).

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `group_id` | BIGINT | NOT NULL, FK → `operator_group.id` ON DELETE CASCADE | internal | **`operator_group.id` (BIGINT PK) 참조**. 외부 UUID `group_id` 컬럼이 아님. 그룹 삭제 시 멤버십 edge 는 DB CASCADE 로 제거되나, 그 멤버들의 fan-out `group_origin` row revoke 는 별도 애플리케이션 cascade(감사·outbox 원자성, D5) |
| `operator_id` | BIGINT | NOT NULL, FK → `admin_operators.id` ON DELETE CASCADE | internal | 멤버 operator. **canonical 불변식(아래): 이 operator 의 home `tenant_id` 는 그룹의 `tenant_id` 와 반드시 일치**(`tenant_partnership_participant` 의 own-operator 불변식과 동형) — 그룹은 자기 테넌트 operator 만 담는다 |
| `added_at` | DATETIME(6) | NOT NULL | internal | 멤버 추가 시각 |
| `added_by` | BIGINT | NULL, FK → `admin_operators.id` ON DELETE SET NULL | internal | 멤버를 추가한 operator. seed/시스템 경로는 NULL |

**Primary Key**: (`group_id`, `operator_id`) 복합 PK — 동일 (group, operator) 중복 멤버십 방지(중복 add → `409 GROUP_MEMBER_ALREADY_EXISTS`)
**인덱스**:
- `idx_operator_group_member_operator` (`operator_id`) — **역검색: 한 operator 가 속한 그룹 집합**(operator 삭제/조회 시 cascade 대상 파악)

> **멤버 불변식 (TASK-BE-519 / ADR-MONO-046 D3/D5)**:
> - 멤버 operator 의 `admin_operators.tenant_id` == 부모 그룹의 `tenant_id` — 그룹은 자기 테넌트 operator 만 담는다(cross-tenant 멤버는 fan-out 이 tenant confinement 을 넘게 만든다). 위반 → `422 GROUP_MEMBER_TENANT_MISMATCH`.
> - 멤버 추가는 그룹의 현행 grant 를 그 멤버로 fan-out 하며, 이때 no-escalation cap(D4)이 **재검사**된다(그룹 관리자가 자기 미보유 grant 를 새 멤버에게 우회 부여 불가). fan-out 은 멤버가 이미 보유한 동등 직접 grant 를 중복 생성하지 않는다(idempotence, 아래 `group_origin` 마커 절).

### `operator_group_grant`

**신규 (TASK-BE-519 spec / TASK-BE-520 impl, Flyway `V00NN__create_operator_group_tables.sql`, ADR-MONO-046 D5)** — 그룹에 부여된 **grant 템플릿**(역할 또는 tenant-assignment). fan-out 은 이 템플릿을 각 멤버로 materialise 한다. 이 테이블이 **없으면 D5 의 "add-member → 그룹의 현행 grant 를 새 멤버로 fan-out" 과 멤버 0 인 그룹의 grant 보존이 불가능**하다(멤버 rows 만으로는 grant 를 복원할 수 없음) — 그래서 group-level grant 는 별도 aggregate 로 영속한다. materialise 된 per-operator row 는 `operator_tenant_assignment`/`admin_operator_roles` 에 `group_origin=<group.id>` 로 태깅되며(아래 절), 개별 grant 는 `group_origin` + 그 row 자신의 `role_id`/`tenant_id` 자연키로 식별된다(그룹 내 grant 는 `(type, role/tenant)` 로 유일 — 아래 UNIQUE).

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | 내부 PK. 외부 노출 식별자는 `grant_id`(아래) |
| `grant_id` | VARCHAR(36) | UNIQUE, NOT NULL | internal | UUID v7. HTTP path(`/api/admin/groups/{groupId}/grants/{grantId}`)에 실리는 외부 식별자 |
| `group_id` | BIGINT | NOT NULL, FK → `operator_group.id` ON DELETE CASCADE | internal | **`operator_group.id` (BIGINT PK) 참조**. 그룹 삭제 시 grant 템플릿도 CASCADE(멤버 fan-out row 는 별도 애플리케이션 cascade-revoke, D5/D6) |
| `grant_type` | VARCHAR(20) | NOT NULL | internal | `ROLE`(역할 부여) \| `TENANT_ASSIGNMENT`(tenant-assignment 부여). fan-out 대상 substrate 를 결정 |
| `role_id` | BIGINT | NULL, FK → `admin_roles.id` ON DELETE RESTRICT | internal | `grant_type=ROLE` 일 때 부여 역할. 참조 role 은 바인딩 존재 시 삭제 불가(RESTRICT) |
| `tenant_id` | VARCHAR(32) | NULL | internal | `grant_type=TENANT_ASSIGNMENT` 일 때 부여 대상(ASSIGNED) 테넌트. no-escalation cap(D4)이 grant/add-member 시점에 granter 보유 이내로 강제 |
| `granted_by` | BIGINT | NULL, FK → `admin_operators.id` ON DELETE SET NULL | internal | grant 를 부여한 operator. seed/시스템 경로는 NULL |
| `granted_at` | DATETIME(6) | NOT NULL | internal | grant 부여 시각 |

**제약**:
- `CHECK ((grant_type='ROLE' AND role_id IS NOT NULL AND tenant_id IS NULL) OR (grant_type='TENANT_ASSIGNMENT' AND tenant_id IS NOT NULL AND role_id IS NULL))` — grant_type 에 맞는 정확히 한 참조만 채워짐.

**인덱스**:
- `uk_operator_group_grant_grant_id` UNIQUE (`grant_id`)
- `uk_operator_group_grant_natural` UNIQUE (`group_id`, `grant_type`, `role_id`, `tenant_id`) — 그룹 내 동일 grant 중복 방지(중복 → `409 GROUP_GRANT_ALREADY_EXISTS`); fan-out row 를 `group_origin` + 자연키로 유일 식별하는 근거
- `idx_operator_group_grant_group` (`group_id`) — 그룹의 현행 grant 목록(add-member fan-out·`GET .../grants`)

### `group_origin` 마커 (fan-out substrate)

**신규 (TASK-BE-519 spec / TASK-BE-520 impl, Flyway `V00NN__add_group_origin_marker.sql`, ADR-MONO-046 D5)** — fan-out row 가 직접 grant 와 **동일한** `operator_tenant_assignment` / `admin_operator_roles` 테이블에 살면서(별도 테이블 아님) group-materialise 여부를 구별하는 discriminator. ADR-MONO-045 cascade-trail 의 형제 개념.

- **`admin_operator_roles.group_origin`** BIGINT NULL DEFAULT NULL, FK → `operator_group.id` ON DELETE CASCADE — 위 `admin_operator_roles` 표에 컬럼 정의. group 이 **역할**을 grant 하면 각 멤버의 역할 바인딩이 이 마커와 함께 materialise 된다.
- **`operator_tenant_assignment.group_origin`** BIGINT NULL DEFAULT NULL, FK → `operator_group.id` ON DELETE CASCADE — 같은 형상으로 `operator_tenant_assignment`(V0030, `operator_id`+`tenant_id` 복합 PK) 에 추가한다. group 이 **tenant-assignment** 를 grant 하면 각 멤버의 assignment row 가 이 마커와 함께 materialise 된다. (V0030 의 완전 DDL 은 Flyway 마이그레이션이 canonical — 본 절은 추가 컬럼만 정의, `org_scope`/`permission_set_id` 형제.)

**분류**: 두 컬럼 모두 **internal**(같은 서비스 소유 `operator_group.id` 를 참조하는 lifecycle 부기 값 — PII·credential 아님).

**backward-compatible**: `NULL` + `DEFAULT NULL` 이라 마이그레이션 후 기존 모든 직접 grant row 는 byte-identical 하게 `group_origin IS NULL`(= 직접 grant) 로 남는다. 마커가 nullable+defaulted 인 점이 곧 마이그레이션이 backward-compatible 인 이유다.

> **Idempotence & cascade 불변식 (TASK-BE-519 / ADR-MONO-046 D5)**:
> - **fan-out 은 동등 직접 grant 를 중복 생성하지 않는다** — 두 테이블 모두 grant 를 유일하게 식별하는 복합 PK(`operator_tenant_assignment` = `(operator_id, tenant_id)`, `admin_operator_roles` = `(operator_id, role_id)`)를 갖는다. 멤버가 이미 그 key 의 row 를 보유하면(직접이든 다른 그룹발이든) fan-out 은 **no-op skip**(PK 충돌 없음). 즉 grant 당 최대 1 row.
> - **cascade-revoke 는 `group_origin = <groupId>` 로 엄격 필터** — remove-member(그 멤버의 `group_origin=<groupId>` row) / delete-group(그 그룹의 모든 `group_origin=<groupId>` row)은 마커가 정확히 일치하는 row 만 삭제하고, **직접 grant(`group_origin IS NULL`)는 절대 파괴하지 않는다**. FK ON DELETE CASCADE 가 delete-group 시 DB 레벨 안전망을 제공하나, 감사(`admin_actions`)·outbox 원자성을 위해 애플리케이션이 명시 revoke 로 수행한다(D5/D6).
> - **단일-소유 마커(v1 규약)** — `group_origin` 은 스칼라(하나의 그룹 id)이고 PK 상 grant 당 1 row 이므로, 두 그룹 G1·G2 가 같은 `(operator, grant)` 를 grant 하면 **먼저 materialise 한 그룹**이 마커를 소유하고 두 번째 fan-out 은 no-op skip 된다. 따라서 G1 삭제 시 G2 가 여전히 "의도"하더라도 그 row 는 revoke 된다(G2 가 다음 grant/멤버 재적용 시 재-materialise). 이는 fan-out(D2-A) 의 알려진 v1 특성이며, membership-을-평가-edge 로 승격하는 inheritance(D2-B, 후속 ADR)가 자연히 해소한다. **어느 경우에도 직접 grant 는 손상되지 않는다**(cascade 는 `group_origin IS NULL` 을 절대 건드리지 않음).

### `tenant_partnership`

**신규 (TASK-BE-476 / ADR-MONO-045 D1, Flyway `V00NN__create_partnership_tables.sql`)** — 두 **독립 소유** 테넌트 사이의 **cross-org 파트너십**을 모델링하는 일급 aggregate. host 테넌트 A 가 partner 테넌트 B 에게 bounded `delegated_scope` 를 위임하며, **grant 의 단위이자 revocation 의 단위**(D6 cascade)이다. B 의 operator 가 A 를 assume-tenant 로 운영하는 권한은 **오직** ACTIVE 파트너십에서 파생된다(D4/D5). admin-service 는 자체 aggregate 를 거의 두지 않는 command gateway 이나(§Design Decision), 파트너십은 admin-service 소유 관계 상태이므로(ADR-MONO-045 D8 — 모든 재사용 프리미티브가 admin-service 에 존재) 예외적으로 로컬 aggregate 로 보관한다.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | 내부 PK. 외부 노출 식별자는 `partnership_id`(아래) |
| `partnership_id` | VARCHAR(36) | UNIQUE, NOT NULL | internal | UUID v7. HTTP path·이벤트 partitionKey 에 실리는 외부 식별자 |
| `host_tenant_id` | VARCHAR(32) | NOT NULL | internal | 위임하는 host 테넌트(A). `delegated_scope` 의 authoring 주체. sentinel `'*'` 금지(플랫폼은 파트너십의 host 가 될 수 없음 — 아래 불변식) |
| `partner_tenant_id` | VARCHAR(32) | NOT NULL | internal | 위임받는 partner 테넌트(B). B 의 `TENANT_ADMIN` 이 accept + participant 관리(D4). sentinel `'*'` 금지 |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | internal | `PENDING` → `ACTIVE` → `SUSPENDED`/`TERMINATED`. PENDING=invite 미수락(파생 0). 상태머신은 [admin-api.md](../../contracts/http/admin-api.md#partnership-management-adr-mono-045) 전이 매트릭스가 canonical |
| `delegated_scope` | JSON | NOT NULL | internal | host 가 위임하는 bounded `{domains}×{roles}` 집합: `{"domains": ["wms","scm"], "roles": ["WMS_OUTBOUND_OPERATOR", ...]}`. **cap 불변식**(아래): admin role(`TENANT_ADMIN`/`TENANT_BILLING_ADMIN`/`SUPER_ADMIN`) 및 플랫폼-scope 절대 불가; host 자신이 보유(≤-own)한 것 이하. B-operator 가 A 에서 갖는 유효 권한은 `delegated_scope ∩ participant-scope ∩ A-holds`([rbac.md](./rbac.md) Cross-Org Partner Delegation Confinement) |
| `invited_by` | BIGINT | NULL, FK → `admin_operators.id` | internal | invite 를 발행한 host 측 `TENANT_ADMIN`(D2). seed/시스템 경로는 NULL |
| `accepted_by` | BIGINT | NULL, FK → `admin_operators.id` | internal | accept 한 partner 측 `TENANT_ADMIN`(D2). PENDING 동안 NULL |
| `invited_at` | DATETIME(6) | NOT NULL | internal | invite 시각(=row 생성) |
| `accepted_at` | DATETIME(6) | NULL | internal | ACTIVE 전이 시각. PENDING 동안 NULL |
| `terminated_at` | DATETIME(6) | NULL | internal | TERMINATED 전이 시각. cascade-revoke 기준시각(D6) |
| `created_at` | DATETIME(6) | NOT NULL | internal | — |
| `updated_at` | DATETIME(6) | NOT NULL | internal | — |
| `version` | INT | NOT NULL, DEFAULT 0 | internal | 낙관적 락 (T5) — 동시 accept/terminate 경합 방지 |

**인덱스**:
- `uk_tenant_partnership_partnership_id` UNIQUE (`partnership_id`)
- `uk_tenant_partnership_pair` UNIQUE (`host_tenant_id`, `partner_tenant_id`) — ordered pair 당 관계 1건(중복 invite → `409 PARTNERSHIP_ALREADY_EXISTS`)
- `idx_tenant_partnership_partner` (`partner_tenant_id`, `status`) — B-side list(내가 참여하는 파트너십) + 상태 필터
- `idx_tenant_partnership_host` (`host_tenant_id`, `status`) — A-side list(내가 발행한 파트너십)

> **관계 불변식 (TASK-BE-476 / ADR-MONO-045 D1/D3)**:
> - `host_tenant_id != partner_tenant_id` — self-partnership 금지(같은 테넌트 내 위임은 ADR-MONO-024 within-tenant 경로).
> - `host_tenant_id`·`partner_tenant_id` 모두 `'*'` 금지 — 플랫폼(SUPER_ADMIN sentinel)은 파트너십의 당사자가 될 수 없다. cross-org 파트너십은 **두 실제 고객 테넌트** 사이에서만 성립.
> - **`delegated_scope` cap** — 어떤 admin role(`TENANT_ADMIN`/`TENANT_BILLING_ADMIN`/`SUPER_ADMIN`)·플랫폼-scope 도 포함 불가; host 가 **자신이 보유**한 domain·role 만 위임 가능(≤-own 을 조직 경계 너머로 확장). invite 시점에 검증(위반 → `422 PARTNERSHIP_SCOPE_INVALID`). 이는 ADR-MONO-024 grant-menu no-escalation 을 cross-org 로 확장한 것.
>   - **concrete enforcement (TASK-BE-479)**: "보유" 는 invite 시점에 두 축으로 구체 검증된다 — (a) 각 **도메인 ∈ host 의 ACTIVE 도메인 구독**(account-service = entitlement authority, `TenantDomainSubscriptionPort` host 필터; command 경로라 cross-service 조회 허용), (b) 각 **role ∈ `DelegatableRoleCatalog`** operator-tier 허용목록(auth-service `OperatorRoleDerivation` 값집합 미러 — admin-tier `WMS_ADMIN` 등 및 tenant-admin 3종 제외). account-service 장애 시 **fail-CLOSED**(위임 미발급). request-time 의 `∩ host-holds` 는 **의도적 unbounded**(ADR-MONO-020 §3.1 hot-path cross-service 금지 + BE-478 step 2b 가 mint 시 도메인을 이미 클립) — 상세 [rbac.md § Cross-Org Partner Delegation Confinement](rbac.md#cross-org-partner-delegation-confinement-adr-mono-045-d3d5).

### `tenant_partnership_participant`

**신규 (TASK-BE-476 / ADR-MONO-045 D4, Flyway `V00NN__create_partnership_tables.sql`)** — ACTIVE 파트너십 안에서 **partner 테넌트 B 가 지정한, A 를 운영할 자기 소유 operator** 들의 바인딩. B 의 `TENANT_ADMIN` 이 관리하며(D4 partner-side self-governance), 이 파생이 곧 offboarding 의 단위 — B 가 자기 직원을 suspend/offboard 하면(B 의 일반 operator lifecycle) 해당 파생이 사라져 A-접근이 A-측 조치 없이 소멸한다.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `partnership_id` | BIGINT | NOT NULL, FK → `tenant_partnership.id` ON DELETE CASCADE | internal | **`tenant_partnership.id` (BIGINT PK) 참조**. 외부 UUID `partnership_id` 컬럼이 아님 |
| `operator_id` | BIGINT | NOT NULL, FK → `admin_operators.id` ON DELETE CASCADE | internal | 참여 operator(=B 소유). **canonical 불변식(아래): 이 operator 의 home `tenant_id` 는 파트너십의 `partner_tenant_id`(B)와 반드시 일치** — B 는 오직 자기 사람만 배정 가능 |
| `participant_scope` | JSON | NULL | internal | `delegated_scope` **안에서의** 선택적 추가 좁힘(`{"domains":[...],"roles":[...]}`). `NULL ⟺ delegated_scope 전체`(net-zero 기본). `operator_tenant_assignment.org_scope`(BE-338) 의 형제 개념 — 비-NULL 은 정규화(trim·dedupe·cap) 후 영속 |
| `assigned_at` | DATETIME(6) | NOT NULL | internal | 배정 시각 |
| `assigned_by` | BIGINT | NULL, FK → `admin_operators.id` | internal | 배정한 B 측 `TENANT_ADMIN`(D4) |

**Primary Key**: (`partnership_id`, `operator_id`) 복합 PK
**인덱스**:
- `idx_partnership_participant_operator` (`operator_id`) — **역검색: 한 operator 가 참여하는 ACTIVE 파트너십 집합**. assume-tenant effective-scope 파생(B-operator 가 A 를 reach 가능한지)의 조회 인덱스([rbac.md](./rbac.md) Cross-Org Partner Delegation Confinement)

> **참여자 불변식 (TASK-BE-476 / ADR-MONO-045 D4/D3)**:
> - 참여 operator 의 `admin_operators.tenant_id` == 부모 파트너십의 `partner_tenant_id`(B). host(A) 는 개별 B-사람을 지명하지 않는다(D4-B 거부) — A 는 envelope(`delegated_scope`)만 authoring, B 가 자기 사람을 채운다. 위반 → `422 PARTICIPANT_NOT_OWN_OPERATOR`.
> - `participant_scope ⊆ delegated_scope` — 참여자 좁힘은 host 위임을 넘을 수 없다. `delegated_scope` 밖 원소는 파생되지 않는다(초과분 무시, confinement 이 request-time 교집합으로 강제).
> - **no transitive re-delegation** — participant 는 자신이 파생한 A-scope 를 다시 제3자에게 위임할 수 없다(confused-deputy default deny, [rbac.md](./rbac.md)). participant 는 B 소유 operator 로 한정되며 그 자체가 재-origination 지점이 될 수 없다.

### `admin_actions`

감사 원장. **append-only** ([architecture.md](./architecture.md) Forbidden Dependencies, [rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A3).

본 태스크(TASK-BE-027)에서 **추가되는 컬럼**: `operator_id`, `permission_used`. 기존 `outcome` enum은 `DENIED` 값을 추가하도록 **확장**한다.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `action_code` | VARCHAR(40) | NOT NULL, INDEX | internal | `ACCOUNT_LOCK` / `ACCOUNT_UNLOCK` / `SESSION_REVOKE` / `AUDIT_QUERY` / `SECURITY_EVENT_QUERY` 등 |
| `operator_id` | BIGINT | **NOT NULL**, FK → `admin_operators.id` | internal | **신규 (TASK-BE-027)** — 권한 평가 주체와 감사 주체를 동일 entity로 고정. admin-service는 bootstrap 마이그레이션이 아직 존재하지 않으므로 레거시 `operator_external_id` 같은 선행 컬럼은 없다 (TASK-BE-028 신규 생성 전제) |
| `permission_used` | VARCHAR(80) | NULL | internal | **신규 (TASK-BE-027)** — 본 action이 통과(또는 거부)된 permission key. annotation 없는 endpoint의 경우 NULL이 아닌 sentinel `"<missing>"` 기록 ([rbac.md](./rbac.md) Missing-Annotation 정책) |
| `target_type` | VARCHAR(20) | NULL | internal | `ACCOUNT` / `SESSION` / `AUDIT_QUERY` 등 |
| `target_id` | VARCHAR(64) | NULL, INDEX | internal | 대상 식별자 (account_id 등). 조회 액션은 NULL 허용 |
| `reason` | VARCHAR(500) | NOT NULL | **confidential** | `X-Operator-Reason` 원문. 개인정보 포함 가능성 — 감사 조회 시 권한 검증 후 노출 |
| `ticket_id` | VARCHAR(64) | NULL | internal | 내부 티켓 연결 |
| `request_id` | VARCHAR(64) | NOT NULL, INDEX | internal | Idempotency 및 로그 상관관계 키 |
| `outcome` | VARCHAR(20) | NOT NULL | internal | `SUCCESS` / `FAILURE` / **`DENIED`** (신규). 기존 enum 확장 — DB는 VARCHAR + 애플리케이션 제약 |
| `detail` | TEXT | NULL | internal | 실패/거부 상세 (downstream error code, denied permission 등). PII 금지 (`admin_actions.downstream_detail` 컬럼으로 구현) |
| `started_at` | DATETIME(6) | NOT NULL, INDEX | internal | 액션 시작 시각. IN_PROGRESS INSERT 시점. 감사 타임라인의 canonical timestamp |
| `completed_at` | DATETIME(6) | NULL | internal | 터미널 outcome(SUCCESS/FAILURE/DENIED) 확정 시각. DENIED row는 start=end 동일 값 기록 |

> `occurred_at` 컬럼은 별도로 존재하지 않는다. TASK-BE-028b 통과 후 본 스펙은 실제 테이블(`started_at`/`completed_at`)을 canonical로 유지한다. envelope `occurredAt`은 아래 매핑 표에 따라 `started_at`에서 파생한다.

**인덱스**:
- `idx_admin_actions_operator_time` (`operator_id`, `started_at`) — 특정 운영자 활동 타임라인
- `idx_admin_actions_target_time` (`target_type`, `target_id`, `started_at`) — 특정 계정 대상 감사
- `idx_admin_actions_action_code` (`action_code`)
- `idx_admin_actions_request_id` (`request_id`) — 멱등성 조회
- `idx_admin_actions_outcome_time` (`outcome`, `started_at`) — DENIED 빈도 모니터링

**Immutability**: UPDATE/DELETE는 DB 레벨 권한 제거 또는 트리거로 차단 ([architecture.md](./architecture.md) Forbidden Dependencies).

### `outbox`

[libs/java-messaging](../../../../../libs/java-messaging) 표준 스키마. `admin.action.performed` 발행 버퍼.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `aggregate_type` | VARCHAR(100) | `admin` |
| `aggregate_id` | VARCHAR(255) | `admin_actions.id` (string화) |
| `event_type` | VARCHAR(100) | `admin.action.performed` |
| `payload` | TEXT (JSON) | 이벤트 envelope |
| `created_at` | TIMESTAMP | — |
| `published_at` | TIMESTAMP | NULL이면 미발행 |
| `status` | VARCHAR(20) | `PENDING` / `PUBLISHED` |

보존·cleanup 정책은 [retention.md](./retention.md)에서 [libs/java-messaging](../../../../../libs/java-messaging) 공통 정책에 위임됨을 명시.

#### `admin.action.performed` Event Envelope

`outbox.payload`는 [libs/java-messaging](../../../../../libs/java-messaging) 표준 envelope([../../contracts/events/auth-events.md](../../contracts/events/auth-events.md) Event Envelope 절 참조)의 `payload` 필드에 다음 구조를 싣는다. `eventType = "admin.action.performed"`, `source = "admin-service"`, `partitionKey = actor.id` (동일 operator 액션의 순서 보장).

이 envelope은 [rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A2 표준 스키마(actor/action/target/outcome)를 준수한다. `specs/contracts/events/admin-events.md` 파일은 현재 존재하지 않으므로(`2026-04-13 확인`) 본 문서가 canonical source이다. 해당 contract 파일이 신설되는 시점에 본 절은 링크로 축약하고 정의를 이관한다.

```json
{
  "eventId": "UUID v7",
  "occurredAt": "2026-04-13T10:00:00.123Z (ISO-8601 UTC, ms+ 정밀도)",
  "actor": {
    "type": "operator",
    "id": "admin_operators.operator_id (UUID v7)",
    "sessionId": "operator JWT jti"
  },
  "action": {
    "permission": "audit.read | account.lock | ...  (rbac.md Permission Keys)",
    "endpoint": "/api/admin/accounts/{accountId}/lock",
    "method": "POST"
  },
  "target": {
    "type": "ACCOUNT | OPERATOR | ROLE | AUDIT_QUERY",
    "id": "target identifier or null",
    "displayHint": "masked string or null"
  },
  "outcome": "SUCCESS | DENIED | FAILURE",
  "reason": "X-Operator-Reason 원문 or '<not_provided>'"
}
```

**필드 규칙**:

- `actor.type`은 항상 `"operator"` (system-originated admin 액션은 본 서비스 범위 외).
- `actor.sessionId`는 operator JWT `jti` 클레임 값([rbac.md](./rbac.md) JWT claim 절 참조 — `jti`는 표준 claim으로 이미 발급 중). 로그인 세션 추적 ID가 아닌 JWT `jti`를 택한 이유: admin-service는 자체 세션 저장소를 갖지 않으며 JWT stateless, `jti`는 이미 발급 시점에 유일성이 보장되어 추가 저장소 없이 재구성 가능.
- `target.displayHint`는 **반드시 마스킹된 값만** 포함한다 ([rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R4). email은 `j***@example.com`, 계정 ID 등 non-PII는 전체 가능. 마스킹은 TASK-BE-028에서 도입될 admin-service 전용 masking utility(패키지: `com.example.admin.domain.util.AdminPiiMaskingUtils` 예정)로 **중앙화**한다. 개별 outbox 호출 지점에서 ad-hoc 마스킹 금지. 참조 구현: [apps/security-service/src/main/java/com/example/security/application/pii/PiiMaskingService.java](../../../apps/security-service/src/main/java/com/example/security/application/pii/PiiMaskingService.java).
- `outcome=DENIED`인 경우 `action.permission`은 요청 annotation의 permission 키(누락이면 sentinel `"<missing>"`). 교차 권한 엔드포인트의 DENIED 시 복합 키(`"audit.read+security.event.read"`) 규칙은 [rbac.md](./rbac.md) Permission Evaluation Algorithm 참조.
- `reason` 필드는 upstream에서 이미 운영자가 입력한 사유 원문. PII 포함 가능성이 있으므로 envelope 분류는 `internal`이되 소비자 측에서 추가 마스킹이 필요할 수 있음 (아래 Data Classification Summary 참조).

**DB Column → Envelope Field 매핑**:

| Envelope field | Source (admin_actions column) | 비고 |
|---|---|---|
| `eventId` | (신규 UUID v7, outbox row 생성 시) | `admin_actions.id`와 별개 |
| `occurredAt` | `admin_actions.started_at` | UTC ISO-8601 ms+ 정밀도 (A6). `started_at`이 canonical 시각 |
| `actor.type` | (상수 `"operator"`) | — |
| `actor.id` | `admin_operators.operator_id` (JOIN via `admin_actions.operator_id → admin_operators.id`) | 외부 UUID 사용 (내부 BIGINT PK 노출 금지) |
| `actor.sessionId` | operator JWT `jti` (request context) | admin_actions에 별도 컬럼은 두지 않음 |
| `action.permission` | `admin_actions.permission_used` | DENIED 시 sentinel 또는 복합 키 |
| `action.endpoint` | (HTTP request context) | admin_actions에 저장 안 함 — 발행 시점 captured |
| `action.method` | (HTTP request context) | 동일 |
| `target.type` | `admin_actions.target_type` | — |
| `target.id` | `admin_actions.target_id` | — |
| `target.displayHint` | (조회 시점 마스킹 변환) | 원문 저장 없음. 마스킹 함수 결과만 envelope에 투입 |
| `outcome` | `admin_actions.outcome` | SUCCESS/DENIED/FAILURE |
| `reason` | `admin_actions.reason` | `<not_provided>` sentinel 허용 |

비어있는 필드는 명시적 `null`로 기록(A2: 필드 생략 금지).

---

## Migration Strategy

- **Flyway**: `V{nnnn}__{description}.sql`
- 본 태스크(TASK-BE-027)는 specs-only. 실제 DDL/Flyway는 TASK-BE-028에서 작성한다.
- 권장 migration 분할:
  - `V0001__create_admin_actions_and_outbox.sql` (기존, admin-service bootstrap에서 생성 가정)
  - `V00NN__create_admin_rbac_tables.sql` — `admin_operators`, `admin_roles`, `admin_role_permissions`, `admin_operator_roles` 신규
  - `V00NN+1__extend_admin_actions_rbac_columns.sql` — `admin_actions.operator_id` FK, `permission_used`, `outcome` enum 값 확장
  - `V00NN+2__seed_admin_roles_and_permissions.sql` — [rbac.md](./rbac.md) seed 매트릭스 INSERT
- `totp_secret_encrypted`, `totp_enrolled_at`, `admin_roles.require_2fa`는 본 태스크에서 컬럼만 생성하고 평가 로직은 추가하지 않는다 (TASK-BE-029 예약).
- PII/시크릿 컬럼(`password_hash`, `totp_secret_encrypted`, `email`) 변경은 단방향만 허용 — down migration 금지.
- **TASK-BE-298 (Flyway `V0027__add_oidc_subject_to_admin_operators.sql`)**:
  `admin_operators.oidc_subject VARCHAR(255) NULL` + `UNIQUE` 인덱스
  `uk_admin_operators_oidc_subject` 추가. **forward-only**(down 금지),
  **idempotent**(`INFORMATION_SCHEMA` 기반 컬럼/인덱스 존재 가드 — 컬럼이
  이미 있으면 no-op), **MySQL-structural**(`ALTER TABLE ... ADD COLUMN` /
  `ADD UNIQUE INDEX` DDL — text-substring / `@var` 접근 금지). TASK-BE-297
  V0016 cycle-3 교훈 직접 적용: cross-statement MySQL user variable 금지,
  idempotency 가드는 NULL-safe(`INFORMATION_SCHEMA` count = 0 비교). 비-Docker
  shape-pin 테스트가 본 구조 불변식을 고정한다 (text-substring/`@var` 회귀
  fast-fail).
- **TASK-BE-304 (Flyway `V0029__add_finance_default_account_id_to_admin_operators.sql`)**:
  `admin_operators.finance_default_account_id VARCHAR(36) NULL` 단일 컬럼
  추가. **forward-only**(down 금지), **idempotent**(`INFORMATION_SCHEMA`
  기반 컬럼 존재 가드 — V0027 패턴 verbatim 재사용, NULL-safe `count = 0`
  비교), **MySQL-structural**(`ALTER TABLE … ADD COLUMN` DDL — V0027
  교훈 적용: text-substring / `@var` 접근 금지). **인덱스 부재** (조회는
  `findByOperatorId` 단건 PK 룩업 — secondary 인덱스 불필요, write cost
  추가 회피). **백필 부재** — 기존 운영자 row 의 새 컬럼 값은 NULL (= MVP
  option (b) 의 `forbidden / MISSING_PREREQUISITE` 잔존 path, 운영자 행동
  변화 0). 평문 외부 식별자 저장 (PII / credential 아님 — 분류
  `internal`); 값 변경은 별 admin-api / operator-management mutation
  surface 의 책임(out-of-scope here). 비-Docker shape-pin 테스트가 본
  구조 불변식을 고정한다 (단일 컬럼·NULL 허용·인덱스 부재 회귀
  fast-fail). **버전 노트**: TASK-BE-304 spec PR(#689, main 머지)
  은 `V0028` 로 authored 되었으나 `db/migration-dev/V0028__seed_dev_operator_oidc_subject.sql`
  이 이미 점유 중이라 Flyway CompositeMigrationResolver duplicate-version
  fast-fail 을 일으켰다. 구현 PR 에서 다음 빈 버전 `V0029` 로 기계적
  rename — migration 의 컬럼/내용 shape 은 spec 과 byte-identical, version
  은 sequencing token 에 불과하므로 ADR 불요.
- **TASK-BE-476 (ADR-MONO-045 D1/D4)** — cross-org 파트너십 aggregate. 두 마이그레이션(구현 = §3.4 step 2, 이 태스크는 specs-only):
  - `V00NN__create_partnership_tables.sql` — `tenant_partnership` + `tenant_partnership_participant` 신규. **forward-only**(down 금지 — 관계 상태는 감사 가치 보유), `delegated_scope`/`participant_scope` 는 MySQL `JSON` 컬럼. `uk_tenant_partnership_pair` UNIQUE(host, partner) + FK CASCADE(participant → partnership). V0027/V0029 패턴(idempotent `INFORMATION_SCHEMA` 가드, `@var` 금지)을 재사용.
  - `V00NN+1__seed_partnership_manage_permission.sql` — `partnership.manage` 권한 키 + `TENANT_ADMIN` 매핑 seed([rbac.md](./rbac.md) Seed Matrix). **inert/net-zero** — 어떤 operator 에도 새로 배정하지 않으며, 파트너십이 생성되기 전엔 아무 효력 없음(기존 `TENANT_ADMIN` 보유자가 invite/accept 표면을 얻을 뿐 confinement 이 자기-당사자 파트너십으로 한정).
- **TASK-BE-490 (spec) / TASK-BE-492 (impl) — ADR-MONO-047 § D5 org plane**. 두 마이그레이션(구현 = §4 step 2, 이 태스크는 specs-only; **작성 직전 다음 빈 버전 재확인** — 태스크 기준 최고 = `V0040`, 다음 = `V0041`/`V0042`):
  - `V0041__seed_org_manage_permission_and_org_admin_role.sql` — `org.manage` 권한 키 + `ORG_ADMIN` seed role + `admin_role_permissions` 매핑([rbac.md](./rbac.md) Seed Matrix). `INSERT IGNORE` idempotent. **inert/net-zero** — `ORG_ADMIN` 을 **어떤 operator 에도 배정하지 않는다**(`admin_operator_roles` 무변경 — `V0033__seed_tenant_admin_roles.sql` 와 동일 규율). `SUPER_ADMIN` 은 `org.manage` 를 추가로 얻어 ROOT 노드 생성 유일 주체가 된다.
  - `V0042__admin_operator_roles_org_node_id.sql` — `admin_operator_roles.org_node_id VARCHAR(36) NULL` + `CHECK (org_node_id IS NULL OR tenant_id <> '*')`. **forward-only**(down 금지), **idempotent**(`INFORMATION_SCHEMA` 가드 — V0027/V0029 패턴 재사용, NULL-safe `count = 0`, `@var` 금지), **MySQL-structural**. `tenant_id` 는 **NOT NULL 유지 + operator-mirror 불변식(BE-289 WI-2) 불변 — 재활용 아님**; `org_node_id` 는 **별개의 scope-driver 컬럼**(FK 부재 — account-service 소유 `org_node.id` 불투명 참조). 비-Docker shape-pin 테스트가 `org_node_id ∧ tenant_id='*'` 금지 CHECK 를 고정한다.
- **TASK-BE-519 (spec) / TASK-BE-520 (impl) — ADR-MONO-046 D1/D3/D5/D6 operator-group plane**. 세 마이그레이션(구현 = § 4 step 2, 이 태스크는 specs-only; **작성 직전 다음 빈 버전 재확인**):
  - `V00NN__create_operator_group_tables.sql` — `operator_group` + `operator_group_member` + `operator_group_grant` 신규. **forward-only**(down 금지 — 그룹/멤버십/grant 는 감사 가치 보유). `uk_operator_group_group_id` UNIQUE(`group_id`) + `uk_operator_group_tenant_name` UNIQUE(`tenant_id`, `name`) + `CHECK (tenant_id <> '*')`; `operator_group_member` FK CASCADE(→ `operator_group.id`, → `admin_operators.id`) + PK(`group_id`, `operator_id`); `operator_group_grant` FK CASCADE(→ `operator_group.id`) + RESTRICT(→ `admin_roles.id`) + `uk_operator_group_grant_natural` UNIQUE(`group_id`, `grant_type`, `role_id`, `tenant_id`) + grant_type/참조 정합 `CHECK`. V0027/V0029 패턴(idempotent `INFORMATION_SCHEMA` 가드, `@var` 금지) 재사용.
  - `V00NN+1__add_group_origin_marker.sql` — `admin_operator_roles.group_origin BIGINT NULL DEFAULT NULL` + `operator_tenant_assignment.group_origin BIGINT NULL DEFAULT NULL`, 각각 FK → `operator_group.id` ON DELETE CASCADE. **forward-only**, **idempotent**(`INFORMATION_SCHEMA` 컬럼 존재 가드), **MySQL-structural**. `NULL`+`DEFAULT NULL` 이라 기존 모든 직접 grant row byte-identical(backward-compatible). 비-Docker shape-pin 테스트가 `group_origin` nullable·default·FK 를 고정한다.
  - `V00NN+2__seed_group_manage_permission.sql` — `group.manage` 권한 키 + `SUPER_ADMIN`/`TENANT_ADMIN`/`ORG_ADMIN` 매핑 seed([rbac.md](./rbac.md) Seed Matrix). `INSERT IGNORE` idempotent. **inert/net-zero** — role→permission 매핑만 추가하고 어떤 operator 에도 배정하지 않으며 그룹을 하나도 만들지 않는다(`V0033__seed_tenant_admin_roles.sql` 와 동일 규율 — 첫 그룹 생성·grant 전엔 fan-out row 0).

---

## Data Classification Summary

| 등급 | 컬럼 |
|---|---|
| **restricted** | `admin_operators.password_hash`, `admin_operators.totp_secret_encrypted` |
| **confidential** | `admin_operators.email`, `admin_operators.display_name`, `admin_actions.reason` |
| **internal** | 위에 명시되지 않은 모든 컬럼 — `admin_operators` 나머지 (id, operator_id, status, totp_enrolled_at, **oidc_subject** (불투명 OIDC `sub` UUID — 비-PII 링크 키, TASK-BE-298), **finance_default_account_id** (불투명 finance 계정 UUID — 비-PII 외부 식별자, TASK-BE-304), last_login_at, created_at, updated_at, version), `admin_roles`의 모든 컬럼, `admin_role_permissions`의 모든 컬럼, `admin_operator_roles`의 모든 컬럼 (**`org_node_id`** 포함 — org-node scope-driver, account-service 소유 `org_node.id` 를 참조하는 불투명 식별자·비-PII·credential 아님, TASK-BE-490/ADR-MONO-047; **`group_origin`** 포함 — fan-out 마커, 같은 서비스 `operator_group.id` FK·비-PII·lifecycle 부기, TASK-BE-519/ADR-MONO-046), **`operator_group`의 모든 컬럼** (TASK-BE-519 — group_id·tenant_id·name·description·audit FK·시각. 비-PII 그룹 메타데이터), **`operator_group_member`의 모든 컬럼** (TASK-BE-519 — group/operator FK·배정 메타), **`operator_group_grant`의 모든 컬럼** (TASK-BE-519 — grant_id·group FK·grant_type·role/tenant 참조·audit 메타. 비-PII grant 템플릿), **`tenant_partnership`의 모든 컬럼** (TASK-BE-476 — host/partner tenant_id·status·`delegated_scope`(도메인/역할 키 집합, PII·credential 아님)·audit FK·시각. 비-PII 관계 메타데이터), **`tenant_partnership_participant`의 모든 컬럼** (TASK-BE-476 — operator FK·`participant_scope`·배정 메타), `admin_actions`의 나머지 (id, action_code, operator_id, permission_used, target_type, target_id, ticket_id, request_id, outcome, detail, started_at, completed_at), `outbox` 테이블의 나머지 컬럼 |
| **internal (special)** | `outbox.payload` — `admin.action.performed` envelope을 직렬화하여 포함. `target.displayHint`처럼 **upstream에서 이미 마스킹된** confidential 원본의 파생값을 포함할 수 있다 ([rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R4 — 중앙 masking utility 경유 강제). 원문 PII는 포함되지 않음을 스펙 레벨에서 보장하므로 분류는 `internal`. 단, `reason` 필드(운영자 입력 원문) 전달 시 소비자 측에서 필요에 따라 추가 필터링을 고려한다. |
| **public** | 없음 |

[rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R1 (PII/secret 분리 저장·마스킹)과 R2 (시크릿 평문 저장 금지) 준수.

> RBAC 테이블의 **행동 규칙**(권한 평가 알고리즘, seed 매트릭스, DENIED 기록 정책, JWT claim)은 본 문서가 아닌 [rbac.md](./rbac.md)에서 선언된다. 본 문서는 스키마와 분류만 다룬다.

> **TASK-BE-327 (ADR-MONO-020 D2)**: `operator_tenant_assignment` (V0030) + `admin_operators.{oidc_subject, tenant_id}` 로 구성되는 D1 effective-scope 가 이제 auth-service 에 의해 `GET /internal/operator-assignments/check` 로 **cross-service read** 된다 (assume-tenant 발급 게이트). **스키마 변경 없음** — read-only 노출만 추가. 상세: [rbac.md](./rbac.md), [architecture.md](./architecture.md).

> **TASK-BE-338 (ADR-MONO-020 D3 amendment 2026-06-05)**: `operator_tenant_assignment` 에 **nullable `org_scope`** 컬럼 추가(V0031) — per-assignment **데이터-스코프**(운영자가 그 테넌트에서 act 가능한 부서 **subtree-root id** 들의 JSON 배열; `NULL ⟺ ["*"]` = 테넌트 전체 = **net-zero** 기본, 기존 모든 행 동작 불변). `permission_set_id`(D5, role-set 좁힘)의 data-scope 형제. `/internal/operator-assignments/check` 가 `org_scope` 를 함께 반환(현행 boolean gate 에 additive) → auth-service `TenantClaimTokenCustomizer.customizeForAssumeTenant` 가 하드코딩 `["*"]`(BE-337) 대신 실제 값 주입. 오직 erp 가 소비(subtree-root 를 자기 부서트리로 descendant 확장; TASK-ERP-BE-008). 행동 규칙(claim 형상/주입)은 [rbac.md](./rbac.md) + auth-service [architecture.md](../auth-service/architecture.md).

> **TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up)**: `org_scope` **관리 surface 추가** — `GET /api/admin/operators/{operatorId}/assignments`(활성 테넌트 scope, org_scope 포함 조회) + `PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope`(set/clear). **스키마 변경 없음**(V0031 컬럼 재사용). write 는 `saveAndFlush`(BE-335 명시 flush). 값 의미: `null`=clear(컬럼 NULL ⟺ `["*"]`), `[]`=명시적 zero-scope(NULL 과 구분 영속), `[ids]`=정규화(trim·blank거부·dedupe·cap 256) 후 영속. 활성 테넌트(`X-Tenant-Id`)와 `path tenantId` 불일치 → 403 `TENANT_SCOPE_MISMATCH`; assignment 행 부재 → 404 `ASSIGNMENT_NOT_FOUND`(행 생성/삭제는 범위 밖). 콘솔(TASK-PC-FE-050)이 SQL 시드 없이 데이터-스코프 설정. 상세: [admin-api.md](../../contracts/http/admin-api.md).
