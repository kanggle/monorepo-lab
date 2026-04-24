# Task ID

TASK-BE-014

# Title

auth-service 도메인 이벤트 발행 — UserSignedUp, UserLoggedIn, UserLoggedOut, TokenRefreshed, LoginFailed

# Status

review

# Owner

backend

# Task Tags

- code
- event

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

auth-service에서 인증 관련 도메인 이벤트를 발행하여, 다른 서비스(감사, 알림, 분석 등)가 인증 활동을 비동기적으로 수신할 수 있도록 한다.

이 태스크 완료 후: `specs/contracts/events/auth-events.md`에 정의된 이벤트들이 각 인증 플로우에서 발행된다.

---

# Scope

## In Scope

- 이벤트 envelope 공통 클래스 생성 (`AuthEvent`)
- 이벤트 페이로드 DTO 생성 (UserSignedUp, UserLoggedIn, UserLoggedOut, TokenRefreshed, LoginFailed)
- `AuthEventPublisher` 도메인 인터페이스 생성
- `SpringAuthEventPublisher` 인프라 구현체 (Spring ApplicationEventPublisher 사용)
- `SignupService`, `LoginService`, `LogoutService`, `RefreshTokenService`에서 이벤트 발행 호출
- 각 이벤트 발행에 대한 단위 테스트

## Out of Scope

- 외부 메시지 브로커(Kafka, RabbitMQ) 연동 (별도 태스크)
- `SessionLimitExceeded` 이벤트 (TASK-BE-015에서 구현)
- 이벤트 소비자 구현
- 이벤트 재시도/DLQ 처리

---

# Acceptance Criteria

- [ ] `AuthEvent` 엔벨로프 클래스가 `eventId (UUID)`, `eventType`, `occurredAt (ISO 8601)`, `source ("auth-service")`, `payload`를 포함한다
- [ ] `UserSignedUp` 이벤트가 회원가입 성공 시 발행된다 (userId, email, name 포함)
- [ ] `UserLoggedIn` 이벤트가 로그인 성공 시 발행된다 (userId, email, ipAddress, userAgent 포함)
- [ ] `UserLoggedOut` 이벤트가 로그아웃 시 발행된다 (userId, sessionId 포함)
- [ ] `TokenRefreshed` 이벤트가 토큰 갱신 시 발행된다 (userId, sessionId 포함)
- [ ] `LoginFailed` 이벤트가 로그인 실패 시 발행된다 (email, ipAddress, reason 포함)
- [ ] 이벤트 발행 실패가 인증 플로우를 차단하지 않는다
- [ ] `AuthEventPublisher` 인터페이스가 도메인 계층에 위치한다
- [ ] 구현체가 인프라 계층에 위치한다
- [ ] 이벤트 페이로드가 `specs/contracts/events/auth-events.md` 계약과 일치한다
- [ ] 단위 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md`
- `specs/contracts/http/auth-api.md`

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

계층 배치:
- Domain: `AuthEventPublisher` 인터페이스, 이벤트 페이로드 record 클래스들
- Application: 각 서비스에서 `AuthEventPublisher` 호출
- Infrastructure: `SpringAuthEventPublisher` (Spring ApplicationEventPublisher 위임)

---

# Implementation Notes

### 이벤트 구조

```java
public record AuthEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    String source,
    Object payload
) {}
```

### Spring ApplicationEventPublisher 선택 이유

현재 단계에서는 프로세스 내 이벤트 발행으로 충분하다. 추후 Kafka/RabbitMQ 연동 시 인프라 구현체만 교체하면 된다 (도메인 인터페이스 유지).

### IP/User-Agent 전달

`LoginService`에 IP와 User-Agent 정보를 전달하기 위해 `LoginCommand`에 필드를 추가하고, `AuthController`에서 `HttpServletRequest`로부터 추출한다.

---

# Edge Cases

- 이벤트 발행 중 예외 발생 → 인증 응답은 정상 반환, 이벤트 유실 로깅
- 동시 다발 이벤트 발행 → Spring ApplicationEventPublisher는 동기 기본, 필요시 @Async 적용
- 이벤트 페이로드에 민감 정보 포함 금지 → password, token 값은 절대 포함하지 않음

---

# Failure Scenarios

- Spring ApplicationEventPublisher 내부 리스너 예외 → 이벤트 발행 호출을 try-catch로 감싸 격리
- 이벤트 발행 후 트랜잭션 롤백 시 이벤트가 이미 발행됨 → 현재 단계에서는 허용 (프로세스 내 이벤트이므로 영향 제한적)

---

# Test Requirements

- 단위 테스트: 각 서비스에서 이벤트 발행 호출 검증 (mock AuthEventPublisher)
- 단위 테스트: `AuthEvent` 엔벨로프 생성 검증
- 단위 테스트: `SpringAuthEventPublisher` 위임 동작 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
