# security-service — Dependencies

## Internal HTTP (outgoing)

| 대상 | 목적 | 경로 | 계약 |
|---|---|---|---|
| `account-service` | 자동 잠금 명령 (심각한 suspicious 탐지 시) | `POST /internal/accounts/{id}/lock` | [../../contracts/http/internal/security-to-account.md](../../contracts/http/internal/) |

호출 시 반드시 `Idempotency-Key` 헤더 = `suspicious_event_id` ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T1). 실패 시 3회 재시도 + 최종 실패 시 outbox에 `auto.lock.pending` 이벤트 기록하여 수동 개입 경로로 이관.

## Internal HTTP (incoming, read-only)

| 호출자 | 목적 | 엔드포인트 |
|---|---|---|
| `admin-service` | 로그인 이력 조회 (감사) | `GET /internal/security/login-history?accountId=&from=&to=` |
| `admin-service` | 의심 이벤트 조회 (감사) | `GET /internal/security/suspicious-events?accountId=&from=&to=` |

응답에서 PII 마스킹 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R4): IP 일부만, 이메일 마스킹. 상태 변경 메서드는 **절대 없음** (원칙 유지 — [architecture.md](architecture.md) 참조).

## Persistence

### MySQL (`security_db`)

| 테이블 | 용도 | 특이사항 |
|---|---|---|
| `login_history` | `event_id`, `account_id`, `outcome`, `ip_masked`, `user_agent_family`, `device_fingerprint`, `geo_country`, `occurred_at` | **append-only**. DB 트리거 또는 권한으로 UPDATE/DELETE 차단 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A3). 보존 기간 최소 1년 (A4) |
| `suspicious_events` | `id`, `account_id`, `rule_code`, `risk_score`, `evidence` (JSON), `action_taken`, `detected_at` | `action_taken`: AUTO_LOCK / ALERT / NONE |
| `processed_events` | 이벤트 멱등 소비 기록 (`event_id`, `topic`, `processed_at`) | TTL 배치 정리 (6개월) |
| `outbox_events` | `suspicious.detected`, `auto.lock.triggered` 발행 스테이징 | libs/java-messaging |

### Redis

| 키 패턴 | 용도 | TTL |
|---|---|---|
| `security:event-dedup:{eventId}` | 이벤트 소비 dedup (빠른 경로) | 24h |
| `security:velocity:{account_id}:{window}` | 시간당 실패 카운터 (VelocityRule) | window 길이 |
| `security:geo:last:{account_id}` | 마지막 성공 로그인 geo | 30일 |
| `security:device:seen:{account_id}` | 알려진 디바이스 집합 | 90일 |

## Messaging

### Kafka Consumer

구독 토픽 (모두 auth-service 발행):

| 토픽 | 처리 |
|---|---|
| `auth.login.attempted` | `login_history`에 append (outcome=ATTEMPTED) |
| `auth.login.failed` | `login_history` append + VelocityRule 평가 |
| `auth.login.succeeded` | `login_history` append + GeoAnomalyRule / DeviceChangeRule 평가 |
| `auth.token.refreshed` | `login_history` append (outcome=REFRESH) |
| `auth.token.reuse.detected` | `login_history` append + 즉시 `auto.lock.triggered` 발행 (최고 우선순위) |

**consumer group**: `security-service`. 파티션 키는 `account_id`로 auth-service가 설정하므로 동일 계정의 이벤트는 순서 보장.

**재시도/DLQ**: 지수 백오프 3회 → `<topic>.dlq` 이관. DLQ 깊이 메트릭·알림 필수 ([rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) I5).

### Kafka Producer

| 토픽 | 이벤트 | 파티션 키 |
|---|---|---|
| `security.suspicious.detected` | 탐지 결과 + risk_score + action_taken | `account_id` |
| `security.auto.lock.triggered` | 자동 잠금 명령 발행 (관측성/감사용, 실제 명령은 HTTP) | `account_id` |

계약: [../../contracts/events/security-events.md](../../contracts/events/).

## Shared Libraries

| Lib | 용도 |
|---|---|
| [libs/java-common](../../../libs/java-common) | DTO, enum, `Page<T>` |
| [libs/java-web](../../../libs/java-web) | query 컨트롤러용 Spring Web 설정, validation |
| [libs/java-messaging](../../../libs/java-messaging) | Kafka consumer wrapper, outbox producer, envelope 처리 |
| [libs/java-observability](../../../libs/java-observability) | Consumer lag 메트릭, trace propagation (Kafka header ↔ OTel) |
| [libs/java-test-support](../../../libs/java-test-support) | Testcontainers (Kafka + MySQL + Redis), WireMock (account-service) |

`libs/java-security`는 query 엔드포인트에서 operator 인증이 필요하면 사용 (admin-service의 호출을 검증).

## External SaaS / 3rd-Party

| Provider | 용도 | 활성화 여부 |
|---|---|---|
| IP 평판 API (MaxMind GeoIP / AbuseIPDB 등) | GeoAnomalyRule 보강, IP 위험도 | 선택적, 미래 스코프 |

초기 구현은 MaxMind GeoLite2 로컬 DB 사용 (오프라인). 외부 API 통합은 [rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) I7 adapter pattern을 따라 추가.

## Runtime Dependencies

| 구성 요소 | 버전 / 제약 |
|---|---|
| Java | 21 |
| Spring Boot | 3.x |
| Spring Kafka | 3.x (consumer + producer) |
| Spring Data JPA | 3.x |
| Flyway | 9.x |
| MySQL driver | 8.x |
| Lettuce (Redis) | — |
| MaxMind GeoIP2 | 로컬 GeoLite2 DB |
| Jackson | JSON payload + evidence 컬럼 |
| Micrometer + Prometheus | consumer lag, DLQ depth |

## Failure Modes

| 실패 | 영향 | 대응 |
|---|---|---|
| Kafka consumer 지연 | 로그인 이력·탐지 지연 (동기 로그인 경로는 영향 없음) | consumer lag 메트릭 + 알림. DLQ 쌓임 시 긴급 |
| MySQL 장애 | 이력 적재·탐지 불가 | consumer가 재시도 → 최종 DLQ. auth-service 로그인은 영향 없음 |
| Redis 장애 | dedup 우회 위험 → 중복 이력 적재 가능 | `processed_events` 테이블 upsert로 이중 방어 (T8) |
| `account-service` 호출 실패 | 자동 잠금 지연 | 3회 재시도 후 outbox에 pending 이벤트로 이관, 운영자 수동 처리 |
| DLQ 쌓임 | 소비 실패 이벤트 누적 | 운영자 조사 + 재시도 버튼 (admin-service) 또는 스크립트로 replay |
