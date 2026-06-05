# Task ID

TASK-BE-049-admin-request-hash-char-vs-varchar

# Title

admin-service request_hash 컬럼 타입 불일치 — Migration CHAR(64) vs JPA entity VARCHAR(64)

# Status

ready

# Owner

backend

# Task Tags

- code
- fix

---

# Goal

Fix issue found in TASK-BE-045.

`admin_bulk_lock_idempotency.request_hash` 컬럼이 Flyway 마이그레이션(V0012)에서는 `CHAR(64)`로,
JPA 엔티티(`BulkLockIdempotencyJpaEntity`)에서는 `@Column(length = 64)` (= Hibernate 기본 `VARCHAR(64)`)로 선언되어
Hibernate schema-validation(`spring.jpa.hibernate.ddl-auto=validate`)이 타입 불일치로 기동 실패한다.

정답 타입을 결정(CHAR 또는 VARCHAR)하고 두 선언을 일치시킨다.

---

# Scope

## In Scope

1. `request_hash`의 의미적 적합 타입 결정:
   - SHA-256 hex digest(64자 고정) → `CHAR(64)`가 의미상 정확하고 스토리지 효율이 동일
   - JPA 엔티티에 `columnDefinition = "CHAR(64)"`를 추가하여 마이그레이션과 일치시키는 것이 최소 변경
2. 엔티티 `@Column(name = "request_hash", length = 64, nullable = false)` 수정
3. Hibernate schema-validation 통과 확인
4. 기존 단위 테스트 회귀 없음

## Out of Scope

- 마이그레이션 파일 변경 (V0012는 이미 prod에 적용된 것으로 간주 — 엔티티를 마이그레이션에 맞춤)
- 다른 컬럼 타입 검토

---

# Acceptance Criteria

- [ ] `admin-service` 기동 시 Hibernate schema-validation 오류 없음
- [ ] `request_hash` 컬럼이 DB 스키마(`CHAR(64)`)와 JPA 엔티티 선언이 일치
- [ ] 기존 단위/통합 테스트 회귀 없음

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `platform/coding-rules.md`

# Related Skills

- (없음)

---

# Related Contracts

- (없음)

---

# Target Service

- `apps/admin-service`

---

# Architecture

Follow:

- `specs/services/admin-service/architecture.md`

---

# Implementation Notes

- V0012 마이그레이션을 변경하지 말 것 — 이미 배포된 스키마 기준
- `BulkLockIdempotencyJpaEntity.requestHash`의 `@Column`에 `columnDefinition = "CHAR(64)"`를 추가하면 Hibernate가 DDL 검증 시 `CHAR`로 비교함
- Testcontainers 통합 테스트(`@DataJpaTest` 또는 `@SpringBootTest`)에서 entity persist/load round-trip 확인 권장

---

# Edge Cases

- `columnDefinition` 값은 DB 방언(MySQL)에 종속됨 — e2e 전용이므로 허용

---

# Failure Scenarios

- Hibernate가 `CHAR`와 `VARCHAR`를 동일하게 처리하는 버전이면 false positive — 실제 DB 타입 조회로 재검증

---

# Test Requirements

- admin-service 통합 테스트 (Testcontainers) 에서 schema-validation 포함 기동 확인
- 기존 테스트 회귀 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
