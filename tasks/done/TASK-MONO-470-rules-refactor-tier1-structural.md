# Task ID

TASK-MONO-470

# Title

규칙 라이브러리 구조 리팩토링 Tier-1 — platform/ + rules/ 기계적 정합 4종 (의미 무변경)

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

- **origin**: `/refactor-spec platform+rules` 감사 2026-07-23 (3-agent 병렬 discovery). structure/consistency/duplication/dead-reference/orphan/naming/clarity 전 카테고리 스캔. orphan 0·file-level dead-link 0·markdown #anchor 0-broken. 수확은 structure/consistency 축.
- **prerequisite for**: 없음. Tier-2/3 은 각각 후속 task(아래 Out of Scope 열거).
- **execution constraint**: `platform/` + `rules/` = 공유 규칙 라이브러리. classifier block 대상 아님. agent edit+commit+push+merge 가능.
- **model**: 분석=Opus 4.8 (3 subagent sonnet) / 구현=Opus 4.8 (heading 정합, 의미 무변경).

---

# Goal

규칙 라이브러리(`platform/` + `rules/`)의 **의미를 바꾸지 않는** 구조·표기 정합 4종을 적용하여 sibling spec 간 일관성을 높인다. requirement/contract/decision 무변경 — heading-level·heading-label·citation·spacing 만.

---

# Scope

## In Scope (Tier 1 — 기계적, 의미 무변경, 9 파일)

1. **F7 — `platform/testing-strategy.md`: `### G1`–`### G9` → `## G1`–`## G9`.** H1(`# CI Guards...`)→H3 레벨 스킵 수정 + 파일 자체의 declarative-heading 관례(`## A test that bypasses...`)에 정합. **heading 텍스트 무변경 → `§ G4` prose 인용(메모리 등) anchor 그대로 유효** (slug 은 레벨 아닌 텍스트에서 생성). `####` 하위 없음 확인(cascade 불요).
2. **2.2 — `## Anti-patterns` → `## Forbidden Patterns` (5 파일):** `rules/domains/ecommerce.md` + `rules/traits/{batch-heavy,content-heavy,multi-tenant,read-heavy}.md`. 나머지 11 sibling(도메인 6 + trait 5)이 이미 "Forbidden Patterns" 다수관례 → 소수 5개 정합. 내용(금지 목록) 무변경. **anchor 안전 확인**: `#anti-patterns` 링크·`§ Anti-patterns` 인용 전 저장소 0건.
3. **2.4 — `외부(`/`내부(` → `외부 (`/`내부 (` (2 파일):** `rules/domains/saas.md`(×2)·`wms.md`(×2). 4 sibling 도메인(erp/fintech/scm/fan-platform)이 이미 공백 있음. 순수 표기.
4. **DR1 — `platform/lint-remediation-message-standard.md:88` citation `§ Classes and Interfaces` → `§ Classes`.** 실제 heading 은 `## Classes` 하나(인터페이스 명명도 그 아래; "Interfaces" heading 부재 grep 확인). 정경 예시가 없는 섹션을 가리키던 것 정정.

## Out of Scope (감사가 발견했으나 판단/신규내용 필요 — 후속 task 후보)

- **Tier 2 (판단 필요)**: DUP1(도메인 6파일이 `error-handling.md` 코드 재기술→포인터 전환, ecommerce 가 fixed 선례; 코드 누락 없는지 파일별 확인 필요) · 3.1(`erp.md` `OPERATION_NOT_PERMITTED`→`PERMISSION_DENIED`, live emitter 부재 grep 선행=contract-인접) · F4(4파일 trailing 섹션을 Change Rule 앞으로 재배치) · F3(notification-inbox `# Change Rule` 신규 작성) · F2(graphql when-to-adopt 추출) · 2.3(ecommerce Ubiquitous Language 표→불릿 14행) · 4.1(`rules/README.md` Resolution Order vs `entrypoint.md` Core-first 순서 모순 판정) · DR2(erp Korean heading `§` 인용 house-style) · 3.2/3.3(저신뢰).
- **Tier 3 (신규 내용/광역 blast)**: 1.1(ecommerce 누락 4섹션 authoring) · 1.2(trait Family-B 4파일 누락 3섹션 + Overrides 스텁 2.1) · F1(ml-pipeline/identity `#`→`##` umbrella) · F5(`##`-primary 3파일 `#` 승격) · 2.5/F8.

---

# Acceptance Criteria

- [ ] **AC-1 (F7)**: `testing-strategy.md` 에 `### G[0-9]` 0건 / `## G[0-9]` 9건. G4 등 heading 텍스트 무변경.
- [ ] **AC-2 (2.2)**: 5 파일에 `## Anti-patterns` 0건, `## Forbidden Patterns` 각 1건. 하위 불릿 무변경.
- [ ] **AC-3 (2.4)**: saas/wms 의 `### 외부 (`·`### 내부 (` 공백 정합.
- [ ] **AC-4 (DR1)**: lint-remediation 예시가 `§ Classes` 인용.
- [ ] **AC-5 (의미 무변경)**: `git diff` 가 19 insert / 19 delete(대칭) — 모두 heading/citation/spacing swap, 내용 추가·삭제 0.
- [ ] **AC-6 (참조 무손상)**: 저장소에 `§ Anti-patterns`·`Classes and Interfaces`·`#anti-patterns` 잔여 인용 0 (rename 후 grep). `claude-reference-integrity` 가드 GREEN(파일 rename 없음).
- [ ] **AC-7 (scope-lock)**: diff = 위 9 파일 + task lifecycle 만.

---

# Related Specs

- `platform/testing-strategy.md`·`platform/lint-remediation-message-standard.md`·`platform/naming-conventions.md`(citation 대상).
- `rules/domains/{ecommerce,saas,wms}.md`·`rules/traits/{batch-heavy,content-heavy,multi-tenant,read-heavy}.md`.
- `platform/README.md` sibling-consistency 관례(Forbidden Patterns 다수형).

# Related Contracts

- None. 문서 구조/표기만. contract value·error-code semantics 무변경(3.1 은 Out of Scope).

---

# Edge Cases

- **`§ G4` 인용이 레벨 변경으로 깨지나?** — 아니오. anchor slug 은 heading 텍스트 기반, 레벨 무관. 텍스트 무변경 → 유효.
- **`Anti-patterns` anchor 를 누가 링크하나?** — grep 결과 0(모든 "anti-pattern"은 일반 산문). rename 안전.
- **DR1 이 코드블록 내 예시** — lint-remediation 의 canonical 메시지 예시. 없는 섹션을 가리키던 예시를 실섹션으로 정정(예시 품질 개선, 의미 무변경).

---

# Failure Scenarios

- **rename 이 anchor 깨뜨림** → AC-6 grep 으로 사전 차단(0건 확인).
- **의미 변경 혼입** → AC-5 대칭 diff 로 감시(19/19).
- **다른 파일 수정** → AC-7 fail.

---

# Verification

- 2026-07-23, `task/mono-470-rules-refactor-tier1` 브랜치 (off `main` @ b21151ad6).
- 적용 완료. `git diff --stat` = 9 파일·19/19 대칭. `## G` 9/`### G` 0·Anti-patterns 0·stale citation 0·claude-ref 가드 GREEN 로컬 확인.
- 3-dim merge 검증은 close chore 시.
- 분석·구현=Opus 4.8.
