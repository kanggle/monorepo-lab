# ADR-007: 택배 연동을 단일 택배사 직연동이 아닌 물류 중개 플랫폼(aggregator)으로 수렴한 이유

- **Status**: ACCEPTED (2026-06-12)
- **Date**: 2026-06-12
- **Authors**: backend (carrier-integration 회고 + aggregator 방향 결정)
- **Supersedes**: —
- **Superseded by**: —
- **History**: PROPOSED 2026-06-12 — 사용자가 "중개 플랫폼을 끼우는 식이 좋을까?" 질문 + AskUserQuestion 에서 **"중개 플랫폼(aggregator) 채택"** 을 명시 선택. 본 ADR 은 (a) 이미 머지된 carrier 연동(TASK-BE-293/294)의 아키텍처를 **회고적으로 정리**하고 (b) 남은 v2 작업의 **방향을 aggregator 로 확정**하기 위해 PROPOSED 로 작성. → **ACCEPTED 2026-06-12** — 사용자가 전체 ADR(D1–D5) 검토 후 "진행" 명시 intent. **NOT self-ACCEPT**: D2 aggregator 방향은 사용자 AskUserQuestion 선택이고 ACCEPTED 전환도 사용자 직접 지시(ADR-MONO-022 선례와 동형). follow-on TASK-BE-359/360/361 + aggregator-adapter **TASK-BE-362** 를 등록(BE-358 은 동시 머지된 JPA hotfix·ADR-MONO-030 Step3 예약과 충돌하여 헤드라인 task 를 362 로 리넘버).

---

## Context

### 이미 구현되어 있는 것 (회고)

shipping-service 는 spec 이 v2 로 유보했던 "External carrier API 통합"을 두 증분으로 이미 구현했다 (둘 다 2026-06-12 머지):

| Task | 방향 | 핵심 구현 |
|---|---|---|
| [TASK-BE-293](../../tasks/done/TASK-BE-293-shipping-carrier-tracking-integration.md) | **outbound pull** | `CarrierTrackingPort` + `HttpCarrierTrackingAdapter`(`mode=http`, `ResilienceClientFactory` RestClient, best-effort/never-throw) + `MockCarrierTrackingAdapter`(`mock`, blank `mock-status`=OFF) + `CarrierStatusMapper`(ACL) + admin `POST /api/shippings/{id}/refresh-tracking` |
| [TASK-BE-294](../../tasks/done/TASK-BE-294-shipping-carrier-webhook-ingestion.md) | **inbound webhook** | `CarrierWebhookController`(`POST /api/shippings/carrier-webhook`) + `CarrierWebhookVerifier`(HMAC-SHA256, constant-time, blank secret=fail-closed) + `deliveryId` 멱등(`processed_carrier_webhooks` V5) + `ShippingForwardAdvancer`(forward-only) |

이 연동은 **provider-agnostic** 이다 — carrier 식별자를 파라미터로 받는 단일 포트(`CarrierTrackingPort`)와 단일 webhook 엔드포인트로 추상화돼 있고, 어떤 carrier SDK 도 컴파일타임 의존하지 않는다. 기본값은 양방향 모두 **net-zero**(`mode=mock` + blank `mock-status`, blank webhook `secret`=fail-closed)라서, 설정을 켜기 전까지 v1 admin-driven 동작과 완전히 동일하다.

### 기록되지 않은 것 (이 ADR 이 필요한 이유)

위 연동은 `integration-heavy` trait 규칙과 shipping-service `overview.md` 의 "v2 reserved" 메모만 근거로 구현됐고, **아키텍처 결정 자체는 어디에도 기록되지 않았다**. 특히 다음 세 가지가 미기록 상태다:

1. **provider-agnostic 단일 포트**를 택한 이유 (vs 택배사별 어댑터 N개).
2. 실제 운영 시 그 포트 뒤에 **무엇을 연결할 것인가** — 단일 택배사 직연동 vs 물류 중개 플랫폼(aggregator). 이것이 본 ADR 의 핵심 결정.
3. wms `outbound-service` 의 **TMS dispatch 어댑터**(운송장 발급, [ADR-MONO-022](../../../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md))와의 관계 — 두 개의 별도 외부 연동을 하나로 묶을 것인가.

### 현실 제약 (결정 드라이버)

한국 택배사 공식 API 는 **기능에 따라 접근 권한이 갈린다**:

- **운송장 발급 / 집화 접수(booking)** — CJ대한통운·한진·롯데 등 메이저 택배사는 **계약 고객(화주)** 전용. 계약 시 발급되는 고객코드 + API 키가 있어야 하며, 개인·소상공인은 직접 계약이 사실상 불가능하다.
- **배송 조회 / 추적(tracking)** — 운송장 번호만 있으면 비교적 개방적(통합 조회 API 다수).

따라서 "포트폴리오/소규모 셀러가 실제로 발송을 자동화하려면" 택배사 직계약이 아니라 **물류 중개 플랫폼**(굿스플로·스윗트래커·셀메이트 등)을 끼우는 것이 현실 모델이다. 중개사가 여러 택배사와 마스터 계약을 맺어두고, 사용자는 중개사 한 곳의 API/정산만 사용한다.

---

## Decisions

### D1 — 택배 연동은 provider-agnostic 단일 포트로 추상화한다 (이미 채택, 회고 기록)

shipping-service 도메인은 어떤 택배사인지 `carrier` 코드로만 알고, 벤더별 API 차이는 **어댑터(`CarrierTrackingPort` 구현) + `CarrierStatusMapper`** 가 흡수한다.

- **왜**: `integration-heavy` trait 의 ACL 원칙(I7/I8)과 일치 — 도메인·application 계층은 벤더 타입·Resilience4j 타입을 보지 않는다. 택배사를 추가/교체해도 도메인 전이 규칙과 `ShippingStatusChanged` 이벤트 계약은 불변.
- **버린 대안**: 택배사별 전용 어댑터 N개(CJ 어댑터, 한진 어댑터…). 헥사고날 포트가 벤더 수만큼 증식하고, 각 벤더 계약키를 도메인이 알아야 하며, 무엇보다 "계약 협력사만 가능" 제약에 그대로 걸린다.

### D2 — 단일 포트 뒤에 연결할 실 provider 는 **물류 중개 플랫폼(aggregator)** 으로 한다 (이 ADR 의 핵심 결정)

운영 시 `shipping.carrier.mode=http` 의 `base-url`/`api-key` 는 **특정 택배사가 아니라 물류 중개 플랫폼의 엔드포인트·키**를 가리킨다. 중개사가 carrier 선택(또는 자동 배정)·운송장 발급·추적 webhook 푸시를 대행한다.

- **왜**:
  - **접근성** — 중개사 계약 1건으로 여러 택배사를 커버. 개인·소상공인도 가능한 현실 모델(Context 제약 참조).
  - **통합 표면 1개** — D1 의 단일 포트가 그대로 중개사 1개에 매핑된다. 인증키 1개, 어댑터 1개. 택배사 직연동의 "벤더당 어댑터·키" 폭증을 피한다.
  - **기존 구현과 정합** — `CarrierStatusMapper` 는 이미 "벤더 vocab 에 관대한 매핑"으로 설계됐다. 중개사의 통일 상태 코드 체계와 자연스럽게 맞는다.
- **버린 대안**:
  - **CJ대한통운/롯데 실 단일 택배사 직연동** — 화주 계약·고객코드가 전제라 포트폴리오에서 실증 불가. 또한 택배사를 바꾸면 어댑터·계약을 다시 트는 구조라 확장성이 낮다. (계약이 실재하는 단일-택배사 사업자라면 합리적 — 그 경우 D1 포트에 해당 택배사 어댑터를 꽂으면 되고, 본 ADR 은 그 경로를 막지 않는다.)
  - **택배사 직조회 무인증 스크래핑** — 약관 위반·취약, 배제.

### D3 — wms TMS dispatch 와 ecommerce carrier-tracking 은 **분리 유지**한다 (v1)

[ADR-MONO-022](../../../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) 가 정한 책임 경계를 따른다:

- **wms `outbound-service` TMS 어댑터** = 창고가 물건을 실제 출고하며 **운송장을 발급(dispatch/booking)** 하는 leg. wms 도메인 소유.
- **ecommerce shipping-service carrier 연동** = 발급된 운송장의 **배송 상태를 추적(tracking)** 하고 주문 상태로 전파하는 leg. ecommerce 도메인 소유(ADR-MONO-022 D6: ACL 은 commerce 측).

- **왜 통합하지 않나**: 두 leg 는 서로 다른 계층·다른 소유 도메인의 관심사다. 하나의 "broker" 서비스로 묶으면 ADR-MONO-022 가 명시적으로 세운 도메인 분리(§1.4 "wms 는 commerce 개념을 import 하지 않는다")를 깨고, 단일-published-axis 독립성(D8)도 훼손한다.
- **연결점**: 두 leg 는 운송장 번호(`trackingNumber` + `carrier`)로 이어진다. wms 가 발급한 운송장이 `wms.outbound.shipping.confirmed.v1`(이미 `carrierCode`/`shipmentNo` 포함) → ecommerce shipping `PREPARING→SHIPPED`(tracking 세팅) → 이후 중개사 추적이 이어받는다. **데이터 흐름은 연결, 코드 소유는 분리.**
- **v2 후보(미결)**: 실제로는 동일 중개사가 발급(wms 측)과 추적(ecommerce 측)을 모두 대행할 수 있다. 그 통합(공유 중개사 클라이언트 lib)은 두 leg 가 각각 실 중개사에 붙은 뒤 별도 ADR 로 재검토한다. v1 에서는 과설계 배제.

### D4 — 기본 OFF(net-zero) + fail-closed 보안 자세를 유지한다 (이미 채택, 회고 기록)

- outbound: `mode=mock` + blank `mock-status` = 호출 없음(v1 baseline).
- inbound: blank webhook `secret` = 모든 webhook 401(fail-closed). 서명 검증은 HMAC-SHA256 constant-time.
- **왜**: 설정 미주입 상태에서 외부 입력이 배송 상태를 멋대로 전진시키는 회귀를 원천 차단. 각 프로젝트의 standalone-publish 독립성(ADR-MONO-022 D8)과도 정합 — 중개사 미연동 환경은 admin-driven 으로 자연 degrade.

### D5 — 남은 v2 작업 분해 (follow-on ready tasks)

본 ADR 와 함께 다음 후보 task 를 `tasks/ready/` 에 등록한다(우선순위 순):

1. **TASK-BE-362** — 중개사(aggregator) 어댑터 매핑 + credential 배선. D2 실현의 헤드라인. (당초 BE-358 로 등록했으나 동시 머지된 JPA hotfix 및 ADR-MONO-030 Step3 예약과 번호 충돌 → 362 로 리넘버.)
2. **TASK-BE-359** — `carrier-webhook` 경로의 gateway public-route 노출(현재 미노출 → 중개사 webhook 도달 불가).
3. **TASK-BE-360** — 무인 auto-collect tracking 스케줄러(ShedLock; in-flight 배송 폴링).
4. **TASK-BE-361** — webhook dedup-marker(`processed_carrier_webhooks`) retention/cleanup sweep.

---

## Consequences

- **긍정**: 택배 연동의 아키텍처 결정이 처음으로 명문화된다(provider-agnostic + aggregator). 포트폴리오 서사가 "계약 없이도 실제로 발송 자동화 가능한 현실 모델"로 강화된다. 기존 코드는 무변경(이미 aggregator-shape).
- **비용**: 실 중개사 어댑터·계약/자격증명이 있어야 `mode=http` 가 실가동(포트폴리오에서는 mock/sandbox 로 실증). webhook 의 gateway 노출(BE-359)이 없으면 inbound leg 가 실제로는 닫혀 있다.
- **리스크 표면**: 중개사 응답 vocab 이 `CarrierStatusMapper` 매핑을 벗어나면 no-op(상태 정체)로 나타난다 — best-effort 설계상 크래시가 아니라 미동작이므로, 미매핑 상태는 메트릭/로그로 가시화해야 한다(BE-362 범위).
- **가역성**: 높음. provider-agnostic 포트라 중개사 → 단일 택배사(D2 대안)로 되돌리는 것도 어댑터 교체 한 번. ADR-MONO-022 분리(D3) 덕에 wms 와 결합되지 않아 독립 롤백 가능.

---

## Provenance

- 사용자 질문 2026-06-12 ("택배사 연동은 일반 사용자도 가능? … 중개 플랫폼을 끼우는 식으로 만드는 게 좋을까?") + AskUserQuestion("중개 플랫폼(aggregator) 채택").
- 회고 대상: TASK-BE-293 / TASK-BE-294 (shipping-service carrier 연동, 2026-06-12 머지).
- 경계 근거: ADR-MONO-022 (ecommerce↔wms fulfillment, 도메인 분리 §1.4 / ACL 소유 D6 / standalone 독립 D8).
- spec: `specs/services/shipping-service/overview.md` (carrier 증분 + "잔여 v2" 메모), `specs/contracts/http/shipping-api.md`.
- trait: `PROJECT.md` `integration-heavy` ("택배 트래킹 … Circuit breaker, retry, DLQ, idempotent side-effect").

분석=Opus 4.8 / 구현 권장=Opus (외부 연동 아키텍처 결정 + 도메인 경계 정합).
