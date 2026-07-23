# Task ID

TASK-MONO-475

# Title

규칙 리팩토링 잔여 — `platform/refactoring-policy.md` heading-level skip (H1→H3) 정합 (MONO-470 감사 F-class 잔여)

# Status

done

# Owner

monorepo (root tasks/ — shared `platform/`)

# Task Tags

- docs

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

- **origin**: `/refactor-spec platform rules --dry-run`(2026-07-23, MONO-470~474 아크 직후 재감사)의 유일 mechanical finding. `platform/refactoring-policy.md` 의 `# Rules`(H1) 아래 `### Mandatory`/`### Prohibited`(H3)가 H2 를 건너뜀 — MONO-470 이 `testing-strategy.md` G1~G9(`###`→`##`)에서 고친 것과 **동일 heading-level-skip 클래스**. 470 감사가 이 파일을 놓친 pre-existing 잔여(Tier-3).
- **F1/F5 defer 와의 차이**: MONO-472/474 가 heading-level cosmetic(F1/F5)을 defer 한 근거는 `hardstop-rules.md` 의 훅 결합(`hardstop-detect.ps1`+`hardstop-body-canonical-sync.ps1` 이 heading 구조 파싱). **본 파일엔 그 결합이 없다** — `refactoring-policy.md` 는 `check-claude-reference-integrity.sh` 에 경로/§Prioritization 인용·selftest 픽스처로만 등장하고 `### Mandatory`/`### Prohibited` **레벨을 파싱하지 않는다**. 그래서 이 인스턴스는 안전하게 정합 가능.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (구조 무손상 검증).

---

# Goal

`platform/refactoring-policy.md` 의 `# Rules`(H1) 하위 `### Mandatory`/`### Prohibited` 를 `## `(H2)로 승격해 heading-level skip 을 제거한다. 파일의 top-level 섹션 컨벤션(모든 주요 섹션 `# `)과 정합 — Rules 의 하위 두 절이 정확히 한 레벨 아래(H1→H2)에 위치.

---

# Scope

## In Scope

- `platform/refactoring-policy.md`: `### Mandatory` → `## Mandatory`, `### Prohibited` → `## Prohibited` (2줄, heading 마커만).

## Out of Scope

- **F1/F5 (`hardstop-rules.md` 등 훅 결합 파일의 heading 정규화)** — 훅 파싱 결합 위험으로 계속 defer(MONO-472 조사).
- **rule 본문·순서·의미** — heading 마커 외 무변경.
- 다른 platform/ 파일(재감사에서 heading-skip 0 확인).

---

# Acceptance Criteria

- [ ] **AC-1**: `refactoring-policy.md` 에 H1→H3 skip 부재(`# Rules` 하위가 `## `).
- [ ] **AC-2 (의미 무변경)**: heading **텍스트**("Mandatory"/"Prohibited")와 rule 본문 byte-unchanged — 마커만 변경.
- [ ] **AC-3 (참조 무손상)**: heading 텍스트 불변이라 `#mandatory`/`#prohibited` anchor·`refactoring-policy.md#*` 인용 무손상(재감사: 그런 인용 0건). `check-claude-reference-integrity.sh` selftest+guard GREEN(경로·§Prioritization 인용 불변).
- [ ] **AC-4 (scope-lock)**: diff = `refactoring-policy.md` + task lifecycle 만.

---

# Related Specs

- `platform/refactoring-policy.md`(대상). sibling heading 컨벤션=platform/ 각 파일 top-level `# `.
- `scripts/check-claude-reference-integrity.sh`(경로·selftest 픽스처로 이 파일 참조 — 레벨 미파싱).

# Related Contracts

- None. 문서 구조. 계약 무영향.

---

# Edge Cases

- **heading 텍스트를 바꾸면 anchor 깨짐** → 마커(`###`→`##`)만 바꾸고 텍스트 보존(GitHub anchor 는 텍스트 파생, 레벨 무관 = MONO-470 선례).
- **다른 H1 섹션도 H3 자식 보유?** → 재감사 확인: `# Rules` 만 `### ` 자식 보유, 나머지 top-level 섹션은 flat.

---

# Failure Scenarios

- **rule 본문/순서 변경** → AC-2 위반. 마커만.
- **hardstop-rules.md 류 훅 결합 파일까지 끌어옴** → Out of Scope(F1/F5 defer 근거).

---

# Verification

- (미착수 — ready) 출처=MONO-470~474 아크 직후 `/refactor-spec platform rules --dry-run` 재감사(dead-ref 0·도메인7/trait9 섹션집합 균일·이 finding 1건뿐). 분석·구현=Opus 4.8.
