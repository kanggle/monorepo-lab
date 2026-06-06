# Task ID

TASK-BE-030

# Title

admin-service — 대량 account lock 엔드포인트 (idempotent, 배치 상한)

# Status

backlog

# Owner

backend

# Task Tags

- code
- api

# depends_on

- TASK-BE-028

---

# Goal

보안 사고 대응 시 다수 계정을 한 번에 lock할 수 있게 한다. 멱등 키·per-row outcome·배치 상한·개별 감사 row를 보장한다.

---

# Scope

## In Scope

- `POST /api/admin/accounts/bulk-lock` — body: `{accountIds: [], reason: string}`, header: `Idempotency-Key`
- 배치 상한 100개, 초과 시 422
- 처리 방식: 순차 처리, 각 계정별로 기존 `LockAccountUseCase` 호출
- 응답: `{results: [{accountId, outcome, error?}]}` — 부분 실패 허용
- `admin_actions`에 타겟 계정별 1 row 기록
- idempotency: 동일 Idempotency-Key 재요청 시 이전 응답 재생성 없이 동일 body 반환

## Out of Scope

- 비동기/큐 기반 대량 처리 (현 스코프 100개 수준이므로 동기)
- bulk-unlock (추후 필요 시 별도 태스크)

---

# Acceptance Criteria

- [ ] 101개 전달 → 422 `BATCH_SIZE_EXCEEDED`
- [ ] 50개 중 5개 실패 → 200, per-row outcome에 실패 사유 포함
- [ ] `admin_actions` row 수 = 성공+실패 합계
- [ ] 동일 Idempotency-Key 재전송 → 이전 결과 그대로 반환, 추가 row 생성 없음
- [ ] `account.lock` permission 없으면 403

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/rbac.md`

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- 리스트 중복 accountId → 1회만 처리
- 존재하지 않는 accountId → per-row outcome=NOT_FOUND

---

# Failure Scenarios

- 중간 계정 처리 중 DB 장애 → 이미 lock된 계정은 유지, 이후 실패 outcome 기록 (부분 성공)

---

# Test Requirements

- Unit: 배치 상한·중복 제거
- Integration: 멱등성, 부분 실패 시나리오

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Ready for review
