# auth-service — Dependencies

## Internal HTTP (outgoing)

| 대상 | 목적 | 경로 | 계약 |
|---|---|---|---|
| `account-service` | Credential lookup (이메일 → account_id + credential_hash) | `GET /internal/accounts/credentials?email=...` | [../../contracts/http/internal/auth-to-account.md](../../contracts/http/internal/) |
| `account-service` | 계정 상태 조회 (로그인 허용 여부 판단) | `GET /internal/accounts/{id}/status` | 같은 파일 |

**타임아웃·재시도**: 연결 3s, 읽기 5s, 재시도 2회(지수 백오프 + jitter), circuit breaker 임계 50% 실패율 / 10초 반개방. 4xx는 재시도 금지 ([rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) I3).

## Persistence

### MySQL (`auth_db`)

| 테이블 | 용도 | 특이사항 |
|---|---|---|
| `credentials` | `account_id`, `credential_hash` (argon2id), `algorithm`, `updated_at`, `version` | 낙관적 락(version), credential rotation 시 update |
| `refresh_tokens` | `jti`, `account_id`, `issued_at`, `expires_at`, `rotated_from`, `revoked`, `device_fingerprint` | `rotated_from` 체인으로 재사용 탐지 |
| `outbox_events` | `event_id`, `aggregate_id`, `type`, `payload`, `occurred_at`, `published_at` | [libs/java-messaging/outbox](../../../libs/java-messaging) 사용 |
| `processed_events` | 해당 없음 (auth-service는 현재 이벤트 소비 없음) | — |

스키마 변경은 Flyway migration. 상세는 [data-model.md](data-model.md).

### Redis

상세 키 스키마는 [redis-keys.md](redis-keys.md).

- `login:fail:{email_hash}` — 실패 카운터, TTL 15분
- `refresh:blacklist:{jti}` — 즉시 revoke된 토큰의 블랙리스트

## Messaging

### Kafka Producer

발행 토픽 (outbox 경유):

| 토픽 | 이벤트 | 파티션 키 |
|---|---|---|
| `auth.login.attempted` | 로그인 시도 발생 (성공·실패 불문) | `account_id` 또는 `email_hash` |
| `auth.login.failed` | 로그인 실패 | `account_id` |
| `auth.login.succeeded` | 로그인 성공 + 토큰 발급 | `account_id` |
| `auth.token.refreshed` | Refresh rotation 성공 | `account_id` |
| `auth.token.reuse.detected` | 이미 회전된 refresh token의 재사용 탐지 — 전체 세션 즉시 invalidate | `account_id` |

계약: [../../contracts/events/auth-events.md](../../contracts/events/).

**재시도/DLQ 정책**: producer 실패 시 outbox에 유지 (아직 `published_at=NULL`). 별도 relay job이 폴링하며 발행. outbox 레이턴시 메트릭: `auth_outbox_lag_seconds`.

### Kafka Consumer

없음. auth-service는 이벤트를 발행만 한다.

## Shared Libraries

| Lib | 용도 |
|---|---|
| [libs/java-common](../../../libs/java-common) | 공통 DTO 베이스, `Page<T>`, 에러 코드 enum |
| [libs/java-web](../../../libs/java-web) | Spring Web 설정, 요청 DTO validation, 표준 에러 응답 |
| [libs/java-messaging](../../../libs/java-messaging) | Outbox 패턴 구현, Kafka producer wrapper, event envelope |
| [libs/java-security](../../../libs/java-security) | JWT 서명·검증, 패스워드 해시(argon2id wrapper), RS256 키 로딩 |
| [libs/java-observability](../../../libs/java-observability) | MDC, Prometheus 메트릭, OTel 트레이스 propagation |
| [libs/java-test-support](../../../libs/java-test-support) | Testcontainers (MySQL + Kafka + Redis), WireMock 헬퍼, 테스트 데이터 픽스처 |

## External SaaS / 3rd-Party

| Provider | 용도 | 활성화 여부 |
|---|---|---|
| Google OAuth 2.0 | 소셜 로그인 (선택) | `GOOGLE_CLIENT_ID` 존재 시 |
| Apple Sign In | 소셜 로그인 (선택) | `APPLE_CLIENT_ID` 존재 시 |
| Kakao OAuth | 소셜 로그인 (선택) | `KAKAO_CLIENT_ID` 존재 시 |
| 이메일/SMS provider | 비밀번호 재설정·2FA (선택, 미래) | 미정 |

OAuth는 백로그 (TASK-BE-008 이후). 초기 골든패스는 이메일·패스워드만.

## Runtime Dependencies

| 구성 요소 | 버전 / 제약 |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 3.x |
| Spring Security | 6.x (JWT filter, BCrypt/Argon2 encoder) |
| Spring Data JPA | 3.x |
| QueryDSL | 5.x (refresh token 조회) |
| Flyway | 9.x |
| Argon2 JVM (`argon2-jvm` 또는 `password4j`) | credential hash |
| JJWT 또는 Nimbus JWT | JWT 발급·검증 |
| MySQL driver | 8.x |
| Lettuce | Redis client |
| Spring Kafka | 3.x |
| Micrometer + Prometheus | 메트릭 |

## Failure Modes

| 실패 | 영향 | 대응 |
|---|---|---|
| `account-service` 장애 | 로그인 불가 | Circuit breaker → 사용자에게 503. 로그인 차단이 스펙 (fail-closed) |
| MySQL 장애 | 로그인·토큰 발급 불가 | fail-closed. 운영자 알림 |
| Redis 장애 | 실패 카운터·블랙리스트 조회 불가 | Rate limit은 fail-closed(보수적), 블랙리스트는 fail-closed (안전 우선) |
| Kafka 장애 | 이벤트 발행 지연 | Outbox에 계속 쌓임, relay가 복구 시 일괄 발행. 로그인 경로는 영향 없음 (outbox write만 트랜잭션에 포함) |
| Outbox relay 지연 | security-service 탐지 지연 | `auth_outbox_lag_seconds` 알림 |
