# Feature: Audit Trail

## Purpose

"누가 언제 무엇을 했는가"를 **불변 기록**으로 남기는 감사 체계. 규제 보고, 보안 인시던트 분석, 운영자 행위 추적의 근거가 된다.

## Related Services

| Service | Role |
|---|---|
| security-service | `login_history` (append-only) — 로그인 감사의 원본 |
| admin-service | `admin_actions` (append-only) — 운영자 행위 감사의 원본 |
| account-service | `account_status_history` (append-only) — 상태 변경 감사 |
| gateway-service | 없음 (감사 데이터 미보유) |
| auth-service | 이벤트 발행만 (감사 데이터 저장은 security-service) |

## Audit Data Sources

| 소스 | 테이블 | 소유 서비스 | 불변성 |
|---|---|---|---|
| 로그인 이력 | `login_history` | security-service | append-only, DB 트리거 |
| 비정상 탐지 | `suspicious_events` | security-service | append-only |
| 계정 상태 변경 | `account_status_history` | account-service | append-only, DB 트리거 |
| 운영자 행위 | `admin_actions` | admin-service | append-only, DB 트리거 |

## Auditable Actions (감사 대상 목록, A1)

| Category | Actions |
|---|---|
| 인증 | login attempt, login success, login failure, token refresh, token reuse detected |
| 세션 | session revoked (logout, admin force, reuse detection, account lock/delete) |
| 계정 상태 | ACTIVE→LOCKED, LOCKED→ACTIVE, ACTIVE→DORMANT, DORMANT→ACTIVE, *→DELETED, DELETED→ACTIVE |
| 운영자 작업 | account lock, account unlock, account delete, session force revoke, audit query |
| 보안 탐지 | suspicious detected (velocity, geo, device, token reuse), auto lock triggered |
| 데이터 접근 | 감사 로그 조회 자체 (meta-audit, A5) |

## Audit Event Schema (A2)

모든 감사 이벤트는 다음 최소 필드를 가진다 ([audit-heavy.md](../../rules/traits/audit-heavy.md) A2):

```json
{
  "event_id": "UUID",
  "occurred_at": "ISO-8601 UTC",
  "actor": { "type": "user|operator|system", "id": "string", "session_id": "optional" },
  "action": "standardized action code",
  "target": { "type": "resource type", "id": "resource id" },
  "context": { "ip": "masked", "user_agent": "optional", "reason": "string" },
  "outcome": "success|failure",
  "metadata": {}
}
```

## Immutability (A3)

- 모든 감사 테이블에 **DB 트리거** 또는 **MySQL 권한 제한**으로 UPDATE/DELETE 차단
- 수정이 필요한 경우 **정정 이벤트(correction event)**를 새 row로 추가. 원본은 유지
- 테스트에서 반드시 검증: UPDATE 시도 → 거부 확인

## Retention (A4)

| 데이터 | 최소 보존 | 아카이브 | 삭제 |
|---|---|---|---|
| login_history | 1년 | 1년 후 cold storage | 5년 후 완전 삭제 |
| suspicious_events | 2년 | 2년 후 cold storage | 7년 후 |
| account_status_history | 계정 존속 기간 + 1년 | — | 익명화 후 1년 |
| admin_actions | 5년 | — | 7년 후 |

구체 기간은 환경 변수로 조정 가능. 규제 요구 변화에 대응.

## Meta-Audit (A5)

감사 로그 **조회 자체**가 감사된다:
- admin-service의 `GET /api/admin/audit` → `admin_actions` 에 `actionCode=AUDIT_QUERY` 기록
- security-service의 query 엔드포인트 → 내부 `admin_query_audit` 로그

## Fail-Closed (A10)

감사 기록 쓰기 실패 시 **비즈니스 액션도 실패**:
- admin-service: `admin_actions` INSERT 실패 → downstream 호출 중단 → 502
- auth-service: outbox 이벤트 INSERT 실패 → 로그인 트랜잭션 전체 롤백
- account-service: `account_status_history` INSERT 실패 → 상태 전이 롤백

"감사는 best-effort"는 **금지**.

## Related Contracts

- Events: [auth-events.md](../contracts/events/auth-events.md), [account-events.md](../contracts/events/account-events.md), [admin-events.md](../contracts/events/admin-events.md), [security-events.md](../contracts/events/security-events.md)
- Query: [admin-api.md](../contracts/http/admin-api.md) `GET /api/admin/audit`, [security-query-api.md](../contracts/http/security-query-api.md)
