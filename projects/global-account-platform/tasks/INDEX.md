# Tasks Index

This document defines task lifecycle, naming, and move rules.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-BE-XXX`: backend
- `TASK-INT-XXX`: integration

(`TASK-FE-XXX` is reserved but not used in this backend-only project.)

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract/spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-BE-002").
- Do not modify a task file after it moves to `review/` or `done/`.

## Move Method (all transitions)
All task file moves between directories must use `git mv`, never copy-then-delete or Write-new-file.
Example: `git mv tasks/review/TASK-BE-001.md tasks/done/TASK-BE-001.md`
Rationale: preserves git history and prevents duplicate files across directories.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty — tasks will be added after TASK-BE-000 cleanup is merged)

## ready

GAP IdP 승급 잔여 (post 267 → review on 2026-05-02, decisions D1=A / D2=D2-b / D3=D3-b / D4=D4-c). 자세한 결정 근거는 [docs/adr/ADR-001-oidc-adoption.md](../../docs/adr/ADR-001-oidc-adoption.md):

- `TASK-BE-253-community-membership-oidc-integration.md` — community/membership-service OIDC 통합 (P1, FROZEN 예외)
- `TASK-BE-257-bulk-provisioning-api.md` — bulk provisioning API (P2)
- `TASK-BE-258-gdpr-deletion-downstream-contract.md` — GDPR 삭제 downstream 전파 계약 + security-service reference (P2)

Recommended order: P0 선행(251/252) 및 256 contract 가 모두 done 이라 잔여 ready 작업은 즉시 시작 가능.
- 253 / TASK-MONO-019: 둘 다 251/252 의존, 서로 병렬 가능 — Resource Server 마이그레이션 끝맺음.
- 257 / 258: ADR 과 독립, 병렬 가능. 단 257 은 250 (admin tenant lifecycle API) 가 done 진입한 후 stable. 258 은 255 가 머지된 `account-events.md` v3 위에서 작업.

Cross-project follow-up (root `tasks/`):
- `tasks/ready/TASK-MONO-019-wms-platform-oidc-resource-server-migration.md` — wms-platform OIDC Resource Server 전환 (TASK-BE-251/252 의존, 둘 다 done)

## in-progress

(empty)

## review

(empty — TASK-BE-267 reviewed and moved to done on 2026-05-02)

## done

259 backend tasks + 26 frontend tasks completed. Latest BE batch (2026-05-01..02):
TASK-BE-248 (security tenant events), TASK-BE-249 (admin tenant audit schema),
TASK-BE-251 (Spring Authorization Server), TASK-BE-252 (OAuth schema),
TASK-BE-256 (tenant onboarding API contract), and follow-ups 260/261/262.
Newly reviewed (2026-05-02): TASK-BE-254 (consumer integration guide,
FIX_NEEDED → 263), TASK-BE-255 (account_roles schema, FIX_NEEDED → 265),
TASK-BE-259 (auth.token.reuse.detected tenant_id, APPROVED), TASK-BE-263
(auth-api.md discovery, APPROVED), TASK-BE-265 (255 review fix, FIX_NEEDED → 267),
TASK-BE-267 (267 review, APPROVED).
Numbers TASK-BE-238/239/240/244 were not assigned.
