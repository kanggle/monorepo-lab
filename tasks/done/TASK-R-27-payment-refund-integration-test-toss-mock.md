# Task ID

TASK-R-27

# Title

payment-service PaymentRefundIntegrationTest Toss 실제 호출 제거 (PgClient mock 주입)

# Status

review

# Owner

backend

# Task Tags

- test
- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`PaymentRefundIntegrationTest`는 `paymentConfirmService.confirm(...)` 호출 시 실제 `TossPaymentsAdapter`가 주입되어 `https://api.tosspayments.com`에 `test_sk_placeholder` 시크릿 키로 HTTP 요청을 보내며, 그 결과 `401 UNAUTHORIZED_KEY`로 테스트가 실패한다. 통합 테스트가 외부 PG 시스템에 의존하지 않도록 `TossPaymentsAdapter`(혹은 상위 PG 포트)를 테스트 범위에서 mock/stub으로 대체하여 안정적으로 REFUNDED 전이를 검증한다.

---

# Scope

## In Scope

- `apps/payment-service/src/test/java/com/example/payment/PaymentRefundIntegrationTest.java`에서 PG 승인 경로가 외부 HTTP 호출 없이 성공하도록 수정
  - 우선순위: `@MockitoBean`으로 PG 포트(`PgClient` 또는 `TossPaymentsAdapter`)를 대체하여 성공 응답 stub
  - 차선: `@DynamicPropertySource`로 로컬 WireMock 서버 base-url 주입
- 기존 두 시나리오(`orderCancelled_refundsPayment`, `orderCancelled_noPayment_isIgnored`) 모두 통과 유지
- 통합 테스트는 Testcontainers Postgres + EmbeddedKafka만 외부 의존으로 유지

## Out of Scope

- `TossPaymentsAdapter` 프로덕션 코드 수정
- 신규 시나리오 추가 (환불 실패, 부분환불 등)
- `TossPaymentsAdapterTest`(WireMock 단위 테스트) 변경
- 다른 통합 테스트 개편
- Toss API 계약 변경

---

# Acceptance Criteria

- [ ] `./gradlew :apps:payment-service:test`가 성공한다
- [ ] `PaymentRefundIntegrationTest` 두 시나리오 모두 통과한다
- [ ] 테스트 실행 로그에 `api.tosspayments.com` 대상 HTTP 호출 및 `401 UNAUTHORIZED_KEY` 문자열이 나타나지 않는다
- [ ] 테스트는 외부 네트워크 없이 실행 가능하다

---

# Related Specs

- `specs/services/payment-service/architecture.md`
- `specs/platform/testing-strategy.md` (통합 테스트 외부 의존 격리 원칙)

# Related Skills

- `.claude/skills/testing/` (통합 테스트 관련 스킬 있으면 참조)

---

# Related Contracts

- `specs/contracts/http/payment-http.md` 또는 관련 HTTP 계약 (확인 필요)

---

# Target Service

- `payment-service`

---

# Architecture

Follow:

- `specs/services/payment-service/architecture.md`

---

# Implementation Notes

- 실패 경로: [PaymentRefundIntegrationTest.java:102](apps/payment-service/src/test/java/com/example/payment/PaymentRefundIntegrationTest.java#L102) → `PaymentConfirmService.confirm` → [TossPaymentsAdapter.confirmPayment:72](apps/payment-service/src/main/java/com/example/payment/adapter/out/pg/TossPaymentsAdapter.java#L72)
- `PaymentConfirmService`가 의존하는 PG 포트 인터페이스(예: `PgClient`)를 먼저 식별하고 해당 포트를 `@MockitoBean`으로 대체
- 포트가 아닌 구현 클래스만 있으면 `TossPaymentsAdapter` 자체를 `@MockitoBean`으로 대체
- Stub 동작: 정상 승인 응답 객체 반환 (`paymentKey`, `orderId`, `approvedAt` 등 기존 테스트에 필요한 필드만 채움)
- 이미 존재하는 `TossPaymentsAdapterTest`(WireMock)가 어댑터 HTTP 로직을 단위 수준에서 검증하므로, 통합 테스트는 이 경로를 중복 검증할 필요 없음
- 기존 integration 테스트 자바코드 스타일과 일관성 유지 (`@SpringBootTest` + `@AutoConfigureMockMvc` 구조 유지)

## 구현 결과

- [PaymentRefundIntegrationTest.java](apps/payment-service/src/test/java/com/example/payment/PaymentRefundIntegrationTest.java) 수정
  - `PaymentGatewayPort`를 `@MockitoBean`으로 대체 (Toss 실제 HTTP 호출 제거)
  - `@BeforeEach`에서 `confirmPayment(...)` stub 추가: `PaymentGatewayConfirmResult("CARD", "https://receipt.test/mock")` 반환
  - `cancelPayment(...)`는 void 반환이라 기본 mock 동작 그대로 사용
- PG 포트가 이미 클린 인터페이스(`PaymentGatewayPort`)로 분리되어 있어 `TossPaymentsAdapter`가 아닌 포트만 교체하면 충분
- `./gradlew :apps:payment-service:test --tests PaymentRefundIntegrationTest` BUILD SUCCESSFUL
- `./gradlew :apps:payment-service:test` (전체) BUILD SUCCESSFUL — 회귀 없음
- 테스트 실행 중 `api.tosspayments.com` 호출 없음, `401 UNAUTHORIZED_KEY` 로그 사라짐

---

# Edge Cases

- `PgClient` 포트 이름/위치가 다를 경우 → `PaymentConfirmService` 필드를 먼저 확인
- `@MockBean` deprecated 경고: 프로젝트가 Spring Boot 3.4+를 쓰면 `@MockitoBean` 사용
- Testcontainers Postgres가 여전히 필요함 — Docker 미가동 환경은 원래 이 테스트 실행 불가 (out of scope)
- PaymentConfirmService 내부에서 RefundAdapter 등 다른 Toss 경로를 호출한다면 해당도 stub 필요

---

# Failure Scenarios

- Mock이 실제 경로를 덮지 못해 여전히 Toss로 HTTP가 나감 → `Mockito.verify` 또는 로그로 확인
- stub 반환 객체의 필수 필드 누락 → NullPointerException 발생, 필드 충족 후 재실행
- 다른 Toss 경로(refund 실행)가 존재해 REFUNDED 전이 단계에서 실패 → 해당 포트도 함께 mock

---

# Test Requirements

- `./gradlew :apps:payment-service:test` 전체 통과
- `PaymentRefundIntegrationTest` 두 시나리오 모두 통과
- 기존 `TossPaymentsAdapterTest`(WireMock) 영향 없음

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
