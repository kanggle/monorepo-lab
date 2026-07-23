# Task ID

TASK-MONO-476

# Title

`CLAUDE.md` heading-level 정규화 — all-H1 → H1 제목 + H2 섹션 (저장소 컨벤션 정합)

# Status

review

# Owner

monorepo (root tasks/ — shared `CLAUDE.md`)

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

- **origin**: 사용자 요청 "claude.md 파일 리팩토링" 의 refactor-spec discovery 결과. CLAUDE.md 는 mechanical clean(dead-ref 0·anchor 전부 유효·"8 active" 정확·heading-skip 0)이나, 제목+12개 섹션이 **전부 H1**(13 H1 / 0 H2) — 저장소의 다른 모든 문서(`platform/`·`rules/`)가 쓰는 **H1 제목 + H2 섹션** 컨벤션과 어긋남.
- **안전성(측정)**: heading **레벨** 변경은 무손상 — (a) hook(`hardstop-detect`·`spec-check`·`warn-shared-checkout-switch`)은 CLAUDE.md 를 **섹션 이름**(`§ Hard Stop Rules` 등)으로 참조하거나 `Test-Path` 존재확인만 함, **레벨 미파싱**(Select-String/Get-Content 파싱 0건 측정); (b) GitHub anchor 는 텍스트 파생이라 `#hard-stop-rules` 등 불변; (c) classifier-blocked `.claude/hooks/` 은 건드릴 필요 없음(이름참조라 무영향).
- **F1/F5 defer 와 차이**: MONO-472/474 가 defer 한 F1/F5 는 `hardstop-rules.md`(hardstop-detect + body-canonical-sync 가 heading **구조** 파싱, classifier-blocked)에 한정. CLAUDE.md 는 그 구조 파싱 결합이 없음. MONO-475(refactoring-policy.md) 와 같은 안전 인스턴스.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8.

---

# Goal

`CLAUDE.md` 의 12개 섹션 heading 을 `#`(H1) → `##`(H2)로 승격하고 제목 `# CLAUDE.md` 만 H1 로 유지해, 저장소 전역 컨벤션(H1 제목 1개 + H2 섹션)과 정합한다. heading **마커**만 변경 — 섹션 텍스트·규칙 본문·순서 전부 불변.

---

# Scope

## In Scope

- `CLAUDE.md` 12개 섹션 heading `# ` → `## ` (Repository Layout / Identify the Target Project / Project Classification / Core Principles / Source of Truth Priority / Task Rules / Required Workflow / Hard Stop Rules / Layer Rules / Cross-Project Changes / Local Network Convention / Recommending Tasks and Dispatching Agents). 제목 `# CLAUDE.md` 는 H1 유지.

## Out of Scope

- **규칙 의미·순서·문구** — heading 마커 외 무변경(refactor-spec: no requirement/rule change).
- **catalog 포인터 dedup** — CLAUDE.md 는 명시적 "Catalog + safety net"(의도적 restatement), dedup 대상 아님.
- **F1/F5(`hardstop-rules.md` 등 구조-파싱 hook 결합 파일)** — 계속 defer.
- 예시 모델버전(L207 "Opus 4.7/Sonnet 4.6") 등 minor staleness — 별건(내용 변경 소지).

---

# Acceptance Criteria

- [ ] **AC-1**: CLAUDE.md heading = H1 **1개**(`# CLAUDE.md` 제목) + H2 **12개**(섹션). H1-only 아님.
- [ ] **AC-2 (의미 무변경)**: diff = 정확히 12개 `# X`→`## X`, 섹션 **텍스트·규칙 본문 byte-unchanged**(numstat 12/12 순수 치환, 라인 수 불변).
- [ ] **AC-3 (참조 무손상)**: 섹션 텍스트 불변 → GitHub anchor(`#source-of-truth-priority` 등) 및 hook 의 `§ <섹션명>` 이름참조 전부 유효. anchor 인용처(있으면) 무손상.
- [ ] **AC-4 (scope-lock)**: diff = `CLAUDE.md` + task lifecycle 만.

---

# Related Specs

- `CLAUDE.md`(대상). sibling heading 컨벤션 = `platform/*.md`·`rules/**/*.md` (전부 H1 제목 + H2 섹션).
- `.claude/hooks/{hardstop-detect,spec-check,warn-shared-checkout-switch}.ps1`(CLAUDE.md 를 이름/존재로 참조 — 레벨 미파싱, 무영향).

# Related Contracts

- None. 문서 구조. 계약 무영향.

---

# Edge Cases

- **섹션 텍스트를 바꾸면 anchor·hook 이름참조 깨짐** → 마커(`#`→`##`)만 바꾸고 텍스트 보존(GitHub anchor 는 텍스트 파생, 레벨 무관 = MONO-470/475 선례).
- **body 의 inline `# complex`/`# routine fix`(Agent 예시 주석)** → line-start 아님, `^# ` 매치 안 됨(미변경 확인).
- **code fence 내 tree(`├── CLAUDE.md`)** → `^# ` 아님, 무영향.

---

# Failure Scenarios

- **규칙 문구/순서 변경** → AC-2 위반. 마커만.
- **제목까지 H2 로** → AC-1 위반(제목은 H1 유지).
- **hardstop-rules.md 등 구조-파싱 hook 결합 파일까지 확대** → Out of Scope.

---

# Verification

- (미착수 — ready) 출처=사용자 "claude.md 리팩토링" 요청 + refactor-spec discovery(mechanical clean, 유일 후보=heading 레벨 컨벤션). 사용자 결정="heading 정규화 진행". 분석·구현=Opus 4.8.
