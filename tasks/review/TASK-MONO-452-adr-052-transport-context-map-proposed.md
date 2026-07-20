# Task ID

TASK-MONO-452

# Title

ADR-MONO-052 작성 (PROPOSED) — 운송(transport)은 scm 컨텍스트, wms 는 도크까지 + 미소유 5건 소유권 맵 + 재개 트리거(D8)

# Status

review

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

사용자 질문 연쇄 (2026-07-20): "멀티창고고 서로 이동도 가능하고 외부로부터 들어오는것도 가능해?" → "창고 간 이동(출고→입고 사가) 관리는 누가해?" → "erp나 scm이 아니라 wms에서 관리하는거야?" → "tms 옮기는거랑 관련있어?" → "지금 3pl을 연동하는건 wms야?" → "외부 운송회사에 물건 보낼때 무슨 플랫폼/서비스에서 해?" → "내 프로젝트에서 플랫폼 급으로 tms를 만들고 거기서 외부 tms를 연동하는건?" → "c 초안부터 시작".

**미소유 5건이 전부 운송 축에서 나왔다.** ① 재배치 결정 ② A→B 운송·in-transit ③ 3PL 집행 ④ TMS 벤더 연동 ⑤ `logistics-service` 부트스트랩. 이 다섯을 한 번에 배치하는 컨텍스트 맵 ADR 을 PROPOSED 로 작성한다.

## 왜 ADR 인가 (채팅 답변으로 끝내면 안 되는 이유)

같은 질문("창고 간 이동은 누가 소유?")에 **한 대화 안에서 답이 세 번 바뀌었다** — *wms 소유* → *실행만 wms* → *운송 구간도 wms 아님* — 매번 문서를 하나 더 읽어서. 이건 부주의가 아니라 **경계가 문서상 미결정**이라는 신호다. 현재 이 설계 전체를 지탱하는 문장은 [ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md):105 의 **종속절 하나**("carrier/3PL execution lives in scm's v2-deferred `logistics-service`")뿐이고, 그 문장은 **배치가 아니라 거부를 정당화하려고** 쓰였다. 반면 **9개 문서**가 존재하지 않는 서비스를 가리킨다. 다섯 스펙을 교차 독해해야 복원되는 경계는 건드릴 때마다 재논쟁된다.

## 실측 근거 (착수 시 AC-0 로 재측정 대상 — 본 task 작성 시점 2026-07-20 실측)

- **창고 간 이동은 코드에서 거부**: `TransferStockService` 가 `"Cross-warehouse transfers are not supported in v1"` throw, `stock_transfer` 는 `warehouse_id` **단수** — 두 시설을 표현할 컬럼이 없음.
- **소유자를 지정한 유일 문장**: [inventory-service/architecture.md:589-590](../../projects/wms-platform/specs/services/inventory-service/architecture.md#L589-L590) — "outbound-from-A + inbound-to-B saga in `outbound-service` orchestration". *Extensibility Notes*(v2 안내용) 안에 있고, **wms 가 모델링하지 않는 중간 구간까지 wms 에 배정**한다.
- **"orchestration" 은 명령이 아니라 상태보관**: 정본 사가 스펙 heading 이 [outbound-saga.md:62](../../projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md#L62) **"Choreographed (not Orchestrated)"**. `outbound-service` 에 내부 서비스행 HTTP 클라이언트 0개(유일 아웃바운드=TMS 벤더).
- **wms 는 3PL 을 이미 거부**: `CreateScmInboundExpectationService` 화이트리스트 게이트 → `WMS_WAREHOUSE` 외 전부 DLT. IT `thirdPartyNodeTypeCreatesNoAsn`.
- **scm 이 룰 레이어에서 이미 선점**: [rules/domains/scm.md:24](../../rules/domains/scm.md#L24) Logistics Coordination = "carrier 연동, ETA/추적, **출발지·도착지 라우팅**". `NodeType.IN_TRANSIT` 존재하나 **팩토리 메서드 없음**(생성 불가 enum 값).
- **wms 에 in-transit 없음**: `projects/wms-platform/**` 에 `IN_TRANSIT` 0건. `Inventory` 는 status enum 자체가 없고 수량 버킷뿐.
- **`logistics-service` 부재**: `projects/scm-platform/apps/` 4개(demand-planning, gateway, inventory-visibility, procurement) — 없음. `specs/services/logistics-service/` 도 없음. 그런데 **9개 파일이 참조**.
- **TMS 벤더는 placeholder**: `base-url` 기본값 `https://tms.example.com/api/v1`. 계약서 자신이 *indicative*.
- **창고는 1개만 시드**: `V99__seed_dev_warehouse.sql` → `WH01` 뿐. 멀티창고는 구조상 지원되나 운영상 미사용.
- **포트폴리오는 7도메인에서 완결 선언**: [README.md:46](../../README.md#L46) "erp is the portfolio's **final** domain … no further bootstrap ADR is planned", [docs/project-overview.md:190](../../docs/project-overview.md#L190) "추가 신규 도메인 부트스트랩 ADR 은 예정 없음". `tms` 는 taxonomy 41개에 없고 [ADR-MONO-002](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) §D4 후보(scm/erp/mes)에도 없었음. 단 **`logistics` 도메인은 taxonomy 에 존재하고 미claim**.
- **신설 비용 실측**: 최소 부트스트랩(finance) impl PR = **39 files / 1646+ lines** ([TASK-MONO-119:187](../../tasks/done/TASK-MONO-119-erp-platform-bootstrap-artifact.md#L187)) + 2단계 ADR.

---

# Scope

## In Scope (impl PR 가 수행)

### 1. `docs/adr/ADR-MONO-052-transport-context-map.md` 신규 작성 (Status: PROPOSED)

ADR-050/051 구조 미러 (§1 Context / §2 Decision / §3 Implementation plan / §4 Alternatives / §5 Consequences / §6 Verification / §7 Outstanding follow-ups / §8 Status history). 핵심 결정:

- **§Status** = `PROPOSED`, history = `PROPOSED 2026-07-20 (TASK-MONO-452)`. **ACCEPTED 줄 작성 금지.**
- **D1 — 운송은 별개 bounded context 이고 scm 것**: wms 는 사방 벽 안 + 도크 인계 순간까지. 시설 **사이**(carrier 선택, in-transit 보관, ETA, 추적, 3PL 집행)는 scm 의 기선언 Logistics Coordination. 분할선 = **보관 책임(custody)** — 차량 위 재고는 어느 창고에도 없고, wms 모델(창고 스코프 수량 버킷, status enum 없음)이 담을 수 없다.
- **D2 — 5건 소유권 표**: ① 재배치 결정 → scm `demand-planning-service`(ADR-027 의 거울 결정을 이미 소유) / ② 운송·in-transit → scm `logistics-service` / ③ 3PL → scm `logistics-service`(ADR-050 §D4 를 거부근거에서 **배치**로 승격) / ④ TMS 벤더 → scm `logistics-service`(**목표**, 잠정은 wms — D7) / ⑤ 부트스트랩 → scm, **서비스 레벨**.
- **D3 — 창고 간 이동 = outbound-from-A + inbound-to-B, 단일 트랜잭션 아님**: 589-590 의 **구조는 확인, 소유는 정정**. 두 leg 은 wms 기존 흐름, 중간은 scm, **셋을 다 가진 서비스는 없다.** 기존 가드는 v1 한계가 아니라 **경계의 집행**.
- **D4 — in-transit 은 어느 창고도 아닌 운송 컨텍스트 소유**: wms `inventory-service` 는 `IN_TRANSIT` 버킷/상태를 **갖지 않는다**. 근거: B 를 도착 전 크레딧하면 수령 흐름이 되돌려야 할 거짓이 되고, 안 하면 A 출고가 미대사로 남는다 — 둘 다 wms 가 권위인 원장을 오염.
- **D5 — wms↔운송 seam 은 fact 이벤트, 동기 호출 금지**: 기존 `outbound.shipping.confirmed`(outbox) 를 `logistics-service` 가 구독. **새 이벤트 계약 불필요.** ADR-027:67 / ADR-050:79 가 **양방향으로 이미 기각한 원칙의 3번째 적용**.
- **D6 — `tms-platform` 미신설, 서비스 레벨**: ① 7도메인 완결 선언 자체가 산출물(스코프 규율 기록) ② 추가 증명 0(외부 벤더 통합·서킷·멱등·in-transit·사가 참여는 서비스 1개로 전부 시연) ③ **9개 문서가 이미 `logistics-service` 를 가리킴** — 플랫폼 답은 그걸 전부 무효화, 서비스 답은 이행. taxonomy 의 `logistics` 미claim 은 **절차적 경로가 있음을 명기하되 위 근거로 사절**(절차 이유로 거부하는 게 아님을 분명히).
- **D7 — TMS 어댑터는 수신자 생길 때까지 wms 잔류**: 목적지만 지정하고 **이동은 스케줄하지 않는다**. 지금 옮기면 `SHIPPED_NOT_NOTIFIED` 복구 경로와 운영자 retry 엔드포인트가 대화 상대를 잃는다. 이동 시 모양은 D5 가 이미 결정. 잠정이 싼 이유 2개 = 벤더 부재(placeholder) + 이미 포트 뒤(`ShipmentNotificationPort`).
- **D8 — 트리거(부트스트랩 재개 조건)**: ① 두 번째 창고 실운영(현 시드 `WH01` 뿐 ⇒ 창고 간 이동은 지금 **대상이 없다**) ② 실제 TMS 벤더 등장(D7 잠정이 공짜가 아니게 됨) ③ 거부가 아니라 **처리해야 할** 3PL 목적지 ④ 실행할 의사가 있는 재배치 결정.
- **§4 Alternatives**: A1 `tms-platform` 8번째 플랫폼 / A2 wms 가 운송 end-to-end(모델이 표현 불가 — `Shipment` origin/destination 없음, TMS 계약이 주소 필드 **금지**) / A3 wms→logistics 동기 호출(027 §D1 거울, D7 의 자연스러운 오이행) / A4 TMS 영구 wms 잔류(**잠정으론 채택**, 종착으론 기각) / A5 지금 부트스트랩(목적지는 맞고 시점이 이름 — D8 4조건 전부 미충족) / A6 기록 없이 대화로만(본 ADR 이 거부하는 그 선택지).
- **§6 Verification**: 본문 주장 12건 재실행 체크표. "선행 숫자는 출처가 아니라 가설" 규율 명기.
- **§7**: 후속 스케줄 **없음**. ACCEPT 시 승격 후보로 D1 custody 문장 → `rules/domains/wms.md`(현 Transfer 정의가 "같은 창고 내 **또는 창고 간**"으로 **구현보다·D1 보다 넓다**)만 기록.

### 2. `docs/adr/INDEX.md` 에 052 행 추가

기존 표 포맷(링크 · 1줄 요약 · Status · Date) 준수, `PROPOSED` / `2026-07-20`.

### 3. Lifecycle

impl PR 이 본 task 를 `ready/` → `review/` 이동 + Status 갱신 + root `tasks/INDEX.md` review 목록 반영.

## Out of Scope

- **ACCEPTED 전환 절대 금지** — user-explicit ADR-naming intent + 별 task. 대화의 "c 초안부터 시작"은 **작성** 승인이지 ACCEPT 가 아니다.
- **구현 0** — `logistics-service` 부트스트랩, TMS 어댑터 이전, wms 에 `IN_TRANSIT` 추가, 창고간 가드 제거, 3PL 어댑터, 재배치 결정 서비스 전부 **의도적 미착수**.
- **기존 문서 본문 수정 0** — ADR-050/027/022, `inventory-service/architecture.md:589-590`, `rules/domains/scm.md`, `rules/domains/wms.md` 전부 인용만. (589-590 정정은 ACCEPT 이후 별건.)
- `rules/domains/wms.md` 로의 D1 승격 0 (ACCEPT 이후 별건).
- projects/** · 코드 · 빌드 · CI 변경 0.

---

# Acceptance Criteria

0. **AC-0 재측정 (착수 시 필수)** — Goal 실측 근거를 코드/스펙에서 재확인. 특히 (a) `logistics-service` 디렉터리 부재, (b) wms `IN_TRANSIT` 0건, (c) 창고 시드 `WH01` 단일, (d) TMS `base-url` placeholder, (e) `logistics-service` 참조 문서 수(9는 **가설**), (f) taxonomy 에 `tms` 부재 / `logistics` 존재. **하나라도 어긋나면 해당 D 를 실측에 맞춰 다시 쓴다 — 코드가 이긴다.** 특히 (a)/(c) 가 어긋나면 **D8 트리거가 이미 발화**한 것이므로 ADR 은 그 사실을 반영해 재작성.
1. `docs/adr/ADR-MONO-052-transport-context-map.md` 신규, **Status: PROPOSED** (선언으로서의 `ACCEPTED` 문자열 0 — §8 게이트 설명 문맥은 예외). §1–§8 구조, D1–D8.
2. ADR 번호 = **052** (impl 직전 `ls docs/adr` 재실측으로 free 확인).
3. **D2 가 5건 전부를 표로 배치**하고 각 행에 **근거**를 달 것 — 근거 없는 배치는 다음 사람이 뒤집는다.
4. **D8 트리거가 "조건"을 검사 가능한 형태로** 담을 것(시드 창고 수, 벤더 URL, 3PL 목적지, 실행 의사 있는 재배치 결정).
5. **D5 가 ADR-027:67 / ADR-050:79 를 양방향 선례로 인용** — 재발명이 아니라 3번째 적용임을 명시.
6. **D6 이 "포트폴리오 완결 선언"을 근거로 삼되, 그것이 사용자의 선행 결정임을 명기**하고 재개 시 supersede 대상(ADR-002 §D4, ADR-016)을 지정. **taxonomy `logistics` 미claim = 절차적 경로 존재**를 숨기지 말 것(절차가 아니라 판단으로 사절).
7. **D7 이 "목적지 지정 ≠ 이동 스케줄"을 분명히** 하고, §5 Negative 에 "D2 가 소유 아니라고 한 프로젝트에 어댑터가 무기한 잔류"를 **자진 기재**.
8. **§4 에 A1–A6 기각 사유** 기록. 특히 A1(플랫폼)은 **도메인 직관이 타당함을 인정한 뒤** 기각(재제안 차단은 근거로 하지, 일축으로 하지 않는다).
9. **§6 Verification 표**가 본문 주장별 재실행 체크 제공.
10. **self-ACCEPT 0**: §8 에 ACCEPT 게이트 명기 (`platform/architecture-decision-rule.md` § The ACCEPTED Gate 인용, bare "진행" 불가). **D6 이 사용자 선행 결정을 건드리므로 "적어둔 것이 곧 재개는 아님"을 명기.**
11. doc-only: git diff = ADR-052 신규 + `docs/adr/INDEX.md` 1행 + lifecycle(task 파일 + root INDEX). 코드/projects/빌드/CI 0.

---

# Related Specs

- [ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) — §D4(현재 이 설계를 지탱하는 유일 문장), §D1(동기 REST 기각)
- [ADR-MONO-027](../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) — §D1 fact-event seam / §D2 재주문 결정(① 배치 근거)
- [ADR-MONO-022](../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) — 비-wms 도메인이 wms 물리작업을 트리거한 선례
- [ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) — 동형(미선언 구조 명명 + 트리거)
- [ADR-MONO-002](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) §D4 / [ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) — 완결 선언(D6 이 재개하지 않음)
- [ADR-MONO-003a](../../docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md) §D2.1 — 신규 도메인 스켈레톤 = 별도 user-explicit 승인
- [`rules/domains/scm.md`](../../rules/domains/scm.md) §Logistics Coordination / [`rules/domains/wms.md`](../../rules/domains/wms.md) §Transfer 정의
- [`rules/taxonomy.md`](../../rules/taxonomy.md) §Logistics & Mobility — `logistics` 존재, `tms` 부재
- wms `inventory-service/architecture.md` §Extensibility Notes / `outbound-service/sagas/outbound-saga.md` §1.4
- [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § The ACCEPTED Gate

---

# Related Contracts

- 본 task = ADR 문서 작성 (governance). **컨트랙트 변경 0** — 인용만: wms `tms-shipment-api.md`, wms `scm-inbound-expected-subscriptions.md`, scm `scm-procurement-events.md`, scm `gateway-public-routes.md`(`/api/v1/logistics/**` deferred).

---

# Target Service / Component

- `docs/adr/ADR-MONO-052-transport-context-map.md` (신규)
- `docs/adr/INDEX.md` (1행)
- (no production / project / build / CI change)

---

# Edge Cases

1. **번호 재-collision**: impl 직전 `ls docs/adr` 재실측 — 052 free 확인(동시 세션 선점 가능). 점유 시 다음 free + 본문·INDEX 갱신.
2. **AC-0 이 D8 을 뒤집는 경우**: 착수 시점에 두 번째 창고가 시드됐거나 `logistics-service` 가 생겼으면 **트리거가 이미 발화** — ADR 은 "미래 조건"이 아니라 "현재 상태"로 재작성하고, 부트스트랩을 스케줄할지 사용자에게 묻는다. **선행 조사 숫자를 물려받지 말 것.**
3. **"9개 문서" 를 세지 않고 인용**: 참조 수는 **가설**이다. 재측정해서 실제 수로 쓰거나 수를 빼라. 세는 식도 술어다.
4. **D1 을 "wms 축소"로 오독**: D1 은 wms 에서 기능을 빼앗지 않는다 — wms 가 애초에 갖지 않은 것을 다른 곳에 배치할 뿐이다. 문안이 이 구분을 잃으면 wms 스펙 수정 압력으로 잘못 읽힌다.
5. **D3 이 589-590 을 "틀렸다"고 쓰는 유혹**: 그 문장은 **구조가 맞고 소유가 넓다**. "정정"이 아니라 "구조 확인 + 소유 정정"으로 쓴다. ACCEPT 전에는 그 파일을 수정하지 않는다.
6. **인용 정확성**: 파일:라인 앵커는 실제로 읽은 것만. `TransferStockService` throw 는 메서드 `resolveSameWarehouse` 내부이며 문자열 리터럴 줄과 `if` 줄이 다르다 — 블록으로 인용할 것.

---

# Failure Scenarios

## A. D6 을 "사용자 아이디어 기각"으로 쓰게 됨

→ 사용자가 제안한 `tms-platform` 은 **도메인적으로 타당**하다(현실 TMS 는 WMS 의 동급). §4 A1 은 그 타당성을 먼저 인정하고, 기각 근거를 **포트폴리오 스코프 + 증명 중복 + 9문서 정합**으로 한정한다. 일축하면 ADR 이 논거 대신 권위로 읽힌다.

## B. self-ACCEPT 유혹 / "초안 시작했으니 확정"

→ PROPOSED 고수. "c 초안부터 시작"은 **작성** 승인 (`project_adr_accept_gate_exact_intent`). 특히 D6 은 사용자의 2026-05-07 완결 결정을 **인용**하는 것이지 대신 재확인하는 게 아니다.

## C. 범위 확장 (부트스트랩까지 해버리기 / 589-590 수정)

→ Out of Scope 위반. D8 미발화 상태의 부트스트랩은 **트래픽 없는 서비스 + 추측 인터페이스 4개**. 산출물은 문서 2개(ADR + INDEX 행)뿐.

## D. "결론이 대부분 as-is 유지라 가치 없다"는 판단으로 축소 작성

→ 가치는 결론이 아니라 **D2 배치표 + D8 트리거 + §6 검증표**다. 이것들이 빠지면 A6(기록 없이 대화)와 동치가 되고, 같은 질문이 또 세 번 다른 답을 얻는다.

## E. ① 배치를 과잉확신

→ ①(재배치 결정 → `demand-planning-service`)은 **기존 스펙이 아니라 ADR-027 유비**로 배정된 유일 항목이다. §5 Negative 에 "여기서 가장 약한 배치"로 **자진 기재**할 것. 숨기면 나중에 뒤집힐 때 ADR 전체 신뢰가 깎인다.

---

# Test Requirements

- impl PR `git diff` = ADR-052 신규 + `docs/adr/INDEX.md` 1행 + lifecycle(task+root INDEX)만; 코드/projects/빌드/CI 0.
- ADR-052 `Status: PROPOSED` 단언 — 선언으로서의 `ACCEPTED` 0.
- D1–D8 전부 존재, D2 가 5행 표 + 행별 근거, D8 이 검사 가능 조건 4개, §4 에 A1–A6, §6 검증표 존재.
- 본문 파일:라인 앵커가 실제 파일에 존재(무작위 3개 이상 스팟 검증).
- markdown lint green.

---

# Definition of Done

- [ ] AC-0 재측정 완료 (실측이 D2/D8 을 뒤집지 않음 확인)
- [ ] ADR-MONO-052 신규 (PROPOSED, §1–§8, D1–D8)
- [ ] D2 = 5건 배치표 + 행별 근거
- [ ] D8 = 검사 가능 트리거 4개
- [ ] D5 가 027/050 양방향 선례 인용
- [ ] D6 이 완결 선언을 사용자 선행 결정으로 명기 + supersede 대상 지정 + `logistics` 경로 존재 공개
- [ ] §5 Negative 에 D7 잔류 + ① 약한 배치 자진 기재
- [ ] §6 Verification 재실행 체크표
- [ ] self-ACCEPT 0
- [ ] doc-only diff scope
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.8 / 구현 권장=Opus 4.8 — 컨텍스트 맵 ADR: 도메인 경계 판단, 5건 배치 근거 설계, 트리거 조건 설계, 사용자 선행 결정과의 관계 서술은 전부 interpretive judgement. TASK-MONO-430/433 동형, dispatcher 직접 작성).
- **분량**: small — ADR 1 신규 + INDEX 1행. 조사는 선행 완료(본 task Goal 에 실측 인용).
- **dependency**:
  - `선행`: 없음 (ADR-050/027/022/051 전부 ACCEPTED, main 존재).
  - `후속`: ACCEPT 전환 task — **user-explicit intent 시점에만** 스폰. ACCEPT 후 선택적으로 (a) D1 custody 문장의 `rules/domains/wms.md` 승격, (b) `inventory-service/architecture.md:589-590` 소유 정정.
- **이 ADR 이 방어하는 실패 모드**: 경계가 종속절 하나에만 적히면 같은 질문이 매번 다른 답을 얻는다(이번 대화에서 3회 실측). 그 사이 9개 문서는 없는 서비스를 계속 가리키고, wms 에는 소유 아닌 어댑터가 쌓인다. D8 이 그 표류를 검사 가능한 조건으로 바꾼다.
