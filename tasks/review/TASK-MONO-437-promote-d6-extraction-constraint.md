# Task ID

TASK-MONO-437

# Title

ADR-MONO-051 D6 승격 — 「허브형 제안은 standalone 추출 생존을 선실증하라」를 `TEMPLATE.md` 정경으로

# Status

review

# Owner

architecture / platform

# Task Tags

- docs
- governance
- platform

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

[ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) **D6** — "모든 프로젝트가 정체성 해석을 위해 호출해야 하는 컴포넌트를 도입하려는 제안은, `scripts/sync-portfolio.sh` 추출과 컨트랙트별 *no hard dependency* degradation 조항이 그것을 견디는지 **먼저 실증**해야 한다. 실증 실패는 추가 architectural 논증 없이 기각 사유로 충분하다" — 를 [`TEMPLATE.md`](../../TEMPLATE.md) 로 승격한다.

[TASK-MONO-435](../done/TASK-MONO-435-promote-d2-cross-project-code-identity.md) 가 D2 를 `platform/service-boundaries.md` 로 옮기면서 **D6 는 의도적으로 남겼다** — 집이 다르기 때문이다(D6 는 서비스 경계가 아니라 배포·추출 전략을 구속한다). 435 는 틀린 파일에 밀어넣는 대신 판단을 유보했고 ADR §7 에 그렇게 적었다. 본 task 가 그 유보를 해소한다.

승격이 필요한 이유는 435 와 동일하다: **규칙이 ADR 1곳에만 있으면 사실상 없는 규칙이다.** ADR-051 §7 이 현재 "옮기기 전까지 D6 는 이 ADR 단독으로 구속한다" 고 적어 둔 상태이며, 다음에 허브형 컴포넌트를 제안하는 사람은 ADR 55개가 아니라 `CLAUDE.md` → `TEMPLATE.md` 를 읽는다.

## 🔴 착수 전 재측정에서 드러난 것 (이 task 의 실질이 바뀐다)

**ADR-051 이 지목한 `TEMPLATE.md § Discovery → Distribution` 섹션은 실존하지 않는다.**

- `TEMPLATE.md` 전문 헤딩 스캔 결과 그 이름의 섹션 없음. 문자열 "Discovery → Distribution" 은 **2곳**뿐 — L3(문서 서두의 전략 서술, 헤딩 아님) 과 L366(`#### 4. Reconcile shared-layer duplicates (Discovery → Distribution)`, Option B 임포트 절차의 하위 단계).
- L366 은 **명백히 오답**이다 — 기존 standalone 레포를 임포트할 때 공유계층 중복을 정리하는 절차이지, 아키텍처 제안에 걸리는 게이트가 아니다.
- 435 의 out-of-scope 문구도 "`TEMPLATE.md § Discovery → Distribution` **계열**" 이라고 적어 개념적 지목임을 이미 표시해 뒀다.

⇒ **본 task 의 실질적 결정 = 구체 앵커 선정.** 아래 § Scope 1 이 그 결정과 근거를 담는다. 승격 문안 자체는 그 다음이다.

**그리고 D6 상당 규칙은 공유계층에 정말 없다** — `TEMPLATE.md` · `platform/` · `CLAUDE.md` · `rules/` 전역에서 `sufficient grounds for rejection` / `must first demonstrate` / `hard depend` / `standalone.*degrad` / `resolve identity` → **0건**. 435 가 D1·D3 에서 만난 "이미 전파돼 있음" 상황은 여기서는 발생하지 않는다.

**참조 대상 2개는 실재 확인:**

- `scripts/sync-portfolio.sh` ✅
- 컨트랙트별 degradation 조항 ✅ — `## Standalone-publish degradation` 섹션이 **6개 컨트랙트**에 존재(ADR-022 D8 · ADR-027 D8 · ADR-050 D8 계열). 즉 D6 가 인용하는 관례는 실재하는 반복 컨벤션이다.

---

# Scope

## In Scope (impl 이 수행)

### 1. 앵커 결정 — `## Library vs Project Boundary (Strict)` 직후의 형제 섹션

D6 를 `TEMPLATE.md` L73(`---`) 뒤, `## Phase Timeline` 앞에 **새 최상위 섹션**으로 배치한다.

**근거 (앵커 선정의 논거 — 리뷰 대상):**

- 바로 앞 섹션이 자기를 이렇게 규정한다: *"This is the single most important rule for keeping the template-extraction path viable."* 즉 **그 섹션은 이미 "추출 가능성을 지키는 규칙" 의 집**이다. D6 는 같은 목적을 **다른 축**에서 지키는 두 번째 규칙이다 — 앞 섹션은 *파일이 어디 사는가*(공유계층 vs 프로젝트), D6 는 *런타임에 무엇을 호출해도 되는가*(프로젝트 간 결합).
- 같은 절 **안에** 넣지 않는 이유: 그 절의 두 표는 경로-분류표이고, 제안 게이트를 그 안에 끼우면 분류 규칙으로 오독된다. 축이 다르므로 형제 섹션이 맞다.
- `Phase Timeline` **뒤**로 보내지 않는 이유: 타임라인은 이 저장소의 진행 기록(서술)이고 D6 는 상시 구속 규칙이다. 규칙은 기록보다 앞에 온다.

### 2. D6 규칙문 작성

**보존해야 할 4요소** (승격 무손실 대조 대상):

1. **트리거** — 모든 프로젝트가 정체성 해석을 위해 호출해야 하는 컴포넌트를 도입하는 제안
2. **의무 A** — `scripts/sync-portfolio.sh` standalone 추출이 그것을 견딤을 선실증
3. **의무 B** — 컨트랙트별 *no hard dependency* degradation 조항이 견딤을 선실증
4. **귀결** — 실증 실패 시 **추가 architectural 논증 없이** 기각 사유로 충분

back-link: `docs/adr/ADR-MONO-051` §D6.

**project-agnostic 필수** — 원문의 "all five projects" 는 프로젝트 수를 세므로 그대로 옮기면 안 된다. **"every project in this repository"** 류의 일반형으로 옮긴다. 서비스명·프로젝트명 0건(HARDSTOP-03).

### 3. ADR-051 §7 갱신

D6 관련 문단을 "미승격·ADR 단독 구속" → **승격 완료 + `TEMPLATE.md` 앵커 지시**로 전환. **§1~§6·§8 무수정**(§8 amendment 규정).

### 4. Lifecycle

본 task `ready/` → `review/` + root `tasks/INDEX.md` 반영.

## Out of Scope

- **D6 의미 확장 0** — 원문 트리거는 *정체성 해석* 컴포넌트로 한정된다. "모든 프로젝트가 호출하는 컴포넌트 일반" 으로 넓히면 그건 승격이 아니라 **ACCEPTED 결정의 개정**이다. 넓히고 싶으면 amendment 절차.
- **D1·D2·D3 재서술 0** — D2 는 435 가 `platform/service-boundaries.md` 로 이미 옮겼고 D1·D3 는 그 전부터 거기 있었다. `TEMPLATE.md` 에 복제하면 정경이 갈라진다.
- **`sync-portfolio.sh` 수정 0** — D6 는 그 스크립트를 *인용*할 뿐 바꾸지 않는다.
- **기존 컨트랙트·degradation 조항 수정 0.**
- **`projects/**` 무변경. 코드·빌드·CI 0.**
- ADR-051 §1~§6·§8 수정 0.

---

# Acceptance Criteria

0. **AC-0 재측정** — 착수 시 다시 확인한다: (a) `TEMPLATE.md` 에 `Discovery → Distribution` **헤딩**이 여전히 부재한가, (b) 공유계층에 D6 상당 규칙이 정말 0건인가, (c) `scripts/sync-portfolio.sh` 와 degradation 조항이 여전히 실재하는가. **하나라도 다르면 범위를 재조정한다 — 코드가 이긴다.** (선행 숫자는 출처가 아니라 가설.)
1. D6 규칙이 `TEMPLATE.md` 의 `## Library vs Project Boundary (Strict)` **직후**, `## Phase Timeline` **앞**에 최상위 섹션으로 존재.
2. **4요소 전부 생존** — 트리거 / 추출 실증 / degradation 실증 / "추가 논증 없이 기각" 귀결. **옮긴 뒤 ADR-051 D6 원문과 1:1 대조한다**(과거 정경 승격에서 규칙 1개 유실 전례 — 435 Edge 5).
3. `ADR-MONO-051` §D6 back-link 존재.
4. **project-agnostic**: 추가된 섹션 diff 에 서비스명·프로젝트명 0건, **프로젝트 개수를 세는 표현 0건**("five projects" 류). ⚠️ **AC-4 술어 주의(435 실패 전례)**: 이 grep 은 **인용한 ADR 파일명**을 오검출할 수 있다 — 링크 대상을 벗기고 **규칙 본문에 대해서만** 센다. 출처(provenance)를 내용(content)으로 세지 않는다.
5. **D1/D2/D3 중복 서술 0** — `platform/service-boundaries.md` 무변경(`git diff` 로 확인).
6. ADR-051 §7 D6 문단이 승격 완료 + 앵커 링크로 갱신, **§1~§6·§8 무변경**(`git diff` 로 확인).
7. doc-only: `TEMPLATE.md` 1파일 + `docs/adr/` 1파일 + lifecycle 2파일. `projects/**`·코드·빌드·CI 0.
8. `bash scripts/check-adr-index-drift.sh` GREEN (본 task 는 ADR Status·Date 를 건드리지 않는다).

---

# Related Specs

- [ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) §D6 — 승격 원문, §7 이 미승격 상태를 기록
- [TASK-MONO-435](../done/TASK-MONO-435-promote-d2-cross-project-code-identity.md) — 형제 승격(D2). 앵커 선정·무손실 대조·AC 술어 교훈의 출처
- [`TEMPLATE.md`](../../TEMPLATE.md) § Library vs Project Boundary (Strict) — 승격 대상 앵커의 형제 섹션
- [`platform/service-boundaries.md`](../../platform/service-boundaries.md) § Data Boundaries — D2 가 사는 곳(여기에 D6 를 넣지 **않는** 이유의 대조군)
- [`CLAUDE.md`](../../CLAUDE.md) § Repository Layout — `TEMPLATE.md` 가 project-agnostic 공유 파일임의 근거

---

# Related Contracts

- 없음 — 규칙 문서 승격. 컨트랙트 변경 0. (D6 가 인용하는 `## Standalone-publish degradation` 조항 6곳은 **읽기 전용 참조**이며 수정 대상이 아니다.)

---

# Target Service / Component

- `TEMPLATE.md` (새 최상위 섹션 1개)
- `docs/adr/ADR-MONO-051-master-data-stays-federated.md` (§7 only)
- `tasks/ready/` → `tasks/review/` + `tasks/INDEX.md`
- (no production / project / build / CI change)

---

# Edge Cases

1. **🔴 ADR 이 지목한 집이 실재하지 않음** — 이 task 의 출발점. `§ Discovery → Distribution` 헤딩은 없다. **ADR 문구를 문자 그대로 따라가 L366 하위 단계에 밀어넣으면 오답**이다(그 절은 레포 임포트 절차다). 앵커는 논거와 함께 선정하고, 선정 근거를 커밋 메시지·INDEX 에 남긴다.
2. **승격이 무손실이라는 가정** — D6 는 4요소 복합 규칙이라 D2(2요소)보다 유실 위험이 크다. 특히 **"추가 논증 없이 기각"** 이 가장 떨어지기 쉽다 — 그것이 D6 의 이빨이고, 빠지면 남는 건 권고문이다.
3. **project-agnostic 누출** — 원문 "all five projects" 를 그대로 옮기면 프로젝트 수를 세는 문장이 공유 파일에 들어간다. 게다가 프로젝트가 늘면 즉시 stale 이다.
4. **의미 확장 유혹** — D6 를 "모든 공유 컴포넌트" 로 넓히면 더 유용해 보이지만 ACCEPTED 결정의 무단 개정이다. 승격은 이동이지 개선이 아니다.
5. **ADR 본문 재작성 유혹** — §7 외 수정 금지. 개선은 amendment 섹션(ADR-050 §7 패턴).
6. **`git mv` re-stage** — `ready/` → `review/` 후 Status 를 `review` 로 고치고 **다시 `git add`**, `git show :<review-path>` 로 스테이징된 blob 이 `review` 인지 확인. (mv 와 커밋 사이 `git reset` 금지 — 양쪽 경로 커밋 사고.)

---

# Failure Scenarios

## A. AC-0 에서 D6 상당 규칙이 이미 어딘가에 존재함이 드러남

→ 승격 불필요. **phantom 으로 기록하고 종료**(없는 병에 수술 금지). ADR-051 §7 만 "이미 정경에 존재" 로 갱신.

## B. 앵커 선정이 갈림 — `TEMPLATE.md` 가 아니라 `platform/` 이 맞다고 판단될 때

→ D6 가 서비스 경계 규칙이면 435 가 이미 옮겼을 것이다. 435 는 **집이 다르다고 명시적으로 판단**했고 그 판단을 뒤집으려면 근거가 필요하다. 뒤집는다면 435 의 판단이 왜 틀렸는지를 적고 STOP + 사용자 확인. 조용히 옮기지 않는다.

## C. project-agnostic 표현이 불가능하다고 판단될 때

→ D6 는 실제로 일반 규칙이므로 표현 문제일 가능성이 높다. 그래도 불가능하면 `TEMPLATE.md` 가 집이 아니라는 뜻이므로 STOP + 재검토.

## D. 승격 후 D6 를 위반하는 기존 구조 발견

→ D6 는 **미래 제안에 대한 게이트**이지 소급 규칙이 아니다(ADR-051 §3: "changes no code, no contract, no schema"). 위반으로 보이는 것을 발견하면 **고치지 말고 티켓팅**. 규칙을 현실에 맞춰 약화시키지도 않는다.

---

# Test Requirements

- 추가 섹션 diff 에 서비스명/프로젝트명 grep → 0건 (**링크 대상 제외 후 본문에 대해**, AC-4 술어 주의).
- 프로젝트 개수 표현 grep(`five projects`, `5 projects`, `모든 다섯`) → 0건.
- ADR-051 D6 원문 4요소 ↔ 승격문 1:1 대조표 작성(무손실 확인).
- `git diff` 범위: `TEMPLATE.md` + ADR-051 §7 + lifecycle 2파일. `platform/**` 0, `projects/**` 0.
- `bash scripts/check-adr-index-drift.sh` GREEN.
- 트리 검증: `git ls-tree -r --name-only HEAD tasks/ready tasks/review | grep 437` → **정확히 1개 경로**.
- markdown lint green.

---

# Definition of Done

- [ ] AC-0 재측정 (헤딩 부재 · D6 부재 · 참조 대상 실재)
- [ ] 앵커 선정 + 근거 기록
- [ ] D6 규칙 `TEMPLATE.md` 에 추가 (4요소 + back-link)
- [ ] 무손실 대조 (4요소 생존, 특히 "추가 논증 없이 기각")
- [ ] project-agnostic (프로젝트명 0 · 개수 표현 0)
- [ ] D1/D2/D3 중복 0, `platform/` 무변경
- [ ] ADR-051 §7 갱신, §1~§6·§8 무변경
- [ ] doc-only diff scope
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (AC-0 재측정으로 범위가 이미 좁혀졌고, 남은 것은 앵커 배치 + 규칙문 1개 + 링크 정합).
- **분량**: small — 파일 2개 + lifecycle 2개.
- **dependency**:
  - `선행`: TASK-MONO-433(ADR 작성) · TASK-MONO-434(ACCEPT) · **TASK-MONO-435(D2 승격, D6 를 명시적으로 남김)** — 전부 `done/`.
  - `후속`: 없음. ADR-051 §7 의 미결 항목이 D6 하나였으므로 **본 task 로 §7 후속이 소진된다.**
- **이 task 가 방어하는 실패 모드**: D6 는 *제안을 기각할 권한* 을 주는 규칙이다. 그 권한이 ADR 안에만 있으면, 허브형 제안이 올라왔을 때 그것을 기각할 근거를 아무도 찾지 못한다 — 그리고 ADR-051 이 명시적으로 막으려 한 것이 바로 "federated 형태가 어디에도 선언돼 있지 않아 다음 독자에게 누락으로 읽히는" 상황이다.
- **미승격으로 남는 것 없음**: ADR-051 의 6개 결정 중 D1·D3 = 이미 정경(435 AC-0 발견) · D2 = 435 승격 · **D6 = 본 task** · D4·D5 = 승격 대상 아님(D4 는 조건부 수용 상태, D5 는 트리거이지 규칙이 아님).
