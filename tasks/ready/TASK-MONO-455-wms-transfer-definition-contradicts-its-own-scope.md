# Task ID

TASK-MONO-455

# Title

`rules/domains/wms.md` 의 Transfer 정의가 같은 파일 Scope 선언과 모순 — ADR-MONO-052 D1 이 판정한 방향으로 수렴

# Status

ready

# Owner

architecture / rules

# Task Tags

- docs
- rules
- governance

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

[ADR-MONO-052](../../docs/adr/ADR-MONO-052-transport-context-map.md) §7 이 D1 custody 문장의 `rules/domains/wms.md` 승격을 **후보로만** 기록하며 *"Measure before promoting: if the narrowing is already stated elsewhere, restate nothing"* 을 조건으로 달았다. **그 측정을 수행한 결과 티켓의 성격이 바뀐다.**

## 측정 결과 (2026-07-20, 착수 시 AC-0 로 재확인 대상)

**승격이 아니라 같은 파일 안의 모순이다.** `rules/domains/wms.md` 두 줄:

| 줄 | 내용 |
|---|---|
| **:11** (Scope) | *"이 도메인은 「물건이 창고 안에서 어디에 있고, 어디로 옮겨야 하는가」에 집중한다. 상류(ERP/주문)와 **하류(TMS/배송)는 연동 경계로만 다루며, 그 자체를 구현하지 않는다**"* |
| **:46** (Ubiquitous Language) | *"**Transfer** — 같은 창고 내 **또는 창고 간** 재고를 이동하는 행위. 출발 로케이션 차감 + 도착 로케이션 증가가 **원자적으로** 수행"* |

:11 은 이미 D1 의 취지를 말하고 있다 — 창고 **안**이 이 도메인이고 운송은 경계 밖이라고. :46 이 그것을 뒤집는다. 그리고 :46 은 두 겹으로 틀렸다:

1. **"또는 창고 간"** — ADR-052 D1/D3 이 창고 간 이동을 wms 밖(scm 운송 컨텍스트)으로 배치했다.
2. **"원자적으로"** — D3 이 *"no atomic cross-warehouse transfer will exist"* 를 명시했다. 창고 간 구간에 이 술어를 적용하면 **구현 불가능한 것을 정의가 요구**한다.

**코드는 이미 :11 편이다** — `TransferStockService.resolveSameWarehouse()` 가 `"Cross-warehouse transfers are not supported in v1"` 을 throw 하고 `stock_transfer` 는 `warehouse_id` 가 단수다. 즉 **layer-2 정경(rules)만 혼자 넓다.**

## 왜 이게 결함인가

`CLAUDE.md` § Source of Truth Priority 상 `rules/domains/<domain>.md` 는 **4순위**로 코드(14순위)보다 위다. 문자 그대로 읽으면 **코드가 정경을 위반하는 상태**다. 실제로는 정경 쪽이 낡았고, 그 사실이 어디에도 안 적혀 있다.

**한 파일이 자기 자신과 모순되면 읽는 사람은 자기가 원하는 줄을 고른다.** 창고 간 이동을 구현하려는 다음 사람은 :46 을 인용할 것이고, 그건 D1/D3 이 방금 닫은 문이다.

---

# Scope

## In Scope

### 1. `rules/domains/wms.md:46` Transfer 정의 정정

- "또는 창고 간" 제거 → 같은 창고 내 로케이션 간 이동으로 한정.
- "원자적" 술어는 **한정된 범위 안에서는 유지**(창고 내 이동은 실제로 단일 트랜잭션이다 — `StockTransfer` Javadoc *"Atomic move between two locations in one warehouse"*).
- 창고 간 이동이 **어디로 갔는지** 한 절로 명시 + ADR-052 링크. 단순 삭제는 안 된다 — 다음 사람이 "누락"으로 읽고 되돌린다.

### 2. custody 문장 승격 여부는 **측정 결과에 따라 결정**

ADR-052 §7 이 후보로 든 D1 문장(*"wms is authoritative while goods are on its floor; goods on a vehicle are in no warehouse"*)이 :11 의 재진술인지, 아니면 :11 이 못 담는 **판별 기준**을 더하는지를 판정한다.

- **재진술이면 추가하지 않는다** — `feedback_repo_knows_what_it_does_not_say` 의 "안 갈라졌으면 중복=전파" 규율.
- **판별 기준을 더하면 추가한다** — :11 은 *무엇이 범위인가*를 말하고 custody 문장은 *경계선을 어디에 긋는가*를 말한다. 후자가 없으면 "도크에서 트럭에 실은 순간"이 어느 쪽인지 판정 불가.
- 판정 근거를 task 완료 노트에 남길 것. **어느 쪽이든 이유가 기록돼야 한다.**

## Out of Scope

- **ADR-MONO-052 본문 수정 0** — ACCEPTED 다. 개선은 amendment 절.
- **`projects/wms-platform/specs/services/inventory-service/architecture.md:589-590` 정정 0** — 별 task (`TASK-BE-544`, 프로젝트 큐). 본 task 는 shared `rules/` 만.
- **코드 변경 0** — `TransferStockService` 가드는 이미 옳다. 건드리지 않는다.
- `rules/taxonomy.md` 의 `wms` 정의 수정 0 (*"창고 내 재고·입출고·피킹 관리"* — 이미 좁다).
- 다른 Bounded Context 행(:22 Inventory 의 *"재고 이동(transfer)"*) 수정 여부는 AC-3 판정 대상.

---

# Acceptance Criteria

0. **AC-0 재측정 (착수 시 필수)** — :11 과 :46 의 문안이 위 인용과 일치하는지 확인. **줄 번호가 아니라 문자열로 찾을 것**(다른 세션이 파일을 편집했을 수 있다). 어긋나면 실측에 맞춰 다시 판단 — 코드가 이긴다.
1. `rules/domains/wms.md` Transfer 정의가 창고 내로 한정되고, **창고 간 이동의 행선지가 명시**되며 ADR-052 링크가 붙는다.
2. **"원자적" 술어가 남은 범위에서 참일 것** — 창고 내는 참, 창고 간은 D3 이 부정. 술어를 무범위로 남기면 정정이 반쪽이다.
3. **:22 Bounded Context 행 전수 대조** — *"재고 이동(transfer)"* 이 :46 정정 후에도 오해를 낳는지 판정. 낳으면 같이 고치고, 안 낳으면 **고치지 않은 이유를 기록**(F2 과잉정정 회피).
4. **custody 문장 추가 여부를 판정하고 근거를 남길 것** — 추가/미추가 어느 쪽이든. "판단 없이 추가" 와 "판단 없이 생략" 둘 다 실패.
5. **파일 전수 재독** — :11/:22/:46 외에 창고 간 이동을 전제하는 서술이 더 있는지 확인. **한 줄만 고치는 것이 이 결함을 만든 방식**이다(ADR-051 D2 승격이 한정어 하나를 잃은 것과 같은 지문 — `TASK-MONO-446`).
6. shared 파일이므로 **project-specific 내용 유입 0** (HARDSTOP-03). 서비스명·API 경로·엔티티명 금지 — `logistics-service` 같은 구체 서비스명도 `rules/` 에 넣지 말 것. ADR 링크로 가리킨다.
7. doc-only: git diff = `rules/domains/wms.md` + lifecycle. 코드/projects/빌드/CI 0.

---

# Related Specs

- [ADR-MONO-052](../../docs/adr/ADR-MONO-052-transport-context-map.md) — §D1(custody 분할선) · §D3(2-leg, 원자적 이동 없음) · §7(승격 후보 + 측정 선행 조건)
- [`rules/domains/wms.md`](../../rules/domains/wms.md) — :11 Scope · :22 Bounded Context · :46 Transfer (정정 대상)
- [`rules/README.md`](../../rules/README.md) — 규칙 해상 순서
- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) — HARDSTOP-03(shared 파일 project-agnostic)
- [`CLAUDE.md`](../../CLAUDE.md) § Source of Truth Priority — rules(4) > 코드(14)
- `TASK-MONO-446` / `TASK-MONO-435` — 승격 시 원문 대조 누락으로 한정어를 잃은 선례(동형 실패 모드)

---

# Related Contracts

- **컨트랙트 변경 0.** 인용만.

---

# Target Service / Component

- `rules/domains/wms.md`
- (no production / project / build / CI change)

---

# Edge Cases

1. **"창고 간 이동은 v2" 로 쓰고 싶은 유혹**: 틀렸다. D1/D3 은 **wms 의 v2 가 아니라** 다른 컨텍스트로 배치했다. "나중에 wms 가 한다" 로 읽히면 D1 을 무효화한다.
2. **`logistics-service` 를 `rules/` 에 적기**: HARDSTOP-03. shared 규칙은 project-agnostic — "운송 컨텍스트" 같은 도메인 용어로 쓰고 구체 서비스는 ADR 링크에 맡긴다.
3. **:11 이 이미 말하니 아무것도 안 해도 된다는 판단**: :46 이 모순인 채로 남는다. :11 의 존재는 **custody 문장 추가를 생략할 근거**이지 :46 정정을 생략할 근거가 아니다. 둘을 구분할 것.
4. **동시 편집**: `rules/domains/wms.md` 는 공유 파일이다. 착수 전 `gh pr list` 로 이 파일을 건드리는 열린 PR 확인(`project_shared_file_task_series_single_worktree_serialize`).
5. **줄 번호 인용**: 본 task 의 :11/:22/:46 은 2026-07-20 기준. 문자열로 찾을 것.

---

# Failure Scenarios

## A. 한 줄만 고치고 종료

→ AC-5 위반. 이 결함의 지문 자체가 "한 곳만 고쳐서 파일이 자기와 모순됨" 이다. 전수 재독 없이는 같은 결함을 다른 줄에 남긴다.

## B. 과잉정정 (Transfer 관련 서술 전부 재작성)

→ `feedback_repo_knows_what_it_does_not_say` — 중복이 곧 병은 아니다. **갈라진 것만** 고친다. :22 는 판정 후 결정이지 자동 수정 대상이 아니다.

## C. ADR-052 본문을 같이 고침

→ ACCEPTED 문서다. §1–§6 in-place 수정 금지. 필요하면 amendment 절.

## D. custody 문장을 판단 없이 복붙

→ ADR §7 이 명시적으로 "measure before promoting" 을 걸었고, 예비 측정에서 :11 이 이미 상당 부분을 말한다는 게 드러났다. **재진술이면 정경이 갈라진다** — 1곳에만 있어야 할 규칙이 2곳에 다르게 적히는 것이 435→446 이 만든 문제다.

---

# Test Requirements

- `git diff` = `rules/domains/wms.md` + lifecycle 만.
- Transfer 정의에 "창고 간" 을 **wms 범위로 서술하는** 문구 0.
- 창고 간 이동의 행선지 서술 + ADR-052 링크 존재.
- shared 파일에 서비스명/API 경로/엔티티명 0 (HARDSTOP-03).
- markdown lint green.

---

# Definition of Done

- [ ] AC-0 재측정 (문자열로 확인)
- [ ] Transfer 정의 창고 내 한정 + 행선지 명시 + ADR 링크
- [ ] "원자적" 술어가 남은 범위에서 참
- [ ] :22 판정 + 근거 기록
- [ ] custody 문장 추가/미추가 판정 + 근거 기록
- [ ] 파일 전수 재독 완료
- [ ] HARDSTOP-03 무위반
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.8 / 구현 권장=Opus — 편집량은 작지만 판정이 셋이다: 창고간 서술 행선지 문안 · :22 수정 여부 · custody 문장 추가 여부. 셋 다 "고치지 않을 이유"를 근거로 남겨야 하는 interpretive judgement 이고, 기계적으로 하면 B(과잉정정) 또는 D(정경 분기)로 간다).
- **분량**: small — 파일 1개, 줄 수 적음. 판정이 비용의 대부분.
- **dependency**:
  - `선행`: TASK-MONO-452/453 (ADR-052 ACCEPTED, `27546def9`). 완료.
  - `형제`: **`TASK-BE-544`** — 같은 ADR 이 낳은 프로젝트-측 정정(`inventory-service/architecture.md:589-590`). **독립 실행 가능**(shared vs project 경계가 다름). 다만 둘 다 D1/D3 을 인용하므로 문안이 서로 어긋나지 않게 할 것.
- **이 task 가 방어하는 실패 모드**: 정경이 자기와 모순되면 읽는 사람이 원하는 줄을 고른다. 창고 간 이동을 구현하려는 다음 사람은 :46 을 인용할 것이고, 그건 D1/D3 이 방금 닫은 문이다. 그리고 코드는 이미 옳으므로 **아무 CI 도 이걸 잡지 않는다** — 산출물이 옳고 문장이 틀린 경우다.
