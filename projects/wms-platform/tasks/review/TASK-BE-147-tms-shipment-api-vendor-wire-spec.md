# Task ID

TASK-BE-147

# Title

outbound-service TMS shipment API vendor wire-level spec authoring — `specs/contracts/http/tms-shipment-api.md` (Open Item) closure

# Status

review

# Owner

wms-platform

# Task Tags

- wms
- outbound-service
- spec
- contract
- tms
- integration-heavy
- be

---

# Goal

[TASK-BE-049](../done/TASK-BE-049-tms-real-adapter.md) (PR #315, 2026-05-10 머지) 가 `StubTmsClientAdapter` → 실제 `TmsClientAdapter` 교체를 완료하면서 vendor TMS 의 production HTTP integration (RestClient + Resilience4j + dedupe 테이블) 이 main 에 진입했다. 그러나 vendor wire-level contract 인 `projects/wms-platform/specs/contracts/http/tms-shipment-api.md` 는 여전히 **부재** — `external-integrations.md` §2.1 + § References 가 "(Open Item — vendor-controlled)" marker 만 들고 있다.

이 상태는:

- **spec-vs-code drift** — production code (`TmsShipmentRequest` / `TmsShipmentResponse` / `TmsShipmentMapper` / `TmsClientAdapter.postToTms`) 가 실제 wire 를 정의하지만 spec layer 6 (contracts) 가 비어있어 CLAUDE.md § Core Principles 위반 위험.
- **portfolio 평가자 진입 자료 결손** — outbound-service 가 portfolio 의 integration-heavy 깊이 증명 자산 (138 → 182 unit / I1-I4 + I7-I9 production-level / Resilience4j CB+retry+bulkhead + WireMock IT 6 scenario, project_outbound_sweep_complete 메모리 참조) 인데, "TMS 어떤 wire 로 호출하나?" 단일 질문에 대한 spec 답변이 비어있음.
- **BE-049 production code 의 retrospective spec drift 봉합 backlog** — `project_mono_085_dead_reference_batch.md` 메모리 § "남은 backlog" 에 단독 entry 로 명시: "wms `tms-shipment-api.md` spec authoring (BE-049 production code 존재, vendor wire-level)".

본 task = `tms-shipment-api.md` 신규 authoring (~250-300 line) + `external-integrations.md` §2.1 / § References 의 (Open Item) marker 제거. **production code = 0 변경** — 이미 머지된 BE-049 의 wire 를 그대로 spec 으로 기록 (vendor-controlled "indicative" 마킹은 유지: 실제 vendor 가 schema 를 확정하면 sib mapping 만 갱신).

retrospective spec authoring 답습 패턴: [TASK-BE-145](../done/TASK-BE-145-notification-service-idempotency-spec-and-dlt-replay-runbook.md) (notification idempotency.md + runbooks/dlt-replay.md, ~350 line backfill, BE-043 bootstrap merged 후 retrospective). [TASK-BE-144](../done/TASK-BE-144-notification-events-eventversion-int-string-drift-fix.md) (eventVersion int/string drift fix). 본 task = 7번째 same-day single-PR closure 후보 (BE-141/142 / FAN-BE-006 / MONO-084 / BE-281 / BE-145 / BE-146 precedent).

---

# Scope

## In Scope

### A. 신규 spec authoring: `specs/contracts/http/tms-shipment-api.md`

대상 path: `projects/wms-platform/specs/contracts/http/tms-shipment-api.md`.

답습 sibling pattern: 같은 directory 의 `outbound-service-api.md` (서비스가 노출하는 REST) — 그러나 본 spec 은 **outbound 가 호출하는 vendor 의 wire** 라는 점에서 방향이 반대. 가장 가까운 sibling = `specs/contracts/webhooks/erp-order-webhook.md` (외부 시스템 wire contract) 의 section 구성.

표준 section (~10-12 section, ~250-300 line):

1. **`# HTTP Contract — TMS Shipment Push`** + 1-2 paragraph 도입 (방향=outbound, vendor-controlled 한계, 단일 endpoint 의 핵심 책임).
2. **`## Endpoint`** — `POST {tms-base}/shipments` + `{tms-base}` 의 env-specific 매핑 (prod/stg/dr/dev) + Content-Type / Accept.
3. **`## Authentication`** — `X-Tms-Api-Key`, Secret Manager source, two-key rotation window, TLS trust store + no pinning.
4. **`## Request Headers`** table (Content-Type / Idempotency-Key / X-Tms-Api-Key / X-Request-Id 옵션).
5. **`## Request Body`** — JSON example + Field Reference table (`shipmentId` / `shipmentNo` / `carrierCode` / `shippedAt` / `orderId`) — production `TmsShipmentRequest` record (5 field, `@JsonInclude(NON_NULL)`) 와 byte-identical.
6. **`## Response — 2xx Success`** — JSON example + Field Reference table (`tmsRequestId` / `trackingNumber` / `carrierCode` / `status` / `message`) — production `TmsShipmentResponse` record (5 field, `@JsonIgnoreProperties(ignoreUnknown=true)`) 와 byte-identical. `status` enum (`ACCEPTED` / `PENDING_CARRIER_ASSIGNMENT` / `REJECTED`) success 분류 명시.
7. **`## Response — 4xx Errors`** table — 400/401/403/404/409/422/429 매핑 (production `TmsClientAdapter.postToTms` 의 `onStatus` 분기 + §2.11 의 saga outcome 일치). 409 = vendor-honoured Idempotency-Key (success 처리) 강조.
8. **`## Response — 5xx Errors`** + timeout/IO 분류 — transient 처리 root.
9. **`## Idempotency Semantics`** — `Idempotency-Key: {shipment.id}` (UUID, stable), vendor 측 dedupe 책임 + 클라이언트 `tms_request_dedupe` table fallback (`TmsRequestDedupePersistenceAdapter` REQUIRES_NEW, V13 schema), 24h vs vendor-controlled retention 차이 명시.
10. **`## Vendor Schema Versioning`** — `X-Tms-Schema-Version` (v1 default) + vendor 가 schema 를 finalise 하면 본 spec + `TmsShipmentRequest`/`TmsShipmentResponse`/`TmsShipmentMapper` 만 변경, 도메인은 무영향 (per integration-heavy I7/I8).
11. **`## Out of Scope (v1)`** — TMS quote/rating, pickup/delivery callback (carrier tracking → notification-service v2), multi-tenant TMS, mTLS instead of API key.
12. **`## References`** — `external-integrations.md` §2 / `architecture.md` § TMS Integration / `idempotency.md` § REST Idempotency / `state-machines/saga-status.md` `SHIPPED_NOT_NOTIFIED` row / `sagas/outbound-saga.md` § notify failure path / `rules/traits/integration-heavy.md` I1-I4 + I7-I9 / production code anchor (`TmsClientAdapter.java`, `TmsShipmentRequest.java`, `TmsShipmentResponse.java`).

### B. `external-integrations.md` Open Item marker 제거

대상:

- §2.1 "Full wire-level contract: `specs/contracts/http/tms-shipment-api.md` (Open Item — vendor-controlled)." → "(Open Item — vendor-controlled)" 제거 + 직접 link 활성화.
- § References 의 동일 라인 — "(Open Item)" 제거.

다른 (Open Item) marker (ERP webhook spec, events catalog) 는 본 task scope **외** — 별도 task 후보.

### C. WMS-specific concerns

- `tms-shipment-api.md` 의 wire 가 **production code 와 byte-identical** — 의도된 vendor-shaped DTO 의 field 순서 / 이름 / nullability 가 `TmsShipmentRequest` / `TmsShipmentResponse` record 와 정합.
- `status` enum 의 success / failure 분류가 `TmsShipmentMapper.isSuccess()` 의 switch case 와 정합.
- 4xx 매핑 table 이 `TmsClientAdapter.postToTms()` 의 `onStatus(is4xxClientError)` 분기 + `external-integrations.md` §2.11 의 saga outcome table 양쪽과 정합.
- Idempotency 섹션 의 24h TTL 언급은 **Redis idempotency** (`outbound:idempotency:*`) 와 **TMS dedupe** (DB `tms_request_dedupe`, infinite until purge) 의 차이를 명시 — 후자는 vendor 가 key 를 honour 하지 못할 때 last line of defense.

## Out of Scope

- production code 변경 — `TmsShipmentRequest` / `TmsShipmentResponse` / `TmsShipmentMapper` / `TmsClientAdapter` / `TmsClientConfig` 무수정.
- vendor schema 의 **실제 결정** — vendor 와의 외부 합의가 없는 상태이므로 본 spec 은 "indicative / production code 가 expose 하는 wire" 로 기록. 실제 vendor schema 가 도착하면 별 task 로 mapper + DTO 갱신.
- ERP order webhook spec 의 Open Item closure — 별도 task (해당 spec 은 본 task 와 직교).
- `outbound-events.md` / `idempotency.md` 의 Open Item closure — 별도 task.
- TMS retry endpoint (`POST /api/v1/outbound/shipments/{id}:retry-tms-notify`) 의 internal REST contract — `outbound-service-api.md` 에 이미 documented (TASK-BE-049 시리즈 동안 갱신됨, 본 task 와 직교).
- TMS quote API / pickup callback / delivery webhook — v1 Out of Scope, 본 spec 에 "Not in v1" 섹션으로만 기록.

---

# Acceptance Criteria

### Impl PR

- [ ] `projects/wms-platform/specs/contracts/http/tms-shipment-api.md` 신규 file 작성 (~250-300 line, 12 section 표준).
- [ ] § Endpoint = `POST {tms-base}/shipments` + env-specific `{tms-base}` mapping.
- [ ] § Authentication = `X-Tms-Api-Key` header + Secret Manager + two-key rotation window.
- [ ] § Request Body field reference 가 production `TmsShipmentRequest` 의 5 field (`shipmentId` UUID / `shipmentNo` String / `carrierCode` String / `shippedAt` Instant / `orderId` UUID) 와 정합 — nullable 표기 + `@JsonInclude(NON_NULL)` 의 의도 명시.
- [ ] § Response 2xx field reference 가 production `TmsShipmentResponse` 의 5 field (`tmsRequestId` / `trackingNumber` / `carrierCode` / `status` / `message`) 와 정합 — `@JsonIgnoreProperties(ignoreUnknown=true)` 의 의도 명시 (vendor extra field tolerance).
- [ ] § Response 2xx 의 `status` enum 분류 table — `ACCEPTED` / `PENDING_CARRIER_ASSIGNMENT` → success / `REJECTED` → failure / 미지정 → success (`TmsShipmentMapper.isSuccess()` 의 default branch 와 정합).
- [ ] § Response 4xx table = 400 / 401 / 403 / 404 / 409 / 422 / 429 분기 — 각 row 가 `TmsClientAdapter.postToTms()` 의 분기 (TmsPermanentException / TmsTransientException / 409 success 처리) + `external-integrations.md` §2.11 의 saga outcome 양쪽과 정합.
- [ ] § Response 5xx + timeout/IO = transient 처리 root 명시.
- [ ] § Idempotency = `Idempotency-Key: {shipment.id}` (UUID) + `tms_request_dedupe` (V13 schema, `request_id` PK, `sent_at`, `response_snapshot` JSON) 의 client-side fallback 명시 + REQUIRES_NEW persistence 의도 인용.
- [ ] § Vendor Schema Versioning = `X-Tms-Schema-Version` header (default 1) + I7/I8 격리 원칙 인용.
- [ ] § Out of Scope (v1) = TMS quote/rating / pickup-completion callback / multi-tenant TMS / mTLS / certificate pinning.
- [ ] § References = `external-integrations.md` §2 / `architecture.md` § TMS Integration / `idempotency.md` § REST Idempotency / `state-machines/saga-status.md` / `sagas/outbound-saga.md` / `rules/traits/integration-heavy.md` I1-I4+I7-I9 / 3 production code path (`TmsClientAdapter.java`, `TmsShipmentRequest.java`, `TmsShipmentResponse.java`).
- [ ] `external-integrations.md` §2.1 의 "(Open Item — vendor-controlled)" marker 제거 — link 직접 활성화.
- [ ] `external-integrations.md` § References 의 동일 라인 "(Open Item)" 제거.
- [ ] 다른 (Open Item) marker (ERP webhook / outbound-events / idempotency 의 Open Item) 는 변경 없음 — scope drift 없음.
- [ ] cross-ref 검증 — 신규 spec 에서 인용한 6 production code path + 5 sibling spec link 가 모두 dead reference 0.
- [ ] HARDSTOP-03 PASS — 본 file 은 wms project-specific spec (shared paths 무관).
- [ ] CI self-CI PASS (path-filter wms markdown-only — 15 SKIP + 1 changes PASS 예상, TASK-MONO-075 자연 검증).
- [ ] task lifecycle ready → review (in-progress 우회, same-day single-PR closure 패턴 — TASK-BE-145 / BE-146 precedent).
- [ ] wms `tasks/INDEX.md` 동기 (`## ready` 제거 / `## review` append).

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] wms `tasks/INDEX.md` `## review` 제거, `## done` append outcome.

---

# Related Specs

- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` § 2 (TMS) — 본 spec 의 implementation-side counterpart (timeouts / CB / retry / bulkhead / saga coupling 모두 declared). 본 task 가 §2.1 + § References 의 Open Item marker 만 정리, 본문 변경 없음.
- `projects/wms-platform/specs/services/outbound-service/architecture.md` — § TMS Integration (port boundary + RestClient 채택 정당화).
- `projects/wms-platform/specs/services/outbound-service/idempotency.md` — § REST Idempotency (Idempotency-Key 처리), § Vendor Idempotency (`tms_request_dedupe`).
- `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md` — § notify failure path (SHIPPED → SHIPPED_NOT_NOTIFIED 전이).
- `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md` — `SHIPPED_NOT_NOTIFIED` row, manual retry → `COMPLETED` 전이.
- `rules/traits/integration-heavy.md` — I1 (timeout) / I2 (CB) / I3 (retry) / I4 (idempotency) / I7 (vendor adapter) / I8 (internal model translation) / I9 (bulkhead).
- `platform/security-rules.md` — Secret Manager + API key rotation policy.

# Related Contracts

- (신규) `projects/wms-platform/specs/contracts/http/tms-shipment-api.md` — 본 task 의 핵심 산출물.
- 답습 sibling = `projects/wms-platform/specs/contracts/webhooks/erp-order-webhook.md` (외부 시스템 wire contract, 방향 반대).

# Edge Cases

- **vendor 가 schema 를 변경하는 경우**: 본 spec 은 "indicative / production code 가 expose 하는 wire" 로 기록되므로 vendor 와 실 schema 합의 도착 시 별 task 로 `TmsShipmentRequest`/`TmsShipmentResponse`/`TmsShipmentMapper` 3 file + 본 spec 4 file 갱신. 도메인 / saga / port 인터페이스는 무영향 (per I7/I8).
- **`status` enum 에 신규 값 추가**: production `TmsShipmentMapper.isSuccess()` 의 default branch 가 "success" 로 처리하므로 신규 값은 silent 하게 success 로 routing 됨 — 본 spec 의 § Response 2xx 가 이 의도된 fail-open 정책을 명시.
- **409 vendor-honoured Idempotency-Key**: production code 의 `onStatus(is4xxClientError)` 분기에서 409 만 fall-through 하여 body 를 정상 unmarshall — 본 spec 의 § Response 4xx 가 409 row 를 "success-equivalent" 로 명시 (다른 4xx 와 분리).
- **vendor 의 schema 가 우리 record 보다 더 많은 field 를 반환**: `@JsonIgnoreProperties(ignoreUnknown=true)` 로 silent 하게 drop. 본 spec 이 의도된 vendor extension tolerance 를 명시.
- **외부 vendor 가 wire 변경 commit 없이 schema breaking change 를 release**: production code 의 transient/permanent exception lattice 가 422 (schema mismatch) 로 routing — saga 가 `SHIPPED_NOT_NOTIFIED` + `TMS_SCHEMA_REJECTED` 로 transition. 본 spec 의 § Response 4xx 422 row 가 이 운영 신호를 명시.

# Failure Scenarios

- **production code 와 spec 의 field 정의 drift**: 본 task 는 production code (5 + 5 field record) 를 spec 으로 transcript — spec 작성 시점에 production source 를 직접 reference 하여 byte-identical 보장. 추후 production code 가 변경되면 spec 갱신은 production code 변경 PR 의 의무 (CLAUDE.md § Layer Rules — Contracts).
- **(Open Item) marker 의 의도하지 않은 잔존**: `external-integrations.md` §2.1 + § References 양쪽 라인을 explicit 하게 정리 — Acceptance Criteria 의 2 checkbox 로 강제.
- **신규 spec 의 dead reference**: 인용한 sibling spec path / production code path 가 모두 실재해야 — Acceptance Criteria 의 cross-ref 검증 checkbox 가 강제 (TASK-MONO-085/086 의 dead-reference batch 학습 답습, `project_mono_085_dead_reference_batch.md` 메모리 참조).
- **scope creep — 다른 Open Item closure 시도**: ERP webhook / outbound-events / idempotency 의 다른 (Open Item) marker 는 본 task 변경 없음 — 별도 task 후보.

# Notes

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (vendor wire-level spec authoring routine, sibling pattern 답습 가능). 단 production code 와의 byte-identical 검증이 작업의 핵심 risk 라 backend-engineer 또는 api-designer subagent dispatch 권장.
- 본 task 의 single-PR closure 시 D4 churn freeze 영향 0 (project-internal spec).
- `project_mono_085_dead_reference_batch.md` 메모리 § "남은 backlog" 의 단일 entry 해소 — 잔존 backlog 0 으로 갱신될 수 있음 (메모리 작업 별 step).
- 답습 same-day single-PR closure 패턴 (BE-141 / BE-142 / FAN-BE-006 / MONO-084 / BE-281 / BE-145 / BE-146) — 본 task = 8번째 entry 후보.
