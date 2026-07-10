# account-service — Data Model

## Design Decision

profile(비밀 아님)과 credentials(비밀)는 **물리적으로 별도 서비스·DB**에 저장한다 ([rules/domains/saas.md](../../../../../rules/domains/saas.md) S1). account-service는 accounts + profiles + 상태 이력을 전담하고, credentials는 auth-service가 소유.

**상태 기계**: 계정 상태는 `AccountStatusMachine`에 의해 관리된다. 직접 `UPDATE accounts SET status = ?` 금지 ([rules/traits/transactional.md](../../../../../rules/traits/transactional.md) T4). 모든 전이는 `account_status_history`에 append-only로 기록.

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

**불변성**: **append-only**. DB 트리거로 UPDATE/DELETE 차단 ([rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A3).

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

### `org_node`

> TASK-BE-490 / [ADR-MONO-047](../../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) § D1 — `tenant` **위에** 얹히는 **데이터 없는 그룹핑 노드**. 한 회사(paying company)가 각자 격리된 다수의 service-tenant 를 소유하도록 표현하고, 그 노드 체인에 **deny-only 엔타이틀먼트 실링(ceiling)** 을 붙여 하위로 상속(narrow-only)한다. `org_node` 는 tenant 를 **그룹핑할 뿐 중첩(nest)하지 않는다** — `tenant_id` 는 여전히 단일 flat 격리 키다 (M1 불변, [multi-tenancy.md § Org Node Model](../../features/multi-tenancy.md#org-node-model-adr-mono-047)). DDL/entity 는 후속 TASK-BE-491 이 소유하며 (Flyway `V0027`), 본 스펙은 그보다 **먼저** 계약을 확정한다 (Change Rule).

```sql
CREATE TABLE org_node (
    id              VARCHAR(36)  NOT NULL,
    parent_id       VARCHAR(36)  NULL,
    name            VARCHAR(100) NOT NULL,
    ceiling_mode    VARCHAR(16)  NOT NULL,
    ceiling_domains VARCHAR(255) NOT NULL DEFAULT '',
    depth           INT          NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_org_node_parent  FOREIGN KEY (parent_id) REFERENCES org_node(id),
    CONSTRAINT ck_org_node_depth   CHECK (depth BETWEEN 1 AND 5),
    CONSTRAINT ck_org_node_selfref CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_org_node_mode    CHECK (ceiling_mode IN ('UNBOUNDED','BOUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | VARCHAR(36) | PK (UUID) | internal | 노드 식별자 |
| `parent_id` | VARCHAR(36) | NULL, FK → org_node.id | internal | 상위 노드. NULL = 트리 루트 |
| `name` | VARCHAR(100) | NOT NULL | internal | 회사/부문 표시명. **unique 아님** (PII 아님 — 회사·부서 표시 이름) |
| `ceiling_mode` | VARCHAR(16) | NOT NULL, CHECK IN (`UNBOUNDED`,`BOUNDED`) | internal | 실링 종류. `UNBOUNDED` = 실링 없음(교집합 항등원). `BOUNDED` = `ceiling_domains` 로 상한 지정 |
| `ceiling_domains` | VARCHAR(255) | NOT NULL DEFAULT `''` | internal | domainKey CSV. **`ceiling_mode='BOUNDED'` 일 때만 의미**. BOUNDED 하에서 빈 문자열 = 공집합(아무것도 허용 안 함) |
| `depth` | INT | NOT NULL, CHECK 1..5 | internal | 루트=1, `child = parent + 1`. AWS OU 패리티로 5 상한 |
| `created_at` | DATETIME(6) | NOT NULL | internal | — |
| `updated_at` | DATETIME(6) | NOT NULL | internal | — |

**PK**: `id` (UUID surrogate). 노드는 이동(re-parent) 가능해야 하므로 `parent_id` 를 자연 키에 포함하지 않는다.

**FK**: `parent_id → org_node.id` — 자기참조 트리. `fk_tenants_org_node` (아래 `tenants` 노트) 가 leaf 쪽 연결을 담당.

**인덱스**: `idx_org_node_parent (parent_id)` — 자식/서브트리 조회용.

**Ceiling 모델** ([ADR-MONO-047](../../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) § D2 — deny-only, narrow-only):

```
Ceiling := UNBOUNDED | BOUNDED(ordered set of domainKey)

  UNBOUNDED   = "실링 없음" = 교집합(intersect)의 IDENTITY 원소.
                현재 알려진 모든 도메인의 집합이 *아니다* — 그렇게 모델링하면
                나중에 추가되는 도메인을 조용히 배제하게 된다.
  BOUNDED({}) = 아무것도 허용 안 함 (fail-closed).

  ⚠ UNBOUNDED 와 BOUNDED({}) 는 서로 반대다. `ceiling_mode` 컬럼이
     존재하는 이유가 바로 이것 — 저장 계층에서 둘을 절대 혼동하지 않기 위함.
```

연산 (모두 **narrow-only** — 실링은 도메인을 제거만 할 뿐 **부여하지 않는다**, D2-A):

```
intersect(UNBOUNDED, c)            = c
intersect(c, UNBOUNDED)            = c
intersect(BOUNDED(a), BOUNDED(b))  = BOUNDED(a ∩ b)

subsetOf(BOUNDED(a), UNBOUNDED)    = true
subsetOf(BOUNDED(a), BOUNDED(b))   = a ⊆ b
subsetOf(UNBOUNDED, BOUNDED(_))    = false
subsetOf(UNBOUNDED, UNBOUNDED)     = true

permits(c, domainKey) = (c == UNBOUNDED) || domainKey ∈ c.domains
```

**effectiveCeiling** — 루트→노드 체인의 노드 실링을 전부 교집합한 것 (fold, 항등원=`UNBOUNDED`):

```
effectiveCeiling(node)   = fold(intersect, UNBOUNDED, [n.ceiling for n in chain(root..node)])
effectiveCeiling(tenant) = tenant.org_node_id IS NULL ? UNBOUNDED
                                                      : effectiveCeiling(tenant.org_node)
```

**쓰기 불변식** (write 시 위반이면 **422** 거부):

- **I1 (무순환)**: `parent_id <> id`; 조상 체인 어디에도 순환 없음.
- **I2 (깊이)**: `depth = parent.depth + 1`, 루트 depth = 1, `depth <= 5` (AWS OU 패리티). re-parent 시 이동된 서브트리의 depth 를 재계산하고 상한을 재검증한다.
- **I3 (실링 하위집합)**: `child.ceiling ⊆ parent.ceiling` — create-with-parent, set-ceiling, **그리고** re-parent 에서 강제. re-parent 는 이동 노드뿐 아니라 **모든 자손**을 검사한다.
- **I4 (삭제 제약)**: 자식 노드 또는 붙어 있는 tenant 가 있으면 삭제 거부.
- **I5 (이름 길이)**: `name` 은 trim 후 1..100 자.

**D7 back-compat (net-zero)**: `tenants.org_node_id` 는 **NULLABLE**. `NULL` = "ungrouped singleton" = `effectiveCeiling` 가 `UNBOUNDED` = **바이트 동일한 레거시 동작**. 기존 row 는 무영향이며, 백필 마이그레이션(TASK-BE-493)은 lazy(한 번도 실행 안 함)여도 legal 하다 ([ADR-MONO-047](../../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) § D7).

**D6 seam (단일 지점 강제)**: 실링 교집합은 **account-service 소스에서 딱 한 번** 적용된다 — 전용 internal 엔드포인트 `GET /internal/tenants/{tenantId}/entitled-domains` 가 `ACTIVE subscriptions ∩ effectiveCeiling(tenant)` 를 반환. `GET /internal/tenant-domain-subscriptions` 는 여전히 **raw ACTIVE row** 를 반환한다(콘솔 카탈로그 + 구독 관리의 진실). auth-service `TenantClaimTokenCustomizer` 는 **바이트 불변** — `derive(E ∩ C) = derive(E) ∩ derive(C)` (ADR-035 파생이 도메인-키 기준 per-domain 이므로) ([ADR-MONO-047](../../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) § D6, roadmap step 2 seam note).

**엔타이틀먼트-플레인 쓰기 게이트**: 도메인 `d` 의 `tenant_domain_subscription` 을 활성화(activate)하려는데 `!permits(effectiveCeiling(tenant), d)` 이면 **422 `SUBSCRIPTION_DOMAIN_OUT_OF_CEILING`**. 비활성화(deactivate)는 항상 허용(narrowing). 실링은 **엔타이틀먼트만** 제한할 뿐 IAM 역할을 부여하지 않는다 (ADR-023 plane separation).

### `tenants` (org_node_id 추가)

> TASK-BE-490 / [ADR-MONO-047](../../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) § D1/D7 — `tenants` 테이블 본체는 Flyway `V0009__create_tenants.sql` 및 [multi-tenancy.md § Tenant Model](../../features/multi-tenancy.md#tenant-엔터티-account-service-domaintenant) 에 정의된다. 본 노트는 ADR-047 이 추가하는 **단일 컬럼**만 다룬다.

```sql
ALTER TABLE tenants
  ADD COLUMN org_node_id VARCHAR(36) NULL,
  ADD CONSTRAINT fk_tenants_org_node FOREIGN KEY (org_node_id) REFERENCES org_node(id);
```

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `org_node_id` | VARCHAR(36) | NULL, FK → org_node.id | internal | 이 service-tenant 를 소유한 그룹핑 노드. **NULL = ungrouped singleton = `effectiveCeiling`=`UNBOUNDED`** (D7 net-zero) |

**FK**: `fk_tenants_org_node (org_node_id → org_node.id)`.

**인덱스**: `idx_tenants_org_node (org_node_id)` — 노드별 소속 tenant 조회(서브트리 관리·`ORG_ADMIN` 스코프)용.

**net-zero 규칙**: 컬럼이 NULLABLE 이고 DEFAULT 없음 → 기존 tenant row 는 `org_node_id = NULL` 로 남아 레거시와 바이트 동일하게 동작한다. 회사를 여러 서비스로 쪼개는 것은 **additive** 작업(같은 노드 아래 sibling service-tenant 생성)이며, 어떤 기존 `tenant_id`·격리·구독도 바뀌지 않는다 (D7).

### `outbox_events`

[libs/java-messaging](../../../../../libs/java-messaging) 표준 스키마 — auth-service와 동일 구조.

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

유예 기간 내 `DELETED → ACTIVE` 복구는 admin-only. 유예 만료 후 PII 익명화 실행 ([rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R7). 익명화 후 복구 불가.

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
- `V0027__create_org_node_and_tenant_fk.sql` — TASK-BE-491 (ADR-MONO-047 § 4 step 2): `org_node` 테이블 + `tenants.org_node_id` **nullable** FK + `idx_org_node_parent` / `idx_tenants_org_node`. `org_node_id` NULLABLE 이므로 기존 tenant row 는 백필 없이 net-zero (D7). 노드별 1:1 백필(1 node + 1 service-tenant per 기존 tenant)은 별도 TASK-BE-493 이며 behavioural no-op.
- 각 마이그레이션은 forward-only. down migration은 PII 보존 규칙상 **제공하지 않음** (R6 — 데이터 복원 경로를 제한적으로만 허용)

---

## Data Classification Summary

| 등급 | 컬럼 |
|---|---|
| **confidential** | `accounts.email`, `profiles.display_name`, `profiles.phone_number`, `profiles.birth_date` |
| **internal** | 나머지 모든 컬럼 (`org_node.*` 및 `tenants.org_node_id` 전부 포함 — PII 없음. `org_node.name` 은 회사/부서 표시명이지 개인정보 아님) |
| **public** | 없음 |
| **restricted** | 없음 (credentials는 auth-service 소유) |

[rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R1 준수. `confidential` 이상 컬럼의 조회는 감사 대상 (audit-heavy R5 교차).
