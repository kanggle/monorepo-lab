# auth-service — Data Model

## Design Decision

credentials(비밀)와 profile(비밀 아님)은 **물리적으로 별도 서비스·DB**에 저장한다 ([rules/domains/saas.md](../../../rules/domains/saas.md) S1). auth-service는 credentials 전담, profile은 account-service가 소유.

---

## Tables

### `credentials`

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `account_id` | VARCHAR(36) | UNIQUE, NOT NULL | internal | account-service의 account.id 참조 (FK 없음 — 서비스 간 FK 금지) |
| `credential_hash` | VARCHAR(255) | NOT NULL | **restricted** | argon2id 해시. 평문 저장 금지 (R2) |
| `hash_algorithm` | VARCHAR(30) | NOT NULL, DEFAULT 'argon2id' | internal | 향후 알고리즘 업그레이드 시 구분 |
| `created_at` | DATETIME(6) | NOT NULL | internal | — |
| `updated_at` | DATETIME(6) | NOT NULL | internal | 패스워드 변경 시 갱신 |
| `version` | INT | NOT NULL, DEFAULT 0 | internal | 낙관적 락 (T5) |

**인덱스**: `idx_credentials_account_id` (UNIQUE)

### `refresh_tokens`

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK | internal | — |
| `jti` | VARCHAR(36) | UNIQUE, NOT NULL | confidential | JWT ID (UUID v7 권장) |
| `account_id` | VARCHAR(36) | NOT NULL, INDEX | internal | — |
| `issued_at` | DATETIME(6) | NOT NULL | internal | — |
| `expires_at` | DATETIME(6) | NOT NULL | internal | — |
| `rotated_from` | VARCHAR(36) | NULL, INDEX | confidential | 이전 토큰의 jti. NULL이면 최초 발급 |
| `revoked` | BOOLEAN | NOT NULL, DEFAULT FALSE | internal | 명시적 revoke 여부 |
| `device_id` | VARCHAR(36) | NULL, INDEX | internal | `device_sessions.device_id` 참조 (FK 없음). NULL 허용 (D5 백필 전 기존 row). [device-session.md](./device-session.md) D5 canonical |
| `device_fingerprint` | VARCHAR(128) | NULL | confidential | (deprecated, superseded by `device_id`) 디바이스 식별 (해시) |

**인덱스**: `idx_rt_jti` (UNIQUE), `idx_rt_account_id`, `idx_rt_rotated_from`, `idx_rt_device_id`

**토큰 재사용 탐지 로직**: `POST /api/auth/refresh`가 `jti=A`로 rotation을 요청했을 때, A에 이미 `rotated_from`을 참조하는 자식이 존재하면 → 재사용 탐지. 해당 `account_id`의 모든 refresh_token을 `revoked=TRUE`로 일괄 처리 + `auth.token.reuse.detected` 이벤트 발행.

### `outbox`

[libs/java-messaging](../../../libs/java-messaging) 표준 스키마 사용. 테이블 이름은 라이브러리의 `OutboxJpaEntity`가 `@Table(name = "outbox")`으로 선언하므로 `outbox`를 사용한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `aggregate_type` | VARCHAR(100) | `auth` |
| `aggregate_id` | VARCHAR(255) | `account_id` |
| `event_type` | VARCHAR(100) | `auth.login.succeeded` 등 |
| `payload` | TEXT (JSON) | 이벤트 envelope |
| `created_at` | TIMESTAMP | — |
| `published_at` | TIMESTAMP | NULL이면 미발행 |
| `status` | VARCHAR(20) | `PENDING` / `PUBLISHED` |

---

## Migration Strategy

- **Flyway**: `V{nnnn}__{description}.sql` 네이밍
- 첫 마이그레이션: `V0001__create_credentials_and_refresh_tokens.sql`
- Outbox 테이블: `V0002__create_outbox_events.sql` (libs/java-messaging DDL 재사용, 테이블 이름은 `outbox`)
- PII 마스킹 컬럼 (`credential_hash`, `device_fingerprint`) 변경 시 down migration 금지 — 단방향만 허용

---

## Data Classification Summary

| 등급 | 컬럼 |
|---|---|
| **restricted** | `credentials.credential_hash` |
| **confidential** | `refresh_tokens.jti`, `refresh_tokens.rotated_from`, `refresh_tokens.device_fingerprint`, `device_sessions.device_fingerprint`, `device_sessions.ip_last` |
| **internal** | `refresh_tokens.device_id`, 그리고 위에 명시되지 않은 `credentials`, `refresh_tokens`, `device_sessions`, `outbox`의 모든 컬럼 (예: `device_sessions.device_id`, `account_id`, `user_agent`, `geo_last`, `issued_at`, `last_seen_at`, `revoked_at`, `revoke_reason`) |
| **public** | 없음 |

[rules/traits/regulated.md](../../../rules/traits/regulated.md) R1 준수.

> `device_sessions` DDL/인덱스/제약은 본 문서가 아닌 [specs/services/auth-service/device-session.md](./device-session.md) "Data Model" 절에서 선언된다. 본 Classification Summary는 해당 테이블의 컬럼 분류를 canonical 위치에서 재확인하는 역할만 수행한다.
