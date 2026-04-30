# Feature: Session Management

## Purpose

사용자 세션(access/refresh token pair)의 생명주기를 정의한다. 발급·갱신·만료·무효화·동시 세션 정책을 포함.

## Related Services

| Service | Role |
|---|---|
| auth-service | 세션(토큰) 발급·갱신·revoke 소유 |
| gateway-service | access token 검증, 만료된 토큰 거부 |
| admin-service | 강제 로그아웃 명령 |
| security-service | 세션 무효화 이력 기록 |

## Session Model

이 플랫폼은 **stateless JWT + refresh token rotation** 모델을 사용한다.

| 구성 요소 | 저장 위치 | TTL | 설명 |
|---|---|---|---|
| Access Token | 클라이언트 메모리 | 30분 | RS256 서명 JWT. 서버 측 상태 없음 |
| Refresh Token | 클라이언트 저장소 + auth DB | 7일 | rotation 기반. DB에 `refresh_tokens` row |
| Blacklist | Redis | 토큰 잔여 수명 | revoke된 refresh token의 jti 목록 |

**서버 측 세션 저장소(HttpSession, Redis session)는 사용하지 않는다.** 모든 인증 상태는 JWT claim으로 전달.

## Session Lifecycle

```
[로그인 성공] → access(30min) + refresh(7day) 발급
       ↓
[access 만료] → refresh로 rotation → 새 access + 새 refresh 발급
       ↓                                    ↓
[refresh 만료] → 재로그인 필요        [refresh 재사용 탐지]
                                          ↓
                                   전체 세션 즉시 revoke
```

## Concurrent Sessions

- **동시 세션 제한 없음** (초기 스코프). 여러 디바이스에서 동시 로그인 가능.
- 각 로그인마다 독립적인 refresh token 발급 (device_fingerprint로 구분)
- **미래 옵션**: 최대 N개 세션 제한 (LRU 방식으로 가장 오래된 세션 revoke)

## Invalidation Scenarios

| 시나리오 | 트리거 | 영향 범위 | 이벤트 |
|---|---|---|---|
| 사용자 로그아웃 | `POST /api/auth/logout` | 해당 세션 1개 | `session.revoked` |
| 관리자 강제 로그아웃 | `POST /api/admin/sessions/{id}/revoke` | 해당 계정 전체 | `session.revoked` |
| 토큰 재사용 탐지 | refresh rotation 중 중복 감지 | 해당 계정 전체 | `auth.token.reuse.detected` + `session.revoked` |
| 계정 잠금 | ACTIVE → LOCKED 전이 | 해당 계정 전체 | `account.locked` → auth-service 소비 → revoke |
| 계정 삭제 | ACTIVE → DELETED 전이 | 해당 계정 전체 | `account.deleted` → auth-service 소비 → revoke |
| 패스워드 변경 | (미래) 패스워드 변경 시 | 다른 모든 세션 (선택적) | `session.revoked` |

## Business Rules

- Access token은 서버에서 revoke 불가 (stateless). **만료까지 유효**. 따라서 짧은 TTL(30분)이 중요
- Refresh token blacklist 조회 실패(Redis 장애) 시 **fail-closed**: refresh 거부
- `refresh:invalidate-all:{account_id}` 플래그: 이 키가 존재하면 해당 계정의 `issued_at < 이 값`인 모든 refresh token은 무효
- Idle timeout: 없음 (stateless). Absolute timeout = refresh token TTL

## Related Contracts

- HTTP: [auth-api.md](../contracts/http/auth-api.md) (login/logout/refresh)
- Internal: [admin-to-auth.md](../contracts/http/internal/admin-to-auth.md) (force-logout)
- Events: [auth-events.md](../contracts/events/auth-events.md), [session-events.md](../contracts/events/session-events.md)
