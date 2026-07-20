# Task ID

TASK-BE-544

# Title

wms 스펙이 창고 간 이동을 "v2 진화 경로"로 서술 — ADR-MONO-052 는 **다른 컨텍스트로 이관**했다 (v1↔v2 축이 아님)

# Status

ready

# Owner

backend / specs

# Task Tags

- docs
- specs
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

[ADR-MONO-052](../../../../docs/adr/ADR-MONO-052-transport-context-map.md) (ACCEPTED 2026-07-20) 가 창고 간 재고 이동을 **wms 밖(scm 운송 컨텍스트)** 으로 배치했다. §D1 은 분할선을 custody 로 긋고, §D3 은 *"no atomic cross-warehouse transfer will exist"* 와 함께 **기존 가드가 v1 한계가 아니라 경계의 집행**임을 명시했다.

그런데 wms 스펙은 이걸 **"v1 에는 없고 v2 에 할 일"** 로 서술한다. 대표 문장:

> **Multi-warehouse transfer**: model as outbound-from-A + inbound-to-B saga in `outbound-service` orchestration. Inventory itself stays single-warehouse-only.
> — `specs/services/inventory-service/architecture.md:589-590`, § Extensibility Notes(*"Known evolution paths (not part of v1 — documented to guide v2 decisions)"*)

**두 겹으로 틀렸다:**

1. **축이 틀렸다** — v1↔v2 가 아니다. ADR-052 는 이걸 *나중에* 가 아니라 *다른 곳* 으로 보냈다. "evolution path" 밑에 두면 다음 사람은 **wms 의 로드맵 항목**으로 읽는다.
2. **소유가 틀렸다** — *"in `outbound-service` orchestration"* 이 **전 구간을 wms 에 배정**한다. D1/D3 은 wms 가 **두 leg 만** 갖고 중간(운송·in-transit)은 scm 것이라고 판정했다. 덤으로 이 저장소에서 "orchestration" 은 명령이 아니라 상태보관을 뜻한다(`outbound-saga.md:62` = *"Choreographed (not Orchestrated)"*) — 문장이 없는 결합을 암시한다.

## 🔴 표면은 한 줄이 아니다 (예비 실측 2026-07-20 — AC-0 로 재측정)

`grep` 결과 `projects/wms-platform/specs/` 안에 cross/multi-warehouse 서술이 **약 20곳**, 최소 3개 서비스(inventory·outbound·inbound)에 걸쳐 있다. 확인된 지점 일부:

| 파일 | 성격 |
|---|---|
| `inventory-service/architecture.md:79-81` | Out of Scope — *"out of v1"* |
| `inventory-service/architecture.md:403-405` | *"v1 simplification"* |
| `inventory-service/architecture.md:589-590` | **Extensibility / evolution path** ← 가장 틀린 프레이밍 |
| `inventory-service/domain-model.md:147-148`, `:441` | v1 단순화 서술 |
| `inventory-service/sagas/reservation-saga.md:403-405` | *"v2 multi-warehouse reservation … one parent coordinator"* — 소유자 미지정 |
| `inventory-service/overview.md:74` | *"Multi-warehouse cross-boundary transfer — v2"* |
| `outbound-service/architecture.md:62` · `sagas/outbound-saga.md:46` · `workflows/outbound-flow.md:664` | multi-warehouse 관련 |
| `inbound-service/architecture.md:68` · `overview.md:70` · `workflows/inbound-flow.md:355` | multi-leg cross-warehouse |
| `contracts/http/inventory-service-api.md:405`, `:735` | 거부 동작 서술 |

**전부 고치는 게 답이 아니다.** *"창고 간 이동은 거부된다"* 류는 **지금도 참**이고 D3 이 오히려 승격시켰다. 고칠 대상은 **"wms 가 나중에 한다"고 읽히는 프레이밍**뿐이다. 이 분류가 이 task 의 실제 작업이다.

---

# Scope

## In Scope

### 1. 전수 census 후 3분류

`projects/wms-platform/specs/` 의 cross/multi-warehouse 서술 전부를 열거하고 각각:

- **(A) 그대로 둔다** — 거부 동작·단일창고 제약의 사실 서술. D3 이 강화했으므로 수정 불요.
- **(B) 프레이밍 정정** — *"out of v1" / "v2" / "evolution path" / "not part of v1"* 이 **wms 로드맵**을 암시하는 곳 → "다른 컨텍스트 소유"로 재서술 + ADR-052 링크.
- **(C) 소유 정정** — 전 구간을 wms(특히 `outbound-service`)에 배정하는 곳 → 2-leg 로 정정, 중간 구간은 wms 밖임을 명시.

각 항목의 분류 **근거를 task 완료 노트에 남길 것**. (A) 판정도 근거가 필요하다.

### 2. `:589-590` 은 (C) + (B) 동시 — 최소 요구사항

- "Known evolution paths" 하위에서 **분리**하거나, 그 자리에서 "wms 의 확장 경로가 아님"을 명시.
- *"in `outbound-service` orchestration"* → wms 는 outbound leg + inbound leg, 중간은 운송 컨텍스트.
- ADR-052 링크.

### 3. `reservation-saga.md:403-405` 의 "parent coordinator"

소유자 미지정 상태다. ADR-052 D2 가 배치했으므로 **그 배치를 가리키되, 새 설계를 발명하지 않는다**(ADR 이 말한 것 이상 쓰지 말 것).

## Out of Scope

- **ADR-MONO-052 본문 수정 0** — ACCEPTED. 개선은 amendment 절.
- **`rules/domains/wms.md` 수정 0** — shared 파일이라 **root task `TASK-MONO-455`** 소관. 프로젝트 task 가 shared 경로를 건드리면 HARDSTOP.
- **코드 변경 0** — `TransferStockService` 가드·`stock_transfer` 스키마·테스트 전부 이미 옳다. D3 이 그것들을 승격시켰지 바꾸라고 하지 않았다.
- **구현 0** — `logistics-service`, in-transit, 3PL 어댑터 전부 ADR §3 = `None`, D8 미발화.
- 컨트랙트 버전 변경 0.

---

# Acceptance Criteria

0. **AC-0 전수 재측정 (착수 시 필수)** — Goal 의 "약 20곳"은 **가설이다. 직접 세라.** `projects/wms-platform/specs/` 전역에서 cross-warehouse / multi-warehouse / 창고 간 관련 서술을 열거하고 **실측 수를 기록**. 검색어를 하나만 쓰지 말 것(`cross-warehouse`, `multi-warehouse`, `Multi-warehouse`, `warehouse boundaries`, `single-warehouse`, 한글 서술 포함) — **탐지식의 0건은 "없음"이 아니다.**
1. census 결과가 **A/B/C 로 전량 분류**되고 각 분류에 근거가 있다. 미분류 0.
2. `:589-590` 이 (B)+(C) 로 정정되고 ADR-052 링크를 갖는다.
3. **(A) 로 남긴 항목의 근거가 기록**된다 — "안 고친 것"과 "못 본 것"은 산출물에서 구분되지 않는다. 기록이 그 구분이다.
4. **정정문이 "v2 에 wms 가 한다"로 읽히지 않을 것** — 이 task 의 전체 요지다. 검수 시 각 정정문을 그 문장만 떼어 읽어볼 것.
5. **ADR 이 말하지 않은 것을 쓰지 말 것** — D2 ①행은 ADR 자신이 *"weakest allocation"* 이라 적었다. 스펙이 그것을 확정처럼 서술하면 ADR 보다 강한 주장이 된다.
6. **"orchestration" 표현 주의** — 이 저장소에서 그 단어는 상태보관을 뜻하고 정본 사가 스펙은 스스로를 choreographed 라 한다. 정정문이 동기 명령을 암시하면 D5 위반.
7. doc-only: git diff = `projects/wms-platform/specs/**` + lifecycle. 코드/빌드/CI/shared 경로 0.

---

# Related Specs

- [ADR-MONO-052](../../../../docs/adr/ADR-MONO-052-transport-context-map.md) — §D1 custody · §D3 2-leg · §D5 seam=fact 이벤트 · §7 이 `:589-590` 정정을 후보로 기록
- `specs/services/inventory-service/architecture.md` — :79-81, :403-405, **:589-590**
- `specs/services/inventory-service/domain-model.md` · `overview.md` · `sagas/reservation-saga.md`
- `specs/services/outbound-service/architecture.md` · `sagas/outbound-saga.md`(:62 *"Choreographed (not Orchestrated)"*) · `workflows/outbound-flow.md`
- `specs/services/inbound-service/architecture.md` · `overview.md` · `workflows/inbound-flow.md`
- `specs/contracts/http/inventory-service-api.md`
- **형제**: `TASK-MONO-455` (root) — 같은 ADR 이 낳은 shared `rules/domains/wms.md` 정정

---

# Related Contracts

- `specs/contracts/http/inventory-service-api.md` — 서술 정정 가능성만. **버전 변경 0, 동작 변경 0.**

---

# Target Service / Component

- `projects/wms-platform/specs/**` (문서만)
- (no production code / build / CI / shared-path change)

---

# Edge Cases

1. **전량 수정 반사**: (A) 가 상당수일 것이다 — 거부 동작 서술은 D3 이 강화했다. 반사적으로 다 고치면 **참인 문장을 훼손**한다(과잉정정).
2. **`rules/domains/wms.md` 를 같이 고치고 싶어짐**: shared 경로 → HARDSTOP. `TASK-MONO-455` 소관. 두 task 의 문안이 서로 어긋나지 않게만 할 것.
3. **줄 번호 stale**: Goal 의 줄 번호는 2026-07-20 기준. **문자열로 찾을 것.**
4. **검색어 하나로 census**: 한글 서술("창고 간", "다중 창고")과 영문 변형이 섞여 있다. 단일 패턴 census 는 0건을 "없음"으로 오보한다.
5. **`inventory-service-api.md:735` 같은 계약 문서**: 서술이 이미 참이면 (A). 계약의 **동작**을 바꾸는 정정은 이 task 범위 밖(그건 ADR/컨트랙트 절차).

---

# Failure Scenarios

## A. `:589-590` 만 고치고 종료

→ ADR §7 이 그 줄을 예시로 든 것이지 **표면이 그 줄뿐이라고 말하지 않았다.** 예비 실측만으로도 최소 3서비스에 걸쳐 있다. 한 줄 수정은 나머지를 "검토했고 문제없음"으로 위장한다.

## B. census 수를 선행 문서에서 물려받음

→ AC-0 위반. "약 20곳"은 이 task 를 쓴 사람의 **가설**이다. 세지 않고 인용하면 그 숫자가 사실로 굳는다.

## C. 정정문이 새 설계를 발명

→ AC-5. ADR-052 는 배치만 했고 `logistics-service` 는 존재하지 않는다. 스펙이 인터페이스·이벤트·상태를 지어내면 **미구현 서비스에 대한 허구 계약**이 된다.

## D. 코드까지 손댐

→ 가드·스키마·테스트는 이미 옳고 D3 이 그것들을 **경계의 집행**으로 승격시켰다. 이 task 는 문서가 코드를 따라잡는 작업이지 그 반대가 아니다.

---

# Test Requirements

- `git diff` = `projects/wms-platform/specs/**` + lifecycle 만. shared 경로(`rules/`, `platform/`, `docs/adr/`) 0.
- census 결과(총 수 + A/B/C 분류)가 task 완료 노트 또는 PR 본문에 기록.
- 정정된 문장에 *"v2"* / *"evolution path"* 가 **wms 로드맵 의미로** 남아 있지 않음.
- ADR-052 링크가 최소 `:589-590` 정정 지점에 존재.
- markdown lint green.

---

# Definition of Done

- [ ] AC-0 전수 census (복수 검색어, 실측 수 기록)
- [ ] A/B/C 전량 분류 + 근거
- [ ] `:589-590` (B)+(C) 정정 + ADR 링크
- [ ] (A) 유지 근거 기록
- [ ] 정정문이 "wms 가 v2 에 한다"로 안 읽힘
- [ ] ADR 이 말하지 않은 설계 0
- [ ] doc-only / shared 경로 무변경
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.8 / 구현 권장=Opus — 편집 자체는 단순하나 **A/B/C 분류가 판정**이다. (A) 를 (B) 로 잘못 밀면 참인 문장을 훼손하고, 반대면 결함이 남는다. 20여 곳 각각에 "이 문장이 wms 로드맵으로 읽히는가"를 물어야 한다).
- **분량**: medium — 파일 다수, 지점 다수. 편집량보다 census+분류가 비용.
- **dependency**:
  - `선행`: `TASK-MONO-452`/`453` (ADR-052 ACCEPTED, `27546def9`). 완료.
  - `형제`: `TASK-MONO-455` (shared `rules/` 측). **독립 실행 가능** — 경로가 겹치지 않는다.
- **이 task 가 방어하는 실패 모드**: 스펙이 창고 간 이동을 wms 로드맵으로 서술하는 한, 다음 사람은 ADR 이 아니라 스펙을 읽고 wms 안에 구현한다. 그리고 **어떤 CI 도 이걸 잡지 않는다** — 코드는 이미 옳고 문장만 틀렸다(`TASK-MONO-444` 와 같은 지문: 산출물은 옳고 문장이 틀린 경우는 탐지 밖).
