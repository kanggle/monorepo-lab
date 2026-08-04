# Task ID

TASK-BE-572

# Title

데모 mock PG 프로파일 — 실 PG 없이 결제를 완주시키되 도메인 이벤트는 실제로 발행한다

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- deploy

---

# 배경

포트폴리오 데모에서 **구매 완주**(장바구니 → 주문 → 결제 완료 → 배송 → 리뷰 → 정산)는 이 프로젝트의
핵심 시연 경로다. 그런데 현재 두 갈래 다 막혀 있다.

1. **실 Toss 경로는 사람이 못 넘는다.** `use-toss-payment.ts` 가 API 개별연동(결제창) 패턴으로
   `requestPayment` 를 호출하는데, 실 샌드박스는 카드 ISP/3DS · 카카오페이 QR · SSG페이 PUSH 전부
   **실기기 확인**을 요구한다(3개 수단 독립 확인). 면접관에게 휴대폰 인증을 시킬 수는 없다.
2. **기존 `standalone` 프로파일은 대안이 아니다.** PG 는 스텁으로 바뀌지만
   (`StandaloneConfig.StandalonePaymentGateway.verify` = 무조건 승인) **`PaymentEventPublisher` 도
   no-op** 이라 `PaymentCompleted` 이벤트가 나가지 않는다 → 주문 확정 · 배송 · 정산 사가가 전부 멈춘다.
   결제만 되고 그 뒤가 안 보이면 시연 가치가 없다.

그리고 `infra/demo/demo.env:143` 은 이미 이렇게 적고 있다 —
*"TOSS 결제 게이트웨이 — 데모는 결제 승인까지 가지 않으므로 더미. 실키를 넣지 말 것."*
더미 키로는 `loadTossPayments()` 가 실패해 체크아웃 화면에 **"결제 모듈을 불러오는데 실패했습니다"**
에러가 뜬다. 면접 데모에서 보여선 안 되는 화면이다.

**형제 파리티**: fan-platform 은 이미 이 문제를 풀었다 — `MockPaymentGatewayAdapter` 가 **기본
프로파일**이고 실 PortOne 은 `@Profile("portone")` opt-in 이다. 그리고 `ADR-MONO-056` D2 가
*"Consumers select via Spring config/profile — exactly the `@Profile("portone")` pattern fan-platform
already uses"* 로 이미 결정했다. ⇒ **ADR 신규 불필요. 이 태스크는 그 결정의 ecommerce 측 적용이다.**

---

# Goal

데모 프로파일에서 `PaymentGatewayPort` 는 mock 이 승인하되 **Kafka 도메인 이벤트는 실제로 발행**되어,
주문 → 결제완료 → 배송 → 정산의 하류 사가가 그대로 돈다. 프로덕션 프로파일에서는 이 어댑터가
**구조적으로 활성화될 수 없다.**

---

# Scope

## In Scope

- payment-service: 데모용 프로파일(예: `demo-pg`) + mock `PaymentGatewayPort` 빈
  (승인 응답은 `StandalonePaymentGateway` 를 참고하되 **이벤트 퍼블리셔는 실 구현을 유지**)
- `prod` 와의 **상호배타 가드** — 두 프로파일이 동시에 활성이면 부팅 실패
- web-store 체크아웃: 데모 모드에서 Toss SDK 를 태우지 않고 주문 확정으로 진행하는 최소 분기
- `infra/demo/demo.env` — 데모 프로파일 활성 env + 주석 갱신(현재의 "결제 승인까지 가지 않는다" 기술이
  더 이상 사실이 아니게 되므로)

## Out of Scope

- 실 Toss 연동 코드 삭제/변경 — 그대로 둔다. 데모 프로파일은 **추가**다
- `standalone` 프로파일 변경 — 다른 용도(무DB 로컬 구동)를 갖는다
- 결제 도메인 로직/상태 머신 변경
- 새 결제 수단 추가

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정)** — 코드로 재확인한다: (a) `standalone` 이 `PaymentEventPublisher` 를
      no-op 으로 바꾸는 것이 맞는지, (b) 결제 완료 후 하류 사가가 소비하는 이벤트가 정확히 무엇인지,
      (c) fan-platform 의 mock 선택 방식(기본 프로파일 vs opt-in). 이 티켓의 배경과 어긋나면 코드가 이긴다
- [ ] **AC-1 (mock 승인 + 실 이벤트)** — 데모 프로파일에서 결제 승인 시 `PaymentCompleted` 계열
      이벤트가 **실제 Kafka 로** 발행된다(no-op 아님). 통합 테스트로 단언
- [ ] **AC-2 (prod 상호배타 가드)** — `prod` 와 데모 프로파일이 동시에 활성이면 컨텍스트 기동이
      **실패**한다. 가드 테스트가 이 실패를 단언하고, 가드를 제거하면 테스트가 RED 가 됨을 확인한다
      (무는지 네거티브 검증)
- [ ] **AC-3 (기본값 안전)** — 아무 프로파일도 주지 않았을 때 mock 이 **선택되지 않는다.**
      (fan 은 mock 이 기본이지만 ecommerce 는 실 Toss 가 기본이므로 파리티를 맹목 적용하지 않는다 —
      이 차이와 그 근거를 태스크에 기록)
- [ ] **AC-4 (프런트 분기 최소)** — web-store 는 데모 모드에서 결제 모듈 로드 실패 배너를 띄우지 않고
      주문 완료 흐름으로 진행한다. 분기 조건은 빌드타임 상수가 아니라 **런타임에 판단 가능한 값**이어야
      한다(`NEXT_PUBLIC_*` 는 빌드 시 인라인되어 AMI 에 고정된다)
- [ ] **AC-5 (라이브 완주)** — 통합 데모 스택에서 브라우저로: 장바구니 → 주문 생성 → 결제 완료 →
      주문 상태 전이 → 배송 read-model 반영 → **콘솔 E-Commerce 주문 탭에 반영**까지 확인한다.
      단위/통합 테스트 green 은 이 AC 를 대체하지 않는다
- [ ] **AC-6 (실 경로 무회귀)** — 기존 Toss 어댑터 경로의 테스트가 전부 그대로 통과한다.
      데모 프로파일 추가가 실 결제 경로의 동작을 바꾸지 않는다

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 —
> `projects/ecommerce-microservices-platform/PROJECT.md` 의 domain/traits 로 rule 레이어 로드.

- `docs/adr/ADR-MONO-056-payment-gateway-abstraction.md` — D2(프로파일 선택), D3(프런트는 프로젝트별 패턴)
- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md`
- `projects/fan-platform/apps/membership-service/.../PaymentGatewayConfig.java` — 형제 선례
  (특히 `@Qualifier` 이중 포트 함정 주석)

# Related Skills

- `.claude/skills/backend/...`
- `.claude/skills/INDEX.md`

---

# Related Contracts

- `specs/contracts/events/` — 결제 완료 이벤트 계약. **변경 없음**(같은 이벤트를 같은 형태로 발행).
  형태가 달라져야 한다면 그것은 이 태스크의 스코프 밖이며 스펙 선행 변경이 필요하다

---

# Target Service

- `payment-service`
- `web-store` (AC-4 분기만)

---

# Architecture

- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md`

---

# Implementation Notes

- **`@Qualifier` 이중 포트 함정** — fan 에서 실측된 결함: 어댑터가 `PaymentGatewayPort` 와
  `RefundablePaymentGateway` 를 동시에 구현하면 Spring 의 타입 매칭이 두 포트 모두에 후보를 올려
  `NoUniqueBeanDefinitionException` 이 난다. 그리고 **Lombok `@RequiredArgsConstructor` 는 필드의
  `@Qualifier` 를 생성자 파라미터로 복사하지 않는다** — 명시적 생성자가 필요하다. 같은 함정을 반복하지 말 것
- **프로파일 이름**은 기존 `standalone` / `prod` 와 혼동되지 않게 고른다. 이름 선택 근거를 기록한다
- 데모 mock 은 "무조건 승인" 이므로 **실패 경로 시연이 사라진다.** 실패 시나리오를 보여줄 필요가 있으면
  주문 금액/파라미터로 실패를 유도하는 규칙을 넣을지 판단하고, 넣지 않기로 했다면 그 사실을 기록한다
- `demo.env` 의 기존 주석("결제 승인까지 가지 않으므로 더미")은 이 태스크가 사실을 바꾸므로 **반드시
  갱신**한다. 남겨두면 다음 사람이 틀린 전제를 물려받는다

---

# Edge Cases

- 이벤트는 발행되는데 컨슈머(배송·정산)가 데모 스택에 안 떠 있으면 화면은 여전히 비어 보인다 —
  AC-5 검증 시 컨슈머 기동 여부를 함께 기록
- 결제 멱등키/중복 승인 처리는 mock 에서도 동일하게 지켜져야 한다(중복 클릭)
- 환불 포트(`RefundablePaymentGateway`)도 데모에서 호출될 수 있다 — mock 이 이 포트를 만족하는지 확인

---

# Failure Scenarios

- **데모 프로파일이 prod 에 실린다** → 실제 결제 없이 승인. AC-2 상호배타 가드가 유일한 방어
- **이벤트가 안 나간다** → 결제만 완료되고 주문/배송이 멈춘다. `standalone` 이 정확히 이 형태로 실패한다. AC-1
- **CI green 인데 라이브에서 안 됨** — 결제/인증 계열은 이 저장소가 반복해서 당한 지점이다
  (테스트 헬퍼가 실 요청과 다른 입력을 만들어 통과). AC-5 라이브 완주만이 증거
- **프런트 분기가 빌드타임 상수** → AMI 에 값이 구워져 재굽기 전까지 데모에 도달하지 않는다. AC-4

---

# Test Requirements

- 단위: mock 어댑터 승인/환불/상태조회
- 통합: 데모 프로파일에서 `PaymentCompleted` 이벤트 실발행 단언(AC-1)
- 가드: prod × 데모 프로파일 동시 활성 시 부팅 실패 + 가드 제거 시 RED(AC-2)
- 회귀: 기존 Toss 경로 테스트 전건 통과(AC-6)
- 라이브: 브라우저 구매 완주 → 콘솔 반영(AC-5)

---

# Definition of Done

- [ ] 구현 완료
- [ ] 테스트 추가 · 통과 (가드 네거티브 검증 포함)
- [ ] 라이브 구매 완주 증거 기록
- [ ] `infra/demo/demo.env` 주석 갱신
- [ ] Ready for review
