# Task ID

TASK-MONO-471

# Title

규칙 라이브러리 리팩토링 Tier-2 defects — phantom 에러코드 drift + dead § 인용 + resolution-order 오해소지 (MONO-470 후속)

# Status

done

# Owner

monorepo (root tasks/ — shared `platform/` + `rules/`)

# Task Tags

- onboarding

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **origin**: `TASK-MONO-470` 이 남긴 Tier-2 후속 중 **실제 결함**(drift/dead-ref/모순) 3건만 선별. style-convergence(F4/2.3)·contested-design(DUP1)·authoring(F3/F2)·저신뢰(3.2/3.3)는 제외.
- **prerequisite for**: 없음.
- **execution constraint**: `platform/` + `rules/`. classifier block 아님. agent edit+commit+push+merge 가능.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (각 건 사실 검증 후 판정).

---

# Goal

MONO-470 감사가 Tier-2 로 분류한 항목 중 **문서 결함**(사용처가 없는 phantom 코드·목적지 없는 § 인용·독자 오해 소지 있는 순서 서술) 3건을 사실 검증 후 최소 수정한다. requirement/contract semantics 무변경.

---

# Scope

## In Scope (사실 검증 완료한 3 결함)

1. **3.1 — `rules/domains/erp.md:78` `OPERATION_NOT_PERMITTED` → `PERMISSION_DENIED`.**
   - **검증**: `OPERATION_NOT_PERMITTED` 는 **Java emitter 0건**(전 저장소 grep), erp.md:78 외 등장 0, platform 레지스트리 미등록 → erp.md 자신의 "코드는 error-handling.md 에 먼저 등록" 규칙 위반 phantom. 같은 개념의 등록 코드 = `PERMISSION_DENIED`(error-handling.md:874, "erp-local emission (E6)").
   - **판정(3-option)**: (a) 삭제 — Cross 섹션 유일 항목이라 섹션째 사라짐, "운영자/계정" 뉘앙스 유실 / (b) **rename→`PERMISSION_DENIED`** ← 채택, 뉘앙스 보존 + 등록코드 참조 / (c) 광범위 재구조화 — 과함. Cross 섹션이 cross-cutting 코드용이고 `PERMISSION_DENIED` 가 실제 cross-cutting(error-handling.md:602/874 이 cross-service/context 명시)이라 (b) 정합. 위 Authorization 섹션(:73)과 동일코드임을 인라인 명시(dup-audit 오탐 회피).
2. **DR2 — `platform/error-handling.md:895` dead § 인용 제거.** `rules/domains/erp.md § Internal Event Catalog` 인용하나 실제 heading 은 한국어 `### 내부 이벤트 카탈로그 (권장)`(영어 heading 부재) → 목적지 없는 인용. `§ Internal Event Catalog` → `'s internal event catalog`(자연스러운 영문, § 형식 인용 폐기). 나머지 ~90 § 인용은 전부 resolve(감사 확인).
3. **4.1 — `rules/README.md:64` Resolution Order step 6 오해소지 해소.** step 5(Service-Type)가 step 6("platform/ 나머지 — **Core**/Auxiliary")보다 앞선 번호라 "Service-Type 이 Core 보다 먼저" 로 오독 가능. **검증**: common.md 의 canonical-14 가 **Core 5종 전부 포함**(architecture #1·service-boundaries #3·dependency-rules #4·security-rules #10·shared-library-policy #11) → step 2 common 로드가 이미 Core 를 올린다 = entrypoint.md 의 Core→Service-Type→Auxiliary 와 **모순 아님**. step 6 label 을 "Auxiliary" 로 바로잡고 "Core 는 step 2 에 포함, 번호는 로드시점 아닌 계층열거" 명시.

## Out of Scope (판정: 별건/사용자 결정)

- **DUP1 (도메인 6파일 error-code 재기술 → 포인터화)** — 🔴 **사용자 결정 사안, 자동 리팩토링 부적절.** `rules/README.md § Index File Rule ⚠️`(L125-144)가 **정확히 이 상황을 경고**: 사본이 *갈라지지 않았으면* 중복은 병이 아니라 전파이고, 처방은 삭제가 아니라 "정경 선언 + 인라인 근거 유지". 도메인 코드 목록은 아직 error-handling.md 와 갈라지지 않았음 → 포인터화(ecommerce 선례로 수렴)는 readability(도메인 로컬 한국어 설명) vs single-source 의 **설계 tradeoff**. 감사의 "ecommerce 로 수렴" 은 의견. 별도 결정 필요.
- **F4/F3/F2** (Change Rule 재배치/작성·when-to-adopt) = 재배치·신규작성. **2.3** table→list = 14행 전사. **3.2/3.3** = 저신뢰. **DR2 외 style**.

---

# Acceptance Criteria

- [x] **AC-1 (3.1)**: erp.md 에 `OPERATION_NOT_PERMITTED` 0건; `### Cross` 가 등록코드 `PERMISSION_DENIED` 참조.
- [x] **AC-2 (3.1 안전)**: `OPERATION_NOT_PERMITTED` Java emitter 0(grep) → contract 무영향 확증.
- [x] **AC-3 (DR2)**: error-handling.md 에 `§ Internal Event Catalog` dead 인용 0건.
- [x] **AC-4 (4.1)**: README step 6 이 Core 를 step 2 common 포함으로 명시, entrypoint 순서와 모순 없음 서술.
- [x] **AC-5 (의미 무변경)**: `git diff` = 3파일·3 insert/3 delete. requirement/contract semantics 무변경.
- [x] **AC-6 (참조 무손상)**: 변경 대상에 대한 잔여 dead 인용 0(grep). `claude-reference-integrity` 가드 GREEN.

---

# Related Specs

- `rules/domains/erp.md`(#1) / `platform/error-handling.md`(#1 등록코드 근거·#2 수정대상).
- `rules/README.md`(#3) / `rules/common.md`(canonical-14 Core 포함 근거) / `platform/entrypoint.md`(Core→Service-Type 순서 기준).

# Related Contracts

- None. `OPERATION_NOT_PERMITTED` emitter 0 = 어떤 서비스 계약도 이 코드 미방출. 문서 정합만.

---

# Edge Cases

- **3.1 이 erp.md 에 `PERMISSION_DENIED` 를 두 번 만든다** — :73(Authorization) + :78(Cross). 의도적: cross-cutting 코드가 두 bounded-context 에 유효(error-handling.md:602/874 이 cross-service 명시). :78 인라인에 "동일 코드" 명시로 dup-audit 오탐 차단.
- **4.1 이 순서를 실제로 바꾸나** — 아니오. 로드 시점(Core@step2)은 불변; label 정정 + 명시만. semantics 무변경.

---

# Failure Scenarios

- **phantom 코드가 실은 emit 됨** → AC-2 grep 으로 반증(0건).
- **DUP1 을 자동 처리** → README 자체 경고 위반. 명시적 Out of Scope.
- **다른 파일 수정** → AC-5 fail.

---

# Verification

- 2026-07-23, `task/mono-471-rules-refactor-tier2` 브랜치 (off `main` @ a8048ec57).
- 적용·검증 완료: erp.md OPERATION_NOT_PERMITTED 0·error-handling § 인용 0·README step 6 명시·diff 3/3·claude-ref 가드 GREEN·stale 인용 0.
- 3-dim merge 검증은 close chore 시.
- 분석·구현=Opus 4.8.
