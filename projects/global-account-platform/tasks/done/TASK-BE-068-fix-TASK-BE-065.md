# Task ID

TASK-BE-068

# Title

fix(account-service): TASK-BE-065 리뷰 후속 — error-handling.md 등록 + rollback 통합 테스트 assertion 교정

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- spec

# depends_on

- TASK-BE-065

---

# Goal

TASK-BE-065 리뷰에서 드러난 2건의 gap 수정:

1. **Critical**: `AUTH_SERVICE_UNAVAILABLE` 에러 코드가 `GlobalExceptionHandler` 와 `account-api.md` 에서는 사용되지만 `platform/error-handling.md` 레지스트리에 등록 안 됨. 해당 문서의 Rules: "Error codes must be registered in this document before use." 위반.
2. **Warning**: `SignupRollbackIntegrationTest` 의 profile rollback 검증 assertion 이 무의미(`profileRepository.findByAccountId("nonexistent-guard").isEmpty()` — 어떤 hardcoded non-existent id 든 항상 empty 반환). 실제 요청 email 의 profile 행이 롤백됐는지 확인 안 됨.

추가로: 통합 테스트가 로컬 Docker 미가용으로 skipped — CI 에서 최초 실행 시 green 확보 확인.

---

# Scope

## In Scope

1. `platform/error-handling.md` 에러 코드 레지스트리에 `AUTH_SERVICE_UNAVAILABLE` row 추가:
   - code: `AUTH_SERVICE_UNAVAILABLE`
   - HTTP status: 503
   - message pattern: "Authentication service is temporarily unavailable"
   - scope/owner: account-service signup flow
2. `SignupRollbackIntegrationTest` profile rollback assertion 교정:
   - 요청 email 의 account 가 존재하지 않음을 검증한 뒤, 해당 accountId 로 profile 조회 — 의미 있는 cross-check 가 되도록 재작성
   - 더 단순한 방식: `profileRepository.count()` 이 테스트 전/후 동일함을 검증 + `accountRepository.findByEmail(testEmail).isEmpty()` 병행

## Out of Scope

- resilience4j 설정 변경
- Rollback test 를 Docker 없이 실행 가능하게 만드는 재설계 (테스트 strategy 레벨, 별도 infra task 성격)
- `error-handling.md` 의 다른 에러 코드 정리

---

# Acceptance Criteria

- [ ] `platform/error-handling.md` 에 `AUTH_SERVICE_UNAVAILABLE` 엔트리 등록 (503, account-service)
- [ ] `SignupRollbackIntegrationTest` 의 profile rollback assertion 이 요청 email 의 profile 행 부재를 실제로 검증
- [ ] `./gradlew :apps:account-service:test` green (slice + 신규 assertion 기반 unit 레벨 확인)
- [ ] CI (Docker 가용) 에서 `SignupRollbackIntegrationTest` 실제 실행 + green
- [ ] 기존 `SignupControllerTest` 6 case 회귀 없음

---

# Related Specs

- `platform/error-handling.md` (에러 코드 레지스트리)
- `platform/testing-strategy.md` (통합 테스트 assertion 품질)

---

# Related Contracts

- `specs/contracts/http/account-api.md` (이미 `AUTH_SERVICE_UNAVAILABLE` 기재됨, 정합성 확인만)
- `specs/contracts/http/internal/auth-internal.md`

---

# Target Service

- `apps/account-service`

---

# Architecture

layered 4-layer. 변경 범위는 presentation + test + spec docs 만.

---

# Edge Cases

- 여러 인스턴스가 error-handling.md 에 동일 코드를 등록하려 할 때(중복) — 본 task 는 account-service 소유로 등록
- profile assertion 재작성 시 accountRepository+profileRepository 양쪽 상태를 검증하므로 트랜잭션 롤백 완전성이 더 강하게 보장됨

---

# Failure Scenarios

- CI 에서 Docker 가용해도 `AccountSignupIntegrationTest` 와 동일한 Testcontainers 불안정 이슈에 영향받을 가능성 → `@EnabledIf("isDockerAvailable")` 외 추가 `@RetryingTest` 또는 infra 레이어 보강은 TASK-BE-066 스코프로 남김

---

# Test Requirements

- 수정된 assertion 이 실제로 rollback 되지 않은 시나리오(가상)에서 실패하는지 negative-check 로 한 번 수기 검증
- slice test 는 기존 `SignupControllerTest` 로 커버

---

# Definition of Done

- [ ] error-handling.md 등록 + rollback assertion 교정
- [ ] `./gradlew build` CI green
- [ ] Ready for review
