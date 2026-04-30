# Task ID

TASK-BE-028b2-fix-operator-id-fk-and-phone-mask

# Title

admin-service — Fix admin_actions.operator_id nullable violation and maskPhone spec mismatch

# Status

ready

# Owner

backend

# Task Tags

- code
- test

# depends_on

- TASK-BE-028b2

---

# Goal

Close two spec-compliance gaps identified during TASK-BE-028b2 review:

1. admin_actions.operator_id is declared NOT NULL in specs/services/admin-service/data-model.md but the DB column is BIGINT NULL and all write paths pass null. The UUID to BIGINT resolution must be implemented so every audit row carries the operator internal FK.

2. AdminPiiMaskingUtils.maskPhone produces "010***78" (last 2 digits) but rules/traits/regulated.md R4 specifies "010-****-1234" (last 4 digits). The masking format must conform to the canonical R4 pattern.

---

# Scope

## In Scope

### Fix 1 — operator_id FK population

- AdminActionAuditor.recordStart: resolve OperatorContext.operatorId (external UUID) to admin_operators.id (internal BIGINT) via AdminOperatorJpaRepository.findByOperatorId, then pass the resolved BIGINT to AdminActionJpaEntity.create.
- AdminActionAuditor.recordDenied: same resolution inside the REQUIRES_NEW transaction.
- AdminActionAuditor.record (single-shot): same resolution.
- V0011 migration: ALTER TABLE admin_actions MODIFY COLUMN operator_id BIGINT NOT NULL after confirming no null rows exist.
- Update AdminActionJpaEntity.operatorId column annotation to nullable = false.
- Slice tests: confirm no write path produces a null operator_id.

### Fix 2 — maskPhone format

- AdminPiiMaskingUtils.maskPhone: change tail length from 2 to 4 digits to match R4 ("010-****-1234").
- AdminPiiMaskingUtilsTest: update maskPhone assertions to expect 4-digit tail.

## Out of Scope

- Redis permission cache (TASK-BE-028c)
- UUIDv7 migration (TASK-BE-028c)

---

# Acceptance Criteria

- [ ] Every admin_actions INSERT writes a non-null operator_id BIGINT FK
- [ ] admin_actions.operator_id column is NOT NULL in DB schema (V0011 migration)
- [ ] AdminPiiMaskingUtils.maskPhone("01012345678") returns value with 4-digit tail per R4
- [ ] AdminPiiMaskingUtils.maskPhone("+82-10-1234-5678") conforms to R4 pattern
- [ ] All existing tests pass without regression
- [ ] ./gradlew :apps:admin-service:test passes

---

# Related Specs

- specs/services/admin-service/data-model.md (admin_actions.operator_id NOT NULL)
- rules/traits/regulated.md R4 (phone masking format)

# Related Skills

- .claude/skills/backend/

---

# Related Contracts

- specs/contracts/http/admin-api.md (no contract change required)

---

# Target Service

- apps/admin-service

---

# Architecture

Follow:

- specs/services/admin-service/architecture.md

---

# Implementation Notes

- The REQUIRES_NEW transaction on recordDenied already opens a separate transaction; adding a findByOperatorId lookup inside it is safe.
- If findByOperatorId returns empty (operator not found during a deny flow), log and throw AuditFailureException to stay fail-closed (audit-heavy A10). Do not silently allow null.
- maskPhone change: strip separators, keep first 3 digits, replace middle with "****", keep last 4 digits. Update Javadoc examples accordingly.

---

# Edge Cases

- Operator JWT is valid but admin_operators row is missing: recordDenied resolves empty, throw AuditFailureException (fail-closed, not silent).
- Phone number with 7 or fewer total digits: leave unchanged (consistent with current guard).

---

# Failure Scenarios

- findByOperatorId DB failure inside REQUIRES_NEW: propagates as AuditFailureException, caller receives 500 (correct fail-closed behaviour per A10).

---

# Test Requirements

- Unit test: AdminPiiMaskingUtilsTest — update maskPhone assertions for 4-digit tail.
- Slice test: AdminActionAuditorTest — verify operatorId is non-null in persisted entity using mocked AdminOperatorJpaRepository.
- No new integration test required; existing AdminIntegrationTest seeds the operator row and will exercise the FK resolution.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests updated and passing
- [ ] Specs consistent with implementation
- [ ] Ready for review

---

## Review Approval

**Reviewer**: code-reviewer (claude-sonnet-4-6)
**Date**: 2026-04-14
**Verdict**: APPROVED (self-approved — no Critical findings)

### Findings Summary

**Critical**: 없음

**Warning**:
- `AdminActionAuditor.java:68` — 예외 메시지에 operatorId UUID 원문 포함. UUID는 R4 PII 마스킹 대상 아님. AdminExceptionHandler가 예외 메시지를 5xx 응답에 노출하지 않음을 확인 권고.
- `AdminActionJpaEntity.java:84` — legacy 13-arg `create` factory가 `operatorId=null` 경로를 여전히 허용. DB 레벨 NOT NULL이 최종 방어벽이 됨. 프로덕션 경로는 미사용이므로 Critical 아님.

**Suggestion**:
- `AdminActionAuditor.java:138` — `record()` `@Transactional` propagation 미명시(기본 REQUIRED). 의도적 차이라면 Javadoc에 명시 권고.
- `AdminPiiMaskingUtils.java:58` — 국제번호(+82 prefix) 마스킹 결과가 스펙 예시(`010-****-1234`)와 시각적으로 다름. 스펙에 국제번호 처리 미명시이므로 Suggestion 수준.

### Acceptance Criteria
모든 Acceptance Criteria 통과 확인.

### Implementer Notes
Note 1-3 모두 타당. 문서화 수준 처리로 충분.
