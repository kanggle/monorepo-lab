# Task ID

TASK-BE-006

# Title

auth-service 레이어 의존성 위반 수정 — application→presentation DTO 제거 및 infrastructure config 추상화

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

auth-service의 application 레이어가 presentation 레이어의 DTO를 직접 참조하는 아키텍처 위반을 수정한다.

현재 `LoginService`, `SignupService`, `RefreshTokenService`, `LogoutService`가 모두 `presentation.dto.*`를 직접 import하고 있으며, `LoginService`와 `LogoutService`는 `infrastructure.config.JwtProperties`에도 의존한다.

`specs/services/auth-service/architecture.md`에 따르면 허용된 의존 방향은 `presentation → application → domain`이며, application이 presentation에 의존하는 것은 금지된 패턴이다.

이 태스크 완료 후: application 레이어는 자체 command/result 객체만 사용하고, presentation 레이어에서 DTO↔command 변환을 수행하며, infrastructure config에 대한 직접 의존이 도메인 인터페이스로 추상화된다.

---

# Scope

## In Scope

- application 레이어 전용 command 객체 생성 (`SignupCommand`, `LoginCommand`, `RefreshCommand`, `LogoutCommand`)
- application 레이어 전용 result 객체 생성 (`SignupResult`, `LoginResult`, `RefreshResult`)
- `AuthController`에서 presentation DTO → command 변환 로직 추가
- `AuthController`에서 result → presentation DTO 변환 로직 추가
- 4개 application service에서 presentation DTO import 제거
- `JwtProperties` 직접 의존을 도메인 인터페이스(`TokenProperties` 등)로 추상화
- 기존 테스트 수정 (command/result 객체 사용으로 변경)

## Out of Scope

- 새로운 API 엔드포인트 추가
- 비즈니스 로직 변경
- presentation DTO의 validation 규칙 변경
- infrastructure 레이어 내부 구현 변경 (Redis, JPA 등)

---

# Acceptance Criteria

- [ ] application 레이어의 모든 service 클래스에 `presentation.dto.*` import가 없다
- [ ] application 레이어의 모든 service 클래스에 `infrastructure.config.JwtProperties` import가 없다
- [ ] application 레이어에 command 객체(`SignupCommand`, `LoginCommand`, `RefreshCommand`, `LogoutCommand`)가 존재한다
- [ ] application 레이어에 result 객체(`SignupResult`, `LoginResult`, `RefreshResult`)가 존재한다
- [ ] `AuthController`가 DTO↔command/result 변환을 수행한다
- [ ] 도메인 인터페이스를 통해 refresh token TTL에 접근한다
- [ ] 기존 모든 테스트가 통과한다
- [ ] API 동작이 기존과 동일하다 (요청/응답 형식 변경 없음)

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/overview.md`
- `specs/platform/architecture.md`

# Related Skills

- `.claude/skills/backend/dto-mapping.md`
- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` — API 응답 형식은 변경하지 않음 (내부 구조만 변경)

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

변경 대상 레이어:
- domain: `TokenProperties` 인터페이스 추가 (refreshTokenTtlSeconds 접근용)
- application: command/result 객체 생성, service에서 presentation DTO 참조 제거
- infrastructure: `JwtProperties`가 `TokenProperties` 인터페이스 구현
- presentation: controller에서 DTO↔command/result 변환 수행

의존 방향 (변경 후):
- presentation → application (command/result 사용)
- application → domain (TokenProperties 인터페이스 사용)
- infrastructure → domain (JwtProperties가 TokenProperties 구현)

---

# Implementation Notes

### command/result 객체 위치

`application/dto/` 패키지에 생성한다.

### 변환 책임

presentation 레이어(controller)가 변환 책임을 갖는다:
- 입력: `SignupRequest` → `SignupCommand`
- 출력: `SignupResult` → `SignupResponse`

### TokenProperties 인터페이스

```java
// domain/service/TokenProperties.java
public interface TokenProperties {
    long refreshTokenTtlSeconds();
}
```

`JwtProperties`가 이 인터페이스를 구현하도록 변경한다.

---

# Edge Cases

- command 객체와 presentation DTO의 필드가 정확히 일치해야 데이터 손실이 없음
- `SignupResponse.from(User)` 같은 기존 변환 메서드가 result 객체 기반으로 변경되어야 함
- `LogoutService`는 반환값이 void이므로 result 객체 불필요

---

# Failure Scenarios

- command/result 변환 누락으로 필드가 null로 전달되는 경우 → 단위 테스트로 검증
- 인터페이스 추상화 시 Bean 주입 실패 → 통합 테스트로 검증

---

# Test Requirements

- 단위 테스트: 4개 application service가 command/result 객체로 동작하는지 검증
- 슬라이스 테스트: controller의 DTO↔command 변환이 정확한지 검증
- 통합 테스트: 전체 API 플로우가 기존과 동일하게 동작하는지 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
