# Task ID

TASK-BE-038

# Title

user-service 부트스트랩 — 프로젝트 구조, DB 스키마, 도메인 모델, UserSignedUp 이벤트 소비

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

user-service의 기반 구조를 구축한다. 프로젝트 골격, DB 스키마, 핵심 도메인 모델을 완성하고, auth-service의 UserSignedUp 이벤트를 소비하여 초기 프로필을 자동 생성한다.

이 태스크 완료 후: user-service가 기동되고, DB 마이그레이션이 완료되며, 도메인 모델이 정의되고, UserSignedUp 이벤트 수신 시 프로필 레코드가 생성된다.

---

# Scope

## In Scope

- Gradle 멀티모듈 설정 (apps/user-service)
- application.yml 기본 설정 (DB, Kafka)
- Flyway 마이그레이션: `user_profiles`, `user_addresses` 테이블 생성
- 도메인 모델 구현:
  - Entity: `UserProfile`, `Address`
  - Value Object: `ProfileStatus` (ACTIVE, SUSPENDED, WITHDRAWN)
  - Repository 인터페이스: `UserProfileRepository`, `AddressRepository`
- Infrastructure: JPA 엔티티 및 Spring Data 레포지토리
- Kafka 컨슈머: UserSignedUp 이벤트 소비 → UserProfile 생성
- 기본 헬스체크 (`GET /actuator/health`)

## Out of Scope

- 프로필 조회/수정 API (TASK-BE-039)
- 주소 관리 API (TASK-BE-040)
- 이벤트 발행 (UserProfileUpdated, UserWithdrawn — TASK-BE-039에서)
- admin API (향후 태스크)

---

# Acceptance Criteria

- [ ] `apps/user-service` 모듈이 빌드된다
- [ ] `user_profiles` 테이블: `id (UUID PK)`, `user_id (UUID, UNIQUE)`, `email`, `name`, `nickname`, `phone`, `profile_image_url`, `status`, `created_at`, `updated_at`
- [ ] `user_addresses` 테이블: `id (UUID PK)`, `user_id (UUID, FK)`, `label`, `recipient_name`, `phone`, `zip_code`, `address1`, `address2`, `is_default`, `created_at`, `updated_at`
- [ ] `ProfileStatus` 값 객체: `ACTIVE`, `SUSPENDED`, `WITHDRAWN`
- [ ] `UserProfileRepository`, `AddressRepository` 인터페이스가 도메인 레이어에 위치한다
- [ ] JPA 구현체가 인프라 레이어에 위치한다
- [ ] UserSignedUp 이벤트를 소비하여 UserProfile(status=ACTIVE)을 생성한다
- [ ] 중복 UserSignedUp 이벤트는 무시한다 (멱등성)
- [ ] 서비스가 기동되고 `/actuator/health`가 200을 반환한다
- [ ] Testcontainers로 DB 연동 통합 테스트가 통과한다
- [ ] Kafka 컨슈머 통합 테스트가 통과한다

---

# Related Specs

- `specs/services/user-service/architecture.md`
- `specs/platform/architecture.md`
- `specs/platform/dependency-rules.md`
- `specs/platform/shared-library-policy.md`
- `specs/platform/security-rules.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/coding-rules.md`
- `specs/platform/naming-conventions.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md` (UserSignedUp — 소비)
- `specs/contracts/http/user-api.md` (참고용, 이 태스크에서 API 구현 안 함)

---

# Target Service

- `user-service`

---

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

계층 배치:
- Domain: `UserProfile`, `Address`, `ProfileStatus`, Repository 인터페이스
- Application: UserSignedUp 이벤트 핸들러
- Infrastructure: JPA 엔티티, Spring Data 레포지토리 구현체, Kafka 컨슈머, Flyway 마이그레이션
- Presentation: (TASK-BE-039부터)

패키지 구조:
```
com.example.user
├── presentation/     # (TASK-BE-039부터)
├── application/      # Service, 이벤트 핸들러
├── domain/           # Entity, Repository interface, VO
└── infrastructure/   # JPA impl, Kafka consumer, config
```

---

# Implementation Notes

### 도메인 규칙

- user_id는 auth-service에서 발급한 UUID를 그대로 사용
- email과 name은 UserSignedUp 이벤트에서 수신
- nickname, phone, profileImageUrl은 초기값 null
- 최대 주소 수: 10개 (도메인 레이어에서 제한)

### Kafka 컨슈머

- 토픽: `auth.user.signed-up`
- 그룹: `user-service`
- DLQ 설정 필수
- 기존 `libs/java-messaging` 활용

### 기존 앱 구조 참고

- apps/user-service에 이미 build.gradle, Dockerfile, settings.gradle 존재
- 기존 파일 패턴을 따라 구현

---

# Edge Cases

- UserSignedUp 이벤트 중복 수신 → user_id UNIQUE 제약으로 무시 (멱등)
- UserSignedUp 이벤트 payload에 name이 null인 경우 → 빈 문자열로 대체
- DB 연결 실패 시 → 서비스 기동 차단 (정상 동작)

---

# Failure Scenarios

- Flyway 마이그레이션 실패 → 서비스 기동 차단 (Flyway 기본 동작)
- DB 연결 실패 → 헬스체크 DOWN 반환
- Kafka 연결 실패 → 이벤트 소비 불가, 재연결 시도
- JPA 엔티티와 도메인 모델 매핑 불일치 → 통합 테스트에서 조기 발견

---

# Test Requirements

- 단위 테스트: `UserProfileTest` — 도메인 생성, 상태 전이 검증
- 단위 테스트: `ProfileStatusTest` — VO 유효성
- 단위 테스트: `AddressTest` — 주소 수 제한 검증
- 통합 테스트: `UserProfileRepositoryTest` — Testcontainers PostgreSQL CRUD
- 통합 테스트: `UserSignedUpConsumerTest` — Kafka 이벤트 수신 → 프로필 생성

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
