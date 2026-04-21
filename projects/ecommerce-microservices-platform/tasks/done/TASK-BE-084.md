# Task ID

TASK-BE-084

# Title

auth-service, user-service 이메일 검증 중복 제거 — 서비스별 Email Value Object 추출

# Status

review

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

auth-service의 `User` 도메인 엔티티와 user-service의 `UserProfile` 도메인 모델에서 동일한 이메일 검증 로직(regex 패턴, null/blank 체크, 길이 제한)이 중복되어 있다. 각 서비스 내에서 `Email` Value Object를 추출하여 검증 로직을 캡슐화하고 중복을 제거한다.

---

# Scope

## In Scope

- auth-service: `Email` Value Object 생성 (domain 패키지 내), `User` 엔티티에서 String email → Email 타입 전환
- user-service: `Email` Value Object 생성 (domain 패키지 내), `UserProfile`에서 String email → Email 타입 전환
- 이메일 검증 로직을 Value Object 생성자에 집중

## Out of Scope

- libs/에 공유 Email 클래스 추출 (shared-library-policy에 의해 도메인 로직은 서비스 내부에 유지)
- 이메일 검증 규칙 변경
- 다른 필드의 Value Object 추출

---

# Acceptance Criteria

- [ ] auth-service에 `Email` Value Object가 domain 패키지에 존재한다
- [ ] auth-service `User` 엔티티가 `Email` 타입을 사용한다
- [ ] user-service에 `Email` Value Object가 domain 패키지에 존재한다
- [ ] user-service `UserProfile`이 `Email` 타입을 사용한다
- [ ] 이메일 검증 로직이 각 서비스의 `Email` Value Object에만 존재한다 (도메인 엔티티에서 제거)
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/user-service/architecture.md`
- `specs/platform/shared-library-policy.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/backend/validation.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, API 계약 변경 없음)

---

# Target Service

- `auth-service`
- `user-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`
- `specs/services/user-service/architecture.md`

---

# Implementation Notes

- Value Object는 불변(immutable)으로 구현한다 (record 또는 final 필드).
- `Email.of(String)` 정적 팩토리 메서드에서 검증을 수행한다.
- JPA 매핑 시 `@Embeddable` 또는 AttributeConverter를 사용한다 (TASK-BE-082 JPA 분리 이후라면 JpaEntity의 Mapper에서 String 변환).
- equals/hashCode는 이메일 값 기반으로 구현한다.

---

# Edge Cases

- null 또는 빈 문자열 이메일 → Value Object 생성 시 IllegalArgumentException
- 최대 길이 초과 → Value Object 생성 시 검증
- 대소문자 처리 → 기존 로직과 동일하게 유지 (변경하지 않음)

---

# Failure Scenarios

- Value Object 전환 시 기존 직렬화/역직렬화 호환성 깨짐 → DTO 매핑에서 String 변환 확인
- JPA 매핑 미스로 DB 저장/조회 실패 → Mapper 또는 AttributeConverter 테스트
- 기존 테스트에서 String으로 직접 비교하는 코드 컴파일 오류

---

# Test Requirements

- Email Value Object 단위 테스트 (유효/무효 이메일, 경계값)
- 도메인 엔티티 기존 테스트 통과 확인
- Repository 통합 테스트 (DB 저장/조회 시 Email 정상 매핑)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
