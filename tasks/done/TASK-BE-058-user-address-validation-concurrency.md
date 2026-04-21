# Task ID

TASK-BE-058

# Title

user-service 주소 관리 동시성 수정 및 입력 검증 강화

# Status

backlog

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

user-service AddressService의 기본 배송지 설정 시 레이스 컨디션을 수정하고, 도메인 모델의 입력 검증을 강화한다.

현재 상태:
1. 기본 배송지 변경 시 `isDefault()` 체크와 `unmarkCurrentDefault()` 사이에 동시 요청이 끼어들 수 있다
2. Address, UserProfile의 필드 길이 제한이 DB 컬럼과 불일치하여 silent truncation 가능
3. UserProfile의 이메일 형식 검증이 null/blank 체크만 수행

---

# Scope

## In Scope

- AddressService의 기본 배송지 변경을 단일 쿼리(`UPDATE address SET is_default = false WHERE user_id = ? AND is_default = true`)로 처리
- Address 도메인 모델에 필드 길이 검증 추가 (label, recipientName, phone)
- UserProfile의 이메일 형식 검증 추가
- `@Transactional` 범위 검증 및 수정

## Out of Scope

- 주소 최대 개수 제한 로직 변경
- 배송지 관련 API 스펙 변경
- 분산 락 도입

---

# Acceptance Criteria

- [ ] 기본 배송지 변경이 단일 UPDATE 쿼리로 처리되어 동시성 문제가 해결된다
- [ ] Address 필드 길이가 DB 컬럼 제한과 일치하도록 도메인 검증이 추가된다
- [ ] UserProfile의 이메일이 기본 형식 검증을 통과해야 한다
- [ ] AddressService의 모든 쓰기 작업이 `@Transactional` 내에서 실행된다
- [ ] 동시 기본 배송지 변경 테스트가 추가된다

---

# Related Specs

- `specs/services/user-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/user-api.md`

---

# Target Service

- `user-service`

---

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

---

# Implementation Notes

- `unmarkCurrentDefault()` 를 JPA `@Modifying @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")` 로 변경
- Address 검증: label ≤ 50자, recipientName ≤ 50자, phone ≤ 20자 (DB 컬럼과 일치 확인 필요)
- 이메일 검증: 정규식 또는 `jakarta.validation.constraints.Email` 어노테이션 활용
- `@Transactional` 이 unmarkCurrentDefault + save를 감싸도록 보장

---

# Edge Cases

- 동시에 두 주소를 기본 배송지로 설정하는 요청
- 기본 배송지가 없는 상태에서 새 주소 추가
- 기본 배송지 삭제 시 다음 기본값 자동 지정 여부 (현재 미지정 — 유지)

---

# Failure Scenarios

- UPDATE 쿼리 실패 시 트랜잭션 롤백
- 유효하지 않은 이메일 형식 시 IllegalArgumentException
- DB 컬럼 길이 초과 시 DataIntegrityViolationException 대신 도메인 예외 반환

---

# Test Requirements

- 단위 테스트: Address 필드 길이 초과 시 예외 발생
- 단위 테스트: UserProfile 이메일 형식 검증
- 통합 테스트: 동시 기본 배송지 변경 (CountDownLatch 활용)
- 기존 AddressService 테스트 회귀 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
