# auth-service — Observability

기준: [platform/observability.md](../../../platform/observability.md)

---

## Metrics (Prometheus)

### 필수 메트릭

| 이름 | 타입 | 라벨 | 설명 |
|---|---|---|---|
| `auth_login_total` | counter | `result` (success/failure/rate_limited/account_locked) | 로그인 시도 결과 |
| `auth_login_duration_seconds` | histogram | `result` | 로그인 처리 시간 |
| `auth_token_issued_total` | counter | `type` (access/refresh) | 토큰 발급 수 |
| `auth_token_refresh_total` | counter | `result` (success/expired/reuse_detected/revoked) | Refresh 시도 결과 |
| `auth_token_reuse_detected_total` | counter | — | 재사용 탐지 횟수 (보안 KPI) |
| `auth_credential_verify_duration_seconds` | histogram | — | 패스워드 해시 검증 시간 (argon2 튜닝 지표) |
| `auth_redis_failure_counter_ops_total` | counter | `op` (incr/get/reset) | Redis 실패 카운터 연산 |
| `auth_outbox_lag_seconds` | gauge | — | 아직 발행되지 않은 outbox 이벤트의 최대 나이 |
| `auth_account_service_duration_seconds` | histogram | `endpoint` | account-service 내부 HTTP 호출 시간 |
| `auth_circuit_breaker_state` | gauge | `target` | account-service circuit breaker 상태 |

### 비즈니스 메트릭

| 이름 | 타입 | 설명 |
|---|---|---|
| `auth_active_sessions_total` | gauge | 현재 유효한 refresh token 수 (추정) |
| `auth_login_failure_streak_max` | gauge | 현재 가장 긴 연속 실패 streak (이상 탐지 보조) |

---

## Logs

### MDC 필드

| 필드 | 소스 |
|---|---|
| `traceId` | OTel / gateway 전파 |
| `requestId` | `X-Request-ID` 헤더 |
| `accountId` | 로그인 성공 시 설정, 실패 시 `unknown` |
| `action` | `login` / `logout` / `refresh` / `jwks` |

### 로깅 규칙

- ❌ 이메일 / 전화 / 패스워드 평문 / 토큰 값 / credential hash → **절대 로그 금지** ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R4)
- ✅ `accountId`, `action`, `result`, `duration_ms`, `client_ip_masked` 만 INFO 레벨에 기록
- ✅ Credential 검증 실패 시 `WARN` 레벨 + `client_ip_masked` + `fail_count` (이메일은 해시만)

---

## Traces (OTel)

| Span 이름 | 설명 |
|---|---|
| `auth.login` | 전체 로그인 흐름 (root) |
| `auth.credential.verify` | argon2 해시 비교 |
| `auth.token.issue` | JWT access + refresh 발급 |
| `auth.token.refresh` | Refresh rotation 흐름 |
| `auth.token.reuse.detect` | 재사용 탐지 경로 |
| `auth.account-service.lookup` | account-service 내부 HTTP 호출 |
| `auth.outbox.write` | Outbox 이벤트 저장 |
| `auth.redis.fail-counter` | Redis 실패 카운터 조회/증가 |

---

## Alerts

| 이름 | 조건 | 심각도 | 대응 |
|---|---|---|---|
| `AuthLoginFailureRateHigh` | 실패 비율 > 30% (5분) | warning | Credential stuffing 가능성, IP 분석 |
| `AuthTokenReuseDetected` | `auth_token_reuse_detected_total` 증가 | critical | 잠재적 토큰 탈취. 해당 세션 전체 무효화 확인 |
| `AuthAccountServiceDown` | circuit breaker open | critical | 로그인 불가. account-service 상태 점검 |
| `AuthOutboxLagHigh` | `auth_outbox_lag_seconds` > 60 | warning | Kafka / relay 상태 점검 |
| `AuthCredentialVerifySlow` | p99 > 1초 | warning | argon2 파라미터 튜닝 또는 CPU 부족 |
| `AuthRedisDown` | Redis 연결 실패 연속 3회 | critical | Fail-closed (rate limit + blacklist 검증 불가) |

---

## Dashboard (Grafana)

1. **Login Rate** — success / failure / rate_limited / account_locked
2. **Login Latency** — p50/p95/p99
3. **Token Operations** — issue / refresh / reuse_detected
4. **Credential Verify Time** — histogram (argon2 성능 모니터링)
5. **Account Service Health** — call duration + circuit breaker state
6. **Redis Operations** — fail counter ops, connection errors
7. **Outbox Lag** — gauge + threshold line at 60s
8. **Active Sessions** — estimated from refresh_tokens count
