# Feature: Authentication

> 신규 소비 서비스가 GAP를 OIDC IdP로 이용하는 통합 절차는 [consumer-integration-guide.md](consumer-integration-guide.md) 의 Phase 3 (discovery + JWKS) 와 Phase 4 (S2S `client_credentials`) 를 단일 진입점으로 사용한다.

## Purpose

사용자 자격 증명을 검증하고 JWT 토큰을 발급·갱신·무효화하는 인증 흐름 전체를 정의한다. 이 기능은 플랫폼의 **보안 게이트** 역할.

## Related Services

| Service | Role |
|---|---|
| auth-service | 인증 로직 소유. 로그인·로그아웃·refresh 처리, JWT 발급 |
| account-service | credential lookup 제공 (내부 HTTP) |
| gateway-service | JWT 검증, 공개 엔드포인트 라우팅 |
| security-service | 로그인 이벤트 소비, 비정상 탐지 |

## User Flows

### 이메일·패스워드 로그인

1. 사용자가 `POST /api/auth/login` 에 이메일·패스워드 전송
2. gateway가 rate limit 검사 → 통과 시 auth-service로 전달
3. auth-service가 account-service에 credential lookup 요청 (내부 HTTP)
4. 계정 상태 확인: ACTIVE가 아니면 거부 (LOCKED → 403, DORMANT → 403, DELETED → 403)
5. argon2id 해시 비교. 실패 시 Redis 실패 카운터 증가 → 임계치(5회) 초과 시 429
6. 성공 시: access token(RS256, 30분) + refresh token(7일) 발급
7. `auth.login.succeeded` / `auth.login.failed` / `auth.login.attempted` 이벤트 발행 (outbox)

### 토큰 갱신 (Refresh Token Rotation)

1. 사용자가 `POST /api/auth/refresh` 에 현재 refresh token 전송
2. auth-service가 `jti` 검증: 블랙리스트 확인 → rotated_from 체인 검사 (재사용 탐지)
3. 정상이면 새 access + refresh 발급, 기존 refresh token의 jti를 `rotated_from`으로 연결
4. **재사용 탐지**: 이미 rotation된 token이 다시 제출되면 → 해당 계정의 전체 세션 즉시 revoke + `auth.token.reuse.detected` 이벤트

### 로그아웃

1. 사용자가 `POST /api/auth/logout` 에 refresh token 전송 (access token으로 인증)
2. auth-service가 해당 refresh token을 블랙리스트에 등록 (`refresh:blacklist:{jti}`)
3. `session.revoked` 이벤트 발행

## Business Rules

- Access token TTL: **30분** (환경 변수로 조정 가능, 최소 5분 / 최대 2시간)
- Refresh token TTL: **7일** (조정 가능, 최소 1일 / 최대 30일)
- 로그인 실패 임계치: **5회 / 15분** → 429 + `LOGIN_RATE_LIMITED` ([redis-keys.md](../services/auth-service/redis-keys.md))
- 패스워드 해시: argon2id (memory=65536, iterations=3, parallelism=1)
- JWT 서명: RS256. 키 rotation은 [gateway-to-auth.md](../contracts/http/internal/gateway-to-auth.md) 참조
- 토큰 재사용 탐지 시 **해당 계정의 모든 세션 즉시 무효화** (zero tolerance)
- 에러 응답에 실패 원인 구체적 노출 금지 (credential stuffing 방지) — 일관되게 `CREDENTIALS_INVALID`

## Related Contracts

- HTTP: [auth-api.md](../contracts/http/auth-api.md), [auth-to-account.md](../contracts/http/internal/auth-to-account.md), [gateway-to-auth.md](../contracts/http/internal/gateway-to-auth.md)
- Events: [auth-events.md](../contracts/events/auth-events.md), [session-events.md](../contracts/events/session-events.md)
