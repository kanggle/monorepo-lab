# auth-service — Device / Session Model

## Purpose

auth-service가 계정별로 **활성 refresh token 세션**을 디바이스 단위로 추적·관리하기 위한 스펙. 사용자가 "현재 로그인된 디바이스 목록"을 조회하고, 특정 디바이스의 세션을 원격으로 종료할 수 있도록 한다. 또한 계정당 동시 세션 수 상한을 강제하여 stolen-credential 시 blast radius를 축소한다.

이 문서는 [specs/services/auth-service/architecture.md](./architecture.md)와 [specs/services/auth-service/data-model.md](./data-model.md)의 하위 컴포넌트로서, 기존 `refresh_tokens` 테이블의 `device_fingerprint` 컬럼 역할을 **논리적으로 대체**한다.

---

## Design Decisions

### D1. `device_id`는 fingerprint가 아니다 — opaque surrogate ID

| 항목 | 값 |
|---|---|
| 타입 | `VARCHAR(36)` (UUID v7) |
| 입력 | client-provided fingerprint는 **입력값**이지 식별자가 아니다 |
| 생성 | 서버에서 `(account_id, device_fingerprint, first_seen_at)` 튜플을 관측한 시점에 UUID v7을 신규 발급 |
| 노출 | 클라이언트·API 응답에서 이 `device_id`만 사용. fingerprint 원문은 외부로 노출하지 않음 |

**이유**:
- fingerprint를 그대로 식별자로 쓰면 악의적 클라이언트가 임의 fingerprint를 전송해 타인의 세션 row를 오염시킬 수 있음
- fingerprint는 재조합·스푸핑·re-install로 바뀔 수 있는 **관측값**. 식별자는 서버가 소유해야 함 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R1 — 식별자와 관측 데이터의 분리)
- `device_id`를 서버 생성 opaque ID로 만들면 PII 유출 risk 없이 DELETE 엔드포인트의 path variable로 사용 가능

### D2. 동일 fingerprint + 다른 account = 다른 device_session

`device_sessions`는 **account-scoped**이다. 같은 물리 디바이스에 두 계정이 로그인하면 `device_sessions`에 두 row가 생긴다. 유니크 키는 `(account_id, device_fingerprint, first_seen_at)`이며, `device_id`는 그 surrogate.

### D3. unknown fingerprint 정책

fingerprint가 null·빈 문자열·resolve 실패인 경우:
- `device_fingerprint` 컬럼에 `"unknown"` sentinel 저장 (NULL 허용하지 않음 — 유니크 제약 안정화)
- 같은 account의 "unknown" fingerprint 세션은 **매 로그인마다 신규 `device_id`**로 생성 (first_seen_at 타임스탬프가 다르므로 유니크 보장)
- `max_active_sessions` 상한 적용 대상에 포함됨

### D4. Concurrent-session policy

| 파라미터 | 기본값 | 근거 |
|---|---|---|
| `max_active_sessions` | **10** | 일반 사용자의 실제 디바이스 수(모바일·태블릿·PC·브라우저 다중)를 감안한 상한. 환경변수 `AUTH_MAX_ACTIVE_SESSIONS`로 조정 가능 |

**Eviction 규칙** — 로그인 성공 시 신규 `device_session` row를 insert하기 **직전**에 평가:

1. `SELECT COUNT(*) FROM device_sessions WHERE account_id = ? AND revoked_at IS NULL` 취득
2. 결과가 `max_active_sessions` 이상이면:
   - `ORDER BY last_seen_at ASC LIMIT (count - max_active_sessions + 1)`로 가장 오래된 세션(들)을 선정
   - 각 세션에 대해 **연결된 refresh_tokens를 모두 `revoked = TRUE`로 업데이트** (D5 cascade)
   - `device_sessions.revoked_at = NOW()` 설정
   - 각 evicted 세션마다 `auth.session.revoked` outbox 이벤트 발행 (`reason = "EVICTED_BY_LIMIT"`)
3. 신규 `device_session` insert + 신규 `refresh_tokens` insert + `auth.session.created` outbox 발행

**원자성**: 1~3의 전체 흐름은 단일 DB 트랜잭션. 중간에 실패하면 **신규 device_session 생성도 롤백**되고 로그인은 `500 INTERNAL` 또는 재시도 유도. 부분 eviction 상태는 허용하지 않는다 ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T3).

### D5. refresh_tokens ↔ device_sessions 매핑

- `refresh_tokens.device_id` 컬럼(`VARCHAR(36)`, nullable during migration window)은 `device_sessions.device_id`를 **논리 참조**한다 (서비스 내 참조이므로 FK는 선택적; 실 배포에서는 FK 추가 권장).
- 기존 `refresh_tokens.device_fingerprint` 컬럼은 **deprecated**. 신규 write 경로에서는 더 이상 채우지 않으며, 조회 경로는 `device_sessions` join으로 대체한다. 물리 drop은 별도 마이그레이션 태스크에서 수행.
- Refresh rotation 시 새 `refresh_tokens` row는 **동일한 `device_id`를 상속**하고, `device_sessions.last_seen_at`을 현재 시각으로 갱신한다.
- 세션 revoke는 양방향:
  - `DELETE /api/accounts/me/sessions/{deviceId}` → `device_sessions.revoked_at` 기록 + 해당 `device_id`의 모든 `refresh_tokens.revoked = TRUE`
  - Token reuse detection으로 인해 특정 refresh token family가 revoke되면 → 그 family가 속한 `device_sessions.revoked_at`도 동시 기록

---

## Data Model

### `device_sessions`

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `device_id` | VARCHAR(36) | UNIQUE, NOT NULL | internal | 서버 발급 UUID v7. 외부 노출 식별자 (D1) |
| `account_id` | VARCHAR(36) | NOT NULL, INDEX | internal | account-service의 account.id 참조 (서비스 간 FK 금지) |
| `device_fingerprint` | VARCHAR(128) | NOT NULL | confidential | 관측된 fingerprint 해시. null 대신 `"unknown"` sentinel (D3) |
| `user_agent` | VARCHAR(512) | NULL | internal | 최초 관측된 원본 UA (truncate 512) |
| `ip_last` | VARCHAR(45) | NULL | confidential | 마지막으로 관측된 IP (IPv6 대응 길이). 저장 시 masking 없이 원본 — 응답 시 마스킹 |
| `geo_last` | VARCHAR(2) | NULL | internal | ISO-3166-1 alpha-2 (예: `KR`). resolve 실패 시 `XX` |
| `issued_at` | DATETIME(6) | NOT NULL | internal | 이 device_session이 최초 생성된 시각 (= first_seen_at) |
| `last_seen_at` | DATETIME(6) | NOT NULL, INDEX | internal | 가장 최근 rotation/활동 시각. eviction 순서 결정 |
| `revoked_at` | DATETIME(6) | NULL | internal | 명시적 revoke 또는 eviction으로 종료된 시각. NULL이면 active |
| `revoke_reason` | VARCHAR(40) | NULL | internal | `USER_REQUESTED` / `EVICTED_BY_LIMIT` / `TOKEN_REUSE` / `ADMIN_FORCED` / `LOGOUT_OTHERS` |

**인덱스**:
- `idx_device_sessions_device_id` (UNIQUE)
- `idx_device_sessions_account_active` (`account_id`, `revoked_at`) — 활성 세션 COUNT/리스트 경로
- `idx_device_sessions_last_seen` (`account_id`, `last_seen_at`) — eviction 쿼리 지원
- `uk_device_sessions_account_fp_first_seen` UNIQUE (`account_id`, `device_fingerprint`, `issued_at`) — D2의 불변 보장

**데이터 분류**: [rules/traits/regulated.md](../../../rules/traits/regulated.md) R1에 따라 `device_fingerprint`, `ip_last`는 **confidential**. 감사 로그 조회 외 read API 응답에서는 마스킹된 형태로만 노출.

### IP Masking Format

HTTP 응답·이벤트 payload에서 IP를 노출할 때는 아래 규칙을 **단일 표준**으로 사용한다. 원본 IP는 `device_sessions.ip_last`에만 저장되며 외부 경로에서는 절대 노출하지 않는다.

| 형식 | 마스킹 규칙 | 예시 |
|---|---|---|
| IPv4 | 마지막 **두 옥텟**을 `*`로 치환 | `192.168.1.42` → `192.168.*.*` |
| IPv6 | 마지막 **80 bit**(하위 5 그룹)를 `*`로 치환 후 `::*`로 축약 | `2001:db8:85a3:1:2:3:4:5` → `2001:db8:85a3::*` |

규칙 상세:
- IPv4는 항상 4옥텟 dotted-decimal로 변환 후 마스킹. 축약·leading-zero 없음
- IPv6는 정규화(RFC 5952) 후 상위 48 bit(첫 3 그룹)만 유지, 나머지는 `::*` sentinel 하나로 대체
- resolve 실패·비정상 입력은 `"unknown"` 문자열 반환 (IP를 전혀 노출하지 않음)
- JSON 필드명은 `ipMasked`로 통일 (HTTP 응답·Kafka 이벤트 payload 공통)

이 포맷은 [specs/contracts/http/auth-api.md](../../contracts/http/auth-api.md)의 `GET /api/accounts/me/sessions`, `GET /api/accounts/me/sessions/current` 응답과 [specs/contracts/events/auth-events.md](../../contracts/events/auth-events.md)의 `auth.login.attempted`, `auth.login.succeeded`, `auth.login.failed`, `auth.token.refreshed`, `auth.token.reuse.detected`, `auth.session.created`에 동일하게 적용된다.

### `refresh_tokens` 변경 (참고)

| 컬럼 | 변경 내용 |
|---|---|
| `device_id` | **추가** — `VARCHAR(36) NULL, INDEX`. `device_sessions.device_id`를 참조 |
| `device_fingerprint` | **deprecated** — 읽기 경로 우선 제거. 물리 drop은 후속 마이그레이션 |

> 실제 DDL/Flyway migration은 TASK-BE-023에서 작성한다. 이 문서는 계약·모델만 선언한다.

---

## Lifecycle

```
login success
  → (evict if count >= max_active_sessions)
  → INSERT device_sessions (revoked_at = NULL)
  → INSERT refresh_tokens (device_id = new)
  → outbox: auth.session.created

refresh rotation
  → UPDATE device_sessions SET last_seen_at = NOW() WHERE device_id = ?
  → INSERT refresh_tokens (device_id = same, rotated_from = previous jti)

explicit revoke (user-initiated or admin)
  → UPDATE device_sessions SET revoked_at = NOW(), revoke_reason = ?
  → UPDATE refresh_tokens SET revoked = TRUE WHERE device_id = ?
  → outbox: auth.session.revoked

token reuse detected (기존 탐지 경로)
  → 해당 account의 모든 refresh_tokens revoke
  → 영향 받은 device_sessions 모두 revoked_at 기록 (revoke_reason = 'TOKEN_REUSE')
  → outbox: auth.session.revoked (each)
```

---

## Open Issues (구현 태스크 입력으로 전달)

- `device_id` 발급 시점에 Redis 캐시(`device:session:{deviceId}`)도 함께 갱신할지 여부 — TASK-BE-023 결정
- `auth.session.revoked` consumer(security-service)의 fan-out 구독 여부 — auth-events 스펙상 topic 등록만 확정, consumer 구현은 security-service 개별 태스크
