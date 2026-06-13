# Task ID

TASK-MONO-245

# Title

`platform/error-handling.md` dead `POST_INVALID_STATE` 레지스트리 행 제거 — TASK-MONO-244 WI-3 deferred follow-up 종결 (코드 emit 0 + 소비처 0 확정)

# Status

review

# Owner

monorepo (root tasks/ — shared `platform/error-handling.md`)

# Task Tags

- refactor

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

- **origin**: TASK-MONO-244 WI-3 deferred follow-up. MONO-244 는 `POST_INVALID_STATE`(dead, src/main emit 0)를 보수적으로 registered alias 로 **annotate** 만 했고, 실제 행 제거를 follow-up 으로 nominate. 본 task = 그 제거.
- **scope correction**: MONO-244 본문은 제거를 "fan-platform project task" 로 표기했으나, 제거 대상은 **공유 레지스트리 행**(`platform/error-handling.md`)이며 fan-platform 코드는 변경 0 → 실제로는 **root(monorepo) 스코프**. 따라서 root tasks/.
- **prerequisite for**: nothing.
- **execution constraint**: `platform/error-handling.md` (shared, `.claude/` 아님 → agent edit+commit 가능).
- **model**: 분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (단순 dead-row 제거 + 1 cross-ref 정리).

---

# Goal

MONO-244 가 dead 로 확정한 `POST_INVALID_STATE` 레지스트리 행을 제거하여 중복을 실제 해소한다. MONO-244 는 contract 영향 우려로 annotate(보수적 보존)에 그쳤으나, 후속 repo-wide grep 으로 **소비처 0** 이 확정되어 안전한 제거가 가능해졌다.

제거 후: `POST_STATUS_TRANSITION_INVALID`(canonical, live emit) 단일 행만 남고, 동일-의미 2행 중복이 사라진다.

---

# Scope

## In Scope

1. **`platform/error-handling.md` — `POST_INVALID_STATE`(L548) 행 삭제.**
   - 근거: repo-wide grep 결과 `POST_INVALID_STATE` 는 `error-handling.md` 레지스트리 2행 + `tasks/done/TASK-MONO-244...`(이력 문서)에만 존재. **service 코드(src/main) / contract / frontend / test / spec 소비처 0.**
2. **`POST_STATUS_TRANSITION_INVALID`(L549) 행의 dangling cross-ref 정리.**
   - 현재 설명: "...`POST_INVALID_STATE` (previous row) is its registered, currently-unemitted alias (TASK-MONO-244)". L548 삭제 시 이 참조가 dangling → 해당 alias 언급 제거하고 canonical 단독 설명으로 정리.

## Out of Scope

- 다른 cluster(idempotency / credentials / DOWNSTREAM_ERROR) — MONO-244 에서 (B) alias 로 종결됨(전부 live), 변경 0.
- `tasks/done/TASK-MONO-244...` 문서 — done/ 이력 immutable, `POST_INVALID_STATE` 언급은 당시 disposition 의 정당한 historical record → 무변경.
- production code / contract / status 값 — 변경 0.

---

# Acceptance Criteria

- [x] **AC-1 (소비처 0 재확인)**: 제거 후 `grep "POST_INVALID_STATE"` = `error-handling.md` **0건**. 잔여는 `tasks/done/TASK-MONO-244...`(이력) + 본 task 문서뿐(정당한 historical record).
- [x] **AC-2**: `POST_INVALID_STATE` 레지스트리 행 삭제됨.
- [x] **AC-3**: `POST_STATUS_TRANSITION_INVALID` 행이 canonical 단독 설명으로 정리됨(dangling alias 참조 0; 코드명 prose 언급도 제거하여 registry clean).
- [x] **AC-4 (scope-lock)**: `git diff origin/main` = `platform/error-handling.md`(2행 → 1행) + task lifecycle 만.
- [x] **AC-5**: 신규 broken-ref / dangling alias 0 (canonical 행이 삭제된 코드를 더 이상 참조 안 함).

---

# Related Specs

- `platform/error-handling.md` — 편집 대상(shared registry SoT).
- `tasks/done/TASK-MONO-244-error-handling-code-string-drift-disposition.md` — origin disposition(WI-3) + emit-0 증거.

# Related Skills

- `.claude/commands/refactor-spec.md` — registry hygiene.
- `.claude/commands/validate-rules.md` — post-check.

---

# Related Contracts

- None. `POST_INVALID_STATE` 는 어떤 contract surface 에서도 소비되지 않음(grep 0) → 제거에 contract 영향 없음. live 코드는 `POST_STATUS_TRANSITION_INVALID` 만 emit(무변경).

---

# Edge Cases

- **standalone-extraction / 외부 fork 에서 `POST_INVALID_STATE` 참조 가능성** — 현 monorepo grep 0. standalone 배포본은 monorepo 에서 파생되므로 동일. 신규 참조 발생 시 그때 canonical 행을 가리키면 됨(제거가 막지 않음).
- **done/ 문서의 언급이 dangling 으로 보임** — 아님. done/ 은 immutable 이력이고, 당시 행이 존재했음을 기록한 정당한 historical reference. validate-rules 는 done/ 이력의 코드명 언급을 broken-ref 로 보지 않음.

# Failure Scenarios

- **canonical 행(`POST_STATUS_TRANSITION_INVALID`)까지 삭제** → live emit 코드의 레지스트리 누락 = 파일 규칙("사용 전 등록") 위반. 삭제 대상은 dead row 단 1개.
- **cross-ref 정리 누락** → L549 가 삭제된 행을 계속 가리켜 dangling. AC-3 필수.
- **다른 파일 수정** → AC-4 fail.

---

# Verification

- `POST_INVALID_STATE`(L548) 행 삭제 + `POST_STATUS_TRANSITION_INVALID`(L549→L548) 행을 canonical 단독 설명으로 정리(dangling alias 참조 제거).
- 제거 후 `grep "POST_INVALID_STATE"`: `error-handling.md` 0건; 잔여 = `tasks/done/TASK-MONO-244...` + 본 task(이력).
- `git diff origin/main` = `platform/error-handling.md`(net -1 row) + task lifecycle 한정.
- CI: `platform/*.md` = non-code path-filter → `changes` fast-lane GREEN 예상.
- 분석=Opus 4.8 / 구현=Opus 4.8.
