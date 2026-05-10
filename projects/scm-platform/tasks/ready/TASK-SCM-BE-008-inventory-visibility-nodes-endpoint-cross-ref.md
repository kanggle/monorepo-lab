# Task ID

TASK-SCM-BE-008

# Title

inventory-visibility `/nodes` endpoint cross-ref + architecture.md REST endpoint enumeration

# Status

ready

# Owner

backend

# Task Tags

- spec

---

# Goal

`inventory-visibility-service` has 4 REST endpoints (`GET /snapshot`, `GET /sku/{sku}`, `GET /staleness`, `GET /nodes`) but `architecture.md` does not enumerate them, and `gateway-public-routes.md` only references 2 (snapshot + sku). Decide whether `/nodes` is **gateway-exposed** (add to public-routes) or **internal-only** (document the boundary), and update both spec files.

After this task: every public REST endpoint is in `gateway-public-routes.md`, and every endpoint in any project service is enumerated in its `architecture.md`.

---

# Scope

## In Scope

- Inspect `apps/inventory-visibility-service/.../adapter/in/web/` for the 4 endpoints' actual exposure (any `@PreAuthorize` / role guards / internal annotations).
- Decide: is `/nodes` public (gateway-routed, JWT scope = `scm.read`) or internal-only (admin / system)?
  - **If public**: add row to `projects/scm-platform/specs/services/gateway-public-routes.md`.
  - **If internal-only**: add explicit "internal-only — not gateway-routed" note to `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` REST section.
- Update `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`: add an explicit "REST endpoints (v1)" subsection enumerating all 4 endpoints with their public/internal status.
- Verify against `inventory-visibility-api.md` (contract source of truth) and reconcile.

## Out of Scope

- Adding new endpoints.
- Production code changes (just spec).

---

# Acceptance Criteria

- [ ] All 4 endpoints enumerated in inventory-visibility-service `architecture.md`.
- [ ] `/nodes` exposure decision documented with rationale.
- [ ] gateway-public-routes.md reflects the decision.
- [ ] No drift remaining between architecture.md / gateway-public-routes.md / inventory-visibility-api.md.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` (target)
- `projects/scm-platform/specs/services/gateway-public-routes.md` (target)
- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` (source of truth for endpoints)

---

# Related Contracts

- `inventory-visibility-api.md` (verify, do not change unless drift)

---

# Target Service

- inventory-visibility-service (spec only)
- gateway-service (spec only — public-routes table)

---

# Implementation Notes

- `grep -r "@RestController\|@GetMapping\|@PostMapping" apps/inventory-visibility-service/.../adapter/in/web/` to find actual mappings.
- Match each mapping path against gateway-public-routes.md to find which are gateway-exposed.

---

# Edge Cases

- `/nodes` could be both public (read for ops dashboard) and internal (system polling) — pick one role/scope or split into 2 endpoints (file separately if split).

---

# Failure Scenarios

- Endpoint defined in code but not in `inventory-visibility-api.md` → contract drift → document and reconcile (api.md takes priority per Source of Truth Priority L9).

---

# Test Requirements

- N/A (spec-only).

---

# Definition of Done

- [ ] All 4 endpoints enumerated and decision documented
- [ ] No production code changes
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-spec all` (2026-05-11) audit Finding [SCM 7]. Skipped from PR #326 because requires gateway-routing decision.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6.
