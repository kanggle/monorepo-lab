# Task ID

TASK-FIN-BE-019

# Title

`finance-account-events.md` 의 `failureCode` 필드에 HTTP 에러코드 어휘 재사용 문서화 노트 추가 — iam 정합성 작업의 finance 후속 (doc-only)

# Status

done

# Owner

finance-platform (account-service 이벤트 계약)

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

- **origin**: iam 정합성 작업(TASK-MONO-246 / BE-356) 직후, HTTP code ↔ event enum 네임스페이스 결합 부류를 finance 에서 조사하여 발견. iam 은 두 어휘가 **의도적으로 분리**(INVALID_CREDENTIALS vs CREDENTIALS_INVALID)였던 반면, finance 는 두 어휘가 **정렬(재사용)** — `failureCode` 가 HTTP 에러코드 문자열을 그대로 싣는다. 어느 쪽이든 계약 문서에 명시돼 있어야 소비자가 어휘를 안다.
- **prerequisite for**: nothing. 순수 doc-hygiene.
- **scope**: project-internal (finance-platform `specs/contracts/events/finance-account-events.md` 단일 파일). shared path 무관.
- **model**: 분석=Opus 4.8 / 구현 권장=Haiku 4.5 (1 노트).

---

# Goal

`finance.transaction.failed` 의 `failureCode` 필드가 **fintech HTTP 에러코드 문자열을 그대로 재사용**함(별도 event enum 아님)을 계약 문서에 명시하여, 소비자가 그 값 어휘(현재 `SANCTION_HIT`/`AML_SCREENING_REQUIRED`)를 HTTP 에러코드 레지스트리와 1:1 매핑할 수 있게 한다.

---

# Scope

## In Scope (emit 확인 완료)

- `projects/finance-platform/specs/contracts/events/finance-account-events.md` — `finance.transaction.settled/.completed/.failed` 페이로드 블록 뒤에 `failureCode` 노트 추가: `status: FAILED` 에만 존재, fintech HTTP 에러코드 문자열을 재사용(현재 `SANCTION_HIT`/`AML_SCREENING_REQUIRED`, 둘 다 `platform/error-handling.md` 의 422 코드 = `GlobalExceptionHandler` 매핑·`DomainErrors`), iam `failureReason`(분리 enum, MONO-246)과의 대비도 1줄.

## Out of Scope

- 이벤트 schema/값 변경 — 불변(노트만).
- HTTP 에러코드 표면 — 변경 없음.
- finance 의 기타 audit 잔여(done task 파일의 stale `IDEMPOTENCY_KEY_CONFLICT (422)` 표기 등) — done/ 이력 immutable, 무관.

---

# Acceptance Criteria

- [x] **AC-1**: `finance-account-events.md` 에 `failureCode` 가 fintech HTTP 에러코드 어휘 재사용임을 명시하는 노트 + 값 예시(`SANCTION_HIT`/`AML_SCREENING_REQUIRED`) + iam 대비(MONO-246) 참조 추가.
- [x] **AC-2**: payload schema/값 불변(노트만 추가).
- [x] **AC-3 (scope-lock)**: 변경 = `finance-account-events.md` + task lifecycle 만.
- [x] **AC-4**: green-wash 확인 — `ComplianceFailureRecorder` `markFailed("SANCTION_HIT"/"AML_SCREENING_REQUIRED")` + `GlobalExceptionHandler:52-53` 422 매핑 + `AccountEventPublisher:164` failureCode 발행.

---

# Related Specs

- `projects/finance-platform/specs/contracts/events/finance-account-events.md` — 편집 대상.
- `platform/error-handling.md` fintech 섹션 — `SANCTION_HIT`/`AML_SCREENING_REQUIRED` HTTP 코드.
- `tasks/done/TASK-MONO-246-...` / `projects/iam-platform/tasks/done/TASK-BE-356-...` — 대비(분리 case).

# Related Contracts

- `finance.transaction.failed` 이벤트 — 노트만, schema 불변.

---

# Edge Cases

- **노트가 값을 enum 으로 고정한 것처럼 오해** — 노트는 "현재 값" 예시 + "HTTP 코드 어휘 재사용" 규칙. 새 실패코드 추가 시 HTTP 레지스트리에 등록된 코드를 쓰면 됨(노트가 그 규칙을 명시).
- **iam 과 혼동** — 노트에 iam 은 분리(diverge), finance 는 정렬(reuse)임을 대비하여 명시.

# Failure Scenarios

- **failureCode 를 별도 enum 으로 재정의** → HTTP 레지스트리와 이중관리. 노트는 재사용을 명문화하여 방지.
- **다른 파일 수정** → AC-3 fail.

---

# Verification

- 미수행(ready). emit 확인: `Transaction.markFailed("SANCTION_HIT"/"AML_SCREENING_REQUIRED")` + `GlobalExceptionHandler:52-53` 422 매핑 + `AccountEventPublisher:164` failureCode 발행.
- CI: `specs/**.md` only → fast-lane GREEN 예상.
- 분석=Opus 4.8 / 구현=Opus 4.8.
