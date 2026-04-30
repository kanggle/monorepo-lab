# auth-service — Overview

## Purpose

인증(authentication) 전담 서비스. 사용자 자격 증명을 검증하고, JWT access/refresh token을 발급·회전·무효화한다. 로그인 실패 카운팅, refresh token 재사용 탐지, JWT 서명 키(JWKS) 배포의 단일 소유자. **권한(authorization) 결정은 다루지 않는다** — 그건 각 다운스트림 서비스의 책임.

Global Account Platform에서 "로그인이 성공했는가?"에 대한 유일한 진실 소스.

## Callers

- **gateway-service** — JWKS 페치 (내부 HTTP)
- **외부 사용자** (gateway 경유) — `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/refresh`
- **admin-service** (내부 HTTP) — 강제 로그아웃, refresh token 강제 revoke

## Callees

| 대상 | 목적 | 경로 |
|---|---|---|
| `account-service` | credential lookup (이메일 → account_id, credential_hash) · 계정 상태 조회 | 내부 HTTP [contracts/http/internal/auth-to-account.md](../../contracts/http/internal/) |
| MySQL (`auth_db`) | credentials, refresh_tokens, outbox_events | 직접 JPA |
| Redis | 로그인 실패 카운터, refresh token 블랙리스트, JWKS 캐시 | 직접 접속 |
| Kafka | outbox relay → `auth.login.*`, `auth.token.*` 이벤트 발행 | producer |

## Owned State

### MySQL
- `credentials` — `account_id`, `credential_hash` (argon2/bcrypt), `created_at`, `updated_at`, `version` (낙관적 락)
- `refresh_tokens` — `jti`, `account_id`, `issued_at`, `expires_at`, `rotated_from`, `revoked`, `device_fingerprint`
- `outbox_events` — 모든 auth 이벤트의 영속 스테이징
- `processed_events` — 멱등 소비 dedup (해당되는 경우)

### Redis
- `login:fail:{email_hash}` — 실패 카운터, TTL 15분
- `refresh:blacklist:{jti}` — revoke된 refresh token의 블랙리스트, TTL = token 만료 시각
- `jwks:cache` — auth-service 자체가 퍼블리시 대상이므로 캐시는 **내부적으로 사용하지 않음** (gateway가 소유)

## Change Drivers

1. **새 인증 방식 도입** — OAuth 2.0 제공자 추가 (Google, Apple, Kakao), SAML SSO, WebAuthn
2. **토큰 정책 변경** — access token TTL, refresh token rotation 주기, 서명 알고리즘 교체
3. **2FA 요구사항** — TOTP, SMS OTP, 인증기 앱 연동
4. **비밀번호 정책 변경** — 최소 길이, 복잡도, 해시 알고리즘 업그레이드 (argon2id → argon2 최신판)
5. **보안 인시던트 대응** — 대량 revoke, 키 교체, suspicious 기준 강화
6. **규제 요구** — 로그인 기록 보존 기간 변경, 삭제 요청 처리

## Not This Service

- ❌ **계정(account) 생성** — account-service의 SignupUseCase가 소유. auth-service는 가입 후의 credentials만 관리
- ❌ **프로필 데이터** — 이름·이메일·전화는 account-service의 profile 테이블에 있음
- ❌ **권한 결정** — "이 토큰으로 이 리소스에 접근 가능한가?"는 각 리소스 소유 서비스가 판단
- ❌ **로그인 이력 조회** — 이력 적재는 security-service가 이벤트 소비로 수행. auth-service는 이벤트 발행만
- ❌ **비정상 로그인 탐지** — security-service의 책임. auth-service는 단순 실패 카운터만 관리
- ❌ **관리자 감사 조회** — admin-service가 security-service의 query를 호출

## Related Specs

- Architecture: [architecture.md](architecture.md)
- Data model: [data-model.md](data-model.md)
- Dependencies: [dependencies.md](dependencies.md)
- Observability: [observability.md](observability.md)
- Redis keys: [redis-keys.md](redis-keys.md)
- HTTP contracts: [../../contracts/http/auth-api.md](../../contracts/http/) (외부), [../../contracts/http/internal/auth-to-account.md](../../contracts/http/internal/) (내부)
- Event contract: [../../contracts/events/auth-events.md](../../contracts/events/)
