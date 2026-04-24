# Task ID

TASK-BE-001

# Title

auth-service 프로젝트 부트스트랩 — Spring Boot 구조, DB/Redis 연결, 도메인 모델

# Status

review

# Owner

backend

# Task Tags

- code

---

# Goal

auth-service를 실행 가능한 Spring Boot 애플리케이션으로 초기화한다.
Layered Architecture 구조를 갖추고, PostgreSQL 연결, Redis 연결, 핵심 도메인 모델(User, RefreshToken)을 구현한다.

이 태스크 완료 후: 애플리케이션이 기동되고, DB 마이그레이션이 자동 실행되며, /actuator/health 가 200을 반환해야 한다.

---

# Scope

## In Scope

- build.gradle 의존성 설정 (Spring Boot, Spring Security, JPA, Redis, Flyway, Actuator, Validation)
- Layered 패키지 구조 생성 (presentation / application / domain / infrastructure)
- PostgreSQL DataSource 설정
- Redis 연결 설정 (RefreshToken 저장용)
- Flyway 마이그레이션: users 테이블, refresh_tokens 테이블
- 도메인 모델: User (entity), RefreshToken (value or entity)
- Repository 인터페이스 정의 (JPA)
- application.yml (기본 설정 + local 프로파일 분리)
- /actuator/health 엔드포인트 활성화

## Out of Scope

- 회원가입/로그인 API 구현 (TASK-BE-002)
- JWT 발급 로직 (TASK-BE-002)
- 토큰 갱신/로그아웃 API (TASK-BE-003)
- Spring Security 필터 체인 (TASK-BE-002에서 구성)

---

# Acceptance Criteria

- [ ] `./gradlew :apps:auth-service:bootRun` 으로 애플리케이션이 정상 기동된다
- [ ] `/actuator/health` 가 `{"status":"UP"}` 을 반환한다
- [ ] Flyway 마이그레이션이 실행되어 `users`, `refresh_tokens` 테이블이 생성된다
- [ ] Redis 연결이 정상적으로 이루어진다 (ping 성공)
- [ ] Layered 패키지 구조가 `specs/services/auth-service/architecture.md` 를 따른다
- [ ] 단위 테스트: User 도메인 모델 기본 동작 검증

---

# Related Specs

- `specs/platform/architecture.md`
- `specs/platform/dependency-rules.md`
- `specs/platform/shared-library-policy.md`
- `specs/platform/security-rules.md`
- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/dependencies.md`

# Related Skills

- `.claude/skills/backend/implementation-workflow.md`
- `.claude/skills/database/schema-change-workflow.md`
- `.claude/skills/database/migration-strategy.md`

---

# Related Contracts

없음 (이 태스크는 API를 노출하지 않음)

---

# Target Service

- `auth-service`

---

# Architecture

`specs/services/auth-service/architecture.md` — Layered Architecture

패키지 구조 예시:
```
com.example.auth
├── presentation/     # Controller, Request/Response DTO
├── application/      # Service, UseCase, Command/Query
├── domain/           # Entity, Repository interface, domain rule
└── infrastructure/   # JPA impl, Redis impl, security config
```

---

# Implementation Notes

- DB: PostgreSQL, Redis는 로컬 개발 시 docker-compose로 실행 가정
- Flyway 마이그레이션 파일 위치: `resources/db/migration/`
- users 테이블 필드: id (UUID), email (unique), password_hash, name, created_at, updated_at
- refresh_tokens: Redis에 저장 (key: `refresh:{token}`, value: userId, TTL 30일)
- `libs/java-common`, `libs/java-web`, `libs/java-security` 중 이미 구현된 것이 있으면 우선 활용
- 비밀번호는 BCrypt 해싱

---

# Edge Cases

- DB 연결 실패 시 애플리케이션 기동 실패 (정상 동작)
- Redis 연결 실패 시 애플리케이션 기동 실패 (정상 동작)
- 동일 email로 users 테이블에 UNIQUE 제약

---

# Failure Scenarios

- Flyway 마이그레이션 실패 시 기동 중단 — 마이그레이션 파일 SQL 오류 확인
- Redis 연결 타임아웃 — application.yml의 host/port 확인

---

# Test Requirements

- 단위 테스트: User 도메인 생성, 유효성 규칙 검증
- 통합 테스트: Flyway 마이그레이션 후 테이블 존재 확인 (Testcontainers)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
