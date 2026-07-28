# Task ID

TASK-FAN-BE-034

# Title

membership-service: CI-gated Spring context-boot test for the `portone` profile

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

**선행: [TASK-FAN-BE-033-fix-02](../done/TASK-FAN-BE-033-fix-02-portone-ambiguous-bean.md)** (머지 후 착수).

fix-02는 `membership-service`를 `SPRING_PROFILES_ACTIVE=portone`으로 라이브 기동했을 때만
`NoUniqueBeanDefinitionException`으로 컨텍스트가 기동 실패하던 결함을 `@Qualifier` 배선으로
고쳤다. 이 결함이 CI에서 잡히지 않았던 이유는 **어떤 테스트도 `portone` 프로파일로 풀
Spring 컨텍스트를 기동하지 않았기 때문** — 기존 테스트는 전부 mock 프로파일(`!portone`,
단일-인터페이스 클래스라 타입 모호성이 애초에 없음) 아래에서만 돌았다.

이 태스크는 그 사각을 없앤다: `portone` 프로파일로 컨텍스트가 예외 없이 뜨는지만 검증하는
가벼운 부팅 테스트를 CI에 상시 배선해, 향후 이 DI 배선(`PaymentGatewayConfig`,
`@Qualifier` 짝, 신규 유스케이스의 생성자 파라미터 등)을 건드리는 어떤 변경도 실 PG 키
없이 즉시 잡히게 한다.

# Scope

## In Scope

- `membership-service`에 `SPRING_PROFILES_ACTIVE=portone`으로 `ApplicationContext`를
  로드만 하는 컨텍스트-부팅 테스트 추가(`@SpringBootTest`, 실 HTTP 호출·DB/Kafka
  Testcontainers는 이 테스트의 관심사가 아님 — 순수 DI 배선 검증).
- `fan.payment.portone.api-base`/`fan.payment.portone.api-secret` 등 필수 설정 키는
  플레이스홀더 값으로 주입(실 PortOne 계정 불필요, 실 키를 CI에 노출하지 않음).
- `PortOnePaymentAdapter`/`RecurringBillingGateway` 어댑터 생성자가 컨텍스트 로드
  시점에 실제 아웃바운드 네트워크 호출을 하지 않는지 확인(지연 초기화 여부 실측) —
  호출이 있다면 이 테스트가 외부 의존성 있는 flaky 테스트가 되므로 그 경로는 Out of
  Scope로 남기고 문서화한다.
- 이 테스트를 기존 CI 레인(`Integration (fan-platform)` 등 적합한 레인) 중 하나에
  배선.

## Out of Scope

- 실 PortOne REST 호출 검증(WireMock 계열, 이미 `TASK-FAN-BE-031`의
  `PortOnePaymentAdapterIntegrationTest`가 커버) — 이 태스크는 컨텍스트 부팅(빈 그래프
  해석) 그 자체만 본다.
- 웹훅(`/webhooks/portone`) 흐름, 빌링키 스케줄러 실행 로직 — 무변경.
- mock 프로파일 테스트 커버리지 확장 — 이미 충분.

---

# Acceptance Criteria

- [ ] **AC-1** `SPRING_PROFILES_ACTIVE=portone` + 플레이스홀더 시크릿으로
      membership-service의 `ApplicationContext`가 예외 없이 로드된다(신규 테스트로
      CI에서 직접 확인).
- [ ] **AC-2** 가드 물림 확인: `PaymentGatewayConfig`의 `@Qualifier` 중 하나를 임의로
      제거/역전시키면 이 신규 테스트가 `NoUniqueBeanDefinitionException`으로 RED가
      된다(로컬에서 mutation 방식으로 1회 확인 후 원복).
- [ ] **AC-3** 기존 mock 프로파일(`!portone`) 테스트 전부 무회귀.
- [ ] **AC-4** 신규 테스트가 실 네트워크 호출 없이 CI에서 결정적으로 통과(외부
      의존성으로 인한 flaky 없음).

---

# Related Specs

- `specs/services/membership-service/architecture.md` (§ PG Boundary — Mock + PortOne)
- [ADR-001](../../docs/adr/ADR-001-real-pg-portone-verification-boundary.md)

# Related Contracts

- N/A — 프레임워크/DI 배선 검증 테스트 추가, API 계약 불변.

---

# Target App

- `membership-service`

---

# Edge Cases

- 컨텍스트 로드 중 어댑터가 실제로 PortOne 엔드포인트에 연결을 시도하는 구조라면,
  CI 환경에서 그 호출이 실패해도 컨텍스트 로드 자체는 막지 않아야 한다(연결 실패와
  빈 정의 모호성은 서로 다른 실패 모드 — 이 테스트는 후자만 본다).
- 향후 새 유스케이스가 `PaymentGatewayPort`/`RecurringBillingGateway`를 비한정
  주입으로 추가하면 이 테스트가 다시 그 지점을 잡아야 한다(가드가 특정 클래스
  하드코딩이 아니라 컨텍스트 전체 로드에 걸려 있어야 하는 이유).

# Failure Scenarios

- 이 테스트를 mock 프로파일에서만 돌리고 `portone` 프로파일 활성화를 빠뜨리면,
  fix-02급 결함이 재발해도 CI가 계속 못 잡는다(원래 결함이 발견되지 못했던
  이유를 그대로 반복).
- 컨텍스트 로드 시점에 실 네트워크 호출이 걸려 있는 걸 못 보고 그대로 CI에 배선하면
  간헐적 flaky의 새 원인이 된다 — 배선 전 반드시 어댑터의 초기화 시점을 확인한다.

---

# Test Requirements

- 신규 컨텍스트-부팅 테스트(`portone` 프로파일, `@SpringBootTest`, 실 네트워크 없음).
- 기존 mock 프로파일 스위트 전체 무회귀.

---

# Definition of Done

- [ ] 컨텍스트-부팅 테스트 추가 + CI 레인 배선
- [ ] 가드 물림(mutation) 1회 확인
- [ ] 기존 테스트 무회귀
- [ ] 리뷰 준비 완료
