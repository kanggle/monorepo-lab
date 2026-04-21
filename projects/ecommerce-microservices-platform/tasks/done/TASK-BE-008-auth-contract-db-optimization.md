# Task ID

TASK-BE-008

# Title

auth-service 계약 준수 및 DB 최적화 — 타임스탬프 타입 수정, 중복 인덱스 제거, 트랜잭션 설정

# Status

done

# Owner

backend

# Task Tags

- code
- api

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

auth-service의 계약 불일치, 중복 인덱스, 트랜잭션 미설정 문제를 수정한다.

**문제 1: LocalDateTime vs ISO 8601 타임존 불일치**

`User` 엔티티가 `LocalDateTime`을 사용하는데, `auth-api.md` 계약은 `createdAt`을 ISO 8601 string으로 명시한다. `LocalDateTime`은 타임존 정보가 없어 `2026-03-20T10:30:00` 형태로 직렬화되며, ISO 8601의 `2026-03-20T10:30:00Z`와 다르다.

**문제 2: 중복 인덱스**

`V1__create_users_table.sql`에서 `UNIQUE (email)` 제약조건이 자동으로 unique index를 생성하는데, 별도로 `CREATE INDEX idx_users_email`을 추가로 생성하고 있다. PostgreSQL에서 이는 중복이며 쓰기 성능 오버헤드를 유발한다.

**문제 3: 읽기 전용 트랜잭션 미설정**

`LoginService`와 `RefreshTokenService`가 DB 조회를 수행하지만 `@Transactional(readOnly = true)`가 없다.

이 태스크 완료 후: API 응답의 timestamp가 ISO 8601(UTC) 형식이고, 중복 인덱스가 제거되고, 읽기 전용 서비스에 트랜잭션이 설정된다.

---

# Scope

## In Scope

- `User` 엔티티의 `LocalDateTime` → `Instant`로 변경
- `SignupResponse`의 `createdAt` 타입을 `Instant`로 변경
- Jackson 직렬화 설정으로 ISO 8601(UTC) 형식 출력 보장
- Flyway 마이그레이션 `V2`로 중복 인덱스 제거 (`DROP INDEX idx_users_email`)
- `LoginService`에 `@Transactional(readOnly = true)` 추가
- `RefreshTokenService`에 `@Transactional(readOnly = true)` 추가
- 관련 테스트 수정

## Out of Scope

- DB 스키마 컬럼 타입 변경 (TIMESTAMP → TIMESTAMPTZ는 별도 검토)
- 다른 서비스의 타임스탬프 통일
- 글로벌 트랜잭션 전략 수립

---

# Acceptance Criteria

- [ ] `User` 엔티티의 `createdAt`, `updatedAt`이 `Instant` 타입이다
- [ ] `SignupResponse`의 `createdAt`이 `Instant` 타입이다
- [ ] POST /api/auth/signup 응답의 `createdAt`이 ISO 8601 UTC 형식이다 (예: `2026-03-20T10:30:00Z`)
- [ ] Flyway 마이그레이션 `V2`로 `idx_users_email` 중복 인덱스가 제거된다
- [ ] `uq_users_email` unique 제약조건은 유지된다
- [ ] `LoginService`에 `@Transactional(readOnly = true)`가 설정되어 있다
- [ ] `RefreshTokenService`에 `@Transactional(readOnly = true)`가 설정되어 있다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/overview.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/transaction-handling.md`
- `.claude/skills/database/schema-change-workflow.md`
- `.claude/skills/database/migration-strategy.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` — signup 응답의 `createdAt` 형식이 ISO 8601이어야 함

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

변경 대상 레이어:
- domain: `User` 엔티티 타임스탬프 타입 변경
- application: `LoginService`, `RefreshTokenService` 트랜잭션 설정
- presentation: `SignupResponse` 타입 변경
- infrastructure: Flyway 마이그레이션 추가

---

# Implementation Notes

### Instant 변경

```java
// User.java
private Instant createdAt;
private Instant updatedAt;

// User.create()
Instant now = Instant.now();
```

### Jackson 직렬화

`application.yml`에 다음 설정이 필요할 수 있다:
```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
```

Spring Boot 기본 설정에서 `Instant`는 ISO 8601 UTC 형식으로 직렬화된다.

### Flyway V2 마이그레이션

```sql
-- V2__drop_duplicate_email_index.sql
DROP INDEX IF EXISTS idx_users_email;
```

기존 `uq_users_email` unique 제약조건이 이미 index 역할을 하므로 별도 index는 불필요하다.

### 트랜잭션 설정

`LoginService`와 `RefreshTokenService`는 DB 조회만 수행하므로 `@Transactional(readOnly = true)`를 사용한다. 이는 JPA dirty checking을 비활성화하고 DB 커넥션 최적화에 도움이 된다.

---

# Edge Cases

- 기존 DB에 저장된 `LocalDateTime` 데이터와 `Instant` 간의 호환성 → JPA가 TIMESTAMP 컬럼을 Instant로 매핑 가능 (UTC 기준)
- `Instant.now()`와 `LocalDateTime.now()`의 시점 차이 → Instant는 항상 UTC
- Flyway 마이그레이션 실행 순서 → V2는 V1 이후 실행 보장
- `TIMESTAMP` 컬럼에 타임존 정보 없는 기존 데이터 → JVM 기본 타임존을 UTC로 설정하거나, TIMESTAMPTZ로 변경 검토 (Out of Scope이나 주의 필요)

---

# Failure Scenarios

- Flyway 마이그레이션 실패 시 → 서비스 시작 실패, 롤백 필요
- JPA Instant 매핑에서 타임존 불일치 → JVM 또는 JDBC 타임존 설정 확인 필요
- `readOnly = true` 트랜잭션에서 의도치 않은 쓰기 시도 → 예외 발생 (정상 동작)

---

# Test Requirements

- 단위 테스트: `User.create()` 결과의 `createdAt`이 `Instant` 타입인지 검증
- 슬라이스 테스트: signup 응답의 `createdAt`이 ISO 8601 UTC 형식(`Z` 접미사 포함)인지 검증
- 통합 테스트: Flyway V2 마이그레이션 후 `idx_users_email` 인덱스가 없고 unique 제약조건은 유지되는지 검증
- 통합 테스트: 로그인/리프레시 API가 readOnly 트랜잭션에서 정상 동작하는지 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
