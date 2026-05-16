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
| `password_hash` | VARCHAR(255) | NOT NULL | **restricted** | argon2id 해시. 평문 저장 금지 (R2) |
| `display_name` | VARCHAR(120) | NOT NULL | **confidential** | 감사 UI에 표시되는 이름. 개인 식별 가능 (R1) |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | internal | `ACTIVE` / `DISABLED` / `LOCKED`. 소프트 비활성 플래그 |
| `totp_secret_encrypted` | VARBINARY(255) | NULL | **restricted** | TOTP 시크릿 (envelope encryption). **TASK-BE-029 예약 컬럼** — 본 태스크에서는 NULL만 허용 |
| `totp_enrolled_at` | DATETIME(6) | NULL | internal | TOTP 등록 완료 시각. **TASK-BE-029 예약 컬럼** — 본 태스크에서는 NULL만 허용 |
| `oidc_subject` | VARCHAR(255) | NULL, UNIQUE | internal | **신규 (TASK-BE-298, Flyway V0027)** — GAP OIDC `platform-console-web` access token 의 `sub`(account_id UUID). `POST /api/admin/auth/token-exchange` 의 OIDC↔operator **링크 키**. `NULL` = console-token-exchange 미허용 운영자 (fail-closed 기본값 — 명시적 provisioning 으로만 활성화). 플랫폼-전역 UNIQUE (OIDC subject 공간은 테넌트 무관 — 아래 §OIDC Subject ↔ Operator Link Key). 스코프 상승 비-소스: 이 컬럼은 **링크에만** 쓰이고, tenant 스코프는 `tenant_id` 가 단일 진실 소스 |
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
> 한다고만 고정하고, 링크 키 선택을 GAP 구현 태스크의 sub-decision 으로
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

> **Per-tenant 바인딩 불변식 (TASK-BE-289 WI-2)**: 모든 `admin_operator_roles` 행 생성 경로(`CreateOperatorUseCase`, `PatchOperatorRoleUseCase`)는 `tenant_id`를 대상 operator 의 `tenant_id`와 **반드시 일치**시켜야 한다. 하드코딩 기본값(`"fan-platform"`) 금지 — `PatchOperatorRoleUseCase`가 V0025 이전 레거시 4-arg 팩토리로 인해 이 불변식을 위반했던 회귀(TASK-BE-288 review Finding 1)가 V0026 backfill + 격리 회귀 테스트로 종결됨.

**Primary Key**: (`operator_id`, `role_id`) 복합 PK
**인덱스**:
- `idx_admin_operator_roles_role` (`role_id`) — 역할별 운영자 역검색

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

---

## Data Classification Summary

| 등급 | 컬럼 |
|---|---|
| **restricted** | `admin_operators.password_hash`, `admin_operators.totp_secret_encrypted` |
| **confidential** | `admin_operators.email`, `admin_operators.display_name`, `admin_actions.reason` |
| **internal** | 위에 명시되지 않은 모든 컬럼 — `admin_operators` 나머지 (id, operator_id, status, totp_enrolled_at, **oidc_subject** (불투명 OIDC `sub` UUID — 비-PII 링크 키, TASK-BE-298), last_login_at, created_at, updated_at, version), `admin_roles`의 모든 컬럼, `admin_role_permissions`의 모든 컬럼, `admin_operator_roles`의 모든 컬럼, `admin_actions`의 나머지 (id, action_code, operator_id, permission_used, target_type, target_id, ticket_id, request_id, outcome, detail, started_at, completed_at), `outbox` 테이블의 나머지 컬럼 |
| **internal (special)** | `outbox.payload` — `admin.action.performed` envelope을 직렬화하여 포함. `target.displayHint`처럼 **upstream에서 이미 마스킹된** confidential 원본의 파생값을 포함할 수 있다 ([rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R4 — 중앙 masking utility 경유 강제). 원문 PII는 포함되지 않음을 스펙 레벨에서 보장하므로 분류는 `internal`. 단, `reason` 필드(운영자 입력 원문) 전달 시 소비자 측에서 필요에 따라 추가 필터링을 고려한다. |
| **public** | 없음 |

[rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R1 (PII/secret 분리 저장·마스킹)과 R2 (시크릿 평문 저장 금지) 준수.

> RBAC 테이블의 **행동 규칙**(권한 평가 알고리즘, seed 매트릭스, DENIED 기록 정책, JWT claim)은 본 문서가 아닌 [rbac.md](./rbac.md)에서 선언된다. 본 문서는 스키마와 분류만 다룬다.
