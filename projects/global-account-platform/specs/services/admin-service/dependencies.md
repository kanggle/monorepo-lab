# admin-service — Dependencies

## Internal HTTP (outgoing)

| 대상 | 목적 | 경로 | 계약 |
|---|---|---|---|
| `auth-service` | 강제 로그아웃, refresh token 강제 revoke | `POST /internal/auth/sessions/{id}/revoke`, `POST /internal/auth/accounts/{id}/force-logout` | [../../contracts/http/internal/admin-to-auth.md](../../contracts/http/internal/) |
| `account-service` | 계정 lock / unlock / delete | `POST /internal/accounts/{id}/lock`, `POST .../unlock`, `POST .../delete` | [../../contracts/http/internal/admin-to-account.md](../../contracts/http/internal/) |
| `security-service` (read-only) | 로그인 이력·의심 이벤트 조회 | `GET /internal/security/login-history`, `GET /internal/security/suspicious-events` | security-service의 query 엔드포인트 |

**모든 호출에 반드시 `Idempotency-Key` 헤더 = admin command request ID** ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T1). 타임아웃 3s 연결 / 10s 읽기, 재시도 2회, circuit breaker 적용.

## Internal HTTP (incoming)

없음. admin-service는 다른 서비스로부터 호출받지 않는다. 유일한 호출자는 운영자 클라이언트(admin dashboard, runbook 스크립트) — 이들은 게이트웨이의 `/api/admin/*` 퍼블릭 경로를 통해 들어온다 (단, 별도 인증 필터 체인 적용).

## Persistence

### MySQL (`admin_db`)

| 테이블 | 용도 | 특이사항 |
|---|---|---|
| `admin_actions` | `id`, `action_code`, `actor_id`, `actor_role`, `target_type`, `target_id`, `reason`, `ticket_id`, `outcome`, `downstream_detail` (JSON), `started_at`, `completed_at` | **append-only 감사 원장**. DB 트리거로 UPDATE/DELETE 차단 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A3). `outcome`은 SUCCESS/FAILURE/IN_PROGRESS |
| `outbox_events` | `admin.action.performed` 이벤트 스테이징 | libs/java-messaging |

**도메인 상태 없음**. 계정/세션/credential은 downstream 소유. admin-service는 자체 감사 원장만 소유.

### Redis

| 키 패턴 | 용도 | TTL |
|---|---|---|
| `admin:nonce:{operatorId}:{requestId}` | 중복 요청 차단 (선택) | 24h |
| `admin:ratelimit:{operatorId}` | 운영자별 호출 제한 (선택) | 슬라이딩 윈도우 |

## Messaging

### Kafka Producer

| 토픽 | 이벤트 | 파티션 키 |
|---|---|---|
| `admin.action.performed` | 모든 운영자 행위 (lock, unlock, delete, force-logout, audit query) | `target_id` 또는 `actor_id` |

발행 정책: **모든 명령이 outbox를 경유**. admin command와 이벤트 발행이 **같은 DB 트랜잭션 내에서 커밋** ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A7, fail-closed). 이벤트는 외부 SIEM·모니터링·컴플라이언스 리포팅용.

계약: [../../contracts/events/admin-events.md](../../contracts/events/).

### Kafka Consumer

없음.

## Shared Libraries

| Lib | 용도 |
|---|---|
| [libs/java-common](../../../libs/java-common) | DTO, `Page<T>`, enum |
| [libs/java-web](../../../libs/java-web) | Spring Web, DTO validation, 에러 응답 |
| [libs/java-messaging](../../../libs/java-messaging) | Outbox, Kafka producer |
| [libs/java-security](../../../libs/java-security) | Operator JWT 검증 (별도 scope), role 기반 권한, 2FA helper (선택) |
| [libs/java-observability](../../../libs/java-observability) | MDC (operator_id 자동 태깅), 메트릭, 트레이스 |
| [libs/java-test-support](../../../libs/java-test-support) | Testcontainers (MySQL + Kafka), WireMock (downstream 서비스 mocking), Spring Security 테스트 헬퍼 |

## External SaaS / 3rd-Party

| Provider | 용도 | 활성화 여부 |
|---|---|---|
| 외부 IdP (Okta / Auth0 등) | 운영자 SSO (선택) | 초기 미적용, 필요 시 [rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) I7 adapter pattern으로 추가 |
| SIEM (Splunk / Datadog / ELK) | `admin.action.performed` 이벤트 소비 | Kafka consumer가 외부에서 구독 (admin-service는 발행만 담당) |

초기 스코프에서는 operator JWT를 auth-service가 발급하되 `scope: admin` claim으로 구분.

## Runtime Dependencies

| 구성 요소 | 버전 / 제약 |
|---|---|
| Java | 21 |
| Spring Boot | 3.x |
| Spring Security | 6.x (operator authentication filter chain) |
| Spring Data JPA | 3.x |
| Flyway | 9.x |
| MySQL driver | 8.x |
| Spring Kafka | 3.x (producer only) |
| Lettuce (Redis) | optional |
| Micrometer + Prometheus | — |

## Failure Modes

| 실패 | 영향 | 대응 |
|---|---|---|
| `auth-service` 호출 실패 | 강제 로그아웃 불가 | Circuit breaker → 3회 재시도 → `admin_actions.outcome=FAILURE`로 기록 + 운영자에게 504 반환. 감사 row는 남음 |
| `account-service` 호출 실패 | lock/unlock/delete 불가 | 동일. 운영자가 재시도 또는 수동 개입 |
| `security-service` 조회 실패 | 감사 조회 불가 | 캐시·fallback 없음. 운영자에게 에러 표시 + 재시도 |
| MySQL 장애 (`admin_db`) | **명령 자체가 실행되지 않음** — fail-closed (A10). 감사 기록 없이 downstream 호출 금지 | 운영자에게 503, 긴급 알림 |
| Kafka 장애 | outbox에 쌓이고 relay가 나중에 발행. admin 명령 자체는 성공 (DB 트랜잭션 커밋됨) | outbox lag 메트릭 |
| 운영자 인증 실패 | 401 | 운영자 재인증 |
| 운영자 권한 부족 | 403 + audit row (`action_code=DENIED`) | 권한 요청 프로세스 |
