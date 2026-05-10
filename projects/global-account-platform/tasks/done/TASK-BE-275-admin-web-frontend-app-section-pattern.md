# Task ID

TASK-BE-275

# Title

admin-web frontend-app architecture.md section pattern verification

# Status

ready

# Owner

backend

# Task Tags

- spec

---

# Goal

`projects/global-account-platform/specs/services/admin-web/architecture.md` uses sections (`## Architecture Pattern`, `## Tech Stack`, `## Package Structure`) that diverge from the 6 backend services' canonical sections (`## Service`, `## Service Type`, `## Architecture Style`, `## Why This Architecture`, `## Internal Structure Rule`, etc.). Determine whether this divergence is **intentional** (frontend-app service-type has a different canonical pattern) or **drift** (admin-web should align to the backend pattern), and reconcile.

After this task: the architecture.md pattern for `frontend-app` service-type is explicitly defined (or admin-web is realigned to the backend pattern).

---

# Scope

## In Scope

- Read `platform/service-types/frontend-app.md` to determine the canonical architecture.md pattern for frontend-app services (if it specifies one).
- Compare admin-web/architecture.md against:
  - `projects/global-account-platform/specs/services/auth-service/architecture.md` (backend canonical reference)
  - `projects/fan-platform/specs/services/fan-platform-web/architecture.md` (sibling frontend-app, if exists — verify)
  - `projects/ecommerce-microservices-platform/specs/services/web-store/architecture.md` (sibling frontend-app)
  - `projects/ecommerce-microservices-platform/specs/services/admin-dashboard/architecture.md` (sibling frontend-app)
- Decide:
  - **Scenario A (admin-web drift)**: realign admin-web sections to backend pattern.
  - **Scenario B (frontend-app pattern divergence is canonical)**: document the divergence + ensure all frontend-app services use the same pattern.
  - **Scenario C (need new platform-level decision)**: file follow-up task to define frontend-app canonical pattern in `platform/service-types/frontend-app.md` (THIS WOULD TOUCH SHARED — defer to D4 freeze unlock).

## Out of Scope

- Touching `platform/` files (D4 freeze).
- Backend service architecture changes.
- Production code changes.

---

# Acceptance Criteria

- [ ] Cross-comparison of admin-web vs 3 sibling frontend-app architecture.md completed.
- [ ] Decision documented: realign / canonical-divergence / platform-decision-needed.
- [ ] If A/B: admin-web/architecture.md updated.
- [ ] If C: separate task filed for D4 freeze unlock window.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `platform/service-types/frontend-app.md` (canonical service-type reference — read-only)
- `projects/global-account-platform/specs/services/admin-web/architecture.md` (target)
- `projects/global-account-platform/specs/services/auth-service/architecture.md` (backend reference)
- Other frontend-app architecture.md files across projects (siblings)

---

# Related Contracts

- N/A (architecture-level decision).

---

# Target Service

- admin-web (spec only)

---

# Implementation Notes

- Use the existing canonical pattern from auth-service / community-service / etc. (Service / Service Type / Architecture Style / Why / Internal Structure / Allowed Dependencies / Forbidden Dependencies / Boundary Rules / Integration Rules / Testing Expectations).
- For frontend-app, some sections (Package Structure, Tech Stack) are genuinely useful — consider keeping them as **subsections** under canonical headers rather than top-level.
- Hard Stop: do NOT touch `platform/service-types/frontend-app.md` if D4 freeze active (≥ 2026-06-10 to unlock).

---

# Edge Cases

- All 3 sibling frontend-app architecture.md files use a different pattern each — Scenario C is appropriate (need shared standard).
- frontend-app service-type spec doesn't specify a structure — Scenario C also appropriate.

---

# Failure Scenarios

- During audit, surface that admin-web sections are missing critical content (e.g., no boundary rules) → realignment + content gap fix in the same PR.

---

# Test Requirements

- N/A (spec-only).

---

# Definition of Done

- [ ] Decision + rationale in PR description
- [ ] admin-web/architecture.md updated (Scenario A or B)
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-spec all` (2026-05-11) audit Finding [GAP 1]. Skipped from PR #326 because requires platform-level decision evaluation.

분석=Opus 4.7 / 구현 권장=Opus (cross-platform pattern analysis + decision).
