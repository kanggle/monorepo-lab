# Task ID

TASK-BE-118-fix-001

# Title

TASK-BE-118 리뷰 수정 — 재발행 엔드포인트 통합 테스트 누락 추가 및 보안/로깅 보완

# Status

ready

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

---

# Goal

TASK-BE-118 코드 리뷰에서 발견된 다음 이슈를 수정한다.

1. **[Critical]** `RepublishSignupEventsIntegrationTest` 미구현 — `specs/platform/testing-strategy.md`와 TASK-BE-118 Acceptance Criteria("통합 테스트: 100명 users 시드 후 엔드포인트 호출 → 100건 발행 확인")가 필수 통합 테스트를 요구하나, 해당 파일이 존재하지 않는다.
2. **[Warning]** `UserSignupRepublishService.republishAll()`의 `log.warn`이 `e.getMessage()`를 그대로 기록한다. 예외 메시지에 이메일 등 PII가 포함될 수 있으므로, 메시지는 에러 타입만 남기고 PII 가능성 있는 내용은 제거한다.
3. **[Warning]** `SecurityConfig`에서 `/api/internal/**`가 `permitAll()`로만 처리된다. 게이트웨이가 이 경로를 라우팅하지 않는다는 의도는 유효하나, 서비스 내부 Security Filter 레벨에서 아무런 IP/네트워크 제한이 없는 상태. 최소한 Javadoc 주석으로 접근 제어 정책(kubectl port-forward 전용)을 SecurityConfig 메서드 레벨에 명시하고, 내부 클러스터 IP 대역 제한을 향후 ADR로 등록할 예정임을 코드 주석에 TODO로 남긴다.

---

# Scope

## In Scope

- `RepublishSignupEventsIntegrationTest.java` 신규 작성 (`*IntegrationTest.java` 네이밍 준수)
  - Testcontainers PostgreSQL 사용 (H2 금지)
  - 100명 users 시드 → `POST /api/internal/users/republish-signup-events` 호출 → 응답 검증
  - publisher mock 또는 Testcontainers Kafka로 발행 건수 확인
  - 유저 0명 시나리오 포함
- `UserSignupRepublishService`의 `log.warn` 수정 — `e.getMessage()` 대신 `e.getClass().getSimpleName()` 사용
- `SecurityConfig`의 `/api/internal/**` permitAll 블록에 접근 제어 정책 Javadoc/주석 보강

## Out of Scope

- IP 화이트리스트 또는 mTLS 등 네트워크 레벨 접근 제어 실구현 (별도 ADR + 태스크 필요)
- user-service 쪽 변경
- 기타 기능 추가

---

# Acceptance Criteria

- [ ] `RepublishSignupEventsIntegrationTest` 파일이 `src/test/java/com/example/auth/` 경로에 존재한다
- [ ] 통합 테스트: 100명 users 시드 후 `POST /api/internal/users/republish-signup-events` 호출 → `totalUsers=100`, `publishedCount=100`, `failedCount=0` 응답 검증
- [ ] 통합 테스트: 유저 0명 시나리오 — `totalUsers=0` 응답 검증
- [ ] `UserSignupRepublishService` warn 로그에 `e.getMessage()` 포함되지 않는다
- [ ] `SecurityConfig`의 internal 경로 주석에 접근 제어 정책이 명확히 설명된다
- [ ] 기존 auth-service 전체 테스트 통과

---

# Related Specs

- `specs/platform/testing-strategy.md`
- `specs/platform/security-rules.md`
- `specs/platform/coding-rules.md`
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` (기존 내부 엔드포인트 계약 참조)

---

# Target Service

- `auth-service`

---

# Edge Cases

- Testcontainers 환경에서 publisher를 mock으로 처리하는 경우 발행 건수 카운팅 방식 명확히 할 것
- 통합 테스트에서 Kafka 없이 publish 실패가 failedCount에 반영되는지도 검증

---

# Failure Scenarios

- Testcontainers 시작 실패 → 테스트 전체 skip이 아닌 실패 처리
- users 시드 실패 → 명확한 에러 메시지로 테스트 실패

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if required
- [ ] Specs updated if required
- [ ] Ready for review
