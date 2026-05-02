# security-service — Data Model

## Design Decision

security-service 는 **이상 로그인 탐지·자동 잠금**을 책임지는 `regulated` + `audit-heavy` 서비스다 ([rules/traits/regulated.md](../../../rules/traits/regulated.md), [rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md)). 모든 보안 이력은 **append-only** 로 기록하며, GDPR/PIPA 삭제 요청에 대해서는 row 자체를 삭제하지 않고 PII 컬럼만 마스킹한다 ([specs/contracts/events/account-events.md § Consumer Obligations](../../contracts/events/account-events.md#consumer-obligations-task-be-258)).

**소유 vs 비소유 데이터**:
- 소유: 로그인 이력, 의심 이벤트, 자동 잠금 이력, PII 마스킹 audit log.
- 비소유 (참조만): `accounts.id`, `tenants.id` (account-service 가 SSoT).

**Cross-tenant isolation**: 모든 테이블은 `tenant_id` 컬럼을 보유하며 모든 쿼리 (read/update/delete) 가 `(tenant_id, account_id)` 또는 `(tenant_id, ...)` 로 scope 된다 ([rules/traits/multi-tenant.md](../../../rules/traits/multi-tenant.md)).

---

## Tables

### `login_history`

로그인 시도/성공/실패 이력. 이상 로그인 탐지의 입력 + GDPR 삭제 시 PII 마스킹 대상.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `tenant_id` | VARCHAR(32) | NOT NULL, INDEX | internal | (R8) cross-tenant isolation 키 |
| `account_id` | VARCHAR(36) | NOT NULL, INDEX | internal | account-service.accounts.id 참조 (FK 없음 — 서비스 경계) |
| `event_type` | VARCHAR(20) | NOT NULL | internal | `LOGIN_ATTEMPTED` / `LOGIN_FAILED` / `LOGIN_SUCCEEDED` |
| `ip_masked` | VARCHAR(45) | NOT NULL | **confidential** | (R1) PII. GDPR 삭제 시 `'0.0.0.0'` 으로 마스킹 |
| `user_agent_family` | VARCHAR(64) | NULL | **confidential** | (R1) UA family. GDPR 삭제 시 `'REDACTED'` 으로 마스킹 |
| `device_fingerprint` | VARCHAR(64) | NULL | **confidential** | (R1) device hash. GDPR 삭제 시 `SHA2(CONCAT(accountId, salt), 256)` 으로 마스킹 (TASK-BE-270 — application 수준 고정 salt) |
| `failure_reason` | VARCHAR(50) | NULL | internal | `INVALID_PASSWORD` / `ACCOUNT_LOCKED` 등 |
| `occurred_at` | DATETIME(6) | NOT NULL | internal | UTC |

**불변성**: append-only. UPDATE/DELETE 는 GDPR 마스킹 (TASK-BE-258) 만 허용 — 그 외 트리거로 차단 (V0002).
**인덱스**: `idx_login_history_tenant_account_time` `(tenant_id, account_id, occurred_at DESC)`.
**보존**: 기본 1년 (운영 정책에 따라 조정).

### `suspicious_events`

이상 로그인 탐지가 점수를 산출하면서 발행하는 이벤트.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `tenant_id` | VARCHAR(32) | NOT NULL, INDEX | internal | (R8) |
| `account_id` | VARCHAR(36) | NOT NULL, INDEX | internal | account 참조 |
| `rule_name` | VARCHAR(50) | NOT NULL | internal | `VELOCITY` / `IMPOSSIBLE_TRAVEL` / `IP_REPUTATION` / `DEVICE_CHANGE` / `TOKEN_REUSE` 등 |
| `score` | INT | NOT NULL | internal | 0 ~ 100 (탐지 강도) |
| `evidence` | JSON | NOT NULL | **confidential** | (R1) IP/UA fragment 가 포함될 수 있음. GDPR 삭제 시 `'{}'` 로 초기화 |
| `triggered_at` | DATETIME(6) | NOT NULL | internal | UTC |

**불변성**: append-only.
**인덱스**: `idx_suspicious_events_tenant_account_time` `(tenant_id, account_id, triggered_at DESC)`.

### `account_lock_history`

자동/운영 잠금 적용 이력.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `tenant_id` | VARCHAR(32) | NOT NULL, INDEX | internal | (R8) |
| `account_id` | VARCHAR(36) | NOT NULL, INDEX | internal | account 참조 |
| `reason` | VARCHAR(50) | NOT NULL | internal | `AUTO_LOCK_HIGH_SCORE` / `OPERATOR_LOCK` 등 (코드, PII 아님) |
| `locked_by` | VARCHAR(36) | NULL | internal | 운영자 UUID 또는 `system` (UUID/sentinel, PII 아님) |
| `score` | INT | NULL | internal | 자동 잠금 시 트리거 score |
| `locked_at` | DATETIME(6) | NOT NULL | internal | UTC |

**불변성**: append-only.
**PII**: 본 테이블은 PII 컬럼이 없으나, GDPR 마스킹 시 `tableNames` 목록에 포함 — "체크 완료" audit 명시 목적의 no-op UPDATE.

### `processed_events`

auth-events / account-events 등 외부 이벤트 처리 멱등성 추적.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `event_id` | VARCHAR(36) | PK | internal | Kafka 이벤트의 envelope `eventId` |
| `processed_at` | DATETIME(6) | NOT NULL | internal | UTC |

**역할**: consumer 의 멱등성. 동일 `event_id` 재처리 시도 시 PK 충돌로 즉시 차단.
**참고**: TASK-BE-258 의 `account.deleted(anonymized=true)` 는 PII 마스킹 전용 멱등성 보장이 필요해 `pii_masking_log` 가 별도로 존재 (concern 분리).

### `outbox_events`

security-service 가 발행하는 이벤트의 outbox. polling scheduler 가 Kafka 로 전파.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `event_id` | VARCHAR(36) | NOT NULL, UNIQUE | internal | envelope eventId |
| `topic` | VARCHAR(128) | NOT NULL | internal | 발행 토픽 (`security.pii.masked` 등) |
| `payload` | JSON | NOT NULL | **mixed** | 이벤트 페이로드. PII 포함 금지 (이미 마스킹된 audit 이벤트만) |
| `status` | VARCHAR(20) | NOT NULL, INDEX | internal | `PENDING` / `PUBLISHED` / `FAILED` |
| `created_at` | DATETIME(6) | NOT NULL | internal | UTC |
| `published_at` | DATETIME(6) | NULL | internal | UTC |

**`security.pii.masked`** 페이로드: `accountId`, `tenantId`, `maskedAt`, `tableNames[]`. PII 자체는 포함하지 않으며 audit trail 제공 ([specs/contracts/events/security-events.md § security.pii.masked](../../contracts/events/security-events.md#securitypiimasked-task-be-258)).

### `pii_masking_log` (TASK-BE-258)

GDPR 삭제 마스킹 처리의 멱등성 + audit 로그.

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `event_id` | VARCHAR(36) | NOT NULL, UNIQUE (`uq_pii_masking_log_event_id`) | internal | `account.deleted` 이벤트의 envelope eventId — 멱등성 키 |
| `tenant_id` | VARCHAR(32) | NOT NULL, INDEX | internal | (R8) |
| `account_id` | VARCHAR(36) | NOT NULL, INDEX | internal | account 참조 |
| `masked_at` | DATETIME(6) | NOT NULL | internal | UTC |
| `table_names` | VARCHAR(512) | NOT NULL | internal | JSON 배열: 마스킹된 테이블 이름 목록 |
| `created_at` | DATETIME(6) | NOT NULL, DEFAULT NOW(6) | internal | — |

**불변성**: append-only. 멱등성 unique constraint `event_id`.
**인덱스**: `idx_pii_masking_log_tenant_account` `(tenant_id, account_id)` — 컴플라이언스 audit 조회용.
**역할**: GDPR/PIPA 컴플라이언스 audit trail. 외부 감사인 또는 사용자가 "내 PII 가 모두 마스킹됐는가" 질문 시 본 테이블 + outbox `security.pii.masked` 이벤트 조합으로 증명.
**보존**: 영구 (audit 무결성 — 다른 표와 달리 자동 만료 없음).

---

## Migration Strategy

| 버전 | 변경 |
|---|---|
| V0001 | `login_history` 신규 |
| V0002 | `login_history` UPDATE/DELETE 차단 트리거 |
| V0003 | `processed_events` 신규 |
| V0004 | `outbox` (구) 신규 |
| V0005 | `outbox` → `outbox_events` rename |
| V0006 | `suspicious_events` 신규 |
| V0007 | `account_lock_history` 신규 |
| V0008 | 모든 테이블 `tenant_id` 컬럼 추가 (multi-tenant 도입) |
| V0009 | `pii_masking_log` 신규 (TASK-BE-258) |

---

## PII / Anonymization Notes (TASK-BE-258, TASK-BE-270)

- 마스킹 대상: `login_history.{ip_masked, user_agent_family, device_fingerprint}`, `suspicious_events.evidence`.
- 마스킹 sentinel: `'0.0.0.0'` (IP), `'REDACTED'` (UA), `SHA2(CONCAT(accountId, salt), 256)` (fingerprint), `'{}'` (evidence JSON).
- `device_fingerprint` salt: `app.pii.masking.fingerprint-salt` 로 주입되는 application 수준 고정값. 운영 환경은 env / secret manager override.
- **Salt rotation은 out of scope**: 신규 salt 적용 시 기존 마스킹된 row의 hash 일관성이 깨지므로 별도 절차 필요 (배치 re-mask + downstream consumer 재인덱싱). 현재 single fixed salt 정책.
- **legacy un-salted hash**: TASK-BE-270 이전에 마스킹된 row 가 있다면 un-salted SHA-256 인 상태로 잔존. 별도 데이터 보정 마이그레이션 필요 시 후속 task 로 분리.

---

## Related Specs

- [services/security-service/architecture.md](architecture.md) — service 책임 / consumer 토픽 / 발행 이벤트
- [services/security-service/redis-keys.md](redis-keys.md) — token reuse counter, velocity counter 등 단기 상태
- [contracts/events/account-events.md](../../contracts/events/account-events.md) — `account.deleted` Consumer Obligations
- [contracts/events/security-events.md](../../contracts/events/security-events.md) — `security.pii.masked` 페이로드
- [features/data-rights.md](../../features/data-rights.md) — GDPR 삭제 흐름 전체
