# Task ID

TASK-BE-127

# Title

[Security P0] auth-service AdminAccountSeeder prod 프로파일 노출 수정 — 하드코딩 관리자 계정 씨딩 제거

# Status

review

# Owner

backend

# Task Tags

- code
- security
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

`AdminAccountSeeder`가 `@Profile` 가드 없이 모든 환경에서 `admin@admin.com` / `admin1234` 계정을 무조건 씨딩한다. `docker-compose.yml`이 `SPRING_PROFILES_ACTIVE=prod`로 auth-service를 기동하므로 prod 환경에서도 추측 가능한 관리자 계정이 생성된다.

씨딩을 `local` / `standalone` 프로파일로 제한하거나, prod에서는 환경변수 기반 초기 비밀번호를 사용하도록 수정한다.

---

# Scope

## In Scope

- `AdminAccountSeeder`에 `@Profile({"local", "standalone"})` 추가
  - 또는 `ADMIN_INITIAL_PASSWORD` 환경변수 필수화 + prod에서 해당 환경변수로 계정 생성
- `docker-compose.yml`의 auth-service에 `ADMIN_INITIAL_PASSWORD` 환경변수 항목 추가 (`.env.example`에 플레이스홀더 포함)
- 기존 `RepublishSignupEventsIntegrationTest` 등 `AdminAccountSeeder`에 의존하는 테스트 호환성 확인 및 수정
- `application-prod.yml` 또는 `application.yml`의 prod 프로파일에 씨딩 비활성 명시 (주석 또는 설정)

## Out of Scope

- 관리자 계정 생성 전용 CLI/스크립트 제공 (별도 태스크로 분리 가능)
- 비밀번호 정책 강화 (현행 유지)

---

# Acceptance Criteria

- [ ] `prod` 프로파일로 기동 시 `admin@admin.com` 계정이 자동 생성되지 않는다
- [ ] `local` / `standalone` 프로파일에서는 기존과 동일하게 씨딩이 동작한다
- [ ] `docker-compose.yml`에 `ADMIN_INITIAL_PASSWORD` 환경변수가 명시되어 있고 `.env.example`에 플레이스홀더가 존재한다
- [ ] `AdminAccountSeeder`에 의존하는 기존 통합 테스트가 수정 없이 통과하거나, 필요 시 `@ActiveProfiles("local")` 또는 `@Import(AdminAccountSeeder.class)` 등으로 보정된다
- [ ] `README` 또는 주석에 prod 최초 관리자 계정 생성 절차가 명시된다

---

# Related Specs

- `specs/platform/error-handling.md`
- `specs/platform/coding-rules.md`
- `specs/services/auth-service/architecture.md`

---

# Related Skills

- `.claude/skills/backend/spring-boot-service.md`

---

# Related Contracts

- 변경 없음

---

# Target Service

- `auth-service`

---

# Architecture

**Option A (권장)**: `@Profile({"local", "standalone"})` 추가

```java
@Configuration
@Profile({"local", "standalone"})
public class AdminAccountSeeder {
    ...
}
```

prod에서 관리자 계정이 필요하면 별도 bootstrap 스크립트 또는 최초 실행 시 `ADMIN_EMAIL` / `ADMIN_INITIAL_PASSWORD` 환경변수로 생성.

**Option B**: 환경변수 기반 조건부 씨딩

```java
@ConditionalOnProperty(name = "admin.seeder.enabled", havingValue = "true")
```

`application-prod.yml`에서 `admin.seeder.enabled=false` 명시.

---

# Edge Cases

- 기존 prod DB에 이미 `admin@admin.com`이 존재하는 경우 → 씨더 제거 후에도 기존 계정은 잔존 (별도 운영 절차로 비밀번호 변경 필요 — 주석으로 안내)
- 통합 테스트가 `@SpringBootTest`로 실제 컨텍스트를 로드할 때 씨더가 실행되지 않으면 관리자 계정이 없어 테스트 실패 가능 → 테스트 픽스처에서 직접 계정 생성하거나 `@ActiveProfiles("local")` 사용

---

# Failure Scenarios

- `ADMIN_INITIAL_PASSWORD` 미설정 시(Option B 선택 경우) → 애플리케이션 시작 시 `@Value` 바인딩 실패로 명시적 에러 — 의도된 동작

---

# Test Requirements

- **통합 테스트**: `prod` 프로파일 컨텍스트에서 `AdminAccountSeeder` Bean이 등록되지 않음을 검증
- **기존 테스트**: `RepublishSignupEventsIntegrationTest` 등 씨더 의존 테스트가 계속 통과함을 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
