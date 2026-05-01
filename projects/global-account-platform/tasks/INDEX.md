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

Multi-tenancy gap-fill (post `multi-tenant` trait adoption on 2026-04-29):

- `TASK-BE-248-security-service-tenant-events.md` — security-service `tenant_id` 페이로드/스키마/per-tenant detection
- `TASK-BE-249-admin-service-tenant-audit-schema.md` — admin schema `tenant_id` + tenant-scoped audit query
- `TASK-BE-250-admin-service-tenant-management-api.md` — `POST/PATCH /api/admin/tenants` lifecycle API

Recommended order: 248 → 249 → 250 (security events first because they unblock per-tenant detection; admin schema next because TASK-BE-250 depends on it).

GAP IdP 승급 (post ADR-001 ACCEPTED on 2026-05-01, decisions D1=A / D2=D2-b / D3=D3-b / D4=D4-c). 자세한 결정 근거는 [docs/adr/ADR-001-oidc-adoption.md](../../docs/adr/ADR-001-oidc-adoption.md):

- `TASK-BE-251-spring-authorization-server-oidc-endpoints.md` — Spring Authorization Server 도입 + `/oauth2/*` 표준 엔드포인트 (P0)
- `TASK-BE-252-oauth-clients-scopes-consent-schema.md` — OAuth client/scope/consent JPA 스키마 (P0)
- `TASK-BE-253-community-membership-oidc-integration.md` — community/membership-service OIDC 통합 (P1, FROZEN 예외)
- `TASK-BE-254-consumer-integration-guide-doc.md` — 소비 서비스 통합 가이드 신규 (P1)
- `TASK-BE-255-account-roles-schema.md` — `account_roles` 테이블 스키마 + provisioning API 정합 (P1)
- `TASK-BE-256-tenant-onboarding-api-contract.md` — 테넌트 onboarding API 계약 완성 (P1, TASK-BE-250 선행 contract)
- `TASK-BE-257-bulk-provisioning-api.md` — bulk provisioning API (P2)
- `TASK-BE-258-gdpr-deletion-downstream-contract.md` — GDPR 삭제 downstream 전파 계약 + security-service reference (P2)
- `TASK-BE-259-auth-token-reuse-detected-tenant-id.md` — `auth.token.reuse.detected` 에 `tenant_id` 추가 (P3)

Recommended order: 251 → 252 (둘은 병렬 가능) → 253 / TASK-MONO-019 (consumer 마이그레이션, 251·252 완료 후) → 254 (가이드, 선행 작성도 가능). 255/256/257/258/259는 ADR과 독립이므로 251/252와 병렬 진행 가능. 262 (`tenant_id` cleanup) 는 TASK-BE-248 와 정합 점검 후 진행.

Cross-project follow-up (root `tasks/`):
- `tasks/ready/TASK-MONO-019-wms-platform-oidc-resource-server-migration.md` — wms-platform OIDC Resource Server 전환 (TASK-BE-251/252 의존)

## in-progress

(empty)

## review

(empty)

## done

247 backend tasks + 26 frontend tasks completed (latest standalone-master commit
captured: `34ef5e9` on 2026-04-30, post-`9830ecb` catch-up sync). Latest BE:
TASK-BE-247 (signup half-commit idempotency); latest FE: TASK-FE-026 (DashboardTabs
server/client boundary). Numbers TASK-BE-238/239/240/244 were not assigned.
