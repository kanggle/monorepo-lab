# ADR-001: 실 PG 연동 — PortOne V2 클라이언트 개시 결제 + 서버측 검증 경계

**Status**: Proposed
**Date**: 2026-07-24 (proposed)
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: `specs/services/membership-service/architecture.md` (§ PG Boundary — 원래 § PG Mock Boundary 가 "실 PG 어댑터는 미래 증분, profile 로 스왑" 을 이미 예고), `specs/contracts/http/membership-api.md` (subscribe 요청 필드), TASK-FAN-BE-031(백엔드 어댑터), TASK-FAN-FE-010(프런트 SDK)

---

## Context

멤버십 구독 결제는 v1 에서 **결정적 mock** 이다 — `PaymentGatewayPort.authorize(amountMinor, planMonths, paymentToken, idempotencyKey)` 를 `MockPaymentGatewayAdapter` 가 구현하고, 예약 sentinel `tok_decline` 만 거절, 그 외 전부 승인(`paymentRef = pgmock_<uuid>`)한다. 외부 PG SDK 는 프런트·백엔드 어디에도 없다.

이 구조는 데모에서 **결제 흐름이 시각적으로 존재하지 않는다**: 구독 버튼을 누르면 서버 액션이 곧바로 `POST /memberships` 를 호출해 201/422 를 즉시 반환할 뿐, 사용자가 카드 정보를 입력하는 **결제창(payment window)** 이 뜨지 않는다. 포트폴리오 관점에서 "실 PG 연동 역량" 을 보여주려면 실제 PG 결제창이 뜨고, 서버가 그 결제를 **검증**한 뒤에야 멤버십이 생성되는 흐름이 필요하다.

`architecture.md § PG Mock Boundary` 는 이미 이 증분을 예고해 두었다:

> *"A real PG adapter is a future increment: it re-implements `PaymentGatewayPort` and is wired via `@ConditionalOnMissingBean` / profile, with the mock retained for dev + integration tests."*

본 ADR 은 그 "future increment" 를 **구체화**한다 — 어떤 PG 를, 어떤 신뢰 경계로, CI 안전성을 어떻게 유지하며 붙이는가.

### 실 PG 는 "토큰 인가" 가 아니라 "결제 검증" 이다

mock 은 클라이언트가 준 문자열(token)을 백엔드가 그 자리에서 승인/거절한다. 실 PG(카드 결제)는 근본적으로 다르다:

1. **클라이언트가 결제를 개시·완료**한다 — PG 브라우저 SDK 가 결제창(iframe/팝업)을 띄우고, 사용자가 카드 정보를 PG(우리 서버가 아님)에 직접 입력한다. 카드번호는 우리 시스템을 절대 통과하지 않는다(PCI-DSS 범위 축소). SDK 는 결과로 **`paymentId`**(PG 결제 참조)를 돌려준다.
2. **백엔드는 그 결제를 검증**한다 — 클라이언트가 보낸 `paymentId` 를 그대로 신뢰하지 않고, PG REST API 로 **서버측 조회**해 `status == PAID` 이고 **결제금액이 우리가 청구하려던 금액과 일치**하는지 확인한 뒤에야 멤버십을 만든다.

즉 신뢰 경계가 "token → authorize" 에서 "client paymentId → **server-side verify**" 로 이동한다. **클라이언트가 보낸 성공 신호를 절대 믿지 않는 것**이 이 설계의 핵심 보안 속성이다.

---

## Decision (Proposed)

### PG 선택 — 옵션 비교

| 옵션 | 설명 | 결제창 | 가입/키 부담 | 포트폴리오 적합도 |
|---|---|---|---|---|
| **A. PortOne V2 (구 아임포트)** | 다중 PG 를 단일 SDK/REST 로 추상화하는 결제 오케스트레이션 레이어. 테스트 채널(KG이니시스/토스 테스트)을 콘솔에서 연결 | iframe/팝업(PG별) | 무료 가입, `storeId`+`channelKey`+`API secret` | **높음** — 한국 실무에서 가장 흔한 통합 방식, 다중 PG 추상화 역량 시연 |
| B. 토스페이먼츠 직접 | 결제위젯 SDK 직접 연동 | 페이지 내 모달 | 테스트 상점 키 | 중 — 단일 PG 결합 |
| C. Stripe 테스트 | 글로벌 표준, test 카드(`4242...`) | 페이지 내 모달/Checkout | 테스트 키(가입 쉬움) | 중 — 국내 데모 맥락과 거리 |

**결정: A — PortOne V2.** 한국 데모 맥락에서 가장 자연스럽고, "다중 PG 를 추상화하는 통합 레이어" 라는 우리 `PaymentGatewayPort` 의 존재 이유와 개념적으로 정확히 일치한다(포트 뒤에 mock/PortOne 를 바꿔 끼우는 것 = PortOne 이 그 뒤에서 여러 PG 를 바꿔 끼우는 것의 상위 반영).

### 포트 경계 — 시그니처는 보존, 의미만 이동

`PaymentGatewayPort` 는 **이미 `amountMinor` 를 넘겨받으므로** 실 어댑터가 서버측 금액 검증을 할 재료를 이미 가지고 있다. 시그니처를 바꿀 필요가 거의 없다 — 클라이언트 제공 문자열의 **의미만** `paymentToken`(백엔드가 인가) → `paymentId`(백엔드가 검증)로 이동한다. 도메인·유스케이스 계층(`SubscribeUseCase`, `Membership`, `AccessPolicy`)은 **불변**이다. 반환 `PaymentResult(approved, paymentRef)` 도 그대로 — 실 모드에선 `paymentRef = 검증된 paymentId`.

파라미터명은 `paymentReference` 로 중립화한다(두 어댑터 모두 의미 있게 읽히도록): mock 은 sentinel token 으로, PortOne 어댑터는 조회할 paymentId 로 해석.

### 어댑터 선택 — profile 게이팅 (mock 은 CI/test 기본값 유지)

`MockPaymentGatewayAdapter` 의 클래스 주석이 이미 경고하듯 `@ConditionalOnMissingBean` 을 component-scan `@Component` 에 거는 것은 비결정적이다. 따라서:

- **mock = 기본값**: `@Profile("!portone")` (또는 property 부재 시 back-off). **CI·통합테스트·키 없는 로컬은 자동으로 mock** 을 쓴다 — 실 PG 호출은 CI 에서 절대 발생하지 않는다.
- **PortOne = 명시 활성화**: `@Profile("portone")` + 필수 property(`fan.payment.portone.api-secret` 등) 존재 시에만. 데모/운영에서 `SPRING_PROFILES_ACTIVE=portone` + `.env` 주입.

이로써 **키 부재가 곧 안전한 mock 폴백**이다 — 키 없는 어떤 환경도 깨지지 않는다.

### 시크릿 — 레포·CI 에 절대 없음

`storeId`/`channelKey`(프런트, 반공개) 와 `API secret`(백엔드, 시크릿)은 전부 **런타임 env 주입**(`.env` = gitignore, docker-compose 오버레이). 레포 커밋·CI secret 어디에도 넣지 않는다. `NEXT_PUBLIC_PORTONE_STORE_ID`/`NEXT_PUBLIC_PORTONE_CHANNEL_KEY` 는 빌드타임 인라인이므로 웹 이미지 빌드 시 주입(민감도 낮음), `API secret` 은 백엔드 런타임 env.

### 계약 변경 (contract-first)

`membership-api.md` subscribe 요청 `paymentToken` → `paymentId` (breaking). 리포지토리 정책상 계약이 구현을 선행하므로 Phase 0 에서 계약을 먼저 갱신하고, 필드의 **profile 별 해석**(mock=opaque token, portone=검증 대상 paymentId)을 명시한다. 오류 코드 `422 PAYMENT_DECLINED` 는 유지하고, 검증 실패(status≠PAID·금액 불일치·조회 실패)를 그 코드로 매핑한다.

---

## Consequences

**긍정**
- 실제 PG 결제창이 뜨는 데모 — 포트폴리오의 "결제 통합" 서사 확보.
- 카드정보가 우리 시스템을 통과하지 않음(PCI 범위 축소) — 올바른 보안 자세.
- 도메인·유스케이스 불변 — 포트 추상화가 값을 증명(어댑터 스왑만으로 실 PG 전환).
- 키 부재 = mock 자동 폴백 → CI·기여자 로컬 무손상.

**비용/리스크**
- **외부 의존 + 시크릿 운영**: PortOne 테스트 계정·키를 사람이 발급·주입해야 함. 라이브 검증은 키 없이는 불가(이 ADR 이 `Proposed` 로 남는 이유 — 라이브 결제창 확인 전까지 ACCEPTED 승격 금지).
- **breaking 계약 변경**: `paymentToken` → `paymentId`. Phase 1 에서 백엔드·프런트·테스트를 **원자적으로** 전환해 main 에 깨진 결제 경로가 남지 않게 한다.
- **비결정적 외부 API**: PortOne 조회는 네트워크 의존. 통합테스트는 **WireMock 으로 PortOne 응답을 스텁**(PAID/실패/금액불일치)해 CI 결정성을 유지 — 실 API 는 CI 에서 호출하지 않는다.
- **웹허브 무결성**: `@portone/browser-sdk` 신규 의존 추가(번들 영향 소).

### Phase 분할

- **Phase 0 (본 ADR + 스펙 + 계약 + 태스크 저작)** — 키 불필요. 아키텍처 확정, breaking 계약 문서화, 구현 태스크(BE-031/FE-010) ready 화. 이 PR 은 docs-only 로 안전 병합.
- **Phase 1 (TASK-FAN-BE-031 + TASK-FAN-FE-010)** — PortOne 테스트 키 필요. 백엔드 어댑터(WireMock 테스트) + 프런트 SDK 체크아웃(vitest SDK mock) + env 배선 + **라이브 결제창 검증**. 검증 성공 시 본 ADR 을 kanggle 의 정확형 intent 로 ACCEPTED 승격.

---

## Status History

- 2026-07-24 **Proposed** — 라이브 결제창 미검증(PortOne 키 대기). self-ACCEPT 금지(`platform/architecture-decision-rule.md § The ACCEPTED Gate`); Phase 1 라이브 검증 후 kanggle 승격.
