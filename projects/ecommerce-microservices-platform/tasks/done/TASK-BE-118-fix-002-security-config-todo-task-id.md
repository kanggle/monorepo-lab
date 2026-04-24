# Task ID

TASK-BE-118-fix-002

# Title

TASK-BE-118-fix-001 리뷰 수정 — SecurityConfig TODO 주석에 연결된 태스크 ID 추가

# Status

ready

# Owner

backend

# Task Tags

- code

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

TASK-BE-118-fix-001 코드 리뷰에서 발견된 이슈를 수정한다.

1. **[Warning]** `SecurityConfig`의 `/api/internal/**` 블록에 추가된 TODO 주석(line 49)에 연결된 태스크 ID가 없다. `specs/platform/coding-rules.md`는 "No `TODO` comments without a linked task ID"를 명시하고 있다. TASK-BE-119(또는 향후 NetworkPolicy/mTLS ADR 태스크)의 ID를 TODO에 명시해야 한다.

---

# Scope

## In Scope

- `SecurityConfig.java`의 TODO 주석에 연결 태스크 ID 추가
  - NetworkPolicy/mTLS ADR 태스크가 아직 미생성인 경우, TASK-BE-119를 임시 참조로 추가하거나 별도 ADR 태스크 번호를 신규 채번하여 주석에 기록한다

## Out of Scope

- NetworkPolicy 또는 mTLS 실구현 (별도 태스크)
- 기타 SecurityConfig 기능 변경

---

# Acceptance Criteria

- [ ] `SecurityConfig`의 TODO 주석에 유효한 태스크 ID(`TASK-BE-XXX` 형식)가 명시된다
- [ ] `specs/platform/coding-rules.md` TODO 규칙을 준수한다
- [ ] 기존 auth-service 전체 테스트 통과

---

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/services/auth-service/architecture.md`

# Related Contracts

- 없음 (코드 주석만 변경)

---

# Target Service

- `auth-service`

---

# Edge Cases

- ADR 태스크가 아직 없는 경우 → 이 fix 태스크 ID(TASK-BE-118-fix-002)를 임시로 사용 가능

---

# Failure Scenarios

- 없음 (주석 수정만)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing (no new tests required for comment-only change)
- [ ] Contracts updated if required
- [ ] Specs updated if required
- [ ] Ready for review
