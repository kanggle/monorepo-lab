# TASK-BE-523 — admin-service: read-path authorization gap (GET users/roles/assignments ungated) — DECISION REQUIRED

- **Type**: TASK-BE (INVESTIGATION-first — security-behavior change gated on a spec-vs-code decision)
- **Status**: ready
- **Service**: admin-service (wms-platform)
- **Domain/traits**: wms / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: TBD after AC-0
- **⚠️ INVESTIGATION-FIRST / DO NOT IMPLEMENT YET**: tightening authz is a security-behavior change that
  can break existing VIEWER read consumers (admin dashboard). Pin the direction (AC-0) before any change.

## Observed gap (verified 2026-07-19 wms audit + orchestrator re-verification)

`UserService.findById` / `search`, `RoleService.findById` / `search`, `AssignmentService.search` carry
`@Transactional(readOnly = true)` but **no `@PreAuthorize`** (verified: `UserService` has 5 `@PreAuthorize`
— all on write methods; the read methods have none). The controllers don't gate them either. So any
authenticated caller — including a `WMS_VIEWER` or an entitlement-trust-synthesised VIEWER-only token —
can read the full user/role/assignment list (PII: email, phone).

The contract `admin-service-api.md` §2.3/§3.3/§4.2 declares "Auth: WMS_ADMIN or higher" for these reads.
So **code ↔ contract diverge**. The admin test suite ALSO documents this as a known-deferred item
("Hardening is BE-046 follow-up if §2.3 enforcement tightens") but no such task exists, and at least one
slice test (`UserControllerWebMvcTest` viewer-read case) reportedly asserts 200, not 403 — a green test
that pins the CURRENT (ungated) behavior, masking the contract divergence.

## The decision (AC-0)

- **Option A — enforce the contract**: add `@PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")` to the
  read methods, add the RED→GREEN proxy-tested authz coverage (reuse the `UserServiceAuthzTest` ProxyFactory
  harness), and update/flip the misleading VIEWER-read slice assertions to 403. **Risk**: breaks any VIEWER
  read consumer (admin dashboard operators) — must confirm no UI/e2e relies on VIEWER read first.
- **Option B — update the contract**: if VIEWER read is intentional (dashboards need read-only operators),
  correct `admin-service-api.md` §2.3/§3.3/§4.2 to "WMS_VIEWER or higher" and ADD the missing authz test
  that pins the intended (open-to-authenticated) behavior deliberately, replacing the accidentally-passing one.

Per source-of-truth priority, specs win — but only once we confirm which state is *intended*; the contract
may itself be stale. INVESTIGATION-FIRST: check the admin console/BFF and any e2e for VIEWER read reliance
BEFORE picking A or B.

## Acceptance Criteria

- **AC-0 (gate)**: pin Option A or B with evidence (does any live consumer read these as VIEWER?).
- **AC-1 (A)**: read methods gated + proxy-tested authz (RED→GREEN) + contract-consistent slice assertions; CI incl. wms Testcontainers lane GREEN.
- **AC-1 (B)**: contract corrected + a deliberate authz test pinning the intended open-read behavior.

## Related

- 2026-07-19 wms test audit (admin-service section — this + the Role/Assignment/Settings `@PreAuthorize`-untested CRITICAL gaps).
- `UserServiceAuthzTest` (the real-interceptor proxy harness = the template for the missing coverage).
- Memory: `env_shared_issuer_authenticated_is_not_authorized` (a verified token ≠ authorized), `feedback_guard_predicate_wrong_verify_the_artifact` (a green test can pin the wrong behavior), `project_enforcement_straggler_sibling_parity`.
