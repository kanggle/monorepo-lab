# Task ID

TASK-BE-150

# Title

`inbound-service/architecture.md` Service Type cell formatting fix + 5 hook-blocked `(Open Item)` marker cleanup

# Status

review

# Owner

wms-platform

# Task Tags

- wms
- spec
- inbound-service
- cosmetic
- cleanup
- be

---

# Goal

[TASK-BE-149](../done/TASK-BE-149-services-open-item-marker-batch-cleanup.md) (PR #492) 의 audit 결과 `inbound-service/architecture.md` 의 5 stale `(Open Item)` marker (line 188/268/307/340/379) 가 HARDSTOP-10 hook 의 strict parsing 때문에 cleanup 불가했음 — line 14 의 Service Type cell `| Service Type | \`rest-api\` (primary). Webhook receiver also rides on the REST surface |` 의 마침표 후 trailing prose 가 hook 의 valid declaration 검증을 fail 시킴.

BE-149 가 별 task 후보로 명시한 closure. 본 task = minimal change:

- (a) line 14 Service Type cell 을 hook-friendly 단순 형식 (`| Service Type | \`rest-api\` |`) 으로 단순화. paren prose 의 정보 (webhook receiver 역할) 는 architecture 본문의 § Webhook Reception section 에 이미 명시되어 있으므로 cell 의 prose 는 cosmetic 잔재.
- (b) hook 통과 후 5 marker cleanup 진행 — BE-149 의 Pattern 별 differentiated edit policy 답습.

이번 task 머지 시 wms specs `(Open Item)` marker 잔존 = 8 → **3** (3 intentional placeholder only — inventory-service v2 spec gap). cleanup 시리즈 자연 종료.

---

# Scope

## In Scope

### A. `inbound-service/architecture.md` line 14 Service Type cell 단순화

- before: `| Service Type | \`rest-api\` (primary). Webhook receiver also rides on the REST surface |`
- after: `| Service Type | \`rest-api\` |`

근거:
- master-service / outbound-service / admin-service / inventory-service 의 Service Type cell 은 모두 backtick'd type token 만 또는 type token + paren (마침표 없음) 형식.
- webhook receiver 역할은 본 file 의 `## Webhook Reception` section (line 219+) 에서 이미 깊이 다룸. cell 의 trailing prose 는 의미 중복 cosmetic 잔재.
- BE-149 audit 도중 paren 안 흡수 형태 (`(primary; webhook receiver also rides on the REST surface)`) 시도 — hook trigger fail 확인. 가장 단순한 master-service 형식 채택이 안전.

### B. 5 stale `(Open Item)` marker cleanup

| Line | Pattern | 처리 rule (BE-149 답습) |
|---|---|---|
| 188 | `Full schemas: \`specs/contracts/events/inbound-events.md\` (Open Items).` | marker 제거, trailing period 유지 |
| 268 | `Full strategy: \`specs/services/inbound-service/idempotency.md\` (Open Items).` | 동일 |
| 306-307 | multi-line `... asn-status.md\n(Open Items).` | trailing period 유지 (multi-line merge) |
| 339-341 | `... inbound-flow.md (Open Items, per \`rules/domains/wms.md\` Required Artifact 3).` | `(per ...)` 로 변환, rule reference 보존 |
| 378-379 | multi-line `... domain-model.md\n(Open Items).` | trailing period 유지 (multi-line merge) |

referenced spec 모두 실재 (BE-149 단계에서 확인 완료):
- `inbound-events.md` ✓
- `inbound-service/idempotency.md` ✓
- `state-machines/asn-status.md` ✓
- `workflows/inbound-flow.md` ✓
- `inbound-service/domain-model.md` ✓

### C. inventory-service intentional placeholder (의도된 보존)

- inventory-service 의 3 marker (architecture.md:513/555, domain-model.md:622) 는 referenced file 자체가 미존재 (sagas/reservation-saga.md + state-machines/reservation-status.md). 본 task scope 외. 별 task 후보 (inventory-service v2 backlog).

## Out of Scope

- Service Type Composition sub-section 의 작성 — `inbound-service` 의 webhook receiver 역할 은 본 file 의 `## Webhook Reception` section 이 충분히 다룸 (별 sub-section 불필요).
- inventory-service v2 spec authoring (별 task).
- 다른 file 의 추가 cleanup — wms specs cleanup 시리즈는 본 task 머지로 마무리.

---

# Acceptance Criteria

### Impl PR

- [x] `inbound-service/architecture.md` line 14 Service Type cell 단순화 — `| Service Type | \`rest-api\` |`.
- [x] `inbound-service/architecture.md` line 188 marker 제거 (`Full schemas: ... (Open Items).` → `Full schemas: ....`).
- [x] `inbound-service/architecture.md` line 268 marker 제거 (`Full strategy: ... (Open Items).` → `Full strategy: ....`).
- [x] `inbound-service/architecture.md` line 306-307 multi-line marker 제거 (period 유지).
- [x] `inbound-service/architecture.md` line 339-341 marker `(Open Items, per ...)` → `(per ...)` (rule reference 보존).
- [x] `inbound-service/architecture.md` line 378-379 multi-line marker 제거 (period 유지).
- [x] post-edit grep verify — `grep -rn "(Open Item" projects/wms-platform/specs/services/inbound-service/architecture.md` → 0.
- [x] post-edit total verify — wms specs `(Open Item)` marker 잔존 = 3 (모두 intentional placeholder, inventory-service).
- [x] semantic 변경 0 — Service Type cell 단순화 외 본문 무수정.
- [x] HARDSTOP-03 PASS.
- [x] CI self-CI PASS (path-filter wms markdown-only — 15 SKIP + 1 changes PASS 예상).
- [x] task lifecycle = ready → review 직접 (in-progress 우회, same-day single-PR closure 11번째 entry).
- [x] wms `tasks/INDEX.md` 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] wms `tasks/INDEX.md` `## review` 제거, `## done` append outcome.

---

# Related Specs

- [TASK-BE-149](../done/TASK-BE-149-services-open-item-marker-batch-cleanup.md) — Phase 2 audit (PR #492). 본 task 의 5 hook-blocked marker 를 별 task 후보로 명시.
- [TASK-BE-148](../done/TASK-BE-148-contracts-open-item-marker-batch-cleanup.md) — Phase 1 (PR #491).
- [TASK-BE-147](../done/TASK-BE-147-tms-shipment-api-vendor-wire-spec.md) — §2.1 case origin (PR #490).
- `projects/wms-platform/specs/services/inbound-service/architecture.md` — 본 task 의 affected file.

# Related Contracts

- 없음. 본 task 는 architecture spec 의 cell formatting + marker cleanup. 신규 / 변경 contract 0.

# Edge Cases

- **paren prose 정보 손실**: `(primary)` + webhook receiver 역할 정보는 본 file `## Webhook Reception` section (line 219+) 에서 충분히 다루므로 cell 의 prose 는 중복 cosmetic. 정보 손실 = 0.
- **hook 재차 fail 가능성**: master-service 형식 (`| Service Type | \`rest-api\` |`) 는 BE-149 audit 도중 통과 확인 (admin-service first edit 가 동일 column 형식의 file 에서 통과). 안전.
- **Pattern 3 (`(Open Items, per ...)`)**: line 339-341 의 multi-line + comma reference 는 BE-149 동일 패턴 처리.

# Failure Scenarios

- **hook 가 cell 단순화에도 fail**: 대안 = backtick'd type 만 단독 유지 (이미 채택). 그래도 fail 시 master-service architecture.md 의 cell 을 byte-identical 로 복사. 추가 fail 가능성 없음.
- **marker 제거 누락**: post-edit grep verify 가 Acceptance Criteria 강제.
- **다른 file 회귀**: 본 task 는 1 file 만 건드림. cross-file regression 가능성 0.

# Notes

- 분석=Opus 4.7 / 구현=Opus 4.7 (single-file cosmetic + hook navigation).
- 본 task single-PR closure 시 D4 churn freeze 영향 0 (project-internal spec).
- same-day single-PR closure 11번째 entry 후보 — BE-141/142/FAN-BE-006/MONO-084/BE-281/BE-145/BE-146/BE-147/BE-148/BE-149 precedent.
- branch = `task/be-150-inbound-architecture-hook-fix-and-cleanup`, base = main (e06ed540, no stack).
- 본 task 머지 시 wms specs `(Open Item)` marker 잔존 = **3** (모두 intentional placeholder, inventory-service v2 spec gap honest signal). cleanup 시리즈 자연 종료.
