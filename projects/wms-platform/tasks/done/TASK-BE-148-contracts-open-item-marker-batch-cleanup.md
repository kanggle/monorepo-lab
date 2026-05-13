# Task ID

TASK-BE-148

# Title

wms `specs/contracts/` 디렉토리 의 stale `(Open Item)` marker batch cleanup — Phase 1 (events + http + webhooks)

# Status

done

# Owner

wms-platform

# Task Tags

- wms
- spec
- contracts
- cosmetic
- cleanup
- be

---

# Goal

[TASK-BE-147](./TASK-BE-147-tms-shipment-api-vendor-wire-spec.md) 의 §2.1 `(Open Item — vendor-controlled)` marker 청소가 단일 case 였다. 후속 portfolio-wide audit (2026-05-14, `/refactor-spec all` style 검색) 에서 `wms-platform/specs/` 의 `Open Item` 표기가 **101 occurrence × 20+ file** 잔존 발견. 그러나 marker 형태와 의도가 혼재:

- (a) **stale cross-reference marker** — referenced spec 이 이미 실재하는데 marker 만 남은 cosmetic 잔재. (BE-147 §2.1 case 와 동일 패턴.)
- (b) **intentional placeholder** — section 자체가 미완성 (`Full schemas: ... (Open Items)` 식).
- (c) **section header** — `## Open Items` 자체는 marker 가 아닌 design 의 일부.

본 task = **Phase 1 = `specs/contracts/` 디렉토리만**. 본 sub-tree 는 cross-reference 가 dense 하고 marker 형태가 거의 (a) type 으로 균질 — verify 완료한 referenced spec 이 모두 실재 (8 referenced spec × 4 event 명칭) 하므로 mechanical batch cleanup 적합.

Phase 2 (`specs/services/` + plural variant) 는 careful per-marker audit 가 필요하므로 별 task 후보 (본 task scope 외).

provenance: `/refactor-spec all` style audit 2026-05-14 contracts-sub-tree 부분. BE-147 closure (PR #490 open, 2026-05-14) 직속 carry-out.

---

# Scope

## In Scope

### A. `specs/contracts/` 디렉토리 의 stale `(Open Item)` marker 제거

대상 8 file × ~22 marker:

| File | marker line | referenced spec (실재 확인) |
|---|---|---|
| `contracts/events/inbound-events.md` | 438 / 439 | `contracts/http/inbound-service-api.md` / `contracts/webhooks/erp-asn-webhook.md` |
| `contracts/events/outbound-events.md` | 698 / 699 | `services/outbound-service/sagas/outbound-saga.md` / `state-machines/saga-status.md` |
| `contracts/events/inventory-events.md` | 525 / 526 | `contracts/events/inbound-events.md` (`inbound.putaway.completed` 정의 line 36/73/257 실재) / `outbound-events.md` (`outbound.picking.*` 정의 line 40/75-77 실재) |
| `contracts/http/inbound-service-api.md` | 854 | `services/inbound-service/idempotency.md` |
| `contracts/http/outbound-service-api.md` | 854 / 855 / 856 / 857 | `state-machines/order-status.md` / `saga-status.md` / `sagas/outbound-saga.md` / `idempotency.md` (전부 실재) |
| `contracts/webhooks/erp-asn-webhook.md` | 13 / 15 / 433 / 434 | `services/inbound-service/external-integrations.md` / `idempotency.md` (각 intro + References 양쪽) |
| `contracts/webhooks/erp-order-webhook.md` | 13 / 15 / 442 / 443 | `services/outbound-service/external-integrations.md` / `idempotency.md` (각 intro + References 양쪽) |
| `contracts/events/notification-subscriptions.md` | 78 | `services/notification-service/runbooks/dlt-replay.md` (BE-145 closure PR #470 머지, 2026-05-14) |

### B. marker 제거 + link 활성화 패턴

각 marker 의 형태별 정리 rule:

- `(Open Item)` singular trailing — 단순 제거. 이미 link 가 있으면 그대로 유지.
- `(Open Items)` plural — singular 와 동일 처리.
- `(consumed: foo — Open Item)` parenthetical 내장형 — `— Open Item` 만 제거하고 `(consumed: foo)` 유지.
- intro section 의 `[link](path) (Open Item) and [link](path) (Open Item)` 구조 (erp-*-webhook.md line 12-15) — `(Open Item)` 만 제거, link 활성화 상태 그대로.
- References section 의 backtick reference (예: `- \`specs/.../foo.md\` (Open Item)`) — `(Open Item)` 만 제거 + 추가로 markdown link 형태로 변환할지 결정 (선택 1: marker 만 제거 = mechanical; 선택 2: backtick → link 변환 = semantic 개선).

**본 task 채택**: 선택 1 (marker 제거만, backtick reference 는 형태 유지). 이유 = mechanical batch scope 유지, link 변환은 후속 cosmetic task 후보. BE-147 §2.1 case 도 link 형태였으므로 sibling 답습.

### C. Phase 1 scope 외 (의도된 제외)

- `specs/services/` 디렉토리의 `(Open Item)` / `(Open Items)` marker — Phase 2 별 task.
- `## Open Items` section header — design 의 일부, marker 아님. 제거 0.
- `Open Items §N` numbered cross-reference (예: `architecture.md Open Items §5`) — section 내부 참조, marker 아님. 제거 0.
- backtick path → markdown link 변환 — 후속 cosmetic task 후보.
- spec content 변경 — `(Open Item)` 제거 외 본문 무수정.

## Out of Scope

- Phase 2 (`specs/services/` 디렉토리, ~80 marker × 17 file) — 별 task.
- `(Open Items)` 가 intentional placeholder 인 경우의 audit — Phase 2 scope.
- backtick reference → markdown link 변환 — 후속 cosmetic task.
- spec content 추가 / section 재구성 — 본 task = marker 제거만.
- `## Open Items` section header 변경.

---

# Acceptance Criteria

### Impl PR

- [x] `inbound-events.md` line 438/439 marker 제거 (2 marker, References section).
- [x] `outbound-events.md` line 698/699 marker 제거 (2 marker, References section).
- [x] `inventory-events.md` line 525/526 marker 제거 (2 marker, References section, parenthetical "— Open Item" 형태).
- [x] `inbound-service-api.md` line 854 marker 제거 (1 marker, References section).
- [x] `outbound-service-api.md` line 854-857 marker 제거 (4 marker, References section).
- [x] `erp-asn-webhook.md` line 13/15/433/434 marker 제거 (4 marker — intro 2 + References 2).
- [x] `erp-order-webhook.md` line 13/15/442/443 marker 제거 (4 marker — intro 2 + References 2).
- [x] `notification-subscriptions.md` line 78 marker 제거 (1 marker — Operator runbook reference).
- [x] cross-ref 검증 — marker 제거 후 각 referenced spec path 의 dead reference 0 (Phase 1 사전 검증 완료).
- [x] semantic 변경 0 — marker 제거 외 본문 무수정.
- [x] HARDSTOP-03 PASS — 본 file 들은 wms project-specific spec.
- [x] CI self-CI PASS (path-filter wms markdown-only — 15 SKIP + 1 changes PASS 예상).
- [x] task lifecycle = ready → review 직접 (in-progress 우회, BE-147 / BE-145 / BE-146 precedent).
- [x] wms `tasks/INDEX.md` 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] wms `tasks/INDEX.md` `## review` 제거, `## done` append outcome.

---

# Related Specs

- [TASK-BE-147](./TASK-BE-147-tms-shipment-api-vendor-wire-spec.md) — `external-integrations.md` §2.1 의 단일 case marker 청소, 본 task 의 동일 패턴 확장.
- `projects/wms-platform/specs/contracts/` 의 8 affected file.
- `project_mono_085_dead_reference_batch.md` 메모리 § "옵션 C sequence" (A actionable-0 → B Tier 2 → C cosmetic carry-out) — 본 task 는 동일 옵션 C 패턴 ("cosmetic carry-out" 의 confidence-graded extension).

# Related Contracts

- 본 task 자체가 contract-layer spec 의 marker cleanup. 신규 / 변경 contract 0.

# Edge Cases

- **marker 제거 후 trailing whitespace / 빈 줄 등 cosmetic 잔재**: regex / sed replace 가 아닌 Edit tool 의 정확 string match 사용으로 잔재 0 보장.
- **referenced spec 의 content 가 "complete" 가 아닌 경우**: 본 task scope 외. content depth audit 은 `## Open Items` section 의 책임 — Phase 2 task 후보.
- **`(Open Items)` plural variant 가 contracts/ 디렉토리 에 추가로 잔존**: 사전 grep 으로 추가 marker 발견 시 본 task scope 에 포함 (mechanical), 단 형태가 (b) intentional 인 경우 제외.
- **multiple marker 가 같은 line 에 등장**: Edit tool 의 정확 match 사용으로 정확히 1:1 처리.

# Failure Scenarios

- **marker 제거 누락**: 사전 grep result table (Acceptance Criteria 의 8 checkbox) 가 강제. file-by-file 작업 후 최종 verify grep 으로 잔존 marker 0 확인.
- **semantic 변경 발생**: Edit tool 의 정확 string match — `(Open Item)` 또는 `(Open Items)` 문자열만 제거, surrounding 내용 무수정. final diff review 로 강제.
- **referenced spec 의 link 무력화**: marker 만 제거, link / reference path 는 그대로 유지. backtick reference 도 backtick 형태 유지 (link 변환은 본 task scope 외).
- **Phase 2 영역 침범**: `specs/services/` 디렉토리 의 marker 는 본 task 변경 없음. Acceptance Criteria 의 8 file list 가 강제.

# Notes

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical marker removal, sibling pattern 답습).
- 본 task 의 single-PR closure 시 D4 churn freeze 영향 0 (project-internal spec).
- same-day single-PR closure 패턴 (BE-141 / BE-142 / FAN-BE-006 / MONO-084 / BE-281 / BE-145 / BE-146 / BE-147 의 9번째 entry 후보).
- Phase 2 task 후보 — `specs/services/` 디렉토리, ~80 marker × 17 file, careful per-marker audit (intentional placeholder vs stale cross-reference 분류) 필요. 별 task spec 작성 시 본 task 의 verify-then-remove 패턴 답습 권장.
