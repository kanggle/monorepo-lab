# Task ID

TASK-BE-572

# Title

데모 mock PG 프로파일 — 실 PG 없이 결제를 완주시키되 도메인 이벤트는 실제로 발행한다

# Status

done

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

- [x] **AC-0 (착수 = 재측정)** — 세 항목 모두 코드로 확인했고, **티켓이 예상하지 못한 것 두 가지**가 나왔다.
      (a) 맞다 — `StandaloneConfig` 는 `@Profile("standalone")` 하나로 스텁 게이트웨이 **와**
      no-op `PaymentEventPublisher` 를 함께 등록한다. (b) 하류 소비자는 `PaymentCompleted` 를
      기준으로 order · product · notification · settlement **4개** 서비스이고, 발행은 아웃박스
      (`PaymentEventOutboxWriter` → `PaymentOutboxPublisher`)다. (c) fan 은 `@Profile("!portone")`
      로 **mock 이 기본**이다.
      🔴 **추가 발견 1 — `prod` 프로파일은 ecommerce 에서 완전히 무효다.** 전수 확인:
      `@Profile("prod")` 0건 · `application-prod.yml` 0건 · `spring.config.activate.on-profile` 0건
      (앱·리소스·공유 libs 전부). 모든 프로파일 게이트가 `standalone` 기준으로만 쓰여 있다.
      ⇒ AC-2 의 상호배타 가드가 **데모에서 아무것도 잃지 않는다**(아래).
      🔴 **추가 발견 2 — `PaymentConfirmService.confirm` 은 `PaymentAuthorization.approved()` 를
      아예 읽지 않는다.** `verify(...)` 직후 곧바로 `payment.confirm(...)` 으로 간다. 실 Toss
      어댑터는 `declined()` 를 **반환한 적이 없고** 예외(`PgConfirmFailedException`)로만 거절하며,
      `approved()` 를 검사하는 곳은 **fan 뿐**이다. ⇒ mock 이 `declined()` 를 반환하면 그 결제는
      **성공으로 기록된다.** 그래서 데모 게이트웨이의 거절은 반드시 **예외**여야 한다(구현·테스트로 고정).
      이 비대칭 자체는 도메인 로직이라 이 티켓의 Out of Scope 이며 별도 티켓으로 남긴다.
- [x] **AC-1 (mock 승인 + 실 이벤트)** — `DemoPgProfileEventPublishIntegrationTest`
      (Testcontainers postgres + EmbeddedKafka). 형제 테스트와 달리 **`@MockitoBean` 이 하나도 없다** —
      승인은 프로파일이 등록한 빈이, 발행은 실 아웃박스가 한다. `payment.payment.completed` 토픽에서
      envelope 을 실제로 수신하고 `published_at` 이 채워진 것까지 단언한다. 2/2 통과.
- [x] **AC-2 (prod 상호배타 가드)** — `DemoPgProfileGuard` = `@Profile("demo-pg & prod")` +
      생성자에서 throw. Spring 이 프로파일 식을 직접 평가하므로 프로퍼티·순서·리스너 누락으로
      우회되지 않는다. `DemoPgProfileGuardTest` 5케이스(동시활성 실패 · 선언순서 역전도 실패 ·
      demo-pg 단독 성공+mock 등록 · **prod 단독 성공+mock 없음** · 무프로파일 mock 없음).
      **네거티브 확인**: `throw` 를 지우면 2케이스 RED, 복구하면 GREEN.
      🔵 이 가드가 데모에 비용을 물리지 않는 이유는 AC-0 의 추가 발견 1 이다 — `prod` 가
      아무것도 담고 있지 않으므로 데모가 그것을 빼도 잃는 것이 없다. `prod` 가 언젠가 실제 설정을
      갖게 되면 이 판단은 다시 해야 한다(가드를 지우는 것이 아니라).
- [x] **AC-3 (기본값 안전)** — `PaymentGatewayConfig` 는 `@Profile("!standalone & !demo-pg")` 로,
      **아무 프로파일도 없으면 실 Toss 어댑터가 등록된다.** fan 의 `!portone`(mock 이 기본)을
      **의도적으로 복제하지 않았다**: fan 은 CI 에 실 PG 시크릿이 없어 mock 이 기본이어야 하지만,
      ecommerce 는 Toss 가 프로덕션 경로다. 파리티는 **메커니즘**(프로파일 선택, ADR-MONO-056 D2)에
      대한 것이지 스위치가 어느 쪽에 놓이는지에 대한 것이 아니다. 테스트로 고정(무프로파일 → mock 없음).
- [x] **AC-4 (프런트 분기 최소)** — 분기를 컴포넌트가 아니라 **`useTossPayment` 훅 한 곳**에 뒀다.
      `CheckoutForm` 과 `PaymentWidget` 이 **둘 다** 이 훅을 쓰고 둘 다 데모에서 깨진다(폼은 주문을
      이미 만든 **뒤** "결제 모듈이 준비되지 않았습니다" 로 죽고, 위젯은 "결제 모듈을 불러오는데
      실패했습니다" 배너를 띄운다). 훅에서 한 번 분기하면 두 컴포넌트는 데모의 존재를 몰라도 된다.
      플래그는 **`NEXT_PUBLIC_*` 가 아니라** `/api/store-config` (`force-dynamic`) 가 요청 시점에
      `process.env.DEMO_PAYMENT_MOCK` 를 읽어 준다 — 프리베이크 AMI 가 부팅 때 결정을 받는다.
      데모 경로는 Toss 가 리다이렉트하는 **바로 그 success URL** 로 이동해 confirm 이후 전 구간을
      실 코드로 지난다. 🔵 설정 조회 실패는 **데모 아님**으로 폴백한다(반대로 두면 일시적 네트워크
      오류가 실 스토어를 "모든 결제 성공" 으로 바꾼다).
- [x] **AC-5 (라이브 완주)** — 통합 데모(traefik + iam + console + ecommerce 22컨)에서 **진짜
      브라우저(Playwright)로** 완주했다. 9/10 PASS(유일한 실패는 드라이버가 주문목록 경로를
      `/orders` 로 잘못 적은 것 — 실제 경로는 `/my/orders`). 화면 판정만으로 끝내지 않고
      **DB 상태를 따로 확인**했다(아래 실주행 증거). 콘솔 E-Commerce 섹션도 `/ecommerce` 에서
      렌더된다.
      🔴 **이 AC 를 하려다 데모가 로그인부터 불가능하다는 것이 드러났다** — web-store compose 에
      next-auth env 가 **통째로 없어서**(`NEXTAUTH_SECRET` 부재 → `MissingSecret`) 스토어프런트
      로그인이 성립한 적이 없었다. 형제 `fan-platform-web` 은 `TASK-FAN-FE-014` 에서 같은 다섯
      값을 받았는데 web-store 만 남겨진 straggler 다. AC-5 가 그 위에 서 있으므로 함께 고쳤다
      (IAM 콜백은 이미 등록돼 있어 마이그레이션은 불필요 — 실측 확인).
- [x] **AC-6 (실 경로 무회귀)** — payment-service `test` **205** / `integrationTest` **24**,
      실패 0 · **스킵 0**(`BUILD SUCCESSFUL` 만 믿지 않고 test-results XML 에서 카운트를 읽었다).
      기존 Toss 경로 테스트(`PaymentEventPublishIntegrationTest`, 환불·스위퍼·멀티테넌트)가 전건 포함.
      web-store 는 `tsc --noEmit` rc=0. 🔴 **로컬 vitest 는 기동 자체가 불가**하다
      (vitest 4 × Node 24 `#module-evaluator` — 이 저장소에 기록된 기존 함정) ⇒ **CI 가 권위**다.

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

- [x] 구현 완료
- [x] 테스트 추가 · 통과 (가드 네거티브 검증 포함)
- [x] 라이브 구매 완주 증거 기록
- [x] `infra/demo/demo.env` 주석 갱신 ("결제 승인까지 가지 않는다" 는 더 이상 사실이 아니다)
- [x] Ready for review

---

# 실주행 증거 (통합 데모, 브라우저)

**브라우저 경로 — 9/10 PASS**

```
PASS  IAM OIDC 왕복 후 스토어 오리진으로 복귀
PASS  장바구니에 상품이 담겨 있다 (product-service V8 시드 행)
PASS  항목 선택 후 주문 진입점이 활성화된다        ← 체크박스 선택 → "주문하기"
PASS  체크아웃 페이지에 진입했다                    finalPath=/checkout?items=...
PASS  런타임 설정이 데모 결제 모드를 보고한다        {"demoPayment":true}   ← 빌드타임 상수 아님
PASS  체크아웃에 결제 모듈 실패 배너가 없다          []                     ← 이 티켓의 존재 이유
PASS  주문 생성 (CheckoutForm 이 부르는 그 엔드포인트)  201
PASS  결제 완주 → /checkout/complete 에 도달        finalUrl=/checkout/complete?orderId=593f4371-…
PASS  완료 화면에 실패 문구가 없다
FAIL  주문 목록에 이 주문이 보인다                   ← 드라이버가 `/orders` 로 적었다(실제 `/my/orders`)
```

**화면이 아니라 서버 상태 — 이것이 `standalone` 과 갈리는 지점이다**

```
payment_db     status=COMPLETED  payment_key=demo_593f4371-…  method=CARD  amount=29000
payment_outbox event_type=PaymentCompleted  published_at IS NOT NULL   ← 실제 발행(no-op 아님)
order_db       status=CONFIRMED
settlement_db  commission_accrual 1행 (order=593f4371-…, ACCRUAL, 29000)  ← 하류 사가 반응
shipping_db    processed_events = 3
console        /ecommerce 가 finalPath=/ecommerce 에서 200, degraded 마커 0건
```

`standalone` 이었다면 결제만 COMPLETED 가 되고 outbox·order·settlement 는 **전부 비어 있다.**

**측정 중 배운 것 (다음 사람 몫)**

- 🔴 주문 직후 곧바로 confirm 하면 `PAYMENT_NOT_FOUND` 다. Payment 행은 payment-service 가
  `OrderPlaced` 를 **소비해서** 만들기 때문 — 사가가 비동기라는 사실이지 결함이 아니다.
  `GET /api/payments/orders/{id}` 가 200 이 될 때까지 기다리면 된다.
- 🔴 confirm 을 미리 부르면 멱등 전이를 소모해 success 화면이 "이미 완료" 로 멈춘다.
  브라우저 검증에서는 **페이지가 confirm 하도록** 두어야 한다.

---

# 이 티켓이 고치지 않고 남긴 것 (전부 실측, 별개 원인)

1. **`PaymentConfirmService.confirm` 이 `PaymentAuthorization.approved()` 를 읽지 않는다** —
   `declined()` 를 반환하는 게이트웨이가 생기면 그 결제가 **성공으로 기록된다.** 오늘은
   잠복이다(Toss 는 예외로만 거절, standalone/demo-pg 는 승인 또는 예외). 도메인 로직이라
   이 티켓의 Out of Scope 이며 **`TASK-BE-574`** 로 분리했다.
2. **`POST /api/users/me/addresses` → 500** (데모 계정의 user 프로필 미프로비저닝).
   체크아웃의 `address1`/`zipCode` 는 readOnly(주소검색 위젯 전용)라 저장된 배송지가 없으면
   폼 제출 버튼이 활성화되지 않는다 ⇒ **`TASK-MONO-506`**(도메인 데이터 시드)의 범위.
   이번 라이브는 주문을 같은 BFF 엔드포인트로 만들어 우회했다(배송지는 인라인이다).
3. **상품 상세의 옵션 위젯이 로그인 세션에서 구매 버튼을 활성화하지 않는다**(익명 세션에서는
   동작 — 실측). 이 티켓의 변경과 무관한 앞단이라 쫓지 않고 사실만 남긴다.
