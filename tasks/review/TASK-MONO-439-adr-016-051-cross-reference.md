# Task ID

TASK-MONO-439

# Title

ADR-MONO-016 ↔ ADR-MONO-051 상호참조 + ADR-051 header 의 stale `TEMPLATE.md` 앵커 정정

# Status

review

# Owner

architecture / platform

# Task Tags

- docs
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

두 개의 dead/누락 참조를 정정한다.

**① ADR-016 → ADR-051 링크 부재 (본 task 의 출발점).**
[ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) §D2(L62)가 erp 소유 범위를 선언한다 — *"erp owns **master data (부서/직원/직급/비용센터/거래처)** + approval workflow + integrated read model and **NO domain business logic**"*. [ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) §D5 가 그중 **거래처(business partner)** 의 소유권을 세 층으로 정밀화했다 — erp = **정체성**(code / 법인·연락 정보 / 지급조건 / 유효기간), scm v2 `supplier-service` = **소싱 속성**(계약만료 / 리드타임), wms `Partner` = **수신측 프로젝션**(`partnerCode` 키). 그런데 **ADR-016 은 051 을 가리키지 않는다.**

실패 모드: erp masterdata 작업을 시작하는 사람은 ADR-016 을 읽는다. 거기서 "erp 가 거래처를 소유한다" 만 보고 **3층 분할을 모른 채** 착수하면, 051 이 명시적으로 기각한 방향(erp 를 거래처 레코드 전체의 주인으로 취급)으로 설계한다. **051 은 016 을 인지하고 정밀화했으나 016 은 그 사실을 모른다 — 한 방향만 연결된 참조.**

**② ADR-051 header 가 존재하지 않는 섹션을 가리킴.**
`Related:`(L8) 이 `TEMPLATE.md § Discovery → Distribution` 을 인용하는데 [TASK-MONO-437](../review/TASK-MONO-437-promote-d6-extraction-constraint.md) 이 **그 헤딩이 실존하지 않음**을 확인했다(문자열은 서두 산문 + Option B 임포트 하위단계 2곳뿐). 437 이 D6 를 승격하며 실제 섹션 `## Cross-Project Runtime Coupling (Extraction Constraint)` 을 만들었으므로, 이제 **가리킬 대상이 생겼다.**

## 🔴 소비자 전수 grep 결과 — 범위가 이것으로 확정된다

`Discovery → Distribution` 전역 검색 결과, **대부분은 정상**이다: `README.md` · `docs/project-overview.md` · `docs/guides/**` · `CLAUDE.md:31` · `TEMPLATE.md:3` 등은 **전략의 이름**을 산문으로 부르는 것이고 전략은 실재한다. 정정 대상이 아니다.

**TEMPLATE.md 의 *섹션 앵커* 로 인용한 곳은 4개이며 전부 `ADR-MONO-051`** 이다:

| 위치 | 상태 | 처리 |
|---|---|---|
| L8 `**Related:**` (header) | stale | **본 task 가 정정** — header 는 §1~§6 이 아니므로 amendment 규정 밖 |
| L65 (§1.4 본문) | stale | **범위 밖 — amendment 보호** |
| L196 (§6 검증표) | stale | **범위 밖 — amendment 보호** |
| L209 (§7) | TASK-MONO-437 이 정정 중 | 437 소관 |

§8 이 못박는다: *"Later improvements … go through an amendment section, **not an in-place rewrite of §1–§6**."* ⇒ **L65·L196 은 건드리지 않는다.** 대신 **437 이 §7 에 이미 이 불일치를 ADR 전체에 대해 기록**했다(원래 지목한 이름이 헤딩으로 존재하지 않으며 왜 다른 앵커를 골랐는지). 그것이 §1~§6 의 stale 앵커를 읽는 사람에게 주는 정정 경로다.

`tasks/done/` 의 과거 task 파일(`TASK-MONO-433:111` · `TASK-MONO-435:85`) 과 `tasks/INDEX.md:173` 도 같은 문자열을 갖지만 **역사 기록이므로 무수정**(done/ in-place 편집 금지).

---

# Scope

## In Scope

### 1. `ADR-MONO-016` `**Related:**` 에 ADR-051 추가

무엇을 정밀화했는지 한 줄로 밝힌다 — 링크만 걸면 독자가 다시 열어봐야 한다. 요지: **051 §D5 가 거래처 소유권을 erp(정체성) / scm v2 supplier-service(소싱) / wms Partner(수신 프로젝션) 3층으로 분할했고, erp 는 identity owner 로 유지되나 레코드 전체의 주인은 아니다.**

### 2. `ADR-MONO-051` `**Related:**` 에 ADR-016 추가

역방향. 016 이 **거래처를 erp 소유로 선언한 원출처**임을 밝힌다(051 §D5 가 정밀화한 대상).

### 3. `ADR-MONO-051` `**Related:**` L8 의 `TEMPLATE.md` 앵커 정정

`§ Discovery → Distribution` → `§ Cross-Project Runtime Coupling (Extraction Constraint)`.

### 4. Lifecycle

본 task `ready/` → `review/` + root `tasks/INDEX.md` 반영.

## Out of Scope

- **ADR-051 §1.4(L65)·§6(L196) 앵커 0** — amendment 보호(§8). 437 의 §7 기록이 정정 경로다. 필요하면 별 amendment task.
- **ADR-016 본문 수정 0** — §D2 L62 의 소유 선언은 **틀리지 않았다**. 051 §D5 와 A2 가 "erp 는 거래처 identity owner 로 유지" 를 확인했으므로 정정 대상이 아니라 **연결 대상**이다. 문장을 고치지 말고 링크만 건다.
- **ADR Status·Date·History 0** — 두 ADR 다 ACCEPTED 유지.
- **`tasks/done/**` · `INDEX` 과거 항목 0** — 역사 기록.
- **`TEMPLATE.md` 0** — 437 소관. 본 task 는 그것을 *가리킬* 뿐이다.
- **`platform/**` · `projects/**` · 코드 · CI 0.**

---

# Acceptance Criteria

0. **AC-0 재측정** — 착수 시 확인: (a) ADR-016 `Related:` 에 051 이 여전히 없는가, (b) ADR-051 `Related:` 에 016 이 없고 `TEMPLATE.md` 앵커가 여전히 stale 인가, (c) **TASK-MONO-437 이 만든 `## Cross-Project Runtime Coupling (Extraction Constraint)` 섹션이 참조 시점에 실재하는가**. (c) 가 거짓이면 § Scope 3 을 보류한다 — **없는 섹션을 가리키도록 바꾸면 stale 앵커를 다른 stale 앵커로 교체하는 것**이다.
1. ADR-016 `**Related:**` 에 ADR-051 링크 + **무엇이 정밀화됐는지** 한 줄 존재.
2. ADR-051 `**Related:**` 에 ADR-016 링크 + 원출처임을 밝히는 한 줄 존재.
3. ADR-051 L8 의 `TEMPLATE.md` 앵커가 실존 섹션명으로 정정.
4. **ADR-051 §1~§6·§8 무변경** (`git diff` 로 확인 — L65·L196 포함).
5. **ADR-016 본문 무변경** — diff 가 `**Related:**` 한 줄에 한정.
6. 두 ADR 의 `Status`·`Date`·`History` 무변경.
7. `bash scripts/check-adr-index-drift.sh` GREEN.
8. doc-only: `docs/adr/` 2파일 + lifecycle 2파일. 그 외 0.

---

# Related Specs

- [ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) §D2 — erp 소유 범위 원 선언(거래처 포함)
- [ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) §D5 / §A2 — 거래처 소유권 3층 분할 + "erp as hub(기각) ≠ erp as owner(유지)" 구분
- [TASK-MONO-437](../review/TASK-MONO-437-promote-d6-extraction-constraint.md) — `TEMPLATE.md` 실존 섹션을 만든 선행 task
- [TASK-MONO-435](../done/TASK-MONO-435-promote-d2-cross-project-code-identity.md) — 같은 승격 시리즈

---

# Related Contracts

- 없음 — ADR 상호참조 정정. 컨트랙트·스키마 변경 0.

---

# Target Service / Component

- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` (`**Related:**` 한 줄)
- `docs/adr/ADR-MONO-051-master-data-stays-federated.md` (`**Related:**` 한 줄)
- `tasks/ready/` → `tasks/review/` + `tasks/INDEX.md`

---

# Edge Cases

1. **🔴 선행 미랜딩 시 앵커가 여전히 허공** — Scope 3 은 437 이 만든 섹션을 가리킨다. 437 이 main 에 없으면 **stale 앵커를 다른 stale 앵커로 바꾸는 것**이다. 다만 *현재도 이미 깨져 있으므로* 어느 순서로 머지돼도 나빠지지 않는다. AC-0 (c) 로 확인하고, 순서는 **437 → 439** 를 권장.
2. **ADR-016 본문을 고치고 싶은 유혹** — L62 는 틀리지 않았다. 051 이 정밀화했을 뿐이고 A2 가 "erp 는 owner 로 유지" 를 명시했다. **틀린 문장을 고치는 task 가 아니라 끊긴 링크를 잇는 task.**
3. **amendment 보호 침범** — §1.4·§6 의 같은 stale 문자열이 눈에 띄어도 건드리지 않는다. §8 이 금지하고, 437 §7 이 이미 문서화했다.
4. **`Related:` 줄이 매우 길다** — 두 ADR 다 한 줄에 다수 링크가 나열된 형태다. 기존 형식을 유지하며 append 한다(줄을 쪼개면 diff 가 커지고 형식이 갈라진다).
5. **`git mv` re-stage** — `ready/` → `review/` 후 Status 를 `review` 로 고치고 **다시 `git add`**, `git show :<review-path>` 로 확인.

---

# Failure Scenarios

## A. AC-0 에서 이미 상호참조가 존재함이 드러남

→ phantom. 기록하고 종료. (다른 세션이 동시에 손댔을 가능성 — `git fetch` 후 재확인.)

## B. TASK-MONO-437 이 리뷰에서 앵커를 다른 이름으로 바꿔 머지됨

→ Scope 3 의 대상 문자열이 달라진다. **머지된 `origin/main` 의 실제 헤딩을 읽고 그것에 맞춘다** — 437 task 파일의 서술이 아니라 **랜딩된 파일이 권위**.

## C. ADR-016 을 읽다 051 과의 실질적 모순을 발견

→ 본 task 는 링크 task 다. 모순은 **고치지 말고 티켓팅** — ACCEPTED ADR 2개 사이의 실질 조정은 amendment 절차가 필요하다.

---

# Test Requirements

- `git diff docs/adr/ADR-MONO-016-*.md` → `**Related:**` 한 줄만.
- `git diff docs/adr/ADR-MONO-051-*.md` → `**Related:**` 한 줄만 (L65·L196·§7·§8 무변경).
- `grep -n "ADR-MONO-051" ADR-MONO-016-*.md` → ≥1건.
- `grep -n "ADR-MONO-016" ADR-MONO-051-*.md` → ≥1건.
- `grep -c "Discovery → Distribution" ADR-MONO-051-*.md` → **3** (L65·L196·L209; L8 이 빠져 4→3. 437 랜딩 후엔 2).
- `bash scripts/check-adr-index-drift.sh` GREEN.
- 트리 검증: `git ls-tree -r --name-only HEAD tasks/ready tasks/review | grep 439` → 정확히 1개.

---

# Definition of Done

- [ ] AC-0 재측정 (양방향 부재 확인 + 437 섹션 실재 확인)
- [ ] ADR-016 `Related:` 에 051 + 정밀화 요지 한 줄
- [ ] ADR-051 `Related:` 에 016 + 원출처 표시
- [ ] ADR-051 `TEMPLATE.md` 앵커 정정
- [ ] §1~§6·§8 무변경, Status/Date 무변경
- [ ] doc-only diff scope
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (링크 2개 + 앵커 1개. 판단은 이 티켓이 이미 했다).
- **분량**: XS — ADR 2파일의 각 한 줄.
- **dependency**:
  - `선행(권장)`: **TASK-MONO-437** — Scope 3 이 그것이 만든 섹션을 가리킨다. 필수는 아니나(현재도 깨져 있음) 순서대로가 깔끔.
  - `후속`: 없음.
- **이 task 가 방어하는 실패 모드**: **참조는 한 방향만 이어져도 이어진 것처럼 보인다.** 051 은 016 을 알고 정밀화했으므로 051 을 읽는 사람은 안전하다 — 위험한 쪽은 **016 에서 출발하는 사람**이고, 그 사람이 정확히 erp masterdata 작업자다.
- **의도적으로 남긴 것**: ADR-051 §1.4·§6 의 stale 앵커 2곳(amendment 보호). 437 §7 이 ADR 전체에 대해 이 불일치를 기록했으므로 독자에게 정정 경로는 있다. 그래도 거슬리면 별 amendment task.
