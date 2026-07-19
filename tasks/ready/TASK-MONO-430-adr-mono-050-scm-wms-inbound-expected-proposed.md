# Task ID

TASK-MONO-430

# Title

ADR-MONO-050 작성 (PROPOSED) — scm 발주(CONFIRMED PO) → wms inbound-expected 폐루프 (ADR-027 미결 leg 종결, 단일+멀티창고, self-ACCEPT 금지)

# Status

ready

# Owner

architecture / docs

# Task Tags

- docs
- governance
- adr

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

[ADR-MONO-027](../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) §1.3/§D5 가 **의도적으로 열어둔** leg — scm procurement 가 replenishment PO 를 CONFIRMED 하면 그 입고 예정이 wms 에 pre-create 되어야 하는 연결 — 을 닫는 fresh ADR 을 **PROPOSED 상태로** 작성한다. 사용자 요청 (2026-07-19): "C 설계(scm 발주 → wms 입고예정)를 실제 ADR/task 로 만들지 진행. 단일창고도 가능하고 멀티창고도 가능하도록."

이 ADR 은 **최초의 `scm → wms` 런타임 커플링 방향**을 도입한다 (지금까지 wms↔scm 은 `wms → scm` 단방향뿐: inventory-visibility snapshot + 027 low-stock alert). wms `inbound-service` 가 **scm 발행 토픽을 구독하는 첫 wms 서비스**가 되며 커플링이 양방향이 된다 — 커플링 방향·트리거 PO 상태·창고 주소지정(단일/멀티)·3PL 목적지 경계·멱등/취소 시맨틱은 구현 전 기록해야 할 진짜 아키텍처 결정이므로 fresh ADR 이 필수 (ADR-003a §D2.1 class). PROPOSED 로 작성해 사용자가 §2 를 검토한 뒤 ACCEPTED 전환 (별 task TASK-MONO-431, user-explicit intent — self-ACCEPT 절대 금지, ADR-027 §6 동형).

## 선행 문맥 (열린 leg 의 출처)

ADR-027 은 replenishment 루프를 scm-internal DRAFT PO 까지만 짓고 멈췄다 (§1.3: "loop terminates at scm's procurement boundary", "no synchronous path" with wms). 물건이 실제 도착하면 wms 의 기존 **수동** 입고/ASN 경로로 재진입하나, scm PO 와 wms 입고예정을 프로그램으로 잇는 다리는 없다. ADR-050 이 그 다리다. 전방 루프(ecommerce→wms, [ADR-022](../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md))는 두 시스템이 모두 in-repo 라 이미 닫혀 있음 — 050 은 동형을 replenishment 방향에 적용.

---

# Scope

## In Scope (impl PR 가 수행)

### 1. `docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md` 신규 작성 (Status: PROPOSED)

ADR-027 구조를 미러 (§1 Context / §2 Decision D1–D8 / §3 Implementation plan / §4 Alternatives / §5 Consequences / §6 Status history). 핵심 결정:

- **§Status** = `PROPOSED`, history = `PROPOSED 2026-07-19 (TASK-MONO-430)`. **ACCEPTED 줄 작성 금지** (user 가 §2 미검토; ADR-027 §6 동형).
- **D1 — Transport**: scm `procurement-service` outbox 발행 `scm.procurement.inbound-expected.v1`, wms `inbound-service` consumer(group `wms-inbound-scm-expected-v1`, eventId 멱등, DLT). 권위 스키마=scm(producer), wms=consumer-driven subset. Rejected: scm→wms 동기 REST (scm TX 를 wms 가용성에 묶음).
- **D2 — Trigger point**: PO `CONFIRMED`(supplier ack) 에 발행, `SUBMITTED` 아님 (phantom expectation 회피).
- **D3 — 창고 주소지정 (사용자 요구)**: `destinationWarehouseId` 를 이벤트가 운반 → **단일창고=degenerate case(전 이벤트 동일 id), 멀티창고=주소된 창고에 expectation 생성, 코드 분기 0**. 창고 수 = 배포 사실이지 코드 분기 아님. 미지/비활성 창고 → DLT fail-closed.
- **D4 — 3PL 목적지 = v1 out of scope**: `destinationNodeType` 은 forward-compat 로 두되 v1 은 `WMS_WAREHOUSE` 만 수용. 3PL 창고는 3PL 자기 WMS 운영 → wms inbound 대상 아님 (scm inventory-visibility `THIRD_PARTY_LOGISTICS` 노드 + v2 `logistics-service` 로 이관). producer-side 필터 + wms 방어적 reject.
- **D5 — wms 측**: `InboundExpectation`(status EXPECTED, source=SCM_PROCUREMENT, poNumber/poId traceability) 생성 후 **기존 wms 입고 흐름(검수·적치·재고반영·기존 `wms.inventory.received.v1` 발행) 재사용** — 신규 코드는 event→expectation 뿐, 부분입고 등은 기존 discrepancy 경로.
- **D6 — 멱등/취소/수정**: (1) eventId(UUID v7) dedup, (2) `(poNumber, line)` business dedup, (3) 취소=companion `scm.procurement.inbound-expected.cancelled.v1` v1 포함(phantom 방지), (4) 수량 amendment=v2 defer(v1=cancel+재발행).
- **D7 — 컨트랙트 소유**: scm `scm-procurement-events.md` 권위 스키마 + wms `scm-inbound-expected-subscriptions.md` consumer-driven + scm 문서에 wms consumer 1줄 명기 (027 `replenishment-subscriptions.md` 역할 반전 미러).
- **D8 — standalone degradation**: wms 단독=토픽 미도래, 수동 ASN 경로 무영향; scm 단독=구독자 없는 토픽 발행(무해). Hard dependency 0.
- **§3 Implementation plan**: Phase 0(순차 게이트: MONO-430 작성 → MONO-431 ACCEPTED) / Phase 1(**병렬**: scm lane SCM-BE-034 컨트랙트→035 발행 impl ∥ wms lane BE-506 구독컨트랙트→507 consumer impl — 프로젝트 disjoint, 공유파일 0) / Phase 2(재합류: SCM-INT-004 cross-service + federation-hardening live leg).
- **§4 Alternatives**: fire-on-SUBMITTED / 동기 REST / **erp 를 허브로** (erp=E5 도메인로직 미보유, PO·재고 원장 미소유 → 도메인-blind 라우터 nano-hop, Rejected) / 단일창고 하드코딩(D3 위배) / wms→scm polling.
- **§5 Consequences**: positive(루프 종결, 수동 입력 제거, 단일+멀티 1경로) / negative(최초 scm→wms 커플링 edge, 취소 정합성, amendment defer, 3PL 미지원) / neutral(양측 traits 불변).

### 2. Lifecycle

spec ↔ impl ↔ close chore 분리 (root INDEX). 본 task = ADR PROPOSED 작성 1건 (doc-only).

## Out of Scope

- **ACCEPTED 전환 절대 금지** — PROPOSED→ACCEPTED 는 별 task (TASK-MONO-431) + user-explicit intent 시점. 본 task 산출물에 ACCEPTED 선언 0.
- **구현 0** — scm/wms 코드·컨트랙트·Flyway·이벤트 실배선은 Phase 1 tasks (SCM-BE-034/035, BE-506/507). 본 task = ADR 문서뿐.
- ADR-027/022 파일 수정 0 (참조·준수만; 본 ADR 이 027 의 열린 leg 를 닫는다고 서술하되 027 본문 불변).
- E2E / federation-hardening 실배선 0 (Phase 2 SCM-INT-004).

---

# Acceptance Criteria

1. `docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md` 신규, **Status: PROPOSED** (ACCEPTED 문자열 0). §1–§6 구조, D1–D8, §3 병렬 DAG.
2. ADR 번호 = **050** (docs/adr 실측 049 max 확인 후 다음 free 채택).
3. **D3 가 단일+멀티 창고를 한 경로로** 명시 (사용자 요구 인코딩): `destinationWarehouseId` 주소지정, 단일=degenerate, 멀티=주소 라우팅, 코드 분기 0.
4. **최초 scm→wms 커플링 방향**임을 §Why/§5 에 명시 (양방향화, wms inbound-service = 첫 scm-topic consumer).
5. **erp 를 허브로 쓰지 않는 이유**를 §4 Alternatives 에 Rejected 로 기록 (erp E5 도메인로직/원장 미소유).
6. **self-ACCEPT 0**: §6 에 "PROPOSED, user-explicit intent 시점 별 task 가 ACCEPT" 명기 (ADR-027 §6 / `project_adr_accept_gate_exact_intent` 동형).
7. doc-only: git diff = ADR-050 신규 + (lifecycle: 본 task 파일 + root INDEX). 코드/projects/빌드/CI 0.
8. Goal 이 트리거(사용자 2026-07-19 "진행" + 단일/멀티 요구) + 027 열린-leg 출처 인용.

---

# Related Specs

- [ADR-MONO-027](../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) — **열린 leg 의 출처** (§1.3/§D5 loop terminates at scm procurement; 본 ADR 이 그 leg 종결); §6 PROPOSED→ACCEPTED 2단계 패턴 미러 원본
- [ADR-MONO-022](../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) — 전방 fulfillment 루프 (in-repo 양시스템 폐루프 선례)
- [ADR-MONO-004](../../docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md) — outbox/consumer scaffolding (transport)
- [`platform/service-boundaries.md`](../../platform/service-boundaries.md) § Asynchronous (Events) — cross-project allowed
- scm `projects/scm-platform/specs/contracts/events/replenishment-subscriptions.md` / `inventory-visibility-subscriptions.md` — consumer-driven contract 패턴 (역할 반전 미러)

---

# Related Contracts

- 본 task = ADR 문서 작성 (governance). 실 contract 신규(`scm-procurement-events.md` inbound-expected 스키마 / wms `scm-inbound-expected-subscriptions.md`)는 Phase 1 SCM-BE-034 / BE-506 범위.

---

# Target Service / Component

- `docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md` (신규)
- (no production / project / build / CI change)

---

# Edge Cases

1. **번호 재-collision**: impl 직전 `ls docs/adr | grep -oE 'ADR-MONO-[0-9]+'` 재실측 — 050 free 확인 (다른 세션 선점 가능성). 점유 시 다음 free + 본문 근거 갱신.
2. **027 열린-leg 서술 정확성**: 027 §1.3/§D5 실제 문구 확인 후 인용 (027 본문 rewrite 금지, 참조만).
3. **단일/멀티 요구 인코딩 누락 위험**: D3 가 사용자 핵심 요구 — 주소지정(assumption 아님)으로 단일+멀티 1경로임을 명시적 결정으로 박을 것 (리스트 표면서 멈추지 말 것).
4. **erp-허브 재제안 유혹**: 대화에서 이미 기각됨 (erp E5). §4 에 Rejected 로 박아 재제안 차단.

---

# Failure Scenarios

## A. ADR 작성 중 027/022 와 모순 발견

→ 027/022 는 ACCEPTED (binding). 모순 시 ADR-050 을 그들에 종속시켜 해소 (050 은 027 의 열린 leg 를 닫을 뿐, 027 결정 불변경). 본질 모순이면 STOP + 사용자 보고 (green-wash 금지).

## B. self-ACCEPT 유혹 / "바로 ACCEPTED 로" 요청

→ ADR-027 §6 동형으로 PROPOSED 고수. user-explicit intent 명확해도 ACCEPTED 전환은 별 task (TASK-MONO-431). 작성 ↔ 인가 분리.

## C. Phase 1 병렬성 과대주장

→ scm lane 과 wms lane 이 disjoint 파일임을 §3 에서 실증 (공유파일=D7 문서 1줄 cross-ref, 1회). 만약 impl 단계에서 공유파일 발견되면 그 시리즈만 직렬화 (`project_shared_file_task_series_single_worktree_serialize`), 나머지 병렬 유지.

---

# Test Requirements

- impl PR `git diff` = ADR-050 신규 + lifecycle(task+INDEX)만; 코드/projects/빌드/CI 0.
- ADR-050 Status=PROPOSED 단언 (ACCEPTED 문자열 0 grep).
- D3 단일+멀티 1경로 명시 + §4 erp-허브 Rejected + §6 self-ACCEPT 금지 명기 포함.
- markdown lint green; §3 DAG 표 일관.

---

# Definition of Done

- [ ] ADR-MONO-050 신규 (PROPOSED, §1–§6, D1–D8, §3 병렬 DAG)
- [ ] D3 단일+멀티 창고 1경로 인코딩 (사용자 요구)
- [ ] 최초 scm→wms 커플링 명시 + §4 erp-허브 Rejected
- [ ] self-ACCEPT 0 (PROPOSED-only 명기)
- [ ] doc-only diff scope
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.8 / 구현 권장=Opus 4.8 — meta-policy ADR authoring: 커플링 방향 반전·창고 주소지정 모델·3PL 경계·취소 시맨틱 = interpretive judgement. ADR-027 authoring(TASK-MONO-219) 동형, dispatcher 직접 작성).
- **분량**: medium — ADR 1 신규.
- **dependency**:
  - `선행`: 없음 (ADR-027/022 는 이미 ACCEPTED, main 존재).
  - `후속`: **TASK-MONO-431** (ACCEPTED 전환, user intent). 그 후 Phase 1 병렬(SCM-BE-034/035 ∥ BE-506/507) → Phase 2 SCM-INT-004.
- **self-ACCEPT 절대 금지 (재강조)**: PROPOSED 작성까지가 본 task, 인가는 사용자.
