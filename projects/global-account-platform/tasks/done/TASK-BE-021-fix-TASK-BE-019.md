# Task ID

TASK-BE-021

# Title

community-service — fix TASK-BE-019 review findings (CB test state leak)

# Status

ready

# Owner

backend

# Task Tags

- code

# depends_on

- (없음)

---

# Goal

TASK-BE-019 코드리뷰에서 발견된 CB 테스트 격리 버그를 수정한다.

---

# Scope

## In Scope

### Critical

1. **`MembershipAccessClientCbTest` CircuitBreaker 상태 격리 미비**
   - `returns_false_when_membership_service_returns_503` 테스트가 CB를 OPEN 상태로 남긴 채 종료됨
   - 동일 Spring 컨텍스트 안에서 다음 테스트 `returns_true_on_healthy_allowed_response`가 실행될 때 CB가 여전히 OPEN이어서 fallback `false`를 반환 → `assertThat(check(...)).isTrue()` 실패
   - 현상: `./gradlew :apps:community-service:test --rerun-tasks` 실행 시 1 test FAILED 재현
   - 수정: `@Autowired CircuitBreakerRegistry` 주입 후 `@AfterEach`에서
     `circuitBreakerRegistry.circuitBreaker("membershipService").reset()` 호출
   - 대안: 503 테스트 메서드에 `@DirtiesContext(methodMode = MethodMode.AFTER_METHOD)` 적용 (컨텍스트 재생성 비용이 있어 비권장)

### Warning (권장 수정)

2. **`PublishPostUseCaseTest` 의 죽은 `@InjectMocks` 패턴**
   - `@InjectMocks PublishPostUseCase useCase = new PublishPostUseCase(null, null, null, null, null)` 로 선언 후
     각 테스트에서 `useCase = new PublishPostUseCase(...)` 로 즉시 재할당 → `@InjectMocks` 가 실질적으로 무의미
   - 수정: `@InjectMocks` 어노테이션과 필드 초기화 제거. 필드를 `private PublishPostUseCase useCase;` 로만 선언하거나
     각 테스트에서 로컬 변수로 생성

## Out of Scope

- `GetFeedUseCase` → `GetPostUseCase.REQUIRED_PLAN_LEVEL` 상수 위치 개선 (별도 리팩터링)
- `PostStatusHistoryJpaEntity` occurredAt 전달 누락 수정 (기능 영향 미미, 별도 스프린트)

---

# Acceptance Criteria

- [ ] `./gradlew :apps:community-service:test --rerun-tasks` 전체 테스트 0 FAILED
- [ ] `MembershipAccessClientCbTest`: `@AfterEach`에서 CB 레지스트리 상태 초기화
- [ ] `PublishPostUseCaseTest`: 죽은 `@InjectMocks` 제거 (Warning 수준, 누락 시 별도 보고)

---

# Related Specs

- `specs/services/community-service/architecture.md`

# Related Contracts

- (없음 — 테스트 코드 수정만 포함)

---

# Target Service

- `apps/community-service`

---

# Architecture

변경 없음.

---

# Edge Cases

- CB reset 후 healthy 응답 테스트가 CLOSED 상태에서 정상 `true`를 반환하는지 확인
- CB 설정(`sliding-window-size=4`, `minimum-number-of-calls=4`)은 그대로 유지

---

# Failure Scenarios

- `CircuitBreakerRegistry` 빈이 TestApp 컨텍스트에 없을 경우 주입 실패 → `@ImportAutoConfiguration(CircuitBreakerAutoConfiguration.class)`가 이미 포함되어 있으므로 빈 존재 보장됨

---

# Test Requirements

- `./gradlew :apps:community-service:test --rerun-tasks` GREEN

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing (rerun-tasks)
- [ ] Ready for review
