# Task ID

TASK-BE-139

# Title

`TossPaymentsAdapter` Resilience4j wrap (Category B compliance per ADR-MONO-005) — Toss Payments `confirmPayment` + `cancelPayment` 동기 호출에 CircuitBreaker / Retry / Bulkhead 적용 + 전송 실패와 PG 거절 의미 구분

# Status

ready

# Owner

backend / ecommerce

# Task Tags

- code
- adapter
- resilience4j
- integration

---

# Goal

[ADR-MONO-005](../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) § D6 에서 식별된 Category B 격차 해소. 현재 `TossPaymentsAdapter.confirmPayment` / `cancelPayment` 는 Resilience4j 래퍼 없이 동기 HTTP 호출만 수행하며, 전송 실패 (timeout / 5xx) 와 PG-side 거절 (4xx) 을 같은 `PgConfirmFailedException` 으로 묶어버려 호출자가 의미를 구분할 수 없다.

이는 ADR-MONO-005 의 **마지막 ACCEPTED 게이트** (gate 2/2 — gate 1/2 인 TASK-MONO-055 D7 spec surface 는 PR #361 머지로 완료). 본 task 머지 시 ADR 상태가 **PROPOSED → ACCEPTED** 로 전이된다.

레퍼런스 구현은 scm `RestSupplierAdapter` (Category B 참조 — ADR-MONO-005 § D6 에서 명시) 및 wms `TmsClientAdapter` (Category A 안의 Category B 서브-스텝).

---

# Scope

## In Scope

### 1. R4j 의존성 + 설정 추가

- `projects/ecommerce-microservices-platform/apps/payment-service/build.gradle` — `resilience4j-spring-boot3:2.2.0`, `resilience4j-circuitbreaker`, `resilience4j-retry`, `resilience4j-bulkhead` 추가 (scm `procurement-service/build.gradle` 동일 패턴).
- `application.yml` — `resilience4j.{circuitbreaker,retry,bulkhead}.instances.toss-payments` 설정 추가. 임계치는 procurement reference 정렬:
  - CircuitBreaker: `failure-rate-threshold=50`, `sliding-window-type=TIME_BASED`, `sliding-window-size=10`, `minimum-number-of-calls=5`, `wait-duration-in-open-state=10s`, `permitted-number-of-calls-in-half-open-state=3`
  - Retry: `max-attempts=3`, `wait-duration=500ms`, `enable-exponential-backoff=true`, `multiplier=2`, `randomized-wait=true`, `randomized-wait-factor=0.5`, `retry-exceptions=[java.io.IOException, org.springframework.web.client.ResourceAccessException]`, `ignore-exceptions=[org.springframework.web.client.HttpClientErrorException]`
  - Bulkhead: `max-concurrent-calls=10`, `max-wait-duration=100ms` (페이먼트는 supplier 대비 절반 동시성 — UI 사용자가 직접 호출하는 동기 경로)

### 2. HTTP 클라이언트 타임아웃 명시

- `TossPaymentsAdapter` 생성자의 `HttpClient.newBuilder()` 에 명시적 connect timeout (5초, I1 기본값). Read timeout 은 Spring `RestClient` 의 `ClientHttpRequestFactory` 레벨에서 `Duration.ofSeconds(10)` 설정 (PG confirm 은 일반적으로 < 5s; 10s 는 보수적 상한).

### 3. R4j 어노테이션 + fallback 적용

- `confirmPayment` 에 `@CircuitBreaker(name="toss-payments", fallbackMethod="confirmFallback")` + `@Retry(name="toss-payments")` + `@Bulkhead(name="toss-payments")` 적용.
- `cancelPayment` 에 동일 어노테이션 + `cancelFallback` 적용.
- Fallback 메서드 시그니처는 R4j 규약 (원본 메서드 시그니처 + 마지막에 `Throwable cause` 인자). 분기:
  - `CallNotPermittedException` (CB OPEN), `BulkheadFullException` (Bulkhead 만석), `ResourceAccessException` (네트워크 실패), 그 외 transient → 새 `PgGatewayUnavailableException` 던지기.
  - `PgConfirmFailedException` (이미 어댑터 내에서 4xx 직접 던짐 + R4j `ignore-exceptions` 로 전파) → fallback 호출되지 않음, 원본 그대로 caller 로 전파.

### 4. 새 예외 + 에러 코드

- `PgGatewayUnavailableException` (application/exception 패키지) — `RuntimeException` 상속, cause 보존.
- `platform/error-handling.md` § Payment `[domain: ecommerce]` 표에 새 라인:
  - `PG_GATEWAY_UNAVAILABLE` | 503 | "Payment Gateway 가 retry / circuit-breaker exhaustion 후 도달 불가. `PG_CONFIRM_FAILED` (PG 본인이 명시적으로 거절) 와 구분 — operator playbook 이 다름 (PG 운영팀 ticket 대 단순 재시도)."
- `GlobalExceptionHandler` 에 새 핸들러 추가 → 503 + `PG_GATEWAY_UNAVAILABLE`.

### 5. 호출자 시맨틱 업데이트

- `PaymentConfirmService.confirm(...)`: 현재는 `PgConfirmFailedException` 시 `payment.fail()` + 저장 + rethrow. 새 분기:
  - `PgConfirmFailedException` (PG 본인의 명시적 거절 — 4xx) → 기존대로 `payment.fail()` + 저장.
  - `PgGatewayUnavailableException` (전송 실패 / CB OPEN) → `payment.fail()` **호출하지 않음** + 트랜잭션 rollback (caller 가 idempotent 하게 재시도 가능). 사유: PG 측 실제 상태 불명 — `payments.status` 를 FAILED 로 락하면 사용자가 같은 주문 재시도 불가.
- `PaymentRefundService.refundPayment(...)`: PG `cancelPayment` 가 `PgGatewayUnavailableException` 던지면 트랜잭션 rollback (환불 row 변경 없음) + caller (`OrderCancelledEventConsumer`) 의 retry 정책에 위임.

### 6. 테스트

- `TossPaymentsAdapterTest` 확장:
  - 5xx 5회 연속 → R4j retry 소진 → fallback → `PgGatewayUnavailableException` 검증.
  - 5xx 5회 → CB OPEN → 다음 호출은 즉시 `PgGatewayUnavailableException` (no HTTP attempt) 검증.
  - timeout 시뮬레이션 (WireMock `withFixedDelay`) → `PgGatewayUnavailableException`.
  - 4xx 그대로 `PgConfirmFailedException` (no retry) 검증.
- `PaymentConfirmServiceTest`, `PaymentRefundServiceTest`:
  - `PgGatewayUnavailableException` 발생 시 payment row 가 PENDING 그대로 (FAILED 전환 NOT 호출) 검증.
- 회귀: `payment-service:test` baseline 98/98 PASS 유지.

### 7. 스펙 + ADR 갱신 (impl PR 에 포함)

- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` § "Saga / Long-running Flow" (TASK-MONO-055 으로 추가됨) 의 두 Gap 행을 **Compliant** 로 갱신. CB / Retry / Bulkhead config 값 + fallback exception 명시.
- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`:
  - Status: PROPOSED → **ACCEPTED**.
  - § 4.3 (Verification post-ACCEPTED) 조건 1 (TASK-BE-139 머지) 충족 표시.
  - § 5 Outstanding follow-ups 표의 TASK-BE-139 row 를 ✅ MERGED 로 갱신.
  - History 라인 추가: "ACCEPTED 2026-05-NN (TASK-BE-139 merged — Toss Payments Resilience4j wrap landed; payment-service joined the Category B reference column)."

## Out of Scope

- TASK-BE-138 (ecommerce order stuck-detector) — DEFERRED per ADR § D6.
- TASK-BE-140 (inventory.reservation 메트릭) — DEFERRED per ADR § D6.
- Toss Payments 의 `Idempotency-Key` 헤더 적용 — Toss Payments 가 vendor-side idempotency key 를 v2 API 에서만 지원 (현재 v1 사용). v2 마이그레이션은 별도 task.
- ADR-MONO-005 이외의 정책 변경.

---

# Related Specs

- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` — § D4 Category B sub-rules, § D6 payment-service Scenario B
- `rules/traits/integration-heavy.md` §I1 (timeout), §I2 (CircuitBreaker), §I3 (exponential backoff + jitter, 4xx no retry), §I9 (bulkhead)
- `platform/error-handling.md` § Payment — 새 `PG_GATEWAY_UNAVAILABLE` 코드 추가 대상
- `projects/scm-platform/apps/procurement-service/src/main/java/com/example/scmplatform/procurement/infrastructure/supplier/RestSupplierAdapter.java` — Category B 참조 구현
- `projects/wms-platform/apps/outbound-service/src/main/java/com/wms/outbound/adapter/out/tms/TmsClientAdapter.java` — Resilience4j fallback exception 매핑 참조 구현
- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` — § "Saga / Long-running Flow" 갱신 대상

# Related Contracts

- 없음. PG vendor API contract 는 변경 없음. 내부 예외 / 에러 코드만 추가.

---

# Acceptance Criteria

- AC-01: `confirmPayment` + `cancelPayment` 양쪽 모두 `@CircuitBreaker` + `@Retry` + `@Bulkhead` 어노테이션이 적용된다. 어노테이션 인스턴스 이름은 `toss-payments` (procurement 의 `supplier` 와 충돌 없음).
- AC-02: `PgGatewayUnavailableException` 신규 클래스가 `application/exception` 패키지에 존재하고, fallback 메서드가 5xx-exhaustion / `CallNotPermittedException` / `BulkheadFullException` / `ResourceAccessException` (timeout) 케이스를 모두 이 예외로 통일한다.
- AC-03: `PgConfirmFailedException` (4xx) 은 R4j `ignore-exceptions` 에 등록되어 retry 되지 않고 fallback 호출되지 않는다 — 기존 호출자 시맨틱 (PG 본인의 명시적 거절 → `payment.fail()`) 보존.
- AC-04: `PaymentConfirmService` + `PaymentRefundService` 가 `PgGatewayUnavailableException` 발생 시 payment row 의 status 를 변경하지 않는다 (트랜잭션 rollback). 기존 `PgConfirmFailedException` 경로는 변경 없음.
- AC-05: `application.yml` 의 `resilience4j.*.instances.toss-payments` 블록이 procurement reference 와 동일 패턴 (단, bulkhead `max-concurrent-calls=10`).
- AC-06: HTTP client 가 명시적 connect timeout 5s + read timeout 10s 를 가진다.
- AC-07: `platform/error-handling.md` § Payment 표에 `PG_GATEWAY_UNAVAILABLE | 503` 라인이 추가된다.
- AC-08: `GlobalExceptionHandler` 가 `PgGatewayUnavailableException` 을 503 + `PG_GATEWAY_UNAVAILABLE` 로 매핑한다.
- AC-09: `payment-service/specs/services/payment-service/architecture.md` § "Saga / Long-running Flow" 두 행이 Compliant 로 전환된다.
- AC-10: `docs/adr/ADR-MONO-005-...` Status 가 ACCEPTED 로 전환되고, History + § 4.3 + § 5 가 갱신된다.
- AC-11: 신규 테스트 — `TossPaymentsAdapterTest` 최소 4개 새 메서드 (5xx exhaustion / CB OPEN / timeout / 4xx no-retry) 추가, `PaymentConfirmServiceTest` + `PaymentRefundServiceTest` 각 1개 새 메서드 (PgGatewayUnavailable → 상태 무변경) 추가.
- AC-12: `./gradlew :projects:ecommerce-microservices-platform:apps:payment-service:test` 통과 (회귀 + 신규).
- AC-13: CI 15/15 PASS (rules/platform/payment-service 변경 path-filter 매칭 → 전체 파이프라인 트리거).
- AC-14: Conventional Commit scope: `feat(ecommerce/payment)+adr(mono-005)`.

---

# Edge Cases

- **EC-01**: `@Retry` 가 `RestClientResponseException` 5xx 를 retry-exceptions 로 명시적으로 등록하지 않은 경우 retry 가 되지 않을 위험. 해결: `retry-exceptions` 에 `org.springframework.web.client.HttpServerErrorException` 명시. (Spring `RestClient` 가 5xx 를 `HttpServerErrorException` 으로 변환.)
- **EC-02**: Toss Payments 가 같은 paymentKey 로 두 번 confirm 받으면 두 번째는 `ALREADY_PROCESSED_PAYMENT` (4xx) — R4j retry 가 트리거되어 두 번 호출되면 첫 호출이 5xx + 두 번째가 4xx 패턴이 나올 수 있다. 해결: 4xx 는 `PgConfirmFailedException` 으로 전파되고 `payment.fail()` 이 호출되어 PENDING → FAILED 전환. 사용자는 새 주문으로 재시도 (현 동작과 동일). v2 시 vendor-side Idempotency-Key 도입.
- **EC-03**: `BulkheadFullException` 이 빠르게 fallback 으로 진입 시 (대기 100ms 만에 던짐), UI 사용자 입장에서는 503 응답이 너무 빠르게 떨어질 수 있다. 해결: 의도된 동작 — fail-fast 가 fail-slow 보다 사용자 경험 우월 (브라우저 재시도 버튼 활성화). `max-concurrent-calls=10` 은 payment-service 의 v1 트래픽 추정 (동시 결제 < 10) 에 적합.
- **EC-04**: TASK-MONO-055 이 ADR-MONO-005 § 4.3 verification 조건 2 를 충족하려면 6 service architecture.md + 2 rule pointer 가 모두 main 에 있어야 함. PR #361 머지로 충족 — 본 task 시작 가능.

---

# Failure Scenarios

- **FS-01**: R4j 어노테이션이 Spring AOP 프록시를 통하지 않아 무력화 (CGLIB / JDK proxy 이슈). 해결: `TossPaymentsAdapter` 는 `@Component` 빈이므로 자동으로 프록시됨. self-invocation 회피 (어댑터 내부에서 자기 자신 메서드 호출 없음).
- **FS-02**: PostgreSQL JPA 트랜잭션이 fallback 의 예외 throw 와 충돌. 해결: 어댑터는 transaction-less (외부 호출만), `PaymentConfirmService` 의 `@Transactional` 이 fallback 예외를 catch 하지 않고 propagate 하면 rollback 정상 작동. 단위 테스트로 검증.
- **FS-03**: CI 에서 R4j 인스턴스 미설정으로 인한 `IllegalStateException`. 해결: `application.yml` 의 `resilience4j.*.instances.toss-payments` 블록이 누락되지 않도록 AC-05 로 명시.
- **FS-04**: 기존 회귀 (`PaymentEventPublishIntegrationTest` 등 IT) 실패. 해결: 모든 회귀 테스트가 4xx 경로만 사용 — 새 경로 영향 없음. `payment-service:integrationTest` 별도 PASS 확인.

---

# Notes / Open Questions

- 추후 Toss Payments v2 API 마이그레이션 시 vendor-side `Idempotency-Key` 헤더 적용 가능. EC-02 의 retry-된 경우 두 번째 호출이 idempotent 하게 처리됨. v2 마이그레이션은 별도 task.
- Toss Payments 의 sandbox 환경에서 timeout / 5xx 시뮬레이션이 불가능 — 모든 fallback 케이스는 WireMock 으로 검증.
- `PG_GATEWAY_UNAVAILABLE` 코드는 wms 의 `EXTERNAL_SERVICE_UNAVAILABLE` 과 의미적으로 동일하지만, ecommerce 도메인 카탈로그에 PG 특화 명명으로 추가 (운영자 playbook 이 PG 운영팀 ticket → 카탈로그 검색 시 빠른 매칭).

---

# Recommendation

진행 권장 (분석=Opus 4.7 / 구현 권장=Opus — Resilience4j wiring + exception classification + saga state-machine semantic adjustment + ADR ACCEPTED 전이. cross-cutting production code, ~150 LOC 변경, ~6 신규 테스트 메서드.)
