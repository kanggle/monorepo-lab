---
name: review-checklist
description: Code review checklist
category: root
---

# Review Checklist

Standard review checklist for task implementation verification.
All review commands (`review-task`) must use this checklist as the single source of truth.

## Prerequisite Specs

- `platform/coding-rules.md`
- `platform/naming-conventions.md`
- `platform/error-handling.md`
- `platform/security-rules.md`
- `platform/testing-strategy.md`

---

## Spec Compliance
- [ ] All Acceptance Criteria met
- [ ] Related Specs rules followed
- [ ] Request/Response field names match `specs/contracts/` exactly
- [ ] Event schemas match event contracts

## Architecture Compliance
- [ ] Follows the architecture style declared in `specs/services/<service>/architecture.md`
- [ ] Layer dependency direction is correct
- [ ] No forbidden dependencies (infrastructure imports in application layer, framework imports in domain layer)
- [ ] No domain logic leaked into `libs/`
- [ ] Controller does not call repositories directly

## Code Quality
- [ ] `platform/coding-rules.md` followed
- [ ] `platform/naming-conventions.md` followed (Command/Result, Request/Response patterns, test method naming)
- [ ] Error response format matches `platform/error-handling.md`
- [ ] No duplicate code or excessive complexity
- [ ] No resource leak risk

## Security
- [ ] No `platform/security-rules.md` violations
- [ ] No SQL injection or XSS risk
- [ ] No missing authentication/authorization
- [ ] No hardcoded secrets
- [ ] No missing input validation

## Performance
- [ ] No N+1 queries
- [ ] No unnecessary data loading
- [ ] No frequent queries without indexes
- [ ] Transaction scope is appropriate

## Testing
- [ ] Required test levels from `platform/testing-strategy.md` are covered
- [ ] All tests pass
- [ ] Tests cover Edge Cases and Failure Scenarios listed in the task
