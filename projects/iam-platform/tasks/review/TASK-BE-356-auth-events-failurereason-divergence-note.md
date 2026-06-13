# Task ID

TASK-BE-356

# Title

`auth-events.md` 에 `failureReason` enum ↔ HTTP code 분리-의도 노트 추가 — TASK-MONO-246 완결성 갭 보완 (security-service Kafka 계약 보호)

# Status

review

# Owner

iam-platform (auth-service 이벤트 계약)

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

- **origin**: TASK-MONO-246 완결성 갭. MONO-246 이 IAM auth-service HTTP 에러 code 를 `CREDENTIALS_INVALID` → `INVALID_CREDENTIALS` 로 통일하면서 login-failed 이벤트 `failureReason` enum 은 의도적으로 `CREDENTIALS_INVALID` 로 유지했다. 분리-의도는 `authentication.md` / `rules/domains/saas.md` / `AuthExceptionHandler` 주석엔 기록했으나 **정작 이벤트 계약 문서(`auth-events.md`) 자체엔 누락** — 미래 유지보수자가 두 문자열을 "버그" 로 오인해 통일할 위험.
- **prerequisite for**: nothing. 순수 doc-hygiene.
- **scope**: project-internal (iam-platform `specs/contracts/events/auth-events.md` 단일 파일). shared path 무관 → iam project task.
- **model**: 분석=Opus 4.8 / 구현 권장=Haiku 4.5 (1줄 doc 노트).

---

# Goal

`auth-events.md` 의 `auth.login.failed` 페이로드 `failureReason` 필드에, 그 enum 값 `CREDENTIALS_INVALID` 가 HTTP 응답 code `INVALID_CREDENTIALS`(TASK-MONO-246 통일) 와 **별개 계약**임을 명시하는 노트를 추가하여, 두 네임스페이스를 통일하려는 미래의 잘못된 "fix" 를 차단한다.

---

# Scope

## In Scope

- `projects/iam-platform/specs/contracts/events/auth-events.md` — `auth.login.failed` 의 **필드 노트** 섹션에 `failureReason` 항목 1개 추가: enum 값 `CREDENTIALS_INVALID` 은 security-service 가 소비하는 독립 Kafka 계약이며, 자격증명 실패의 HTTP code `INVALID_CREDENTIALS`(MONO-246) 와 의도적으로 다른 네임스페이스임 — 통일 금지.

## Out of Scope

- 이벤트 enum 값 자체 변경 — **불변**(security-service 소비 계약).
- HTTP code 표면 — MONO-246 에서 이미 통일 완료.
- 크로스프로젝트 이벤트 reason 불일치(ecommerce `auth-events.md` 는 `INVALID_CREDENTIALS` reason 사용) — 별개 pre-existing 사안, 별도 결정 필요. 본 task 무관.

---

# Acceptance Criteria

- [x] **AC-1**: `auth-events.md` `auth.login.failed` 필드 노트에 `failureReason` 분리-의도 + "두 문자열을 통일하지 말 것" + `TASK-MONO-246` 참조 추가됨.
- [x] **AC-2**: enum 값(`CREDENTIALS_INVALID | ACCOUNT_LOCKED | ...`) payload schema 불변 — 노트만 추가.
- [x] **AC-3 (scope-lock)**: `git diff origin/main` = `auth-events.md`(노트 1줄) + task lifecycle 만.
- [x] **AC-4**: 신규 broken-ref 0.

---

# Related Specs

- `projects/iam-platform/specs/contracts/events/auth-events.md` — 편집 대상.
- `projects/iam-platform/specs/features/authentication.md` — 이미 분리-의도 기록(상호 정합).
- `tasks/done/TASK-MONO-246-...` (root) — origin.

# Related Contracts

- `auth.login.failed` 이벤트 — 노트만 추가, 스키마/enum 무변경.

---

# Edge Cases

- **노트가 enum 을 바꾼 것으로 오해** — 노트는 설명 텍스트, payload schema 의 enum 문자열은 그대로.

# Failure Scenarios

- **enum 값을 INVALID_CREDENTIALS 로 바꿔버림** → security-service consumer 계약 breaking. 노트만 추가, 값 불변.
- **다른 파일 수정** → AC-3 fail.

---

# Verification

- 미수행(ready). 구현 시 `git diff origin/main --stat` = auth-events.md + task lifecycle.
- CI: `specs/**.md` = non-code path-filter → fast-lane GREEN 예상.
- 분석=Opus 4.8 / 구현=Opus 4.8.
