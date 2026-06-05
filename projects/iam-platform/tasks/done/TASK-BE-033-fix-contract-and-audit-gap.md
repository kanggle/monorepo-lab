# Task ID

TASK-BE-033-fix-contract-and-audit-gap

# Title

admin-service — TASK-BE-033 후속: 계약 불일치 + CB OPEN 감사 누락 수정

# Status

ready

# Owner

backend

# Task Tags

- code
- contract

# depends_on

- TASK-BE-033

---

# Goal

TASK-BE-033 리뷰에서 발견된 3개 이슈를 수정한다.

1. **계약 불일치 (Critical)**: `admin-api.md`의 단일 lock/unlock (`POST /lock`, `POST /unlock`) 및 session revoke (`POST /sessions/{accountId}/revoke`) 엔드포인트가 여전히 `502 DOWNSTREAM_ERROR`를 반환하도록 명시되어 있으나, 구현은 `503`으로 변경됨. 계약을 503으로 갱신하고 `CIRCUIT_OPEN` 케이스도 추가.

2. **에러 코드 미등록 (Critical)**: `CIRCUIT_OPEN` 에러 코드가 `platform/error-handling.md`의 플랫폼 에러 레지스트리에 등재되지 않음. `DOWNSTREAM_ERROR`도 HTTP 상태가 502→503으로 바뀌었으므로 레지스트리를 수정해야 함.

3. **CB OPEN 시 감사 누락 (Critical)**: `AccountAdminUseCase.lock/unlock`과 `SessionAdminUseCase.revoke`는 `DownstreamFailureException`만 catch하여 `outcome=FAILURE` 감사 기록을 남긴다. CB OPEN 시 throw되는 `CallNotPermittedException`은 catch되지 않아 `admin_actions` FAILURE 행이 기록되지 않음. 아키텍처 규칙(A10, fail-closed)과 태스크 Acceptance Criteria 위반.

---

# Scope

## In Scope

- `specs/contracts/http/admin-api.md`: lock/unlock/revoke 엔드포인트 오류 표 502→503 갱신, `CIRCUIT_OPEN` 503 케이스 추가
- `platform/error-handling.md`: `DOWNSTREAM_ERROR` 502→503 수정, `CIRCUIT_OPEN 503` 신규 등재
- `AccountAdminUseCase.java`: `CallNotPermittedException` catch 추가 → `outcome=FAILURE` 감사 기록 후 rethrow
- `SessionAdminUseCase.java`: 동일

## Out of Scope

- retry jitter 미설정 (Warning 수준, 별도 개선 가능)
- `@CircuitBreaker` 어노테이션 순서 (Suggestion 수준)

---

# Acceptance Criteria

- [ ] `admin-api.md`: lock/unlock/revoke 엔드포인트 오류 표에 502 항목이 없고, 503 `DOWNSTREAM_ERROR` + 503 `CIRCUIT_OPEN` 항목이 존재
- [ ] `platform/error-handling.md`: `DOWNSTREAM_ERROR` → 503, `CIRCUIT_OPEN` 503 신규 등재
- [ ] CB OPEN 상태에서 lock/unlock/revoke 호출 시 `admin_actions` 테이블에 `outcome=FAILURE` 행 기록

---

# Related Specs

- `specs/services/admin-service/architecture.md` (A10 fail-closed)
- `rules/traits/audit-heavy.md` (A10)

# Related Contracts

- `specs/contracts/http/admin-api.md`
- `platform/error-handling.md`

---

# Edge Cases

- `CallNotPermittedException`은 `DownstreamFailureException`의 subclass가 아니므로 별도 catch 블록 필요

# Failure Scenarios

- catch 순서 오류로 `CallNotPermittedException`이 `RuntimeException` 블록에 흡수되면 감사 누락 재발

---

# Test Requirements

- Unit: `AccountAdminUseCase` — CB OPEN(`CallNotPermittedException`) 시 audit row FAILURE 기록 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Ready for review
