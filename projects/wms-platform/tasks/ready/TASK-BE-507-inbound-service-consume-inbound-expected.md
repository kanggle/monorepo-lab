# Task ID

TASK-BE-507

# Title

wms inbound-service — scm inbound-expected 소비 → InboundExpectation(ASN) 생성, 단일+멀티창고 주소지정 (ADR-MONO-050 D3/D5/D6)

# Status

ready

# Owner

wms / backend

# Task Tags

- backend
- event
- wms

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D3/D5/D6 구현: wms `inbound-service` 가 `scm.procurement.inbound-expected.v1`(+cancel)을 소비해 **InboundExpectation(ASN)** 를 생성한다. 이후는 wms 기존 입고 흐름(검수·적치·재고반영·`wms.inventory.received.v1` 발행) 재사용 — 신규 코드는 event→expectation 뿐.

**핵심: D3 창고 주소지정** — 이벤트의 `destinationWarehouseId` 로 라우팅해 **단일창고(전 이벤트 동일 id)와 멀티창고(주소별) 모두 코드 분기 0**. 선행=BE-506. scm lane과 병렬.

---

# Scope

## In Scope

1. Kafka consumer(`@KafkaListener` topic=`scm.procurement.inbound-expected.v1`, group=`wms-inbound-scm-expected-v1`) — 기존 wms consumer 패턴(예: ecommerce-fulfillment consumer) 동형.
2. **멱등 2층(D6)**: `eventId`(UUID v7)→`processed_events` dedup; `(poNumber, line)` open-expectation business dedup.
3. **InboundExpectation 생성(D5)**: status `EXPECTED`, `source=SCM_PROCUREMENT`, `poNumber`/`poId`/`supplierId`/`expectedArrivalDate`/lines 보존. **D3 주소지정**: `destinationWarehouseId` 로 대상 창고 resolve(master-service 창고 마스터), 단일/멀티 동일 경로.
4. **검증 fail-closed(D3/D4)**: 미지/비활성 창고 → DLT + ops alert; `destinationNodeType != WMS_WAREHOUSE` → 방어적 reject(DLT).
5. **취소(D6.3)**: `.cancelled.v1` → 대상 EXPECTED 를 CANCELLED(미수령시). 이미 수령이면 no-op.
6. Flyway migration: `inbound_expectation`(또는 기존 ASN 테이블 확장) + `processed_events`(없으면).
7. 테스트: happy(발행→EXPECTED 생성, 필드 단언) / **멀티창고 라우팅(서로 다른 warehouseId 2건→각 창고)** / **단일창고(동일 id 반복)** / eventId 멱등 / `(poNumber,line)` business dedup / 미지창고→DLT / 3PL nodeType→reject / cancel→CANCELLED. 기존 입고 흐름 무회귀.

## Out of Scope

- 검수/적치/재고반영 로직 변경 0 (기존 재사용; InboundExpectation 하류는 불변).
- scm 발행 0 (SCM-BE-035).
- 부분입고 신규 로직 0 (기존 discrepancy 경로).

---

# Acceptance Criteria

1. consumer 가 이벤트 소비→`InboundExpectation`(EXPECTED, source=SCM_PROCUREMENT) 생성, poNumber/poId traceability 보존.
2. **D3 단일+멀티 1경로 실증**: 서로 다른 `destinationWarehouseId` 2건이 각 창고에 라우팅 + 동일 id 반복(단일)도 정상 — **코드 분기 없이** (테스트로 단언).
3. 멱등 2층(eventId + (poNumber,line)) 동작; 미지창고/3PL nodeType→DLT fail-closed(D3/D4).
4. cancel→CANCELLED(미수령시) (D6.3).
5. 하류 입고 흐름 무변경(도착→검수→적치→`wms.inventory.received.v1`), 기존 테스트 GREEN.
6. `:inbound-service:test`(+통합 레인) GREEN.

---

# Related Specs

- [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) D3/D4/D5/D6/D8
- [TASK-BE-506](TASK-BE-506-scm-inbound-expected-subscription-contract.md) — 구독 컨트랙트(선행)
- wms `ecommerce-fulfillment-subscriptions.md` — 기존 cross-project consumer 패턴 선례
- wms inbound-service `architecture.md`/`domain-model.md`(ASN/입고예정 기존 모델)

---

# Related Contracts

- `scm-inbound-expected-subscriptions.md`(BE-506)
- scm `scm-procurement-events.md`(권위)

---

# Target Service / Component

- `projects/wms-platform/apps/inbound-service/` (consumer + InboundExpectation + Flyway)

---

# Edge Cases

1. 기존 wms ASN/입고예정 테이블이 이미 있으면 **확장**(신규 테이블 남발 금지) — inbound-service domain-model 실측.
2. `processed_events` 기존 여부 확인(다른 wms consumer 재사용).
3. 멀티창고 = master-service 창고 마스터에 대상 창고 존재 전제 — 미존재시 fail-closed(D3).
4. wms PROJECT.md "단일 물류 센터 가정" 은 설계 가정일 뿐 master 는 멀티창고 모델 — 주소지정은 그 위에서 동작(가정 위반 아님, forward-compatible).

---

# Failure Scenarios

## A. InboundExpectation 하류가 자동 재고반영을 유발

→ D5: expectation 은 **약속**이지 재고변경 아님. 재고는 물리 도착+검수 후에만. 자동 반영 배선 금지(테스트로 가드).

## B. 멀티창고 라우팅이 단일창고에 하드코딩됨

→ D3 위반. `destinationWarehouseId` 를 assumption 아닌 event 값으로 resolve. 테스트가 2창고 라우팅 단언.

---

# Test Requirements

- happy/멀티창고/단일창고/eventId멱등/business dedup/미지창고DLT/3PL reject/cancel — mutation 적용여부 선확인.
- `:inbound-service:test` --rerun + 통합 레인 GREEN. 기존 입고 흐름 무회귀.

---

# Definition of Done

- [ ] consumer→InboundExpectation(EXPECTED, 주소지정)
- [ ] D3 단일+멀티 1경로 테스트 실증
- [ ] 멱등 2층 + 미지창고/3PL fail-closed + cancel
- [ ] 하류 무변경 + wms 무회귀

---

# Notes

- **Recommended impl model**: **Opus** (Kafka consumer + 멱등 + 주소지정 라우팅 + Flyway + fail-closed). 선행=BE-506. 병렬 lane=scm SCM-BE-034/035.
