# Feature: Admin Operations

## Purpose

운영자(CS, SRE, 보안팀)가 계정 상태를 관리하고 보안 인시던트에 대응하며 감사 이력을 조회하는 운영 기능 전체를 정의한다.

## Related Services

| Service | Role |
|---|---|
| admin-service | 운영 명령 오케스트레이션. 감사 기록. 유일한 진입점 |
| auth-service | 강제 로그아웃 실행 |
| account-service | lock/unlock/delete 실행 |
| security-service | 감사 조회 데이터 제공 |

## Operator Roles

| Role | 권한 | 예시 사용자 |
|---|---|---|
| `SUPER_ADMIN` | 모든 작업 + 역할 관리 | 시스템 관리자 |
| `ACCOUNT_ADMIN` | lock/unlock/revoke/delete + 감사 조회 | CS팀, 보안팀 |
| `AUDITOR` | 감사 조회만 (읽기 전용) | 컴플라이언스팀, 법무팀 |

## Available Commands

### Account Lock
- **입력**: accountId, reason, ticketId
- **실행**: admin → account-service `POST /internal/accounts/{id}/lock`
- **결과**: ACTIVE→LOCKED + 전체 세션 revoke + 감사 기록

### Account Unlock
- **입력**: accountId, reason, ticketId
- **실행**: admin → account-service `POST /internal/accounts/{id}/unlock`
- **결과**: LOCKED→ACTIVE + 감사 기록

### Force Logout
- **입력**: accountId, reason
- **실행**: admin → auth-service `POST /internal/auth/accounts/{id}/force-logout`
- **결과**: 해당 계정 전체 refresh token revoke + 감사 기록

### Account Delete (강제)
- **입력**: accountId, reason, ticketId
- **실행**: admin → account-service `POST /internal/accounts/{id}/delete`
- **결과**: →DELETED (30일 유예) + 전체 세션 revoke + 감사 기록

### Audit Query
- **입력**: accountId, actionCode, from, to, source, page, size
- **실행**: admin → security-service query + 자체 admin_actions 조회 → 통합 응답
- **결과**: 통합 감사 뷰 + **조회 자체를 meta-audit로 기록**

## Business Rules

- 모든 명령에 **사유(reason) 필수** — 사유 없는 운영 작업 거부 (400 REASON_REQUIRED)
- 모든 명령에 **Idempotency-Key 필수** — 네트워크 재시도 안전
- 모든 명령이 **감사 기록** — 성공·실패 불문 (`admin_actions` append-only)
- **fail-closed**: 감사 기록 실패 시 명령 자체 실패 (A10)
- 운영자 인증은 **일반 사용자와 별도 경계** — operator scope JWT, 별도 발급 경로
- 운영자도 rate limit 적용 (실수 방지, 초당 2~3건 이하)

## Audit Trail Integration

```
[Operator 명령] → admin_actions row (begin, outcome=IN_PROGRESS)
       ↓
[downstream HTTP 호출]
       ↓
[결과] → admin_actions row UPDATE (outcome=SUCCESS|FAILURE)
       → admin.action.performed 이벤트 (outbox)
```

admin_actions 테이블 자체도 **append-only** — UPDATE는 outcome 필드만, DB 트리거로 다른 필드 변경 차단. (구현 옵션: outcome 변경 대신 별도 completion row 추가로 순수 append-only 유지)

## Related Contracts

- HTTP: [admin-api.md](../contracts/http/admin-api.md)
- Internal: [admin-to-auth.md](../contracts/http/internal/admin-to-auth.md), [admin-to-account.md](../contracts/http/internal/admin-to-account.md)
- Events: [admin-events.md](../contracts/events/admin-events.md)
