# Task ID

TASK-BE-050-fix-rotated-from-varchar

# Title

admin-service — admin_operator_refresh_tokens.rotated_from CHAR(36) → VARCHAR(36) 정렬 마이그레이션 (BE-050 fix)

# Status

ready

# Owner

backend

# Task Tags

- db
- fix

# depends_on

- TASK-BE-050-admin-jti-char-to-varchar (done)

---

# Goal

BE-050 리뷰에서 식별된 동일 테이블의 두 번째 Hibernate schema-validation 불일치를 해소한다.

V0015는 `rotated_from CHAR(36) NULL`로 생성했으나 JPA 엔티티 `AdminOperatorRefreshTokenJpaEntity` 의 `@Column(name = "rotated_from", length = 36)`는 VARCHAR(36)를 기대. BE-050가 `jti`만 수정했기 때문에 `ddl-auto=validate` 상태에서 admin-service가 여전히 기동 실패한다.

---

# Scope

## In Scope

1. admin-service에 forward 마이그레이션 추가 (V0019):
   ```sql
   ALTER TABLE admin_operator_refresh_tokens MODIFY COLUMN rotated_from VARCHAR(36) NULL;
   ```
2. V0015 / V0018 원본은 미변경 (Flyway checksum 보존)
3. `./gradlew :apps:admin-service:test` 통과
4. 로컬 compose에서 admin-service 기동 healthy

## Out of Scope

- 다른 admin 컬럼 CHAR/VARCHAR 전수 점검 (필요 시 별도 태스크)

---

# Acceptance Criteria

- [ ] V0019 forward 마이그레이션 추가, NULL 허용 유지
- [ ] admin-service 테스트 통과
- [ ] docker compose up -d 후 admin-service healthy (schema-validation 경고/에러 없음)

---

# Related Specs

- `specs/services/admin-service/architecture.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- 없음

---

# Failure Scenarios

- 없음

---

# Test Requirements

- admin-service 단위 테스트

---

# Definition of Done

- [ ] 마이그레이션 + 검증
- [ ] Ready for review
