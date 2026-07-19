# Task ID

TASK-MONO-433

# Title

ADR-MONO-051 작성 (PROPOSED) — 마스터 데이터는 연합(federated) 유지, 중앙 MDM 허브 미도입 + 재개 트리거(D5) 명문화

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

사용자 질문 (2026-07-20): "프로젝트에 mdm 있어?" → "도입하는 것과 유지하는 것 어떤 게 더 나아?" → "해줘".

저장소에 `mdm` / "master data management" 문자열은 **0건**이지만, 도메인 소유 마스터 서비스는 실재한다(wms `master-service`, erp `masterdata-service`, fan `artist-service`). 즉 **연합형 MDM 이 이미 동작 중인데 어디에도 선언돼 있지 않다.** 결론이 "아무것도 바꾸지 않음"이므로 산출물이 남지 않고, 같은 질문이 재조사를 유발한다. 그 결론과 **재개 조건**을 ADR 로 못박는다.

## 왜 ADR 인가 (채팅 답변으로 끝내면 안 되는 이유)

세 가지는 *기록*되어야 하고 결론만으론 남지 않는다: (a) 연합 형태가 **의도**라는 것 — 안 적으면 다음 독자는 누락으로 읽는다; (b) **트리거(D5)** — 이 결정이 뒤집히는 구체적·검사가능 조건; (c) 허브가 단지 불필요한 게 아니라 이 저장소의 **배포 전략과 구조적으로 충돌**한다는 것(`sync-portfolio.sh` standalone 추출 + 컨트랙트별 "no hard dependency" 조항). **재개 조건 없는 "아니오"는 누락과 구분되지 않는다.**

## 실측 근거 (본 task 착수 시 AC-0 로 재측정 대상)

- SKU 는 이미 wms `master-service` 단일 정본 + `skuCode` **문자열 코드**로 경계를 넘고, 소비자가 로컬 파생 프로젝션을 든다(ecommerce `WmsSkuSnapshot`, scm `inventory_nodes`). 생산자는 매핑 테이블을 갖지 않는다("No wms↔ecommerce id map is stored").
- [ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 가 이 규칙을 이미 자기 leg 에 한해 결정으로 승격했다 — 본 ADR 은 그것을 저장소 전역으로 일반화.
- 진짜 갈라진 유일 실체 = **거래처/공급자 3벌**(erp `BusinessPartner` / scm `suppliers` / wms `Partner`). **그러나 런타임 조인이 0** — scm `purchase_orders.supplier_id` cross-service FK 미선언, erp v1 "no real integration", `erp.masterdata.businesspartner.changed.v1` **구독자 0**, scm→wms `supplierId` 는 `sku_supplier_map` stand-in. ⇒ 중복은 있으나 아직 아프지 않다.

---

# Scope

## In Scope (impl PR 가 수행)

### 1. `docs/adr/ADR-MONO-051-master-data-stays-federated.md` 신규 작성 (Status: PROPOSED)

ADR-050 구조 미러 (§1 Context / §2 Decision D1–D6 / §3 Implementation plan / §4 Alternatives / §5 Consequences / §6 Verification / §7 Outstanding follow-ups / §8 Status history). 핵심 결정:

- **§Status** = `PROPOSED`, history = `PROPOSED 2026-07-20 (TASK-MONO-433)`. **ACCEPTED 줄 작성 금지.**
- **D1 — 연합 유지**: 중앙 MDM 허브·골든레코드 저장소·match/merge 엔진 미도입. 이미 하고 있는 것(정본 1 + 이벤트 전파)이 **곧** MDM 전략임을 명명.
- **D2 — 경계를 넘는 식별자 = 비즈니스 CODE (저장소 전역)**: ADR-050 §7 D9 를 모든 cross-project seam 으로 일반화. 내부 UUID PK 는 경계를 넘지 않고, 소비자가 consume 시점에 code→자기 PK 로 해석.
- **D3 — 복제는 단방향·소비자 소유**: 소비자가 자기 프로젝션을 들고, 생산자는 매핑 테이블도 소비자 지식도 갖지 않는다. 프로젝션은 명시적 eventual-consistency read model 이지 제2 정본이 아니다.
- **D4 — 공급자 3벌은 현시점 결함 아님(as-is 수용)**: 각 도메인 고유 속성(erp 지불조건+유효기간 / scm 계약만료 / wms 사업자번호+수령연락처)은 정당한 로컬 관심사. **조건부 결정이며 D5 에서 만료.**
- **D5 — 트리거(재개 조건 + 그때의 답)**: "단일 공급자 정체성이 두 프로젝트에서 동시에 같은 것을 의미해야 하는 순간". 구체 진입점 3개(scm PO→wms 입고 대사 / finance 정산 매칭 / `businesspartner.changed.v1` 최초 구독자). 그때도 **답은 허브가 아니라** scm v2 `supplier-service` + 기존 미구독 토픽 구독 + wms `Partner` 를 로컬 프로젝션 유지 = D1–D3 를 한 실체에 더 적용.
- **D6 — standalone 추출 제약이 본 결정에 구속력**: 5개 프로젝트가 식별자 해석을 위해 호출해야 하는 컴포넌트를 제안하려면 `sync-portfolio.sh` 추출과 "no hard dependency" degradation 조항이 어떻게 살아남는지 먼저 실증해야 한다. 미실증 = 추가 논증 없이 기각 사유.
- **§4 Alternatives**: A1 중앙 MDM 허브(6번째 플랫폼) / A2 erp 를 허브로 승격(ADR-050 §4 가 이미 같은 이유로 기각 — E5, 원장 미소유, cross-project seam 0) / A3 `libs/` 공유 마스터 스키마(HARDSTOP-03 — `partnerType: SUPPLIER`·`businessNumber` 는 project-specific) / A4 기록 없이 대화로만 답(본 ADR 이 거부하는 그 선택지).
- **§6 Verification**: 본문 주장 7건을 재실행 가능한 체크로 표기(문자열 검색 0건, 구독자 0, FK 미선언 등). "선행 숫자는 출처가 아니라 가설" 규율 명기.
- **§7**: 후속 스케줄 **없음**(D5 는 백로그 아이템이 아니라 트리거). ACCEPT 시 D2 를 `platform/service-boundaries.md` 로 승격 후보로만 기록.

### 2. `docs/adr/INDEX.md` 에 051 행 추가

기존 표 포맷(링크 · 1줄 요약 · Status · Date) 준수, `PROPOSED` / `2026-07-20`.

### 3. Lifecycle

impl PR 이 본 task 를 `ready/` → `review/` 이동 + Status 갱신 + root `tasks/INDEX.md` review 목록 반영.

## Out of Scope

- **ACCEPTED 전환 절대 금지** — PROPOSED→ACCEPTED 는 user-explicit ADR-naming intent + 별 task. 본 task 산출물에 ACCEPTED 선언 0. 대화의 "해줘"는 게이트를 열지 않는다.
- **구현·스키마 변경 0** — 공급자 스키마 통합, `businesspartner.changed.v1` 구독자 추가, 공유 partner 라이브러리, `libs/` 마스터 모듈 전부 **의도적 미착수**.
- ADR-050/027/022 본문 수정 0 (참조만).
- `platform/service-boundaries.md` 로의 D2 승격 0 (ACCEPT 이후 별건).
- projects/** · 코드 · 빌드 · CI 변경 0.

---

# Acceptance Criteria

0. **AC-0 재측정 (착수 시 필수)** — Goal 의 실측 근거를 코드/스펙에서 재확인한다. 특히 (a) `mdm` 문자열 0건, (b) `businesspartner.changed.v1` 구독자 0, (c) scm `purchase_orders.supplier_id` FK 미선언. **하나라도 어긋나면 D4/D5 를 그 실측에 맞춰 다시 쓴다 — 코드가 이긴다.**
1. `docs/adr/ADR-MONO-051-master-data-stays-federated.md` 신규, **Status: PROPOSED** (`ACCEPTED` 문자열 0). §1–§8 구조, D1–D6.
2. ADR 번호 = **051** (impl 직전 `ls docs/adr` 재실측으로 free 확인).
3. **D5 트리거가 "조건 + 그때의 답" 양쪽을 담을 것** — 조건만 적고 답을 비우면 다음 사람이 허브를 재제안한다.
4. **D2 가 ADR-050 §7 D9 의 일반화임을 명시** (출처 인용, 재발명 아님).
5. **§4 에 A2(erp 허브) 기각**이 ADR-050 §4 선례 인용과 함께 기록 — 재제안 차단.
6. **§6 Verification 표**가 본문 주장별 재실행 체크를 제공.
7. **self-ACCEPT 0**: §8 에 ACCEPT 게이트 명기 (`platform/architecture-decision-rule.md` § The ACCEPTED Gate 인용, bare "진행" 불가).
8. doc-only: git diff = ADR-051 신규 + `docs/adr/INDEX.md` 1행 + lifecycle(task 파일 + root INDEX). 코드/projects/빌드/CI 0.

---

# Related Specs

- [ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) — §7 D9 cross-service 식별자=코드 (본 ADR 이 전역 일반화); §4 erp-허브 기각 선례
- [ADR-MONO-027](../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) — `skuCode` 조인 키
- [ADR-MONO-022](../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) — `lines[].skuCode` 로컬 해석, id map 미저장
- [`TEMPLATE.md`](../../TEMPLATE.md) § Discovery → Distribution — standalone 추출 제약 (D6 의 근거)
- [`platform/service-boundaries.md`](../../platform/service-boundaries.md) § Asynchronous (Events) — cross-project allowed
- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) — A3 기각 근거 (HARDSTOP-03)
- [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § The ACCEPTED Gate

---

# Related Contracts

- 본 task = ADR 문서 작성 (governance). **컨트랙트 변경 0** — 인용만: wms `master-events.md`, ecommerce `wms-inventory-subscriptions.md`, wms `ecommerce-fulfillment-subscriptions.md`, scm `replenishment-subscriptions.md` / `scm-procurement-events.md`, erp `erp-masterdata-events.md`, erp `masterdata-api.md`.

---

# Target Service / Component

- `docs/adr/ADR-MONO-051-master-data-stays-federated.md` (신규)
- `docs/adr/INDEX.md` (1행)
- (no production / project / build / CI change)

---

# Edge Cases

1. **번호 재-collision**: impl 직전 `ls docs/adr | grep -oE 'ADR-MONO-[0-9]+' | sort -n | tail -1` 재실측 — 051 free 확인(동시 세션 선점 가능). 점유 시 다음 free + 본문·INDEX 갱신.
2. **AC-0 이 D4 를 뒤집는 경우**: 착수 시점에 `businesspartner.changed.v1` 구독자가 생겼거나 supplier 조인이 이미 존재하면 **D4 "결함 아님" 이 거짓이 된다** → D5 트리거가 이미 발화한 상태이므로 ADR 은 그 사실을 반영해 재작성(허브 도입이 아니라 supplier-service 경로 스케줄링). **선행 조사 숫자를 물려받지 말 것.**
3. **"중복이니까 통합" 반사**: 중복 자체는 병이 아니다 — **갈라졌는지 + 조인되는지**를 물어야 한다. D4 는 갈라짐은 인정하되 조인 부재를 근거로 수용한다. 이 구분이 흐려지면 ADR 이 자기 논지를 잃는다.
4. **D5 를 백로그 아이템으로 오해**: §7 에 "트리거이지 스케줄된 후속이 아님"을 명기. ready/ 에 파생 task 를 만들지 않는다.
5. **인용 정확성**: §1.3 표의 필드는 실제 스펙 파일에서 읽은 것만 기재(추측 필드 금지). 파일:라인 앵커 유효성 확인.

---

# Failure Scenarios

## A. 작성 중 ADR-050 §7 D9 와 문구 충돌

→ ADR-050 은 ACCEPTED(binding). 본 ADR 은 D9 를 **일반화**할 뿐 재정의하지 않는다. 충돌하면 D9 문구를 따르고 본 ADR 을 종속시킨다. 본질 충돌이면 STOP + 사용자 보고.

## B. self-ACCEPT 유혹 / "바로 ACCEPTED 로"

→ PROPOSED 고수. 사용자의 "해줘"는 **작성** 승인이지 ACCEPT 가 아니다 (`project_adr_accept_gate_exact_intent`, ADR-050 §6 동형). 작성 ↔ 인가 분리.

## C. 범위 확장 유혹 (supplier 통합까지 해버리기)

→ Out of Scope 위반. D5 가 아직 발화하지 않았으므로 통합은 **없는 병에 대한 수술**이다. 본 task 산출물은 문서 2개(ADR + INDEX 행)뿐.

## D. ADR 이 "아무것도 안 함"이라 가치 없다는 판단으로 축소 작성

→ 본 ADR 의 가치는 결론이 아니라 **D5 트리거 + D6 구속 조항 + §6 검증표**다. 이것들이 빠지면 A4(기록 없이 대화로만)와 동치가 된다.

---

# Test Requirements

- impl PR `git diff` = ADR-051 신규 + `docs/adr/INDEX.md` 1행 + lifecycle(task+root INDEX)만; 코드/projects/빌드/CI 0.
- ADR-051 `Status: PROPOSED` 단언 — 파일 내 `ACCEPTED` 문자열은 §8 게이트 설명 문맥 외 선언 0.
- D1–D6 전부 존재, D5 가 조건+답 양쪽 보유, §4 에 A1–A4 기각 사유, §6 검증표 존재.
- 본문 파일:라인 앵커가 실제 파일에 존재.
- markdown lint green.

---

# Definition of Done

- [ ] AC-0 재측정 완료 (실측이 D4/D5 를 뒤집지 않음 확인)
- [ ] ADR-MONO-051 신규 (PROPOSED, §1–§8, D1–D6)
- [ ] D5 트리거 = 조건 + 그때의 답 양쪽
- [ ] D2 = ADR-050 §7 D9 일반화 명시 / §4 A2 erp-허브 기각
- [ ] §6 Verification 재실행 체크표
- [ ] self-ACCEPT 0
- [ ] doc-only diff scope
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.8 / 구현 권장=Opus 4.8 — meta-policy ADR authoring: 연합↔중앙 판단, 트리거 조건 설계, 배포 전략 구속 조항은 interpretive judgement. TASK-MONO-430(ADR-050 authoring) 동형, dispatcher 직접 작성).
- **분량**: small — ADR 1 신규 + INDEX 1행. 조사는 선행 완료(본 task Goal 에 실측 인용).
- **dependency**:
  - `선행`: 없음 (ADR-050 은 이미 ACCEPTED, main 존재).
  - `후속`: ACCEPT 전환 task — **user-explicit intent 시점에만** 스폰. ACCEPT 후 선택적으로 D2 의 `platform/service-boundaries.md` 승격 task.
- **이 ADR 이 방어하는 실패 모드**: 결론이 "변경 없음"인 결정은 산출물을 남기지 않아 재조사를 부르고, 그 사이 3벌 스키마는 계속 갈라진다. D5 가 그 표류를 검사 가능한 조건으로 바꾼다.
