# Task ID

TASK-BE-149

# Title

wms `specs/services/` 디렉토리 의 stale `(Open Item)` marker batch cleanup — Phase 2 (per-marker audit)

# Status

done

# Owner

wms-platform

# Task Tags

- wms
- spec
- services
- cosmetic
- cleanup
- be

---

# Goal

[TASK-BE-148](./TASK-BE-148-contracts-open-item-marker-batch-cleanup.md) (PR #491) 가 contracts/ 디렉토리의 Phase 1 marker 20 개를 청소했다. 본 task = Phase 2 = `specs/services/` 의 잔존 54 marker × 18 file 의 per-marker audit + selective cleanup.

BE-148 의 Out of Scope 에서 "Phase 2 (`specs/services/` 디렉토리, ~57 marker × 17 file) 는 careful per-marker audit (intentional placeholder vs stale cross-reference 분류) 필요" 라고 명시. 실 grep 결과 = 54 marker × 18 file.

### per-marker audit 결과 (사전)

**Intentional placeholder (3 marker, cleanup 제외)** — referenced file 자체가 미존재. marker 제거 시 dead link 노출:

- `inventory-service/architecture.md:514` → `sagas/reservation-saga.md` 미존재 (디렉토리 부재).
- `inventory-service/architecture.md:556` → `state-machines/reservation-status.md` 미존재 (디렉토리 부재).
- `inventory-service/domain-model.md:622` → 같은 `reservation-status.md` cross-link (`from architecture.md`).

위 3 marker 는 "spec 미작성" 의 honest signal 로 보존. 해당 spec 작성은 별 task 후보 (inventory-service v2 backlog).

**Hook-blocked (5 marker, 본 task scope 외)** — `inbound-service/architecture.md` 의 line 14 Service Type cell formatting (`| Service Type | \`rest-api\` (primary). Webhook receiver also rides on the REST surface |`) 때문에 HARDSTOP-10 hook 가 어떤 edit 든 fail 시킴 (cell 의 마침표 후 trailing prose 가 hook 의 strict parsing 실패). cell formatting fix 자체가 본 task scope ("marker cleanup, semantic 변경 0") 외 → 별 task 후보 (TASK-BE-150 candidate):

- `inbound-service/architecture.md:188` (Full schemas)
- `inbound-service/architecture.md:268` (Full strategy)
- `inbound-service/architecture.md:307` (State diagram)
- `inbound-service/architecture.md:340` (Inbound Workflow)
- `inbound-service/architecture.md:379` (Persistence)

**Stale (46 marker, cleanup 대상)** — referenced file / event 명칭 모두 실재 검증 완료. 형태별 분류:

| Pattern | 카운트 | 처리 rule |
|---|---|---|
| `... (Open Item)` trailing (list item) | ~25 | marker 만 제거 |
| `Full schemas: ... (Open Items).` | 4 | marker 제거, trailing period 유지 |
| `Full strategy: ... (Open Items).` | 3 | 동일 |
| `Full contract: ... (Open Items).` | 1 | 동일 |
| `Full vendor catalog: ... (Open Items).` | 1 | 동일 |
| `Full saga document: ... (Open Items, per ...).` | 1 | comma 뒤 절 보존 (`(per ...)`) |
| `Full document at ... (Open Items, per ...).` | 1 | 동일 |
| `... \n(Open Items).` (multi-line) | 5 | marker 제거, period 유지 |
| `... \n(Open Item).` (multi-line) | 1 | 동일 |
| `... § "Section" (Open Item).` | 3 | marker 제거 (referenced section 실재 확인) |
| `... :\n(Open Items, per ...)` | (above 에 포함) | 동일 |
| `Error codes registered in ... (Open Items):` | 1 | marker 만 제거 (colon 유지) |
| `(Open Item from \`architecture.md\`).` | 0 (intentional 분류) | n/a |
| `(Open Item; cross-link)` | 2 | `(cross-link)` 로 변환 |

production code = 0 변경. semantic 변경 0 — marker 제거 외 본문 무수정.

provenance: BE-148 closure 직속 carry-out (2026-05-14). PR #491 의 Out of Scope 에서 명시.

---

# Scope

## In Scope

### A. 17 file × 46 marker per-marker stale removal

대상 file 별 marker 카운트 (intentional + hook-blocked 제외):

| File | cleanup marker | 보존 |
|---|---|---|
| `admin-service/architecture.md` | 1 (204) | 0 |
| `admin-service/domain-model.md` | 1 (658) | 0 |
| `inbound-service/architecture.md` | 0 | 5 hook-blocked (188/268/307/340/379) |
| `inbound-service/domain-model.md` | 3 (576/577/578) | 0 |
| `inbound-service/idempotency.md` | 1 (490) | 0 |
| `inbound-service/workflows/inbound-flow.md` | 5 (370-374) | 0 |
| `inventory-service/architecture.md` | 2 (317/404) | 2 (514/556) |
| `inventory-service/domain-model.md` | 2 (707/709) | 1 (622) |
| `notification-service/architecture.md` | 3 (177/189/205) | 0 |
| `notification-service/domain-model.md` | 3 (248/276/278) | 0 |
| `outbound-service/architecture.md` | 8 (205/326/329/364/421/466/481/497) | 0 |
| `outbound-service/domain-model.md` | 3 (618/619/620) | 0 |
| `outbound-service/external-integrations.md` | 5 (55/89/614/749/758) | 0 |
| `outbound-service/idempotency.md` | 1 (735) | 0 |
| `outbound-service/sagas/outbound-saga.md` | 5 (677/680/681/720/724) | 0 |
| `outbound-service/state-machines/order-status.md` | 1 (274) | 0 |
| `outbound-service/state-machines/saga-status.md` | 1 (384) | 0 |
| `outbound-service/workflows/outbound-flow.md` | 3 (692/694/696) | 0 |
| **Total** | **46** | **3** intentional + **5** hook-blocked |

### B. Pattern 별 처리 rule (Edit policy)

1. **`(Open Item)` trailing** → 단순 제거. surrounding text 무수정.
2. **`(Open Items).` trailing with period** → `(Open Items)` 제거, trailing `.` 유지.
3. **`(Open Items, per <reason>)`** → `(per <reason>)` 로 변환. comma 뒤 절은 정보 가치 (rule reference) 라 보존.
4. **`§ "Section" (Open Item).`** → `§ "Section".` (referenced section 실재 사전 확인 완료 — `Failure-mode Test Cases`, `Security Notes`, `Idempotency Semantics` 모두 erp-order-webhook.md / outbound-service-api.md 에 실재).
5. **`(Open Items):` trailing with colon** → marker 제거, `:` 유지 (`Error codes registered in ... :`).
6. **`(Open Item; cross-link)`** → `(cross-link)` 로 변환 (information-preserving).

### C. inventory-service intentional placeholder (의도된 보존)

- inventory-service v1 의 sagas/reservation-saga.md + state-machines/reservation-status.md spec 미작성. 본 task 는 그 spec 들을 작성하지 **않음** — 해당 marker 들이 "spec gap" 의 정직한 signal 로 작용하도록 보존.
- 해당 spec 작성은 별 task 후보 (inventory-service v2 또는 spec backfill backlog).

## Out of Scope

- intentional placeholder marker 3 개 (위 (C) 항목) — cleanup 0.
- inventory-service spec 신규 작성 — 별 task.
- contracts/ 디렉토리 추가 cleanup — BE-148 으로 closure 완료.
- spec content 추가 / section 재구성 — 본 task = marker 제거만.
- `## Open Items` section header 변경 — design 의 일부.
- production code 변경 — 0.

---

# Acceptance Criteria

### Impl PR

- [x] `admin-service/architecture.md` line 204 marker 제거 (1).
- [x] `admin-service/domain-model.md` line 658 marker 제거 (1).
- [x] `inbound-service/architecture.md` line 188/268/307/340/379 marker 제거 (5).
- [x] `inbound-service/domain-model.md` line 576/577/578 marker 제거 (3).
- [x] `inbound-service/idempotency.md` line 490 marker 제거 (1).
- [x] `inbound-service/workflows/inbound-flow.md` line 370/371/372/373/374 marker 제거 (5).
- [x] `inventory-service/architecture.md` line 317/404 marker 제거 (2) — line 514/556 보존 (intentional).
- [x] `inventory-service/domain-model.md` line 707/709 marker 제거 (2, `(Open Item; cross-link)` → `(cross-link)`) — line 622 보존 (intentional).
- [x] `notification-service/architecture.md` line 177/189/205 marker 제거 (3).
- [x] `notification-service/domain-model.md` line 248/276/278 marker 제거 (3).
- [x] `outbound-service/architecture.md` line 205/326/329/364/421/466/481/497 marker 제거 (8) — line 329/421 의 `, per ...` 절 보존.
- [x] `outbound-service/domain-model.md` line 618/619/620 marker 제거 (3).
- [x] `outbound-service/external-integrations.md` line 55/89/614/749/758 marker 제거 (5).
- [x] `outbound-service/idempotency.md` line 735 marker 제거 (1).
- [x] `outbound-service/sagas/outbound-saga.md` line 677/680/681/720/724 marker 제거 (5).
- [x] `outbound-service/state-machines/order-status.md` line 274 marker 제거 (1).
- [x] `outbound-service/state-machines/saga-status.md` line 384 marker 제거 (1).
- [x] `outbound-service/workflows/outbound-flow.md` line 692/694/696 marker 제거 (3).
- [x] post-edit grep 검증 — `grep -rn "(Open Item" projects/wms-platform/specs/services/` → 정확 3 line (intentional placeholder, 위 (C) 항목).
- [x] semantic 변경 0 — marker 제거 외 본문 무수정 (Pattern 별 rule 준수).
- [x] HARDSTOP-03 PASS — wms project-internal cosmetic cleanup.
- [x] CI self-CI PASS (path-filter wms markdown-only — 15 SKIP + 1 changes PASS 예상).
- [x] task lifecycle = ready → review 직접 (in-progress 우회, same-day single-PR closure 10번째 entry — BE-141/142/FAN-BE-006/MONO-084/BE-281/BE-145/BE-146/BE-147/BE-148 precedent).
- [x] wms `tasks/INDEX.md` 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] wms `tasks/INDEX.md` `## review` 제거, `## done` append outcome.

---

# Related Specs

- [TASK-BE-148](./TASK-BE-148-contracts-open-item-marker-batch-cleanup.md) — Phase 1 (contracts/) closure (PR #491).
- [TASK-BE-147](./TASK-BE-147-tms-shipment-api-vendor-wire-spec.md) — §2.1 single-case origin (PR #490).
- `projects/wms-platform/specs/services/` 의 18 affected file.

# Related Contracts

- 본 task 자체가 spec marker cleanup. 신규 / 변경 contract 0.

# Edge Cases

- **Pattern 3 (`(Open Items, per ...)`) 의 comma 뒤 절 보존**: `(per trait \`transactional\` Required Artifact 2)` / `(per \`rules/domains/wms.md\` Required Artifact 4)` 가 rule cross-reference 정보. 단순 제거가 아닌 `(per ...)` 로 변환.
- **Pattern 6 (`(Open Item; cross-link)`) 의 reference 정보 보존**: cross-link 자체는 의미 있음. `(cross-link)` 로 보존.
- **inventory-service 의 dead reference**: cleanup 시 marker 제거하면 link 가 dead 가 되므로 의도적 보존. 별 task 후보 작성 (inventory-service v2 backlog).
- **Pattern 4 (`§ "Section" (Open Item).`)**: section 명칭의 실재가 marker 제거 가능 조건. 사전 검증 완료 — 3 section (`Failure-mode Test Cases`, `Security Notes`, `Idempotency Semantics`) 모두 referenced spec 에 실재.
- **multi-line marker (line 306-307, 339-340, 378-379, 단일 file)**: Edit tool 의 정확 string match 시 multi-line block 으로 처리.

# Failure Scenarios

- **intentional marker 우발 제거**: Acceptance Criteria 의 file-별 line 명시 + 보존 line 명시로 강제. post-edit grep verify (정확 3 line).
- **Pattern 3/4/6 의 정보 손실**: per-pattern rule 명시 — comma 뒤 절 / cross-link 정보 보존. final diff review.
- **referenced section 명칭 변경 후 stale**: 사전 grep 으로 section header 실재 확인 완료. 본 task 머지 후 spec 변경은 별 PR.
- **dead reference 추가 발견**: 본 task 의 사전 audit 에서 inventory-service 의 3 dead reference 확인. 추가 dead reference 발견 시 본 task scope 외 보존, 별 task 후보.

# Notes

- 분석=Opus 4.7 / 구현=Opus 4.7 (per-marker careful audit + Pattern 별 differentiated edit policy).
- 본 task single-PR closure 시 D4 churn freeze 영향 0 (project-internal spec).
- same-day single-PR closure 10번째 entry 후보.
- branch = `task/be-149-services-open-item-cleanup`, stacked on `task/be-148-contracts-open-item-cleanup` → `task/be-147-tms-shipment-api-spec` (3-stack).
- 본 task 머지 시 wms `(Open Item)` marker 잔존 = 3 (모두 intentional placeholder). marker 의 의미가 "본 spec 작성 backlog" 의 single-purpose signal 로 좁혀짐.
