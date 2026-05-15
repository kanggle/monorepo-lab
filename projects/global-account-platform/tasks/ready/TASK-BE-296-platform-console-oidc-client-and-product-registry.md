# Task ID

TASK-BE-296

# Title

platform-console OIDC public client + product/tenant registry surface

# Status

ready

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Provide the GAP-side prerequisites that `platform-console` depends on (ADR-MONO-013 D5, console integration contract § 2.1 / § 2.2). This is the cross-project prerequisite for `platform-console` `TASK-PC-FE-001`.

After this task:

- GAP `auth-service` has a registered **OIDC public client** `platform-console-web` (Authorization Code + PKCE, no client secret) with redirect URI `http://console.local/api/auth/callback`, appropriate scopes, and refresh-token rotation consistent with existing public-client handling (ADR-003 lineage).
- GAP exposes a **product/tenant registry read surface** that returns, per the authenticated operator, the products (`gap`/`wms`/`scm`/`erp`/`finance`) with `available` flag + selectable `tenants` + `displayName` + `baseRoute` — the data-driven catalog source for the console.

# Scope

## In Scope

- Spec-first: update GAP specs (`specs/features/multi-tenancy.md` and/or new `specs/services/<svc>/...` + `specs/contracts/http/...`) describing the public client + registry surface **before** implementation.
- `auth-service` OIDC client seed (Flyway migration, following the existing `V00xx` oauth_clients seed pattern used for scm/fan clients): `platform-console-web`, `client_authentication_methods=none` (public/PKCE), `authorization_grant_types=authorization_code,refresh_token`, redirect `http://console.local/api/auth/callback`, scopes per spec.
- Product/tenant registry endpoint (read-only) on the appropriate GAP service (likely `account-service` or `gateway`-exposed; decided in spec) — operator-scoped, tenant-aware, returns the registry item shape from `platform-console/specs/contracts/console-integration-contract.md` § 2.2.
- Tests: client seed verified; registry endpoint contract + tenant-scoping + cross-tenant rejection.

## Out of Scope

- `console-web` implementation (that is `platform-console` `TASK-PC-FE-001`).
- Any GAP `admin-web` change/retirement (ADR-MONO-013 Phase 3, separate, parity-gated).
- erp/finance registry entries beyond an `available:false` placeholder convention (their bootstrap is ADR-MONO-008 / future erp ADR).
- console-bff (Phase 7).

# Acceptance Criteria

- [ ] GAP spec updated first (public client + registry surface) and merged/contained in the same task's spec step.
- [ ] `platform-console-web` public client resolvable; Auth Code + PKCE flow issues tokens; refresh rotation behaves per existing public-client lineage (no client secret accepted).
- [ ] Registry endpoint returns the contract shape (`productKey`, `displayName`, `available`, `tenants`, `baseRoute`); `erp`/`finance` representable as `available:false`.
- [ ] Registry response is operator-scoped + tenant-aware; cross-tenant access rejected (multi-tenant isolation regression test).
- [ ] Existing GAP auth/OIDC integration tests remain green (no regression to scm/fan/wms clients).

# Related Specs

> Follow `platform/entrypoint.md` Step 0 — read `projects/global-account-platform/PROJECT.md`, then `rules/common.md` + `rules/domains/saas.md` + `rules/traits/{transactional,regulated,audit-heavy,integration-heavy,multi-tenant}.md`.

- `projects/global-account-platform/specs/features/multi-tenancy.md`
- `projects/global-account-platform/specs/services/auth-service/architecture.md`
- `projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md`, `ADR-003-public-client-refresh-token-revoke-converter.md`
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D5
- `projects/platform-console/specs/contracts/console-integration-contract.md` (consumer contract — the shape this task must satisfy)

# Related Skills

- `.claude/skills/backend/...` — OIDC client provisioning, contract-first API, multi-tenant isolation testing

---

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.1 (OIDC) + § 2.2 (registry) — GAP is the **producer**; the contract shape is authoritative.
- New GAP HTTP contract for the registry endpoint under `projects/global-account-platform/specs/contracts/http/` (authored in the spec step).

---

# Target Service

- `auth-service` (OIDC public client seed)
- `account-service` or gateway-exposed registry surface (decided in spec step)

---

# Architecture

Follow `projects/global-account-platform/specs/services/auth-service/architecture.md` (OIDC Authorization Server, public client + PKCE, refresh rotation) and the existing oauth_clients seed migration pattern.

---

# Implementation Notes

- Mirror the existing public-client lineage (ADR-003 — SAS public-client refresh/revoke). Do not introduce a client secret for `platform-console-web`.
- Registry surface is **read-only**, operator-scoped, tenant-aware; it is not a new domain — it projects existing tenant/product metadata into the console contract shape.
- Spec-first is mandatory (contract producer side).

---

# Edge Cases

- Operator with access to a subset of products → registry returns only visible products.
- `erp`/`finance` not yet bootstrapped → represented as `available:false` (console renders "coming soon").
- Single-tenant operator → `tenants` length 1; console switcher degrades gracefully.

---

# Failure Scenarios

- Public client misconfigured (secret required) → console PKCE login fails; covered by client-seed test.
- Registry leaks cross-tenant products → isolation regression test must fail the build.
- Seed migration collides with an existing client_id → namespace `platform-console-web` distinctly; migration idempotent.

---

# Test Requirements

- Client seed / OIDC public-client flow test (Auth Code + PKCE, refresh rotation).
- Registry endpoint contract test (shape matches console-integration-contract § 2.2).
- Multi-tenant isolation regression (cross-tenant registry rejection).
- Regression: existing scm/fan/wms OIDC clients unaffected.

---

# Definition of Done

- [ ] Spec updated first (public client + registry surface)
- [ ] Implementation completed
- [ ] Tests added & passing
- [ ] Contract honored (console-integration-contract § 2.1/§ 2.2 producer side)
- [ ] No regression to existing GAP OIDC clients
- [ ] Ready for review
