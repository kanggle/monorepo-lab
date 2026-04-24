# Task ID

TASK-BE-062

# Title

auth-service, user-service PII 로그 노출 제거

# Status

done

# Owner

backend

# Task Tags

- code

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

# Goal

TASK-INT-012 크로스 리뷰에서 발견된 Critical 이슈 수정. auth-service와 user-service의 로그에서 이메일 주소 등 PII(개인식별정보)가 평문으로 출력되는 문제를 제거한다.

---

# Scope

## In Scope

- auth-service LoginService: 로그인 실패 시 이메일 로깅 제거 (line 66)
- auth-service AuditLogService: 에러 로그의 이메일 노출 제거
- user-service UserSignedUpHandler: 프로필 생성 로그의 이메일 노출 제거 (line 28)
- 전체 서비스 grep으로 추가 PII 로깅 확인

## Out of Scope

- 로그 마스킹 라이브러리 도입

---

# Acceptance Criteria

- [ ] auth-service 로그에서 이메일이 출력되지 않는다
- [ ] user-service 로그에서 이메일이 출력되지 않는다
- [ ] 대신 userId 등 비식별 식별자가 사용된다
- [ ] 기존 테스트가 통과한다

---

# Related Specs

- `specs/platform/security-rules.md`
- `specs/platform/coding-rules.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- 디버깅에 이메일이 꼭 필요한 경우 별도 보안 감사 로그로 분리 고려

---

# Failure Scenarios

_(없음)_

---

# Test Requirements

- 로그 출력에 이메일 패턴이 포함되지 않음을 확인하는 테스트 추가
