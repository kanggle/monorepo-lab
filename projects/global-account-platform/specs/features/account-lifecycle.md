# Feature: Account Lifecycle

## Purpose

계정 상태(ACTIVE / LOCKED / DORMANT / DELETED)의 전이 규칙과 각 상태의 의미·제약·자동 전이·삭제 유예 정책을 정의한다.

## Related Services

| Service | Role |
|---|---|
| account-service | 상태 기계 소유. 모든 전이의 유일한 실행자 |
| auth-service | 상태 변경 이벤트 소비 → 세션 무효화 |
| security-service | AUTO_DETECT 잠금 명령 발행 |
| admin-service | 운영자 lock/unlock/delete 명령 |

## State Definitions

### ACTIVE
정상 사용 중. 로그인·API 사용 가능. 모든 신규 계정의 초기 상태.

### LOCKED
일시 차단. 로그인 시도 시 403 `ACCOUNT_LOCKED`. 잠금 원인:
- `ADMIN_LOCK`: 운영자 수동 잠금
- `AUTO_DETECT`: security-service의 비정상 탐지 (velocity/geo/device/token-reuse)
- `PASSWORD_FAILURE_THRESHOLD`: 로그인 실패 임계치 초과 (미래 자동 잠금)

해제: 운영자 unlock 또는 사용자 본인 복구 (미래).

### DORMANT
장기 미사용 휴면. 로그인 시도 시 403 `ACCOUNT_DORMANT`. 전이 조건:
- 마지막 로그인 성공 후 **365일** 경과 (배치 또는 스케줄러)
- 사용자가 로그인하면 즉시 ACTIVE 복귀

### DELETED
삭제 유예 상태. 로그인 불가. 전이 원인:
- `USER_REQUEST`: 사용자 자발적 탈퇴
- `ADMIN_DELETE`: 운영자 강제 삭제
- `REGULATED_DELETION`: 규제 요구 삭제 (GDPR 등)

유예 기간: **30일**. 이내에 admin-only 복구 가능 (DELETED → ACTIVE). 유예 만료 후 PII 익명화 실행 ([regulated.md](../../rules/traits/regulated.md) R7).

## State Machine

| From | To | Triggers | Actor |
|---|---|---|---|
| ACTIVE | LOCKED | admin_lock, auto_detect, password_failure | operator, system |
| ACTIVE | DORMANT | 365일 미접속 | system (배치) |
| ACTIVE | DELETED | user_request, admin_delete, regulated_deletion | user, operator |
| LOCKED | ACTIVE | admin_unlock, user_recovery | operator, user |
| LOCKED | DELETED | admin_delete, regulated_deletion | operator |
| DORMANT | ACTIVE | user_login | user |
| DORMANT | DELETED | admin_delete, regulated_deletion | operator |
| DELETED | ACTIVE | within_grace_period (admin_only) | operator |

**금지 전이**: DELETED → LOCKED, DELETED → DORMANT, DORMANT → LOCKED (sideways 전이 없음)

## Automatic Transitions

| 전이 | 트리거 | 실행 주체 | 주기 |
|---|---|---|---|
| ACTIVE → DORMANT | last_login_succeeded_at + 365일 | 배치 스케줄러 | 일 1회 |
| DELETED → 익명화 | deleted_at + 30일 | 배치 스케줄러 | 일 1회 |

## Side Effects per Transition

| 전이 | 이벤트 | 추가 작업 |
|---|---|---|
| → LOCKED | `account.locked` | auth-service: 해당 계정 전체 세션 revoke |
| → ACTIVE (from LOCKED) | `account.unlocked` | auth-service: 로그인 제한 해제 |
| → DELETED | `account.deleted` | auth-service: 전체 세션 revoke. 30일 후 PII 익명화 |
| → ACTIVE (from DORMANT) | `account.status.changed` | — (로그인 성공 자체가 활성화) |
| → ACTIVE (from DELETED, grace) | `account.status.changed` | 익명화 배치에서 제외 |

## Audit

모든 전이는 `account_status_history` 테이블에 **append-only**로 기록 ([audit-heavy.md](../../rules/traits/audit-heavy.md) A3):
- `from_status`, `to_status`, `reason_code`, `actor_type`, `actor_id`, `occurred_at`, `details`

## Related Contracts

- HTTP: [account-api.md](../contracts/http/account-api.md) (DELETE /me), [admin-api.md](../contracts/http/admin-api.md) (lock/unlock)
- Internal: [security-to-account.md](../contracts/http/internal/security-to-account.md), [admin-to-account.md](../contracts/http/internal/admin-to-account.md)
- Events: [account-events.md](../contracts/events/account-events.md)
