# Task ID

TASK-BE-154

# Title

`gateway-service/architecture.md` — `### Service Type Composition` standard header backfill + closing `## Routes` paragraph 정정 (TASK-BE-153 HARDSTOP-10 follow-up)

# Status

done

# Owner

backend

# Task Tags

- spec
- adr-followup
- bugfix
- spec-drift
- hook-fix

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

TASK-BE-153 impl PR (commit `7e14a218`, 2026-05-14) 의 직속 follow-up — gateway-service `architecture.md` 의 spec drift 2건 해소:

1. **HARDSTOP-10 hook block 해소** — `## Identity` table L14 cell 의 `| Service Type | \`rest-api\` (edge gateway role) |` 가 standard header pattern (`### Service Type` 또는 `**Service Type**:` bold) 미준수. BE-153 impl 시 spec edit (closing paragraph 정정) 이 hook block 으로 좌초. BE-150 inbound 사례 동일 (memory: `project_be_150_inbound_architecture_hook_fix.md`).

2. **Stale closing paragraph 정정** — `## Routes (v1)` section 의 마지막 paragraph `All other paths return \`404 NOT_FOUND\`. Routes for outbound and notification-service arrive in subsequent \`TASK-INT-*\` tickets.` 가 BE-153 머지 후 stale (outbound = BE-153 으로 등록 완료, notification = event-consumer 영구 제외).

provenance:
- TASK-BE-153 impl commit `7e14a218` body § "HARDSTOP-10 hook block" + INDEX entry follow-up candidate.
- BE-150 inbound 사례 직접 답습 (memory).

---

# Scope

## In Scope

### A. `architecture.md` line ending normalize (필요 시)

- BE-150 절차 답습: PowerShell `[System.IO.File]::WriteAllText` 으로 CRLF → LF normalize.
- hook 의 `$existing.Contains($oldString)` 가 CRLF vs LF mismatch 로 false simulation 회피.

### B. `### Service Type Composition` standard sub-section 추가

- 위치: `## Identity` table 직후, `## Role` section 직전.
- inventory-service / inbound-service architecture.md 답습 pattern.
- gateway-service 는 single-type (rest-api edge gateway role) — Composition body 는 short:
  - `rest-api` (primary) for all proxied traffic (`/api/v1/**`).
  - No `event-consumer` path — gateway emits no events, consumes none.
  - Reference `platform/service-types/rest-api.md`.

### C. `## Identity` table L14 cell 정정

`| Service Type | \`rest-api\` (edge gateway role) |` → `| Service Type | \`rest-api\` (edge gateway role; see Service Type Composition below) |` (inbound-service L14 답습).

### D. Closing paragraph 정정

`## Routes (v1)` 의 마지막 paragraph:

```
All other paths return `404 NOT_FOUND`. Routes for outbound and
notification-service arrive in subsequent `TASK-INT-*` tickets.
```

→

```
All other paths return `404 NOT_FOUND`. `notification-service` is an
event-consumer (no REST surface) and therefore does not appear in this table —
its Kafka-only ingress is documented in
`specs/services/notification-service/architecture.md`.
```

### E. lifecycle 진행

- task ready → in-progress → review → done.
- INDEX 동기.

## Out of Scope

- Routes table 의 outbound 행 backfill — 이미 BE-153 impl PR 에서 완료.
- 본 spec 의 다른 internal restructure — 본 task = 2 spec drift fix only.
- inventory-service + admin-service `application.yml` SERVER_PORT default 정합 — BE-153 surfaced 한 별 spec drift, 별 follow-up task (TASK-BE-155 후보).

---

# Acceptance Criteria

- [ ] gateway-service `architecture.md` line ending = LF only (PowerShell byte check 검증).
- [ ] `### Service Type Composition` sub-section 추가 (## Identity table 직후, ## Role section 직전).
- [ ] L14 cell 정정 (`(edge gateway role)` → `(edge gateway role; see Service Type Composition below)`).
- [ ] `## Routes (v1)` 의 closing paragraph 정정 (outbound + notification 정확 분류).
- [ ] HARDSTOP-10 hook PASS (Edit tool 호출 시 block 없음).
- [ ] task lifecycle ready → review.
- [ ] `wms tasks/INDEX.md` 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] INDEX `## review` 제거, `## done` append 1-line outcome.

---

# Related Specs

- `projects/wms-platform/specs/services/gateway-service/architecture.md` (target file)
- `projects/wms-platform/specs/services/inventory-service/architecture.md` (Composition pattern source, L23-)
- `projects/wms-platform/specs/services/inbound-service/architecture.md` (Composition pattern + L14 cell pattern, BE-150 답습)
- `projects/wms-platform/tasks/done/TASK-BE-153-gateway-service-routes-backfill.md` (provenance, surfaced follow-up)
- `projects/wms-platform/tasks/done/TASK-BE-150-inbound-architecture-hook-fix-and-marker-cleanup.md` (절차 직접 답습)

---

# Related Contracts

본 task = spec edit only. HTTP API contract 자체 변경 0. event contract 변경 0.

---

# Target Service

`gateway-service` (wms-platform). 1 file (`architecture.md`) edit.

---

# Architecture

`### Service Type Composition` sub-section pattern (inventory + inbound 답습):

```
### Service Type Composition

`gateway-service` is `rest-api` (primary) — all traffic is HTTP request/
response proxying for the wms portfolio. There is no `event-consumer` path:
the gateway emits no domain events and consumes none.

Read `platform/service-types/rest-api.md` (primary) when implementing —
no secondary service-type spec applies.
```

위치: `## Identity` table (L7-22) 직후, `## Role` section (L25-) 직전.

---

# Implementation Notes

## BE-150 절차 답습 (memory: `project_be_150_inbound_architecture_hook_fix.md`)

1. **PowerShell `[System.IO.File]::WriteAllText`** 으로 file line ending CRLF → LF normalize. hook 의 `$existing.Contains($oldString)` 가 CRLF vs LF mismatch 로 false simulation 회피.
2. **`### Service Type Composition` sub-section 신규** (short, single-type gateway 적합).
3. **L14 cell 정정** — inbound-service L14 패턴 답습 (`(primary; see Service Type Composition below)`).
4. **Closing paragraph 정정** — outbound (BE-153 완료) + notification (event-consumer 영구 제외) 정확 분류.

## D4 churn impact

- 1 file `projects/wms-platform/specs/services/gateway-service/` edit.
- ~10 line addition + ~2 line modification.
- ADR-MONO-003a § D1.1 인접 (project-internal spec drift fix). D4 OVERRIDE 자연 적용.

---

# Edge Cases

- HARDSTOP-10 hook 이 단순 `### Service Type` 만 요구할 수도, `### Service Type Composition` (Composition 명시) 까지 요구할 수도 있음 — 안전 답습 (Composition pattern). 단순 header 만으로 부족하면 추가 확장.
- gateway-service architecture.md 의 다른 cell pattern drift (`**Service Type**:` bold pattern 의 부재) — 본 task 의 Composition section 추가로 자연 해소 (Hook 가 sub-section 의 standard header 인식).

---

# Failure Scenarios

- Hook 가 Composition section 추가 후에도 HARDSTOP-10 block 시 → header pattern 추가 fine-tune (예: `### Service Type` simple 변형). worst-case = inventory-service / inbound-service file 의 정확한 hook compliance pattern 분석 필요.
- closing paragraph 정정 시 다른 textual drift 발견 → 별 task 후보 (scope discipline).

---

# Test Requirements

- production code = 0.
- CI markdown-only path-filter (TASK-MONO-074/075 답습) → 1 PASS + 15 SKIP 예상.
- 본 spec edit 후 별 unit/integration test 추가 0.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### CI verification

- [ ] markdown-only PR (1 PASS + 15 SKIP), 회귀 0.

### Close chore PR

- [ ] review → done, `wms tasks/INDEX.md` 동기.

---

# Provenance

- TASK-BE-153 impl commit `7e14a218` (2026-05-14) § HARDSTOP-10 hook block surfaced follow-up.
- BE-150 inbound 사례 직접 답습 (memory: `project_be_150_inbound_architecture_hook_fix.md`).
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (substantial = file structure + closing paragraph + L14 cell 3 edit, hook compliance verification).
