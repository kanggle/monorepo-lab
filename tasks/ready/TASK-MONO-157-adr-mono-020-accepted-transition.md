# Task ID

TASK-MONO-157

# Title

ADR-MONO-020 PROPOSED → ACCEPTED 승급 (doc-only governance flip). D3-B operator multi-customer assignment + active-tenant 토큰 스코핑 결정 방향(D1-D6)을 byte-unchanged finalise. sibling ADR-019→MONO-153 / ADR-014→MONO-110 / ADR-017→MONO-126 staged-child ACCEPTED 패턴 답습.

# Status

ready

# Owner

backend

# Task Tags

- docs
- architecture

---

# Dependency Markers

- **depends on**: ADR-MONO-020 PROPOSED(TASK-MONO-156 #977 `3be2ba51`).
- **user-explicit-intent gate**: 2026-05-31 *"진행"* (ADR-020 PROPOSED 머지 후 "ADR-020 ACCEPTED 승급" 제안에 대한 선택) — sibling ADR-019 *"진행해"*(MONO-153) 동형.
- **staged-child 패턴**: ADR-014→MONO-110 / ADR-015→MONO-112 / ADR-017→MONO-126 / ADR-018→MONO-138 / ADR-019→MONO-153.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (doc-only governance flip, HARDSTOP-04 finalise 규율).

---

# Goal

ADR-MONO-020 의 `Status: PROPOSED → ACCEPTED`. D1-D6 CHOSEN-PROPOSED 방향을 **byte-unchanged finalise**(ACCEPTED = *finalise*, re-decide 아님; HARDSTOP-04). 이로써 ADR-020 § 3.3 execution roadmap 을 UNPAUSE — dependency-correct base 확립(ADR-019 step 4 extension entry: operator_tenant_assignment → assume-tenant exchange → console switcher).

# Scope

## In scope (doc-only)

1. `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md`:
   - `**Status:** PROPOSED` → `**Status:** ACCEPTED`.
   - `**History:**` 에 ACCEPTED clause append(TASK-MONO-157 — user-explicit "진행" gate, D1-D6 finalised byte-unchanged, sibling 패턴).
   - § 1 / 어떤 past-tense note 든 최소 조정(있을 경우; staged 언급의 미래형 → 과거형 minimal).
   - § 6 Status Transition History: PROPOSED row `#<this>` → `#977` 해소 + ACCEPTED row append; "ACCEPTED transition (post-PROPOSED)..." 노트 블록을 ACCEPTED 완료 반영(미래형→과거형).
   - **D1-D6 + § 2/3/4/5/7 body byte-unchanged**(ACCEPTED finalises, HARDSTOP-04).
2. `docs/adr/ADR-MONO-003a-...md` § 3 audit row #26 (ADR-020 ACCEPTED transition; sibling row #24 = ADR-019 ACCEPTED, row #23 = ADR-017 ACCEPTED; Meta-policy, does NOT add to § D1).
3. tasks/INDEX(MONO-157 done entry).

## Out of scope

- 구현(operator_tenant_assignment / assume-tenant exchange / console switcher) — ADR-020 § 3.3 future tasks(ACCEPTED 후 dependency-correct).
- D1-D6 body 변경(finalise = byte-unchanged).
- ADR-019/014/017/002 변경.

# Acceptance Criteria

- **AC-1**: ADR-020 `Status: ACCEPTED` + History ACCEPTED clause + § 6 ACCEPTED row(+ `#977` 해소).
- **AC-2 (HARDSTOP-04 finalise)**: D1-D6 + § 2/3/4/5/7 byte-identical to PROPOSED(diff = Status + History append + § 6 row/note + minimal past-tense 만).
- **AC-3**: ADR-003a § 3 row #26 (sibling #24 패턴).
- **AC-4 (doc-only)**: code 0. docs fast-lane.
- **AC-5**: 효과 = ADR-020 § 3.3 execution roadmap UNPAUSED(step 1 operator_tenant_assignment dependency-correct base).

# Related Specs

- `docs/adr/ADR-MONO-020-...md`(flip 대상) + `ADR-MONO-019` § 3.3 step 0 패턴(ACCEPTED 도 audit row) + sibling MONO-153.

# Related Contracts

- 결정 finalise 만 — contract 변경 없음.

# Related Code

- (참조만, 무변경) ADR-020 § 3.3 가 지정하는 future 구현 대상.

# Edge Cases

- **HARDSTOP-04**: ACCEPTED = finalise. D1-D6 한 글자도 변경 금지(sibling ADR-019 ACCEPTED 동형).
- **staged**: 본 task 는 ACCEPTED flip 만 — 구현 금지.
- `#<this>` 해소: PROPOSED publish = #977.

# Failure Scenarios

- D1-D6 re-decide/수정 → HARDSTOP-04 위반 → byte-unchanged only.
- 구현 포함 → scope 위반.

---

# Implementation Design Notes

- sibling MONO-153(ADR-019 ACCEPTED) 정확 답습: Status flip + History append + § 6 ACCEPTED row + 미래형→과거형 minimal + ADR-003a row. docs fast-lane.
- 구현 = Opus(governance flip).

---

# Notes

- ADR-020 ACCEPTED → § 3.3 execution roadmap UNPAUSED = ADR-019 step 4 extension(D3-B) 실행 entry point. 후속 = operator_tenant_assignment(D1) + assume-tenant exchange(D2) + console switcher(D4) future tasks, dependency-correct base = 본 ACCEPTED main. root `docs/adr/` = monorepo-level.
