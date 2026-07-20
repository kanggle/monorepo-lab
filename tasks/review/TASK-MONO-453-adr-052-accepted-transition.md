# Task ID

TASK-MONO-453

# Title

ADR-MONO-052 PROPOSED → ACCEPTED 전환 — 운송(transport)은 scm 컨텍스트, wms 는 도크까지

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

[ADR-MONO-052](../../docs/adr/ADR-MONO-052-transport-context-map.md) 가 `PROPOSED` 로 랜딩됐다(TASK-MONO-452, PR #2785 squash `e77744a23`). 사용자가 **정확형 intent** 로 승인했다:

> **`ADR-MONO-052 ACCEPTED`** (2026-07-20)

[`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md):41 이 요구하는 *"an explicit human decision that **names the ADR**"* 를 충족한다. 본 task 는 그 전환을 기록한다.

## 게이트가 실제로 작동한 경위 (기록)

**직전 턴에 사용자가 맨 `ACCEPT` 를 보냈고, 그것으로는 넘기지 않았다.** 규정 :43-46 이 *"A bare 진행 / proceed / go ahead / OK does NOT accept an ADR, **even when it replies directly to the message that proposed it, and even when the intent seems obvious from context**"* 로 **문맥상 명백함**을 명시적으로 배제하기 때문이다. 에이전트가 "가리키는 ADR 이 하나뿐이니 명백하다"고 추론하려던 지점을 규정이 미리 막았다. 정확형이 도착한 뒤에만 전환한다 — 게이트의 목적은 결정의 **귀속가능성(attributability)** 이다(:53-56).

**self-ACCEPT 아님**: 작성(TASK-MONO-452)과 인가(본 task)는 다른 행위자의 다른 행위다.

---

# Scope

## In Scope (impl PR 가 수행)

### 1. `docs/adr/ADR-MONO-052-transport-context-map.md` 상태 전환

- `**Status:** PROPOSED` → `**Status:** ACCEPTED`
- `**History:**` 줄에 ACCEPTED 이력 추가: `· ACCEPTED 2026-07-20 (TASK-MONO-453 — user-explicit ADR-naming intent "ADR-MONO-052 ACCEPTED")`
- **§8 Status history 표에 ACCEPTED 행 추가** + ADR-051 §8 패턴대로 다음 3개 문단 기술:
  - **ACCEPT gate — cleared, not bypassed**: 작성↔인가 분리, 정확형 도착, **직전 bare `ACCEPT` 는 넘기지 않았다**는 사실 기록(게이트가 장식이 아니었다는 증거).
  - **What acceptance binds**: 무엇이 구속력을 갖는지 명시 — **D1**(custody 분할선) · **D3**(2-leg, 원자적 이동 없음) · **D4**(wms 에 `IN_TRANSIT` 없음) · **D5**(seam=fact 이벤트) · **D6**(`tms-platform` 미신설) 이 binding. **D7 은 조건부**(수신자 생기면 만료) · **D8 은 트리거**(백로그 아이템 아님) · **D2 ①행은 ADR-027 유비 기반**이라 실제 구현 시 재검토 여지 명기.
  - **Amending this ADR**: 승인된 것은 읽힌 그대로의 텍스트. 이후 개선은 §1–§6 in-place 재작성이 아니라 amendment 절(ADR-050 §7 패턴)로.
- **§1–§6 본문 수정 0** — 승인 대상 텍스트를 승인 시점에 바꾸지 않는다.

### 2. `docs/adr/INDEX.md` 052 행 갱신

`PROPOSED` → `ACCEPTED`. Date 열은 `2026-07-20` 유지(작성·승인 동일자). 요약 문구 변경 0.

### 3. Lifecycle

impl PR 이 본 task 를 `ready/` → `review/` 이동 + Status 갱신 + root `tasks/INDEX.md` 반영.

## Out of Scope

- **ADR-052 §1–§6 본문 수정 0.**
- **구현 착수 0** — ACCEPT 는 §3 이 "None" 이라고 명시하므로 **아무 구현도 인가하지 않는다**. `logistics-service` 부트스트랩, TMS 어댑터 이전, wms `IN_TRANSIT`, 창고간 가드 제거, 3PL 어댑터 전부 여전히 미착수.
- **D1 승격 0** — `rules/domains/wms.md` 로의 custody 문장 승격은 ADR §7 이 "promotion candidate" 로만 기록. **별건 task**(ADR-051 → MONO-435/437 선례). 승격 전 **측정 선행** 필수(이미 어딘가에 좁혀 적혀 있으면 재진술 금지).
- **`inventory-service/architecture.md:589-590` 소유 정정 0** — 별건.
- **TASK-MONO-452 close chore 0** — 별도 chore PR(452+453 배치 가능).
- projects/** · 코드 · 빌드 · CI 변경 0.

---

# Acceptance Criteria

0. **AC-0 재확인** — ADR-052 가 origin/main 에 `Status: PROPOSED` 로 존재하고, 사용자 정확형 intent 가 `ADR-MONO-052 ACCEPTED` 였음을 확인. 어긋나면 STOP.
1. `docs/adr/ADR-MONO-052-transport-context-map.md` — `**Status:** ACCEPTED`, History 줄에 ACCEPTED 이력 + 승인 근거 문구.
2. §8 표에 ACCEPTED 행 + 위 3개 문단(gate cleared / what acceptance binds / amending).
3. **§8 이 "bare `ACCEPT` 로는 넘기지 않았다"를 기록** — 게이트가 실제로 물었다는 증거는 기록되지 않으면 남지 않는다.
4. **"What acceptance binds" 가 binding 과 conditional 을 구분** — D7(조건부 만료) · D8(트리거) · D2 ①행(유비 기반, 재검토 여지) 이 무조건 승인으로 읽히지 않을 것.
5. `docs/adr/INDEX.md` 052 행 `PROPOSED` → `ACCEPTED`.
6. **§1–§6 diff 0** (`git diff` 로 확인 — 상태/이력/§8 외 본문 변경 없음).
7. **self-ACCEPT 0** — 본 task 는 사용자 정확형 intent 를 *기록*하는 것이지 스스로 판단하지 않는다.
8. doc-only: git diff = ADR-052 (상태+§8) + `docs/adr/INDEX.md` 1행 + lifecycle. 코드/projects/빌드/CI 0.

---

# Related Specs

- [ADR-MONO-052](../../docs/adr/ADR-MONO-052-transport-context-map.md) — 전환 대상
- [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § The ACCEPTED Gate — :41 정확형 요구, :43-46 bare 토큰 배제, :47-49 self-ACCEPT 금지, :53-56 귀속가능성
- [ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) §8 — 동형 선례(TASK-MONO-434 전환, §8 3문단 패턴)
- [ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 — amendment 절 패턴
- [`rules/domains/wms.md`](../../rules/domains/wms.md) § Transfer — D1 승격 후보(본 task 범위 밖)

---

# Related Contracts

- 본 task = ADR governance 전환. **컨트랙트 변경 0.**

---

# Target Service / Component

- `docs/adr/ADR-MONO-052-transport-context-map.md` (Status + History + §8)
- `docs/adr/INDEX.md` (1행 Status)
- (no production / project / build / CI change)

---

# Edge Cases

1. **ADR-052 가 이미 ACCEPTED 인 경우** (동시 세션 선점): STOP + 보고. 이중 전환 금지.
2. **`ADR index drift` CI 가드**: INDEX 행 Status 와 ADR 파일 Status 가 어긋나면 RED. **둘을 같은 커밋에서** 바꿀 것.
3. **§8 문단을 "승인됐으니 이제 구현" 으로 쓰는 유혹**: ADR §3 이 `None` 이다. ACCEPT 는 **기록의 구속력**을 부여할 뿐 작업을 인가하지 않는다. 문안이 이 구분을 잃으면 다음 세션이 부트스트랩을 착수한다.
4. **D6 을 "사용자가 tms-platform 을 영구 포기했다"로 과대 기술**: D6 은 *현 시점 판단*이고 §D6 말미가 재개 절차(ADR-002 §D4 / ADR-016 supersede)를 이미 명시한다. 그 경로를 §8 이 지우지 말 것.
5. **History 줄 포맷**: ADR-051 형식(`PROPOSED <date> (TASK) · ACCEPTED <date> (TASK — intent)`) 따를 것.

---

# Failure Scenarios

## A. bare 토큰으로 재-전환 시도

→ 본 task 는 정확형이 이미 도착했으므로 해당 없음. 그러나 **후속 ADR 에서 같은 상황이 오면 :43-46 이 답이다** — 문맥상 명백함은 게이트를 열지 않는다.

## B. 승인 텍스트를 승인 시점에 개선하고 싶어짐

→ 금지(AC-6). 승인된 것은 **읽힌 그대로의 텍스트**다. 개선은 amendment 절로.

## C. 범위 확장 (D1 승격 / 589-590 정정 / close chore 까지)

→ 전부 별건. 본 task 산출물은 ADR 상태 전환 + INDEX 1행 + lifecycle.

## D. "ACCEPTED 됐으니 logistics-service 만들자"

→ ADR §3 = None, §D8 = 트리거 4개 전부 미발화(2026-07-20 실측). 착수는 **트리거가 발화한 뒤** 별 task.

---

# Test Requirements

- `git diff` = ADR-052(Status/History/§8) + `docs/adr/INDEX.md` 1행 + lifecycle 만.
- ADR-052 `Status: ACCEPTED` 단언; §8 표에 PROPOSED·ACCEPTED 2행.
- ADR-052 §1–§6 diff 0 (섹션 범위 diff 확인).
- `docs/adr/INDEX.md` 052 행 Status = `ACCEPTED` (CI `ADR index drift` 가드 GREEN).
- markdown lint green.

---

# Definition of Done

- [ ] AC-0 확인 (main 에 PROPOSED 존재, 정확형 intent 확인)
- [ ] ADR-052 Status ACCEPTED + History + §8 3문단
- [ ] §8 이 bare `ACCEPT` 미수용 사실 기록
- [ ] binding ↔ conditional 구분 명시 (D7/D8/D2① )
- [ ] INDEX 행 ACCEPTED
- [ ] §1–§6 diff 0
- [ ] doc-only diff scope
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** (분석=Opus 4.8 / 구현 권장=Sonnet — 판단은 ADR-052 와 사용자 intent 에서 이미 끝났고, 본 task 는 상태 전환 + §8 서술의 기계적 작업. TASK-MONO-434 동형이며 그 선례도 경량이었다).
- **분량**: small — 파일 2개, 라인 소수.
- **dependency**:
  - `선행`: TASK-MONO-452 (ADR 작성, PR #2785 `e77744a23` 머지 완료).
  - `후속`: (a) TASK-MONO-452+453 close chore, (b) **선택** D1 custody 문장 → `rules/domains/wms.md` 승격(측정 선행), (c) **선택** `inventory-service/architecture.md:589-590` 소유 정정. (b)/(c) 는 ACCEPT 로 인가된 것이 아니라 ADR §7 이 후보로만 기록한 것.
- **이 task 가 방어하는 실패 모드**: ACCEPT 가 기록 없이 지나가면 (a) 결정의 귀속이 사라지고 (b) 다음 세션이 "PROPOSED 인데 왜 다들 따르지" 를 재조사하며 (c) 무엇이 binding 이고 무엇이 조건부인지 구분이 사라져 D7·D8 이 무조건 승인으로 읽힌다.
