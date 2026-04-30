# Feature: Rate Limiting

## Purpose

API 남용·credential stuffing·DDoS를 방지하기 위한 요청 제한 정책. 두 레이어에서 작동: (1) gateway의 글로벌 rate limit, (2) auth-service의 로그인별 실패 카운터.

## Related Services

| Service | Role |
|---|---|
| gateway-service | L7 rate limit (토큰 버킷, Redis). 모든 공개 요청에 적용 |
| auth-service | 로그인 실패 카운터 (Redis). 이메일별 연속 실패 추적 |

## Two-Layer Design

### Layer 1: Gateway Rate Limit (L7)

IP / 서브넷 기반. 모든 공개 엔드포인트에 적용.

| Scope | Identifier | Window | Limit (기본) | 응답 |
|---|---|---|---|---|
| `login` | IP /24 서브넷 해시 | 60초 | 20회 | 429 + `Retry-After` |
| `signup` | IP | 60초 | 5회 | 429 |
| `refresh` | account_id | 60초 | 10회 | 429 |
| `global` | IP | 1초 | 100회 | 429 |

구현: Redis 토큰 버킷 ([redis-keys.md](../services/gateway-service/redis-keys.md)).

**Redis 장애 시**: 정책 선택 가능 (환경 변수):
- `fail-open` (기본): rate limit 무시하고 통과. 서비스 가용성 우선
- `fail-closed`: 모든 요청 429. 보안 우선

### Layer 2: Auth Login Failure Counter

이메일별 연속 실패 추적. Layer 1이 IP 기반이라 우회 가능한 distributed attack에 대한 보완.

| Key | `login:fail:{email_hash}` |
|---|---|
| Window | 15분 (TTL) |
| Limit | 5회 연속 실패 |
| 응답 | 429 `LOGIN_RATE_LIMITED` |
| 리셋 | 로그인 성공 시 카운터 삭제 |

이 카운터는 **auth-service 내부에서만** 확인 (gateway에서는 모름).

**Redis 장애 시**: fail-closed (보수적). 카운터를 확인할 수 없으면 로그인은 허용하되 경고 메트릭 발행.

## Why Two Layers?

| 공격 유형 | Layer 1 (IP) | Layer 2 (Email) |
|---|---|---|
| 단일 IP에서 무차별 대입 | ✅ 차단 | ✅ 차단 |
| 분산 IP에서 단일 계정 공격 | ❌ (IP마다 별도 카운트) | ✅ 차단 |
| 단일 IP에서 여러 계정 시도 | ✅ 차단 (전체 호출 수 제한) | ❌ (계정마다 별도) |
| 봇넷 분산 + 다수 계정 | ❌ | ❌ (개별로는 정상) |

봇넷 공격은 rate limit만으로는 방어 불가 → [abnormal-login-detection.md](abnormal-login-detection.md)의 velocity/geo/device 규칙이 보완.

## Business Rules

- 429 응답에는 반드시 `Retry-After` 헤더 포함 (RFC 6585)
- Rate limit 정보(남은 호출 수, 리셋 시각)를 응답 헤더로 노출하지 않음 (공격자에게 정보 제공 방지)
- 임계치는 환경 변수 또는 Redis config hash로 **무중단 조정** 가능
- Layer 1 임계치 조정 시 [observability.md](../services/gateway-service/observability.md)의 `GatewayRateLimitSpiking` 알림 임계치도 동시 조정
- 인증된 요청(access token 포함)도 `global` scope의 rate limit은 적용

## Metrics

- `gateway_ratelimit_total{scope, result}` — allowed vs rejected
- `gateway_ratelimit_rejected_total{scope}` — 429 수
- `auth_redis_failure_counter_ops_total{op}` — Layer 2 Redis 연산

## Related Contracts

- HTTP: [gateway-api.md](../contracts/http/gateway-api.md) (429 응답), [auth-api.md](../contracts/http/auth-api.md) (LOGIN_RATE_LIMITED)
- Redis: [gateway redis-keys.md](../services/gateway-service/redis-keys.md), [auth redis-keys.md](../services/auth-service/redis-keys.md)
