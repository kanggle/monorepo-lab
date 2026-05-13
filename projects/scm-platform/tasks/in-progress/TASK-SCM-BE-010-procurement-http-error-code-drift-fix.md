# Task ID

TASK-SCM-BE-010

# Title

`procurement-service` HTTP error code drift fix (`IDEMPOTENCY_KEY_MISMATCH` 409/422 + optimistic-lock CONFLICT/CONCURRENT_MODIFICATION, refactor-spec critical findings)

# Status

in-progress

# Owner

scm-platform

# Task Tags

- be
- procurement-service
- contract
- spec
- fix

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13) SCM audit Top 1-2 critical findings: **procurement-service 의 HTTP error code drift 2종**.

## drift 1 — `IDEMPOTENCY_KEY_MISMATCH`

- `projects/scm-platform/specs/services/procurement-service/architecture.md:611` 가 **HTTP 409** 명시.
- `projects/scm-platform/specs/contracts/http/procurement-api.md:95` + `:315` 가 **HTTP 422** 명시.

contract layer (procurement-api.md) 가 CLAUDE.md § Source of Truth Priority layer 6 (contracts) 로 canonical. architecture (layer 7) 가 contract 와 mismatch → cleanup 필요.

## drift 2 — optimistic-lock conflict code

- `procurement-api.md:323` 가 **`CONFLICT` 409** (general optimistic-lock).
- `procurement-service/architecture.md:625` 가 **`CONCURRENT_MODIFICATION` 409** (`OptimisticLockingFailureException` mapping).

같은 상황 (concurrent modification) 의 다른 error code name. consumer (admin / 다른 service) 가 어느 code 로 error handler 작성할 지 모름. 동일 의미 → 단일 표기.

fix path:
- drift 1: architecture.md 의 409 → 422 정정 (contracts canonical).
- drift 2: 두 spec 모두 단일 code 로 통일 — 더 명확한 `CONCURRENT_MODIFICATION` 권장 (general `CONFLICT` 보다 의미 명시적).

또 procurement-service 의 production code 의 actual emission 검증 + spec 과 일치 보장 (양방향).

provenance: SCM audit Top 1+2 critical findings. contract vs implementation wire-code consistency.

---

# Scope

## In Scope

### A. `IDEMPOTENCY_KEY_MISMATCH` drift 정정

`architecture.md:611` 의 HTTP 409 → 422 정정. contracts canonical.

### B. optimistic-lock code 통일

두 spec 의 code 통일:
- **옵션 1 (권장)**: `procurement-api.md:323` 의 `CONFLICT` → `CONCURRENT_MODIFICATION` 정정. 의미 명시.
- **옵션 2**: `architecture.md:625` 의 `CONCURRENT_MODIFICATION` → `CONFLICT` 정정. general code 사용.

옵션 1 권장 — `CONCURRENT_MODIFICATION` 이 optimistic-lock semantics 더 명확 + 향후 다른 409 conflict (예: business-rule conflict) 와 구분 가능.

### C. procurement-service production code 검증

`projects/scm-platform/apps/procurement-service/src/main/java/.../ExceptionHandler.java` (또는 `@ControllerAdvice` / `@RestControllerAdvice`) 의 emission 검증:
- `IdempotencyKeyMismatchException` → HTTP status code = ?
- `OptimisticLockingFailureException` → error code constant = ?

spec 과 일치 + integration test 갱신.

### D. 다른 SCM service 검토

inventory-visibility-service / gateway-service 도 같은 패턴 검증 (separate audit). 본 task scope 는 procurement-service 만.

## Out of Scope

- inventory-visibility-service / gateway-service 의 error code 검토 (별도 audit task).
- 다른 SCM contract (events, integration) 변경.
- `platform/error-handling.md` 의 generic mapping 변경.
- 5 project 의 다른 service 의 동일 pattern 검토 (cross-project audit 후보).

---

# Acceptance Criteria

### Impl PR

- [ ] `procurement-service/architecture.md:611` HTTP code 409 → 422 (IDEMPOTENCY_KEY_MISMATCH).
- [ ] `procurement-api.md:323` 또는 `architecture.md:625` 둘 중 하나의 optimistic-lock code 정정 (옵션 1 권장 = api.md 정정).
- [ ] procurement-service `@ControllerAdvice` 의 ExceptionHandler 가 spec 과 일치 보장.
- [ ] integration test (`Integration (scm-platform)`) PASS.
- [ ] CI self-CI 16/16 PASS.
- [ ] task lifecycle ready → in-progress → review.
- [ ] scm tasks/INDEX.md 동기.

### Close chore PR

- [ ] review → done, scm tasks/INDEX.md 동기.

---

# Related Specs

- `projects/scm-platform/specs/services/procurement-service/architecture.md` (수정 대상 line 611, 625).
- `projects/scm-platform/specs/contracts/http/procurement-api.md` (수정 대상 line 95, 315, 323).
- `platform/error-handling.md` (generic error code registry).
- `/refactor-spec all --dry-run` 2026-05-13 SCM audit Top 1+2 findings (consistency #1, #5).
- ADR-MONO-005 (saga policy) 의 idempotency rules — IDEMPOTENCY_KEY_MISMATCH semantics.

# Related Skills

`.claude/skills/backend/idempotency-key/SKILL.md` (관련 패턴).

---

# Related Contracts

- `procurement-api.md` 가 직접 수정 대상.

---

# Target Service

`procurement-service`.

---

# Architecture

SCM procurement-service 의 contract-implementation alignment.

---

# Implementation Notes

## IDEMPOTENCY_KEY_MISMATCH semantics

HTTP 422 (Unprocessable Entity) 가 idempotency key mismatch (같은 key 로 다른 body 가 요청됨) 의 RFC 9110 / 일반 관행. 409 (Conflict) 는 resource state 충돌. 422 가 더 의미 정확. contracts canonical.

## CONCURRENT_MODIFICATION vs CONFLICT

`CONFLICT` 는 generic 409, multiple cause 가능 (resource state / optimistic lock / unique constraint / business rule).
`CONCURRENT_MODIFICATION` 는 optimistic lock 특화. consumer 가 retry strategy 결정 시 더 정확 (concurrent retry 가능 vs business-rule conflict 는 retry 의미 없음).

## ExceptionHandler 패턴

`@RestControllerAdvice` 의 `@ExceptionHandler(OptimisticLockingFailureException.class)` 가 emit 하는 error code 확인. `error-handling.md` 의 registry 와 정렬.

```bash
grep -rn "OptimisticLockingFailureException\|IdempotencyKeyMismatchException\|IDEMPOTENCY_KEY_MISMATCH\|CONCURRENT_MODIFICATION\|CONFLICT" projects/scm-platform/apps/procurement-service/src/main/
```

---

# Edge Cases

- spec 정정 후 production code 도 update 필요 시: bundle 가능 또는 별도 commit (lifecycle PR Separation Rule 따라).
- consumer (gateway routing / 미래 external client) 가 specific code 인용 시: contract change as breaking → conventional commit `!:` marker.
- `error-handling.md` registry 에 `CONCURRENT_MODIFICATION` 정의 부재면 platform task 별도 author.

---

# Failure Scenarios

- ExceptionHandler 가 둘 다 emit 안 함 (generic 500 throw): production bug, 별도 BE task.
- consumer integration test 가 old code expect: contract change breaking, consumer fix bundle.
- platform error-handling.md 에 두 code 정의 부재: platform/ separate task.

---

# Test Requirements

- procurement-service `@ControllerAdvice` 가 spec 과 일치.
- IT (Testcontainers) 가 두 exception → spec code 매핑 검증.
- SCM Integration job PASS.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, scm tasks/INDEX.md 동기.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-13 SCM audit (9 file / 31 finding) Top 1+2 risk-weighted findings (contract vs architecture HTTP code drift).
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (spec edits + production code verify).
