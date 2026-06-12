# Task ID

TASK-BE-362

# Title

shipping-service: 물류 중개 플랫폼(aggregator) 어댑터 매핑 + credential 배선 (ADR-007 D2)

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

[ADR-007](../../docs/adr/ADR-007-logistics-aggregator-carrier-integration.md) D2 가 확정한 방향(단일 택배사 직연동이 아닌 **물류 중개 플랫폼(aggregator)** 연결)을 `mode=http` 경로에 실제로 배선한다.

TASK-BE-293 이 만든 provider-agnostic `HttpCarrierTrackingAdapter`(outbound pull)와 TASK-BE-294 의 `ProcessCarrierWebhookService`(inbound webhook)는 이미 존재한다. 이 task 는 그 단일 포트 뒤에 **하나의 중개사**를 매핑한다 — 중개사 통일 상태코드 ↔ `ShippingStatus` 매핑 테이블 확정, credential(키/시크릿) 외부주입 표준화, 미매핑 상태의 **관측 가능성(metric/log)** 추가. 실 중개사 계정이 없으므로 검증은 mock/sandbox(MockWebServer + 중개사 표준 응답 fixture)로 한다.

이 task 가 끝나면: "중개사 sandbox 자격증명을 env 로 주입하면 outbound 추적과 inbound webhook 이 그 중개사 한 곳과 end-to-end 로 동작하고, 매핑 밖 상태는 조용히 정체되지 않고 메트릭으로 드러난다"가 참이 된다.

---

# Scope

## In Scope (shipping-service only)

- `CarrierStatusMapper` 를 **중개사 통일 상태코드 체계** 기준으로 보강 — 한 곳에 매핑 테이블을 명시(예: 집화완료/이동중/배송출발/배송완료 등 → `SHIPPED`/`IN_TRANSIT`/`DELIVERED`). 미매핑은 기존대로 empty(no-op) 유지하되 **`carrier_status_unmapped` 카운터(metric) + WARN 로그**(raw 상태값 tag)를 추가.
- credential 배선 표준화: `shipping.carrier.{base-url,api-key}` 가 **중개사** 엔드포인트·키를 가리킴을 config 주석·`overview.md` 에 명시. `api-key` 는 env 주입(Secret Manager 대체값), 코드 하드코딩 0.
- (해당 시) outbound 요청에 중개사가 요구하는 carrier 선택 파라미터(자동 배정 vs 명시) 전달 — `CarrierTrackingPort.fetchLatest(carrier, trackingNumber)` 시그니처는 불변, `carrier` 값의 의미(중개사 내 carrier 코드)만 문서화.
- inbound: `CarrierWebhookVerifier` 의 서명 헤더/알고리즘이 중개사 규격(HMAC-SHA256 `X-Carrier-Signature: sha256=<hex>`)과 일치하는지 확인 + 불일치 시 한 곳에서 조정 가능하도록 정리(코드 변경 최소).
- 테스트: 중개사 표준 응답 fixture 로 `HttpCarrierTrackingAdapterTest` 보강(2xx 매핑/미매핑→empty+카운터), `CarrierStatusMapperTest` 매핑표 케이스 추가.
- `overview.md` + `shipping-api.md`: provider = 중개사임을 명시(계약 협력사/단일택배사 직연동과의 구분 한 줄).

## Out of Scope

- gateway public-route 노출 (→ TASK-BE-359).
- 무인 auto-collect 스케줄러 (→ TASK-BE-360).
- webhook dedup-marker cleanup (→ TASK-BE-361).
- 실 중개사 프로덕션 계정/계약 (포트폴리오 범위 밖; mock/sandbox 로 실증).
- 도메인 전이 규칙·`ShippingStatusChanged` 계약·order/notification consumer 변경.
- wms TMS dispatch 연동(ADR-007 D3 분리 유지).
- carrier SDK 신규 의존 추가.

---

# Acceptance Criteria

- [ ] AC-1 — 중개사 표준 2xx 응답(매핑된 상태)에서 outbound refresh 가 forward-only 로 전진하고 `ShippingStatusChanged` 1건 발행(기존 동작 유지).
- [ ] AC-2 — 매핑 밖(미지원 vocab) 상태에서 no-op + `carrier_status_unmapped` 카운터 증가 + WARN 로그(raw 값 포함). 크래시·상태변경 없음.
- [ ] AC-3 — `api-key`/`secret` 는 전부 env 주입; 소스 그렙으로 하드코딩 0 확인.
- [ ] AC-4 — net-zero 불변: `mode=mock`+blank `mock-status`, blank webhook `secret` 기본값에서 기존 동작/테스트 그대로(회귀 0).
- [ ] AC-5 — `:shipping-service:check` BUILD SUCCESSFUL; CI Build & Test GREEN.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, load `rules/common.md` + `rules/traits/integration-heavy.md`.

- `projects/ecommerce-microservices-platform/docs/adr/ADR-007-logistics-aggregator-carrier-integration.md` (D2 — 본 task 의 근거)
- `projects/ecommerce-microservices-platform/specs/services/shipping-service/overview.md`
- `projects/ecommerce-microservices-platform/PROJECT.md` (trait `integration-heavy`)

# Related Skills

- `.claude/skills/backend/external-http-integration` (provider-agnostic 실 HTTP 어댑터 플레이북 — MONO-234)

---

# Related Contracts

- `specs/contracts/http/shipping-api.md` (refresh-tracking / carrier-webhook — 기존, provider 의미 명시 갱신)
- `specs/contracts/events/shipping-events.md` (`ShippingStatusChanged` — 재사용, 불변)

---

# Target Service

- `shipping-service`

---

# Edge Cases

- 중개사가 carrier 자동 배정 → 응답의 carrier 코드가 우리가 보낸 것과 다름: 추적 식별은 우리 `trackingNumber`/`shippingId` 기준이므로 무영향(문서화만).
- 중개사 통일코드가 신설/변경: 매핑표 미스 → AC-2 경로(가시화 후 매핑 추가).
- 2xx 인데 status 필드 없음 / 5xx / timeout: 기존 best-effort empty 유지.

---

# Failure Scenarios

- **F1 — silent 정체**: 미매핑 상태가 조용한 no-op 로 쌓여 운영자가 "왜 안 움직이지"를 모른다. → AC-2 metric/log 로 가시화.
- **F2 — credential 누출**: 키 하드코딩. → AC-3 env-only.
- **F3 — net-zero 회귀**: 매핑 보강이 기본 mock 경로를 건드림. → AC-4.

---

# Test Requirements

- unit: `CarrierStatusMapperTest`(매핑표 + 미매핑→empty), `HttpCarrierTrackingAdapterTest`(MockWebServer 중개사 fixture: 매핑/미매핑/오류).
- slice: 기존 `RefreshTrackingServiceTest`/webhook 테스트 회귀 GREEN.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added & passing
- [ ] Contracts/specs updated (provider=중개사 명시)
- [ ] net-zero 회귀 0 확인
- [ ] Ready for review
