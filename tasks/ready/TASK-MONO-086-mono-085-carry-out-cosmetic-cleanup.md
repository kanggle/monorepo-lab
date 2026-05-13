# Task ID

TASK-MONO-086

# Title

MONO-085 carry-out cosmetic cleanup — 3 finding 재분류 + fix (1 path-level mistake + 2 의도된 placeholder)

# Status

ready

# Owner

monorepo

# Task Tags

- platform
- spec
- refactor
- governance
- cosmetic

---

# Goal

TASK-MONO-085 closure 시 "out-of-scope 잔존 = file 부재 finding 3건" 으로 분류했으나, post-merge 재검증 결과 분류 부정확:

| # | MONO-085 분류 | 재검증 | 처리 |
|---|---|---|---|
| 1 | `platform/architecture.md → ../PROJECT.md` (file 부재) | repo root `PROJECT.md` 실재 부재 ✅ (generic placeholder 의도) | **link 제거** |
| 2 | `GAP account-events.md → ../features/consumer-integration-guide.md` (file 부재) | **FALSE — file 실재** (`projects/global-account-platform/specs/features/consumer-integration-guide.md`), **path-level mistake (1-up → 2-up)** | **path fix (mechanical)** |
| 3 | `WMS outbound external-integrations.md → ../../contracts/http/tms-shipment-api.md` (file 부재) | TRUE — `(Open Item — vendor-controlled)` 명시된 **의도된 placeholder** (sibling L751-752 는 이미 backtick code 만) | **link 형식 정리** (backtick code only, sibling 패턴 답습) |

본 task = 3 file × 1 line × 단순 cleanup. MONO-085 의 "잔존 0 확정" 명세 정정 + cosmetic carry-out.

provenance: TASK-MONO-085 closure (PR #486 머지 2026-05-14 직후) 의 post-merge 재검증.

---

# Scope

## In Scope (3 file fix)

### A. platform/architecture.md L189 link 제거

generic placeholder (repo root `PROJECT.md` 부재 — project-level 만 존재).

변경 전:
```
Project-level architecture deviations are governed by each project's [`PROJECT.md`](../PROJECT.md) `## Overrides` section per [`architecture-decision-rule.md`](architecture-decision-rule.md).
```

변경 후:
```
Project-level architecture deviations are governed by each project's `PROJECT.md` `## Overrides` section per [`architecture-decision-rule.md`](architecture-decision-rule.md).
```

→ `PROJECT.md` link 제거, backtick code 만 유지. `architecture-decision-rule.md` link 는 그대로 (sibling 정상 link).

### B. GAP account-events.md L172 path fix (1-up → 2-up)

`consumer-integration-guide.md` 가 실재 (`projects/global-account-platform/specs/features/`). file path 1-up 부족.

변경 전:
```
세부 통합 패턴 및 코드 예시: [specs/features/consumer-integration-guide.md § Phase 5 GDPR downstream](../features/consumer-integration-guide.md#gdpr-downstream-처리-accountdeleted).
```

변경 후:
```
세부 통합 패턴 및 코드 예시: [specs/features/consumer-integration-guide.md § Phase 5 GDPR downstream](../../features/consumer-integration-guide.md#gdpr-downstream-처리-accountdeleted).
```

→ `../features/` → `../../features/` (path level fix). MONO-085 의 path-level batch scope 였으나 broken_links2.txt 의 분류 단계에서 잘못 file 부재 로 카탈로그됨.

### C. WMS outbound external-integrations.md L155 link 형식 정리

`tms-shipment-api.md` = `(Open Item — vendor-controlled)` 명시된 의도된 placeholder. sibling L751-752 는 이미 backtick code 만. L155 만 link 형식이라 lint noise.

변경 전:
```
Full wire-level contract:
[`specs/contracts/http/tms-shipment-api.md`](../../contracts/http/tms-shipment-api.md)
(Open Item — vendor-controlled).
```

변경 후:
```
Full wire-level contract:
`specs/contracts/http/tms-shipment-api.md`
(Open Item — vendor-controlled).
```

→ link 형식 제거, backtick code 만 (sibling L751-752 답습). "Open Item — vendor-controlled" placeholder 의도 유지.

## Out of Scope

- 실제 WMS TMS contract spec authoring (`tms-shipment-api.md` file 작성) — 별 후속 task 후보 (TASK-BE-NNN), production code BE-049 머지 완료 / vendor-controlled wire-level 명세 가치 있음.
- repo root `PROJECT.md` 추가 — design judgment (placeholder 의도 유지 vs example file 작성), 본 task scope 밖.
- MONO-085 의 INDEX outcome 텍스트 정정 (3 finding → 1 path-level mistake + 2 placeholder cleanup) — 본 task body 가 정정 record, INDEX 는 historical 유지.

---

# Acceptance Criteria

### Impl PR

- [ ] **A**: platform/architecture.md L189 의 `[\`PROJECT.md\`](../PROJECT.md)` → `\`PROJECT.md\`` (link 제거).
- [ ] **B**: projects/global-account-platform/specs/contracts/events/account-events.md L172 의 `../features/` → `../../features/` (path fix).
- [ ] **C**: projects/wms-platform/specs/services/outbound-service/external-integrations.md L155 의 `[\`...\`](../../contracts/http/tms-shipment-api.md)` → `\`specs/contracts/http/tms-shipment-api.md\`` (link 제거, sibling L751-752 답습).
- [ ] **Verification**: `bash /tmp/check_links2.sh` 재실행 → **broken = 0** (잔존 0 확정).
- [ ] task lifecycle ready → review (mechanical batch single-PR closure, MONO-085 precedent 답습).
- [ ] tasks/INDEX.md (root) 동기.
- [ ] CI self-CI PASS (path-filter markdown-only batch).

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] tasks/INDEX.md ## review 제거, ## done append outcome.

---

# Related Specs

- `tasks/done/TASK-MONO-085-cross-axis-dead-reference-batch-fix.md` (parent task; 본 task 가 carry-out cosmetic cleanup).
- `tasks/done/TASK-MONO-084-platform-change-rule-batch-backfill.md` (sibling 답습 mechanical batch single-PR closure).
- `tasks/done/TASK-BE-145-notification-service-idempotency-spec-and-dlt-replay-runbook.md` (WMS dlt-replay.md path-mistake 의 directly precedent).

---

# Related Contracts

본 task = spec markdown link 형식 정정. production code / HTTP API / event payload 변경 0.

---

# Target Service

- platform/ (file 1)
- GAP `specs/contracts/events/` (file 1)
- WMS `specs/services/outbound-service/` (file 1)

cross-axis cosmetic batch → root `tasks/` task.

---

# Architecture

MONO-085 의 broken-link batch fix 의 cosmetic carry-out. dead-link checker 의 false-positive 분류 (path mistake 인데 file 부재로 분류) + 의도된 placeholder 의 link 형식 불일치 (sibling 답습 빠뜨림) 양쪽 정정.

---

# Implementation Notes

## A. PROJECT.md link 제거

generic placeholder — repo root 에 single `PROJECT.md` 부재. project-level (`projects/<name>/PROJECT.md`) 만 존재. context = "each project's PROJECT.md" 가 generic 한 표현이라 link 가 필수 아님.

## B. consumer-integration-guide.md path fix

`projects/global-account-platform/specs/contracts/events/account-events.md` 의 dir depth = 5. target = `projects/global-account-platform/specs/features/consumer-integration-guide.md`.

relative resolve:
- `events/` → `..` = `contracts/`
- `contracts/` → `..` = `specs/`
- `specs/features/consumer-integration-guide.md`
- = **2-up** + `features/consumer-integration-guide.md`

현재 link 1-up = broken. 2-up = valid.

MONO-085 의 broken_links2.txt 가 이 link 를 "file 부재" 로 분류한 것은 path resolve 의 false negative (`../features/` 가 `contracts/features/` 로 resolve 되어 부재 — file 자체는 다른 path 에 실재).

## C. tms-shipment-api.md link 형식 정리

sibling L751-752 (`erp-order-webhook.md (Open Item)` + `tms-shipment-api.md (Open Item)`) 는 이미 backtick code 만. L155 만 link 형식 답습 안 됨. sibling 패턴 통일.

"Open Item — vendor-controlled" 명시 = 의도된 placeholder. 별 spec authoring task 까지는 vendor wire-level 명세 가치 (별 후속 후보).

## D4 churn impact

- 3 file × 1 line trivial cleanup.
- ADR-MONO-003a § D1.1 D4 OVERRIDE 적용 (MONO-085 carry-out, governance polish 범주).
- structural 변경 0.

---

# Edge Cases

- platform/architecture.md L3, L28, L46, L126, L138, L172, L180 등 다른 `PROJECT.md` mention 은 link 가 아니라 backtick code 또는 plain text → 본 task 미영향.
- GAP `consumer-integration-guide.md` 의 다른 참조 (authentication.md, multi-tenancy.md, account-internal-provisioning.md, ADR-001) 는 모두 valid path (intra-file 또는 다른 depth) → 본 task 미영향.
- WMS L751-752 의 sibling line 은 이미 backtick code 만 — 본 fix 가 sibling 답습 완성.

---

# Failure Scenarios

- 부정확한 sed pattern 으로 다른 PROJECT.md mention 도 변경 → file 별 Edit 사용으로 회피.
- WMS L155 의 의도된 placeholder 가 link 제거 후 reader 의 "이 path 가 valid 한가?" 의문 유발 → "(Open Item — vendor-controlled)" 명시 유지로 의도 보존.

---

# Test Requirements

- `bash /tmp/check_links2.sh` 재실행: **broken count = 0** (잔존 0 확정).
- production code = 0 (spec only).
- CI self-CI PASS (path-filter markdown-only batch).

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### Close chore PR

- [ ] review → done, INDEX 동기.

---

# Provenance

- TASK-MONO-085 (PR #484 + #485 + #486 머지 2026-05-14) closure 직후 post-merge 재검증 — INDEX outcome 의 "잔존 = file 부재 3건" 분류 부정확 (1 path-level mistake + 2 placeholder).
- 옵션 C 봉합 sequence 의 cosmetic carry-out — 잔존 0 확정 완전 마무리.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical 3-file × 1-line cleanup, design judgment 0).
