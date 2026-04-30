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

- `TASK-BE-234-security-service-tenant-events.md` — security-service `tenant_id` 페이로드/스키마/per-tenant detection
- `TASK-BE-235-admin-service-tenant-audit-schema.md` — admin schema `tenant_id` + tenant-scoped audit query
- `TASK-BE-236-admin-service-tenant-management-api.md` — `POST/PATCH /api/admin/tenants` lifecycle API

Recommended order: 234 → 235 → 236 (security events first because they unblock per-tenant detection; admin schema next because TASK-BE-236 depends on it).

## in-progress

(empty)

## review

(empty)

## done

233 backend tasks + 24 frontend tasks completed in the standalone repository before
the 2026-04-30 monorepo import (TASK-MONO-017). Latest BE: TASK-BE-233; latest FE:
TASK-FE-024. See `tasks/done/` for the full archive.
