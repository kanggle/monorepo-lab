# Service Architecture — security-service

## Service

`security-service`

## Service Type

`event-consumer` **(+ 좁은 read-only HTTP 표면)**

Primary role은 Kafka 보안 이벤트 소비. 부차적으로 admin-service와 내부 조회를 위한 **매우 제한된 read-only HTTP 엔드포인트**를 제공한다. [platform/service-types/event-consumer.md](../../../platform/service-types/event-consumer.md)가 허용하는 "small set of admin or query endpoints" 범주 내에 머무른다.

적용되는 규칙: [platform/service-types/event-consumer.md](../../../platform/service-types/event-consumer.md)

## Architecture Style

**Layered (Consumer-Driven)** — `consumer / application / domain / infrastructure` 4계층. HTTP 표면은 별도의 `query/` 패키지로 격리되어 consumer 경로와 코드를 공유하지 않는다.

## Why This Architecture

- **Primary inbound = Kafka**: 동기 HTTP 게이트웨이 뒤에 숨지 않음. [platform/service-types/event-consumer.md](../../../platform/service-types/event-consumer.md)의 규칙(구독·멱등성·재시도·DLQ·파티션 키·트레이스 전파)을 모두 준수.
- **보안 판단은 동기 로그인 플로우에서 분리**: auth-service가 로그인 시도를 발행하면 security-service가 **비동기**로 이력 적재와 이상 탐지를 수행. 로그인 경로의 p99에 영향을 주지 않음.
- **HTTP는 부수적이고 읽기 전용**: admin-service가 감사 조회를 요청할 때만 사용. 상태 변경 경로는 HTTP로 노출하지 **않음** (변경은 모두 이벤트 또는 내부 명령 호출).
- **Hybrid 재분류 회피**: HTTP 표면이 커지면 [platform/service-types/event-consumer.md](../../../platform/service-types/event-consumer.md)의 허용 범위를 넘어섬. 그 경우 별도 `security-query-service`로 분할하는 것이 올바른 방향이지 서비스 타입을 바꾸는 것이 아님.

## Internal Structure Rule

```
apps/security-service/src/main/java/com/example/security/
├── SecurityApplication.java
│
├── consumer/                        ← Primary: Kafka 이벤트 소비 경로
│   ├── LoginAttemptedConsumer.java
│   ├── LoginFailedConsumer.java
│   ├── LoginSucceededConsumer.java
│   ├── TokenRefreshedConsumer.java
│   ├── handler/
│   │   └── EventDedupService.java   ← Redis 기반 eventId dedupe
│   └── dlq/
│       └── DlqRelay.java
│
├── application/                     ← use-case, 트랜잭션
│   ├── RecordLoginHistoryUseCase.java
│   ├── DetectSuspiciousActivityUseCase.java
│   ├── IssueAutoLockCommandUseCase.java
│   └── event/
│       └── SecurityEventPublisher.java  ← outbox 경유 suspicious.detected 발행
│
├── domain/
│   ├── history/
│   │   ├── LoginHistoryEntry.java
│   │   └── LoginOutcome.java            ← enum: SUCCESS / FAILURE / RATE_LIMITED / TOKEN_REUSE
│   ├── detection/
│   │   ├── SuspiciousActivityRule.java  ← 전략 인터페이스
│   │   ├── VelocityRule.java            ← 시간당 로그인 시도 임계치
│   │   ├── GeoAnomalyRule.java          ← 지리적 이례성
│   │   ├── DeviceChangeRule.java
│   │   └── RiskScore.java
│   └── repository/
│       ├── LoginHistoryRepository.java
│       └── SuspiciousEventRepository.java
│
├── query/                           ← 좁은 read-only HTTP 표면 (admin 전용)
│   ├── internal/
│   │   ├── LoginHistoryQueryController.java
│   │   └── SuspiciousEventQueryController.java
│   ├── dto/
│   │   ├── LoginHistoryView.java
│   │   └── SuspiciousEventView.java
│   └── SecurityQueryService.java    ← application과 무관한 조회 전용 서비스
│
└── infrastructure/
    ├── persistence/
    │   ├── LoginHistoryJpaEntity.java
    │   ├── SuspiciousEventJpaEntity.java
    │   └── *JpaRepository.java
    ├── redis/
    │   └── RedisEventDedupStore.java
    ├── kafka/
    │   ├── KafkaConsumerConfig.java
    │   └── AutoLockCommandProducer.java
    ├── client/
    │   └── AccountServiceClient.java    ← 내부 HTTP, 자동 잠금 명령
    └── config/
```

## Allowed Dependencies

```
consumer → application → domain
                    ↓
              infrastructure → domain

query → domain (via SecurityQueryService, read-only JPA 경로)
```

- `consumer/` 레이어는 [platform/service-types/event-consumer.md](../../../platform/service-types/event-consumer.md)의 구독·ack·재시도·DLQ 규칙을 엄수
- `query/`는 `application`과 **독립**. 조회 쿼리는 `SecurityQueryService`가 직접 `infrastructure/persistence`의 read-only 메서드를 사용
- `application` → `infrastructure/client/AccountServiceClient` (자동 잠금 명령)

## Forbidden Dependencies

- ❌ `consumer/`가 HTTP 엔드포인트 노출 — 소비는 Kafka만
- ❌ `query/`가 상태 변경 (POST/PUT/DELETE HTTP) 제공 — **읽기 전용**
- ❌ `query/`가 `application` use-case 호출 — 조회 응답에 side effect 없음
- ❌ `LoginHistoryJpaEntity`의 UPDATE/DELETE ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A3 불변성)
- ❌ 로그인 시도 이벤트 소비를 동기 로그인 플로우에 노출 (security-service 장애가 auth-service를 블로킹하면 안 됨)

## Boundary Rules

### consumer/
- 각 consumer는 단일 topic 구독: `auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded`, `auth.token.refreshed`, `auth.token.reuse.detected`, `account.locked` (TASK-BE-041b — `account_lock_history` 적재)
- **멱등 처리 필수**: Redis `EventDedupService`로 eventId 기반 dedupe. 미지원 시 `processed_events` 테이블 upsert ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T8)
- 실패 시 지수 백오프 3회 재시도 후 `<topic>.dlq`로 이관
- 소비 트레이스(`traceparent`)를 Kafka 헤더에서 MDC로 복원 ([platform/service-types/event-consumer.md](../../../platform/service-types/event-consumer.md))
- 파티션 키: `account_id` (같은 계정의 이벤트 순서 보장)

### application/
- `RecordLoginHistoryUseCase`: 소비된 이벤트를 `login_history`에 append-only 기록. 트랜잭션 내에서 outbox 이벤트(`suspicious.detected`, `auto.lock.triggered`, `auto.lock.pending`) 같이 기록
- `DetectSuspiciousActivityUseCase`: 전략 패턴으로 여러 `SuspiciousActivityRule`을 순회 평가. 임계치 초과 시 `suspicious_events` 저장 + 이벤트 발행
- `IssueAutoLockCommandUseCase`: 심각도가 높은 suspicious에 대해 account-service로 내부 HTTP `POST /internal/accounts/{id}/lock` 호출 ([rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) I4 idempotent side effect, 멱등 키 `suspicious_event_id` 사용)

### domain/detection/
- `SuspiciousActivityRule`은 순수 함수적 규칙. 각 rule 구현은 stateless
- 시간 윈도우, 임계치 같은 파라미터는 `@ConfigurationProperties`로 주입 (코드 하드코딩 금지)

### query/
- **허용 엔드포인트** (admin-service 전용, 내부 경로):
  - `GET /internal/security/login-history?accountId=&from=&to=`
  - `GET /internal/security/suspicious-events?accountId=&from=&to=`
- **금지**: 상태 변경 메서드, 외부 노출 경로, 배치 대량 export
- 응답에서 PII 마스킹 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R4) — IP 일부만 노출, 이메일 마스킹
- 조회 액션 자체가 감사됨 — meta-audit ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A5)

## Integration Rules

- **이벤트 구독**: [specs/contracts/events/auth-events.md](../../contracts/events/) — 위 5개 토픽
- **이벤트 발행**: [specs/contracts/events/security-events.md](../../contracts/events/) — `suspicious.detected`, `auto.lock.triggered`, `auto.lock.pending`
- **HTTP 컨트랙트 (내부 query)**: [specs/contracts/http/security-query-api.md](../../contracts/http/) — 읽기 전용
- **HTTP 컨트랙트 (out-going)**: [specs/contracts/http/internal/security-to-account.md](../../contracts/http/internal/) — 자동 잠금 명령
- **퍼시스턴스**: MySQL — `login_history` (append-only), `suspicious_events`, `processed_events` (dedup), `outbox_events`
- **Redis**: `security:event-dedup:{eventId}` TTL 24h

## Testing Expectations

| 레이어 | 목적 | 도구 |
|---|---|---|
| Unit | 각 `SuspiciousActivityRule` 평가, `RiskScore` 산출 | JUnit 5 |
| Consumer slice | 이벤트 payload → LoginHistoryEntry 매핑, dedupe 동작 | Spring Kafka Test |
| Integration | 전체 소비 → DB 적재 → 자동 잠금 HTTP 호출 | Testcontainers (Kafka + MySQL + Redis) + WireMock (account-service) |
| DLQ | 3회 실패 후 이벤트가 `<topic>.dlq`로 이관 | Kafka test |
| Query slice | 조회 컨트롤러 PII 마스킹, operator 인증 | `@WebMvcTest` |
| Idempotency | 동일 eventId 재수신 시 `login_history` 중복 없음 | Integration |

**필수 시나리오**: 동일 eventId 2회 수신 → 1회만 처리 / 5분 내 5개 지역에서 로그인 시도 → `suspicious.detected` + auto-lock / account-service 호출 실패 시 재시도 + 최종 DLQ / login_history UPDATE 시도 → DB 트리거로 거부.

## Change Rule

1. 새로운 `SuspiciousActivityRule` 추가는 [specs/features/abnormal-login-detection.md](../../features/) 업데이트 선행
2. 토픽 구독 추가·변경은 [specs/contracts/events/auth-events.md](../../contracts/events/) 또는 새 컨트랙트 파일 + 이 architecture.md 재확인
3. 조회 엔드포인트 추가는 본 아키텍처의 "좁은 read-only" 원칙을 유지. 상태 변경 메서드를 추가해야 한다면 서비스 분할을 검토
4. 감사 로그 스키마 변경은 [rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A2 준수 확인 + Flyway migration
