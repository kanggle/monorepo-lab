# Task ID

TASK-BE-073

# Title

auth-service application→infrastructure 직접 의존 제거 — AuthMetrics 추상화

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

TASK-INT-012 크로스 리뷰에서 발견된 이슈 수정. auth-service의 application 레이어 서비스들이 infrastructure 레이어의 AuthMetrics를 직접 import하는 아키텍처 위반을 수정한다.

---

# Scope

## In Scope

- AuthMetrics에 대한 domain/application 레이어용 인터페이스(port) 정의
- LoginService, SignupService, RefreshTokenService, LogoutService에서 infrastructure 직접 import 제거
- AuthMetrics를 인터페이스 구현체로 전환
- gateway-service CORS 설정을 환경변수로 외부화 (`${CORS_ALLOWED_ORIGINS}`)

## Out of Scope

- 다른 서비스의 Metrics 추상화 (동일 패턴 존재 시 별도 태스크로)
- 메트릭 종류 변경

---

# Acceptance Criteria

- [x] application 레이어에 AuthMetrics 인터페이스가 정의된다
- [x] LoginService, SignupService, RefreshTokenService, LogoutService가 인터페이스만 import한다
- [x] infrastructure의 AuthMetrics가 해당 인터페이스를 구현한다
- [x] gateway-service CORS 허용 origin이 환경변수로 구성된다
- [x] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/auth-service/architecture.md` (application→infrastructure 직접 의존 금지)
- `specs/platform/coding-rules.md` (하드코딩 금지)

# Related Skills

- `.claude/skills/backend/architecture/layered.md`

---

# Related Contracts

_(없음)_

---

# Target Service

- auth-service, gateway-service

---

# Architecture

- `specs/services/auth-service/architecture.md`

---

# Edge Cases

- 인터페이스 추출 후 Spring Bean 자동 주입이 정상 동작하는지 확인

---

# Failure Scenarios

- Bean 주입 실패로 서비스 기동 불가

---

# Test Requirements

- 기존 단위/통합 테스트 통과 확인
- Mock 인터페이스를 사용한 서비스 단위 테스트 업데이트

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
