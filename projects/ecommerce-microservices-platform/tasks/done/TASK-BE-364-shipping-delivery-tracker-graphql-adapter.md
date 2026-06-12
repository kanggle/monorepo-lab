# Task ID

TASK-BE-364

# Title

shipping-service: 실 물류 중개 플랫폼 = Delivery Tracker GraphQL/OAuth2 outbound 어댑터 배선 (ADR-007 D2)

# Status

done

# Owner

backend

# Task Tags

- code
- api
- adr

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

[ADR-007](../../docs/adr/ADR-007-logistics-aggregator-carrier-integration.md) D2 가 확정한
"단일 포트 뒤에 물류 중개 플랫폼(aggregator)" 방향의 **실 제공사를 Delivery Tracker
(`tracker.delivery`)** 로 확정하고, 그 GraphQL/OAuth2 규격으로 outbound pull 어댑터를 배선한다.

TASK-BE-362 가 `mode=http` 경로에 generic REST 어댑터(`HttpCarrierTrackingAdapter`,
`GET /track?carrier=&trackingNumber=` + flat `status`) + 매핑표 + 미매핑 가시화를 이미 깔아두었다.
이 task 는 그 자리에 **Delivery Tracker 규격 어댑터**를 추가한다 — 실 제공사의 wire 가 generic REST
가 아니라 ① **OAuth2 `client_credentials`** 토큰 + ② **GraphQL** `track(carrierId,trackingNumber){lastEvent{status{code}}}`
이기 때문이다. 새 `shipping.carrier.mode=delivery-tracker` 어댑터로 분기하고, 기존 `mock`/`http`
어댑터와 HMAC webhook 경로는 net-zero 보존한다.

이 task 가 끝나면: "Delivery Tracker sandbox 자격증명(`client-id`/`client-secret`)을 env 로 주입하고
`mode=delivery-tracker` 로 켜면, admin `refresh-tracking` 과 무인 auto-collect 스케줄러(BE-360)가
실 aggregator 한 곳에서 운송장 상태를 end-to-end 로 끌어와 forward-advance 하고, 매핑 밖 상태는
`carrier_status_unmapped` 로 드러난다"가 참이 된다. 자격증명이 없으면 어댑터는 비활성(net-zero).

전체 wire 계약은 [`external-integrations.md`](../../specs/services/shipping-service/external-integrations.md) § 1 이 SoT.

---

# Scope

## In Scope (shipping-service only)

- **OAuth2 토큰 프로바이더** (hand-rolled, `GapClientCredentialsTokenProvider` 패턴 / ADR-005):
  `POST {auth-url}` `Authorization: Basic base64(id:secret)` + `grant_type=client_credentials`
  → `access_token` 메모리 캐시 + 만료 임박 시 재발급. 토큰 실패 = best-effort empty(예외 전파 금지).
- **`DeliveryTrackerCarrierTrackingAdapter`** (`CarrierTrackingPort` 구현,
  `@ConditionalOnProperty shipping.carrier.mode=delivery-tracker`): `ResilienceClientFactory`
  RestClient 로 `POST {graphql-url}` (`Authorization: Bearer {token}`) `track` 쿼리 실행,
  `lastEvent.status.code` 만 읽어 `CarrierTrackingSnapshot(rawStatus)` 반환. **best-effort/never-throw**:
  transport/non-2xx/`errors[]`/`track==null`/no-`lastEvent` → `Optional.empty()`.
- **`CarrierStatusMapper` 보강**: Delivery Tracker `TrackEventStatusCode` enum 매핑 추가
  (`AT_PICKUP→SHIPPED`, `IN_TRANSIT→IN_TRANSIT`, `OUT_FOR_DELIVERY→IN_TRANSIT`,
  `AVAILABLE_FOR_PICKUP→IN_TRANSIT`, `DELIVERED→DELIVERED`; `INFORMATION_RECEIVED`/`ATTEMPT_FAIL`/
  `EXCEPTION`/`UNKNOWN`/`NOT_FOUND` → 미매핑 no-op). 기존 한글/영문 매핑은 불변. (`external-integrations.md` § 1.4 표)
- **config**: `shipping.carrier.delivery-tracker.{auth-url,graphql-url,client-id,client-secret}`
  (`@ConfigurationProperties`), 전부 env 주입·하드코딩 0. blank id/secret = 어댑터 비활성(net-zero).
- **vendor DTO**: GraphQL 요청/응답을 adapter-internal DTO 로 (도메인·포트 경계 누출 0, I8).
- **테스트**: `DeliveryTrackerCarrierTrackingAdapterTest`(MockWebServer: 토큰 200/401, GraphQL
  매핑/미매핑/`errors[]`/`track:null`/timeout), 토큰 프로바이더 캐시 재사용·재발급 테스트,
  `CarrierStatusMapperTest` enum 케이스 추가.
- (관측) `carrier_tracking_fetch{result}` + `delivery_tracker_token_failed` (`external-integrations.md` § 7).

## Out of Scope

- Delivery Tracker thin-ping webhook 경로(`registerTrackWebhook` + 콜백 re-pull) → **TASK-BE-365** (follow-on).
- 기존 generic HMAC `carrier-webhook`(BE-294/359)·`mock`/`http` 어댑터 동작 변경 (보존).
- 무인 auto-collect 스케줄러 로직 변경(BE-360 그대로 새 어댑터를 호출만; 스케줄러 코드 무변경).
- GraphQL event history(`lastEvent` 만 소비), circuit breaker/retry(best-effort no-op 으로 충분).
- 도메인 전이 규칙·`ShippingStatusChanged` 계약·order/notification consumer 변경.
- 실 Delivery Tracker 프로덕션 계약/계정 (데모는 sandbox 키 또는 로컬 GraphQL stub).
- wms TMS dispatch 연동(ADR-007 D3 분리 유지).

---

# Acceptance Criteria

- [ ] AC-1 — `mode=delivery-tracker` + 유효 credential 에서, GraphQL 200 (`lastEvent.status.code`
      매핑됨) 응답 시 outbound refresh 가 forward-only 전진 + `ShippingStatusChanged` 1건 발행.
- [ ] AC-2 — OAuth2 토큰이 메모리 캐시되어 연속 호출에서 재사용되고, 만료 임박 시 1회 재발급된다(MockWebServer 호출 수로 검증).
- [ ] AC-3 — 미매핑(비공백) status (`EXCEPTION` 등)에서 no-op + `carrier_status_unmapped{code}` 증가 + WARN(raw). 크래시·상태변경 없음.
- [ ] AC-4 — best-effort: 토큰 4xx/5xx·GraphQL transport/timeout·`errors[]`·`track:null` 전부 `Optional.empty()`(예외 전파 0, 상태 불변).
- [ ] AC-5 — credential(`client-id`/`client-secret`) env 주입; 소스 그렙 하드코딩 0. blank → 어댑터 bean 비활성(net-zero).
- [ ] AC-6 — net-zero 회귀: `mode=mock`(기본)·`mode=http`·HMAC webhook 경로 기존 동작/테스트 그대로(회귀 0).
- [ ] AC-7 — `:shipping-service:check` BUILD SUCCESSFUL; CI Build & Test GREEN.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, load `rules/common.md` + `rules/traits/integration-heavy.md`.

- `projects/ecommerce-microservices-platform/docs/adr/ADR-007-logistics-aggregator-carrier-integration.md` (D2 — 본 task 의 근거)
- `projects/ecommerce-microservices-platform/specs/services/shipping-service/external-integrations.md` (**§ 1 = wire 계약 SoT**)
- `projects/ecommerce-microservices-platform/specs/services/shipping-service/overview.md`
- `projects/ecommerce-microservices-platform/PROJECT.md` (trait `integration-heavy`)

# Related Skills

- `.claude/skills/backend/external-http-integration` (provider-agnostic 실 HTTP 어댑터 플레이북 — MONO-234)

---

# Related Contracts

- `specs/contracts/http/shipping-api.md` (refresh-tracking / carrier-webhook — 기존, provider=Delivery Tracker 명시 갱신 완료)
- `specs/contracts/events/shipping-events.md` (`ShippingStatusChanged` — 재사용, 불변)

---

# Target Service

- `shipping-service`

---

# Edge Cases

- Delivery Tracker 가 carrier 자동 배정 → 응답에 carrier 없음/상이: 식별은 우리 `trackingNumber`/`shippingId` 기준이라 무영향.
- GraphQL 200 인데 `errors[]` 동반(부분성공): empty 처리(best-effort), raw error WARN.
- `INFORMATION_RECEIVED`(운송장 등록만, 미집화): 우리 로컬은 이미 `SHIPPED` → 미매핑 no-op 가 정확(역행 방지).
- 토큰 응답에 `expires_in` 없음/짧음: 보수적으로 매 호출 직전 만료 검사, 없으면 안전 마진 후 재발급.
- carrierId 가 reverse-DNS 가 아닌 레거시 값: 그대로 vendor 에 전달(매핑은 status 단계, carrierId 는 통과).

---

# Failure Scenarios

- **F1 — 토큰 폭주**: 매 호출 토큰 재발급으로 auth 엔드포인트 rate-limit. → AC-2 캐시.
- **F2 — silent 정체**: Delivery Tracker 신규 enum 미매핑이 조용한 no-op. → AC-3 가시화(BE-362 재사용).
- **F3 — credential 누출**: 키 하드코딩. → AC-5 env-only.
- **F4 — net-zero 회귀**: 새 어댑터가 기본 mock/http 경로를 건드림. → AC-6.
- **F5 — best-effort 위반**: GraphQL 오류가 예외로 전파되어 admin refresh/스케줄러 배치를 깨뜨림. → AC-4.

---

# Test Requirements

- unit: `CarrierStatusMapperTest`(Delivery Tracker enum 표 + 미매핑→empty), `DeliveryTrackerCarrierTrackingAdapterTest`(MockWebServer: 토큰 200/401, GraphQL 매핑/미매핑/`errors[]`/`track:null`/timeout), 토큰 프로바이더(캐시 재사용/재발급/실패→empty).
- slice: 기존 `RefreshTrackingServiceTest`·webhook·BE-360 스케줄러 가드 테스트 회귀 GREEN.
- net-zero: `mode=mock` 기본 경로 테스트 불변; blank credential → 어댑터 비활성.

---

# Definition of Done

- [ ] Implementation completed (DeliveryTracker adapter + OAuth2 token provider + mapper 보강)
- [ ] Tests added & passing (MockWebServer 토큰/GraphQL 매트릭스)
- [ ] Contracts/specs updated (external-integrations.md § 1 = SoT, overview/shipping-api 갱신 완료)
- [ ] net-zero 회귀 0 확인 (mock/http/HMAC webhook)
- [ ] Ready for review
