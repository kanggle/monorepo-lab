# account-service — Data Model

## Design Decision

profile(비밀 아님)과 credentials(비밀)는 **물리적으로 별도 서비스·DB**에 저장한다 ([rules/domains/saas.md](../../../rules/domains/saas.md) S1). account-service는 accounts + profiles + 상태 이력을 전담하고, credentials는 auth-service가 소유.

**상태 기계**: 계정 상태는 `AccountStatusMachine`에 의해 관리된다. 직접 `UPDATE accounts SET status = ?` 금지 ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T4). 모든 전이는 `account_status_history`에 append-only로 기록.

---

## Tables

### `accounts`

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | VARCHAR(36) | PK (UUID) | internal | 전체 플랫폼에서 계정 식별자로 사용 |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | **confidential** | 가입 이메일. PII (R1) |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | internal | `ACTIVE` / `LOCKED` / `DORMANT` / `DELETED` |
| `created_at` | DATETIME(6) | NOT NULL | internal | — |
| `updated_at` | DATETIME(6) | NOT NULL | internal | — |
| `deleted_at` | DATETIME(6) | NULL | internal | 논리 삭제 시점 (유예 시작) |
| `version` | INT | NOT NULL, DEFAULT 0 | internal | 낙관적 락 (T5) |

**인덱스**: `idx_accounts_email` (UNIQUE), `idx_accounts_status`, `idx_accounts_deleted_at` (유예 만료 배치용)

### `profiles`

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK | internal | — |
| `account_id` | VARCHAR(36) | UNIQUE FK → accounts.id | internal | — |
| `display_name` | VARCHAR(100) | NULL | **confidential** | PII |
| `phone_number` | VARCHAR(20) | NULL | **confidential** | PII. 마스킹 대상 (R4) |
| `birth_date` | DATE | NULL | **confidential** | PII |
| `locale` | VARCHAR(10) | DEFAULT 'ko-KR' | internal | — |
| `timezone` | VARCHAR(50) | DEFAULT 'Asia/Seoul' | internal | — |
| `preferences` | JSON | NULL | internal | 알림 설정, 테마 등 |
| `updated_at` | DATETIME(6) | NOT NULL | internal | — |

**인덱스**: `idx_profiles_account_id` (UNIQUE)

**금지 컬럼**: `password_hash`, `credential_hash`, `2fa_secret`, `oauth_refresh_token` — 이들은 auth-service 소유 (S1).

### `account_status_history`

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK | internal | — |
| `account_id` | VARCHAR(36) | NOT NULL, INDEX | internal | — |
| `from_status` | VARCHAR(20) | NOT NULL | internal | 이전 상태 |
| `to_status` | VARCHAR(20) | NOT NULL | internal | 전이 후 상태 |
| `reason_code` | VARCHAR(50) | NOT NULL | internal | `ADMIN_LOCK`, `AUTO_DETECT`, `USER_REQUEST`, `DORMANT_365D`, `REGULATED_DELETION` 등 |
| `actor_type` | VARCHAR(20) | NOT NULL | internal | `user` / `operator` / `system` |
| `actor_id` | VARCHAR(36) | NULL | internal | 운영자 ID, system은 NULL |
| `details` | JSON | NULL | internal | 추가 컨텍스트 |
| `occurred_at` | DATETIME(6) | NOT NULL | internal | UTC |

**불변성**: **append-only**. DB 트리거로 UPDATE/DELETE 차단 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A3).

**인덱스**: `idx_ash_account_id_occurred_at` (복합)

### `account_roles`

> TASK-BE-255: 테넌트별 계정 역할 매핑 테이블. `(tenant_id, role_name)` 복합키 정책 ([specs/features/multi-tenancy.md § Per-Tenant Roles](../../features/multi-tenancy.md#per-tenant-roles)) 의 물리 구현.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `tenant_id` | VARCHAR(32) | NOT NULL, PK | internal | 역할이 부여된 테넌트 |
| `account_id` | VARCHAR(36) | NOT NULL, PK | internal | 역할이 부여된 계정 |
| `role_name` | VARCHAR(64) | NOT NULL, PK | internal | 역할 이름. 정규식 `^[A-Z][A-Z0-9_]*$` (예: `WAREHOUSE_ADMIN`) |
| `granted_by` | VARCHAR(36) | NULL | internal | 역할을 부여한 운영자 ID. system grant 의 경우 NULL |
| `granted_at` | DATETIME(6) | NOT NULL | internal | 부여 시각 (UTC) |

**PK**: `(tenant_id, account_id, role_name)` — surrogate ID 없음. row 자체가 fact ("이 사용자에게 이 role 이 부여되었다") 이므로 자연 키로 충분.

**FK**: `(tenant_id, account_id) → accounts(tenant_id, id)` ON DELETE CASCADE — 계정 삭제 시 역할도 자동 정리. 복합 FK 로 cross-tenant integrity 강화. (`accounts(tenant_id, id)` 에 보조 unique key 가 V0013 에서 추가됨.)

**인덱스**:
- PK 가 `(tenant_id, account_id, role_name)` 이므로 PK 자체가 `(tenant_id, account_id)` prefix lookup 을 커버한다 (계정별 역할 조회).
- `idx_account_roles_tenant_role (tenant_id, role_name)` — 특정 역할을 가진 모든 계정 조회용 (예: WMS 의 `INBOUND_OPERATOR` 전체 조회).

**기본 역할 정책**: admin 이 사전에 등록한 역할만 허용. 등록되지 않은 역할 부여 시도는 application 단에서 400. tenant 별 허용 역할 카탈로그 (`tenant_role_definitions`) 는 별도 task 로 분리됨 — 본 테이블의 `role_name` 은 자유 문자열로 시작, 정규식만 강제. ([specs/features/multi-tenancy.md § Per-Tenant Roles](../../features/multi-tenancy.md#per-tenant-roles))

**operations** (provisioning API contract):
- `replaceAll`: 기존 역할 모두 삭제 후 신규 역할 set 삽입 (단일 트랜잭션).
- `add`: 단건 추가. 이미 존재 시 no-op (멱등). PK 중복은 409 가 아니라 정상 종료.
- `remove`: 단건 삭제. 존재하지 않을 시 no-op.

### `outbox_events`

[libs/java-messaging](../../../libs/java-messaging) 표준 스키마 — auth-service와 동일 구조.

---

## State Machine Reference

```
        ┌──────────── USER_LOGIN ──────────────┐
        │                                      │
        ▼                                      │
    ┌────────┐  ADMIN_LOCK / AUTO_DETECT  ┌────────┐
    │ ACTIVE │ ─────────────────────────→ │ LOCKED │
    └────────┘ ←───────────────────────── └────────┘
        │       ADMIN_UNLOCK / RECOVERY         │
        │                                       │
        │  365D_INACTIVE                        │
        ▼                                       │
    ┌─────────┐  USER_LOGIN                     │
    │ DORMANT │ ────────→ ACTIVE                │
    └─────────┘                                 │
        │                                       │
        │  USER_REQUEST / ADMIN / REGULATED     │
        ▼                                       │
    ┌─────────┐                                 │
    │ DELETED │ ←───────────────────────────────┘
    └─────────┘   USER_REQUEST / ADMIN / REGULATED
        │
        │ WITHIN_GRACE (admin_only)
        ▼
      ACTIVE (복구)
```

유예 기간 내 `DELETED → ACTIVE` 복구는 admin-only. 유예 만료 후 PII 익명화 실행 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R7). 익명화 후 복구 불가.

---

## Anonymization (삭제 유예 + PII 제거)

| 대상 테이블 | 대상 필드 | 처리 |
|---|---|---|
| `accounts` | `email` | `anon_{SHA256(email)[:12]}@deleted.local` |
| `profiles` | `display_name` | `'탈퇴한 사용자'` |
| `profiles` | `phone_number` | `NULL` |
| `profiles` | `birth_date` | `NULL` |
| `profiles` | `preferences` | `NULL` |
| `account_status_history` | — | 그대로 유지 (감사 기록은 삭제 금지, 단 actor_id가 탈퇴 사용자 자신이면 해당 없음) |

유예 기간: **30일** (환경 변수로 조정 가능). `accounts.deleted_at`이 30일 이전인 row를 배치 또는 스케줄러가 처리.

---

## Migration Strategy

- **Flyway**: `V{nnnn}__{description}.sql`
- `V0001__create_accounts_and_profiles.sql`
- `V0002__create_account_status_history.sql`
- `V0003__create_outbox_events.sql`
- `V0004__add_status_history_trigger_prevent_update_delete.sql`
- `V0012__create_account_roles.sql` — TASK-BE-231 초기 surrogate-PK 형태 (legacy)
- `V0013__rebuild_account_roles_composite_pk.sql` — TASK-BE-255 composite PK + composite FK ON DELETE CASCADE + `granted_by` / `granted_at` + `(tenant_id, role_name)` index
- 각 마이그레이션은 forward-only. down migration은 PII 보존 규칙상 **제공하지 않음** (R6 — 데이터 복원 경로를 제한적으로만 허용)

---

## Data Classification Summary

| 등급 | 컬럼 |
|---|---|
| **confidential** | `accounts.email`, `profiles.display_name`, `profiles.phone_number`, `profiles.birth_date` |
| **internal** | 나머지 모든 컬럼 |
| **public** | 없음 |
| **restricted** | 없음 (credentials는 auth-service 소유) |

[rules/traits/regulated.md](../../../rules/traits/regulated.md) R1 준수. `confidential` 이상 컬럼의 조회는 감사 대상 (audit-heavy R5 교차).
