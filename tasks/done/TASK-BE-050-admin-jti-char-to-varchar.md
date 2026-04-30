# Task ID

TASK-BE-050-admin-jti-char-to-varchar

# Title

admin-service — admin_operator_refresh_tokens.jti CHAR(36) → VARCHAR(36) 정렬 마이그레이션

# Status

ready

# Owner

backend

# Task Tags

- db
- fix

# depends_on

- (없음)

---

# Goal

E2E 런타임 중 admin-service Hibernate schema-validation에서:

```
Schema-validation: wrong column type encountered in column [jti] in table
[admin_operator_refresh_tokens]; found [char (Types#CHAR)], but expecting
[varchar(36) (Types#VARCHAR)]
```

V0015 마이그레이션이 `jti CHAR(36)`으로 생성, JPA 엔티티는 `@Column(length=36)` → VARCHAR(36). BE-049가 request_hash에 대해 해결한 것과 동일한 클래스 결함.

---

# Scope

## In Scope

1. admin-service에 forward 마이그레이션 추가 (V0018):
   ```sql
   ALTER TABLE admin_operator_refresh_tokens MODIFY COLUMN jti VARCHAR(36) NOT NULL;
   ```
2. V0015 원본은 미변경 (Flyway checksum 보존)
3. `./gradlew :apps:admin-service:test` 통과
4. 로컬 compose에서 admin-service 기동 healthy

## Out of Scope

- 다른 admin 컬럼 CHAR/VARCHAR 전수 점검 (필요 시 별도 태스크)

---

# Acceptance Criteria

- [ ] V0018 forward 마이그레이션 추가, NOT NULL 보존
- [ ] admin-service 테스트 통과
- [ ] docker compose up -d 후 admin-service healthy

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
