# Task ID

TASK-BE-090

# Title

batch-worker 테스트 커버리지 확대 — 핵심 잡 로직 및 실패 시나리오 테스트 추가

# Status

done

# Owner

backend

# Task Tags

- code
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

batch-worker의 테스트가 4개에 불과하여 주요 배치 잡 로직과 실패 시나리오가 검증되지 않는다. 핵심 잡 실행 로직, 에러 처리, 재시도 동작에 대한 단위/통합 테스트를 추가하여 커버리지를 확보한다.

---

# Scope

## In Scope

- 기존 배치 잡의 정상 실행 단위 테스트
- 잡 실패 시나리오 테스트 (DB 연결 실패, 처리 대상 없음 등)
- 잡 재시도 동작 테스트
- 잡 파라미터 검증 테스트

## Out of Scope

- 새로운 배치 잡 추가
- E2E 통합 테스트
- 성능 테스트

---

# Acceptance Criteria

- [ ] 각 배치 잡의 정상 실행 경로에 대한 단위 테스트가 존재한다
- [ ] 잡 실패 시 예외 처리가 검증된다
- [ ] 처리 대상이 없는 경우 정상 종료됨이 검증된다
- [ ] 테스트 커버리지가 주요 잡 로직 기준 80% 이상이다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/batch-worker/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

없음

---

# Target Service

- `batch-worker`

---

# Architecture

Follow:

- `specs/services/batch-worker/architecture.md`

---

# Implementation Notes

- 기존 4개 테스트를 먼저 분석하여 누락된 시나리오 파악
- Spring Batch Test 의존성 활용 (`JobLauncherTestUtils`)
- 테스트 DB는 H2 인메모리 사용

---

# Edge Cases

- 빈 데이터셋 처리
- 대량 데이터 처리 시 청크 동작
- 중복 잡 실행 방지

---

# Failure Scenarios

- DB 연결 실패 시 잡 종료 상태 확인
- 개별 아이템 처리 실패 시 스킵/재시도 동작
- 잡 중간 중단 후 재시작

---

# Test Requirements

- 배치 잡별 정상 실행 단위 테스트
- 실패 시나리오 단위 테스트
- 스킵/재시도 정책 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
