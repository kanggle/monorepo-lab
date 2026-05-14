# Task ID

TASK-MONO-091

# Title

platform/glossary.md Service Type row → INDEX.md pointer (governance drift 차단, refactor-spec Tier 2 F-08 closure)

# Status

done

# Owner

monorepo

# Task Tags

- monorepo
- platform
- glossary
- governance
- refactor-spec

---

# Goal

`/refactor-spec all --dry-run` (non-deadref audit) **Tier 2 F-08 closure** — `platform/glossary.md` L30 의 Service Type enumeration 이 `platform/service-types/INDEX.md` catalog (8 types) 과 drift (7 types enumerated, missing `identity-platform`). 1-line fix + governance drift class 차단.

**Finding**:
- `platform/glossary.md:30`: `**Service Type** | One of \`rest-api\`, \`event-consumer\`, \`batch-job\`, \`grpc-service\`, \`graphql-service\`, \`ml-pipeline\`, \`frontend-app\`. See \`service-types/INDEX.md\``
- `platform/service-types/INDEX.md`: 8 types including `identity-platform`.
- Per CLAUDE.md Source of Truth Priority: `service-types/INDEX.md` 는 platform/ catalog 의 source of truth, glossary 는 narrative description. Inline enumeration in 2 places creates drift class.

**Fix**: glossary 의 enumeration 제거, INDEX.md 가리키는 pointer 단일화. 추후 Service Type 추가 시 INDEX.md 만 갱신, glossary 자동 sync.

# Scope

## In Scope

- `platform/glossary.md` L30 — Service Type description rewrite (enumeration drop, pointer single-source).

## Out of Scope

- Tier 2 backlog 다른 finding: F-01+F-02 reservation shape (sibling emulation, separate task), F-06+F-07 outbox/processed_events schema authority (HIGH risk, ADR-level + libs touch, separate cycle).
- service-types/INDEX.md 자체 (이미 8 types 정합, source of truth 유지).

# Acceptance Criteria

- [x] `platform/glossary.md:30` Service Type row enumeration 제거, `service-types/INDEX.md` 단일 pointer (검증: `grep 'rest-api.*event-consumer' platform/glossary.md` = 0 hit).
- [x] Service Type 추가 시 glossary 갱신 필요 없는 구조 검증 — pointer 단일화로 future drift class 차단.
- [x] Production code / spec contract / requirement 0 변경 (glossary description polish only).
- [x] 다른 glossary entry 의 표현 일관성 유지 (pointer style 가 다른 entry 와 충돌 안 함).

# Related Specs

- `platform/glossary.md` (target)
- `platform/service-types/INDEX.md` (source of truth — 변경 안 함)
- `.claude/commands/refactor-spec.md` § Operational Patterns (Tier 2 closure pattern)

# Related Contracts

해당 없음.

# Target Service

해당 없음 — platform/ shared library polish.

# Edge Cases

- A: glossary 의 다른 entry 가 동일하게 enumeration + pointer 패턴 사용 시 일관성 검토 — 본 task 는 Service Type entry 만 처리.

# Failure Scenarios

- A: rewrite 후 reader 가 catalog 빠르게 보고 싶을 때 INDEX.md 까지 navigate 필요 — trade-off accepted (drift class 차단이 우선, 1-click navigation 가능).

# Validation Plan

1. Edit 후 `grep "rest-api.*event-consumer" platform/glossary.md` exit 1 (enumeration 제거 검증).
2. `git diff --stat` = 1 file / 1-2 line edit.
3. glossary 의 다른 entry 형식 unchanged.

# Implementation Notes

- monorepo-level shared 영역 → root `tasks/` (project-internal 아님).
- 2 commit / 1 branch: (1) ready/ task author, (2) 1-line Edit + lifecycle move ready/ → review/.
- branch name `task/mono-091-glossary-service-type-pointer-fix` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- TASK-BE-285 / BE-284 sibling precedent (mechanical batch + 1-line fix). 5th refactor-spec cycle task.

# Outcome

**Status: DONE** (PR #520 squash `114c4144`).

1-line fix + governance drift class 차단 — refactor-spec Tier 2 F-08 closure.

`platform/glossary.md:30`:
- Before: `One of \`rest-api\`, \`event-consumer\`, \`batch-job\`, \`grpc-service\`, \`graphql-service\`, \`ml-pipeline\`, \`frontend-app\`. See \`service-types/INDEX.md\`` (7 types enumerated, missing `identity-platform`)
- After: `A category from the canonical catalog. See \`service-types/INDEX.md\` for the authoritative list and selection rules`

**Verification**: `grep 'rest-api.*event-consumer' platform/glossary.md` = 0 hit (enumeration removed). INDEX.md 가 8 types 의 single source of truth, future Service Type 추가 시 glossary 자동 sync.

**CI**: 1 SUCCESS (`changes`) / 16 SKIPPED / 0 fail. platform/ shared 영역 변경이지만 `code-changed` filter false (markdown only) → libs/downstream flag AND composition false → 모든 build/test jobs SKIP. mergeStateStatus CLEAN.

**refactor-spec cycle progression**:

| # | Task | Scope | Category | Fix |
|---|---|---|---|---|
| 1-4 | BE-165/283/SCM-BE-013/BE-284 | deadref Tier 1+2+3 | mech+judg | 54 |
| 5 | BE-285 | WMS non-deadref Tier 1 | mech | 6 |
| 6 | **MONO-091 (this)** | platform Tier 2 governance | mech | 1 |

**Tier 2 backlog 잔여 (별 task)**: F-01+F-02 reservation shape (sibling emulation, ~300+ LOC additive) / F-06+F-07 outbox/processed_events schema authority (HIGH risk, libs/java-messaging cross-check, ADR-level).
