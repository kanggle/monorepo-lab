# Task ID

TASK-MONO-244

# Title

`/validate-rules` 2026-06-13 잔존 Warning 1건 — `platform/error-handling.md` 코드 문자열 drift 4-cluster disposition (cluster별 의도적 alias 등록 vs 통합 vs follow-up 결정, green-wash 금지)

# Status

ready

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

- **origin**: `/validate-rules` full scan 2026-06-13 (read-only, PR #1467 머지 직후 재-scan). Critical 0, Warning 1, Info 0. PR #1467 이 Critical 1 + Warning 5 + Info 5 를 해소했고, 본 task = **유일 잔존 Warning** 인 error-handling 코드 문자열 drift 의 closure.
- **prerequisite for**: nothing (registry hygiene). `/validate-rules` Warning 0 달성이 목표.
- **execution constraint**: 수정 1차 대상은 `platform/error-handling.md` (shared, `.claude/` 아님 → agent edit+commit 가능, [[env_classifier_claude_self_mod_block]] 비대상). 단 일부 cluster 는 **구현 코드가 emit 하는 문자열** 이라 doc 단독 수정 시 spec↔code drift 위험 → §Scope 의 disposition 규칙 준수 필수.
- **model**: 분석=Opus 4.8 / 구현 권장=Opus 4.8 (error registry + contract 민감 판단; 단순 doc 아님). cluster 별 doc-only alias 등록 부분만 Sonnet 위임 가능.

---

# Goal

`/validate-rules` 가 보고한 잔존 Warning(error-handling.md 코드 문자열 drift)을 **명시적으로 dispositioned** 상태로 전환하여 rule 라이브러리를 Warning 0 으로 만든다.

현 상태: 동일 의미의 에러를 가리키는 코드 문자열이 여러 개 공존하고, 각각 인라인 주석으로 "intentional / pending standardization" 이라 적혀 있으나 **추적 태스크가 없어** validate-rules 가 floating drift 로 계속 보고한다. 본 task 는 4 cluster 각각에 대해 disposition 을 **결정·기록** 한다:

- **(B) document-as-alias** — 등록된 의도적 alias 임을 registry 표에 명시(코드 동작 무변경, doc-only, ADR 불요). MONO-151 의 `CONCURRENT_MODIFICATION` alias 등록과 동일 패턴.
- **(A) consolidate** — 한 문자열이 genuinely dead(아무 코드도 emit 안 함)일 때만 통합. 단 live emit 문자열 제거는 **contract 영향** → 별도 per-domain task/ADR 로 nominate(본 task out of scope).

green-wash 금지: emit 출처를 코드에서 실제 확인하지 않고 "통합했다" 고 적지 않는다.

---

# Scope

## In Scope

각 cluster 에 대해 (1) 코드 emit 출처 grep 확인 → (2) disposition 결정 → (3) (B)면 registry 주석/alias 행 명시, (A)·follow-up 이면 nomination 기록.

**WI-1 — Idempotency-key replay cluster (3 코드, 2 status).**
- `DUPLICATE_REQUEST`(409, Platform-Common General, L≈109) / `IDEMPOTENCY_KEY_MISMATCH`(422, scm, L≈405) / `IDEMPOTENCY_KEY_CONFLICT`(409, admin·fintech, L≈505/592).
- 트리거 의미가 사실상 동일(같은 Idempotency-Key + 다른 body/hash). disposition: Platform-Common `DUPLICATE_REQUEST` 를 canonical 로 두고, 도메인 코드 2개를 **등록된 의도적 alias** 로 주석 표기(422 vs 409 status 차이의 근거를 1줄로 명시 — 422=semantic body mismatch, 409=concurrency conflict). live emit 문자열은 제거하지 않음.

**WI-2 — Credentials cluster (2 코드, 1 status).**
- `INVALID_CREDENTIALS`(401, Platform-Common, L≈74) ↔ `CREDENTIALS_INVALID`(401, IAM domain, L≈459, 이미 "pending future standardization" 자인).
- disposition: 둘 다 emit 되면 (B) — IAM 행 주석을 "pending standardization" → "registered intentional alias of `INVALID_CREDENTIALS`" 로 확정(추적 floating 제거). 만약 한쪽이 dead 면 (A) 후보로 nominate(코드 확인 필요).

**WI-3 — fan-platform post-state cluster (2 코드, 1 status).**
- `POST_INVALID_STATE`(422) ↔ `POST_STATUS_TRANSITION_INVALID`(422, L≈545-546, "current code emits this string" 주석).
- disposition: 주석상 current code 는 `POST_STATUS_TRANSITION_INVALID` 만 emit → `POST_INVALID_STATE` 가 dead 후보. **fan-platform 코드 grep 으로 emit 0 확인 시** (A) 통합 가능하나 production code 수정 → fan-platform project task 로 nominate(본 root task 는 doc disposition 만; 코드 제거는 project 범위). 확인 전까지 registry 는 alias 명시 유지.

**WI-4 — DOWNSTREAM_ERROR dual-status (1 코드 문자열, 2 status).**
- `DOWNSTREAM_ERROR` 502(Platform-Common General, L≈130) vs 503(admin/saas, L≈501).
- 이미 양쪽 인라인 cross-note 존재. disposition: **document-as-intentional** 확정 — 동일 문자열 2 status 가 의도임을 표(또는 Change-note)에 1줄로 못박아 validate-rules dual-status 오탐을 회피. status 변경 없음.

## Out of Scope

- **live emit 문자열을 실제 제거/rename 하는 production code 변경** — contract(클라이언트가 code string 으로 분기) 영향 → 해당 도메인 project `tasks/ready/` 또는 ADR 로 별도 처리. 본 task 는 root(shared registry) 의 disposition 기록 + doc-only alias 등록까지.
- 새 HTTP status 도입, 기존 status 변경(422↔409, 502↔503 어느 쪽으로든) — 동작/contract 영향, 금지.
- 이전 스캔의 이미 반영된 Info 5건(423 행 / architect 문구 / validate-rules glob / design-event 예시 / database-designer skill 경로) — PR #1467 에서 closed, 재작업 없음.

---

# Acceptance Criteria

- [ ] **AC-1 (emit 확인 선행)**: 4 cluster 각 코드 문자열에 대해 repo-wide grep 으로 emit 출처(또는 dead 여부)를 확인하고 task 의 Verification/Implementation Notes 에 결과를 기록(green-wash 금지 — 미확인 통합 주장 불가).
- [ ] **AC-2 (WI-1)**: `DUPLICATE_REQUEST`/`IDEMPOTENCY_KEY_MISMATCH`/`IDEMPOTENCY_KEY_CONFLICT` 가 registry 에서 canonical↔alias 관계로 명시되고 422/409 status 차이 근거가 1줄로 기록됨.
- [ ] **AC-3 (WI-2)**: IAM `CREDENTIALS_INVALID` 행이 "registered intentional alias of `INVALID_CREDENTIALS`"(또는 (A) follow-up nomination)로 확정 — "pending standardization" floating 문구 제거.
- [ ] **AC-4 (WI-3)**: `POST_INVALID_STATE`↔`POST_STATUS_TRANSITION_INVALID` 가 alias 로 명시되거나, emit 0 확인 시 fan-platform project task 로 (A) 통합이 nominate 됨(둘 중 하나, 기록 필수).
- [ ] **AC-5 (WI-4)**: `DOWNSTREAM_ERROR` 502/503 dual-status 가 의도임이 registry 에 명시됨.
- [ ] **AC-6 (scope-lock)**: `git diff origin/main` 이 `platform/error-handling.md` + task lifecycle 파일만 건드림(production code·contract 0). (A) 통합이 필요한 cluster 는 nomination 으로만 남김.
- [ ] **AC-7**: 재-scan 시 error-handling 코드 문자열 drift Warning 재현 안 됨(Critical 0 / Warning 0). 남은 (A) 후보는 별도 project task/ADR 로 추적되어 floating 아님.

---

# Related Specs

> **Before reading**: monorepo-level shared-registry task. `platform/error-handling.md` 자체가 단일 authoritative 에러 레지스트리(자체 "Change protocol"/"Change Rule" 보유). 코드 emit 확인을 위해 각 도메인 project 의 exception → code 매핑(서비스 코드)을 참조하되 **수정은 registry 만**.

- `platform/error-handling.md` — **편집 대상**(shared registry SoT). 4 cluster 의 alias/disposition 명시.
- `.claude/commands/validate-rules.md` — origin scan + post-check.
- 선례: `tasks/done/TASK-MONO-151-validate-rules-warnings.md`(`CONCURRENT_MODIFICATION` alias 등록 = (B) 패턴 원형), `tasks/done/TASK-MONO-106-external-timeout-error-code-registry.md`((A)/(B) disposition meta-principle), `tasks/done/TASK-MONO-051-error-handling-catalog-audit.md`.

# Related Skills

- `.claude/commands/refactor-spec.md` — registry drift reconciliation(primary).
- `.claude/commands/validate-rules.md` — post-check.

---

# Related Contracts

- None **directly edited**. 단 cluster 별 코드 문자열은 HTTP 에러 응답 body 의 `code` 필드로 클라이언트가 소비 — 그래서 live 문자열 제거는 contract 영향이며 본 task 에서 out of scope(nomination 처리). doc-only alias 등록은 contract 무변경.

---

# Edge Cases

- **alias 등록이 또 다른 functional-dup 으로 오탐** — MONO-151 처럼 설명 문구에 "Registered intentional alias of `<canonical>`" 명시하여 validate-rules 오탐 회피.
- **status 차이(422 vs 409, 502 vs 503)를 "통일"하려는 유혹** — 금지. status 는 contract. 차이의 *근거* 를 기록할 뿐 값을 바꾸지 않는다.
- **dead 로 보이는 코드가 실제 standalone-extraction 도메인/외부 fork 에서 emit** — grep 범위에 project 별 service 코드 포함, 외부 prototype 합류 잔재 주의([[project_ecommerce_import_readiness]]). 불확실하면 (A) 통합 대신 alias 유지 + follow-up nominate(보수적).

# Failure Scenarios

- **emit 미확인 상태로 "통합 완료" 기록** → green-wash(MONO-106 meta-principle 위반). AC-1 의 grep 증거 필수.
- **live emit 문자열을 root task 에서 직접 제거** → contract 영향을 shared registry 변경에 밀반입. (A) 는 nomination 까지만.
- **status 값 변경으로 "drift 해소"** → 동작/contract 회귀. 값 불변, 근거만 기록.
- **다른 파일 수정** → AC-6 fail; registry + task lifecycle 한정.

---

# Verification

- 미수행(ready). 구현 시: emit grep 결과표 + `git diff origin/main --stat`(error-handling.md + task lifecycle 한정) + 재-scan Warning 0 첨부.
- CI: `platform/*.md` = non-code path-filter → `changes` fast-lane GREEN 예상.
- 분석=Opus 4.8 / 구현 권장=Opus 4.8.
