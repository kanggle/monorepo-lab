# Task ID

TASK-BE-084-fix-001

# Title

TASK-BE-084 리뷰 수정 — Email Value Object 정규식 패턴 불일치, 정규화 처리 불일치, 최대 길이 검증 누락

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

TASK-BE-084 리뷰에서 발견된 3건의 이슈를 수정한다.

1. **이메일 정규식 패턴 불일치**: auth-service(`^[^@\s]+@[^@\s]+\.[^@\s]+$`)와 user-service(`^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$`)의 검증 패턴이 다름. 태스크 Goal에 "동일한 이메일 검증 로직"이 전제되어 있으므로 동일 패턴으로 통일 필요.
2. **소문자 정규화 처리 불일치**: auth-service는 `toLowerCase().trim()`, user-service는 `trim()`만 수행. 태스크 Edge Cases에 "대소문자 처리 → 기존 로직과 동일하게 유지"로 명시되어 있으므로 기존 로직 확인 후 통일 필요.
3. **최대 길이 검증 누락**: Edge Cases에 "최대 길이 초과 → Value Object 생성 시 검증"이 명시되어 있으나 양쪽 서비스 모두 길이 검증이 구현되어 있지 않음.

---

# Scope

## In Scope

- auth-service, user-service Email Value Object 정규식 패턴 통일
- auth-service, user-service Email Value Object 소문자 정규화 동작 통일
- auth-service, user-service Email Value Object 최대 길이 검증 추가
- 관련 테스트 수정/추가

## Out of Scope

- 이메일 검증 규칙 자체 변경 (기존 규칙 유지, 통일만 수행)
- libs/에 공유 Email 클래스 추출

---

# Acceptance Criteria

- [ ] auth-service와 user-service의 Email 정규식 패턴이 동일하다
- [ ] auth-service와 user-service의 소문자 정규화 동작이 동일하다
- [ ] auth-service와 user-service의 Email Value Object에 최대 길이 검증이 존재한다
- [ ] 최대 길이 초과 시 IllegalArgumentException이 발생한다
- [ ] 관련 단위 테스트가 추가/수정되어 통과한다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/user-service/architecture.md`
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

- 정규식 패턴 통일 시, 기존에 두 서비스에서 사용하던 원본 검증 로직을 확인하여 원래 동일했던 패턴으로 맞춘다.
- 소문자 정규화도 기존 동작을 기준으로 통일한다.
- 최대 길이는 일반적인 이메일 표준(RFC 5321)에 따라 254자로 설정한다.

---

# Edge Cases

- 정확히 최대 길이인 이메일 → 허용
- 최대 길이 + 1 이메일 → IllegalArgumentException

---

# Failure Scenarios

- 패턴 변경으로 기존 유효 이메일이 거부됨 → 기존 테스트로 검증
- 길이 제한이 너무 짧아 정상 이메일 거부 → RFC 5321 기준(254자) 적용

---

# Test Requirements

- Email Value Object 최대 길이 검증 단위 테스트
- 패턴 통일 후 기존 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
