# Task ID

TASK-BE-570

# Title

`fan-platform-user-flow-client` OAuth scope registration gap — `V0011` under-registered the client relative to
`fan-platform`'s own spec, causing `invalid_scope` at the OIDC authorize step and blocking all browser login

# Status

done

# Owner

backend

# Task Tags

- code
- db-migration
- bug

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`fan-platform-web`'s NextAuth provider (`web/fan-platform-web/src/shared/auth/auth.ts`) requests:

```
openid profile email offline_access fan-platform.community.read fan-platform.community.write fan-platform.artist.read
```

`fan-platform-user-flow-client`'s registered scope allow-list, seeded by
`V0011__seed_fan_platform_oidc_clients.sql` and unchanged by any later migration (confirmed by repo-wide grep — no
migration after V0011 touches this client's `scopes` column), is only:

```
["openid","profile","email","tenant.read"]
```

Spring Authorization Server rejects any `scope` parameter containing a value outside the client's registered
allow-list with `invalid_scope` at the `/oauth2/authorize` step — before the login form is even shown. This means
**every browser login attempt against `fan-platform-user-flow-client` fails**, regardless of credentials, PKCE, or
redirect_uri correctness (those are independently verified fine — see Provenance).

`fan-platform`'s own contract, `projects/fan-platform/specs/integration/iam-integration.md` § Scopes, already
documents the full intended scope set (`fan-platform.community.read/write`, `fan-platform.artist.read/write`, plus
standard OIDC `offline_access`) as scopes that should be registered against `tenant_id=fan-platform` in IAM. `V0011`
only registered `tenant.read` — the four scopes `auth.ts` actually requests were never added to either the
`oauth_scopes` catalog (for the three non-system ones) or the client's own `scopes` column. This is a seed-migration
gap, not an application bug — `auth.ts` is requesting exactly what the spec says it should be able to.

After this task: `fan-platform-user-flow-client` can request its full documented scope set and reach the login form.

---

# Scope

## In Scope

- New Flyway migration `V0030__grant_fan_platform_client_resource_scopes.sql` under
  `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/` that:
  - Registers three new tenant-scoped catalog rows in `oauth_scopes` (`tenant_id='fan-platform'`, `is_system=false`):
    `fan-platform.community.read`, `fan-platform.community.write`, `fan-platform.artist.read` — matching the
    existing pattern used by `V0010`/`V0012`/`V0013` for other tenants' resource scopes. (`offline_access` is
    **not** re-registered here — it is already a global system scope from `V0008`.)
  - Updates `fan-platform-user-flow-client`'s `oauth_clients.scopes` JSON column (currently
    `["openid","profile","email","tenant.read"]`) to append exactly the four scopes `auth.ts` requests:
    `offline_access`, `fan-platform.community.read`, `fan-platform.community.write`, `fan-platform.artist.read`.
- Update `projects/iam-platform/specs/contracts/http/auth-api.md` § Registered Clients — the
  `fan-platform-user-flow-client` row's scope column, to list the full new set.
- Re-verify (AC-0) at implementation time that `auth.ts`'s requested scope string and the client's currently
  registered scopes are still exactly as described above — time has passed since filing.

## Out of Scope

- `fan-platform.artist.write`, `fan-platform.membership.read/write`, `fan-platform.notification.write` — not
  currently requested by `auth.ts`; adding unused scopes to a client's allow-list is unforced scope creep. If a
  future feature needs them, that task requests them explicitly (also updating `auth.ts` in the same task).
- Any resource-server enforcement of these scopes — repo-wide grep confirms **no** `SecurityConfig` in
  `community-service` or `artist-service` currently checks `SCOPE_fan-platform.*` authorities; access to those
  services' endpoints is gated by JWT validity + tenant claim only, not by OAuth scope. Making the scopes
  *requestable* (this task) is independent from making them *enforced* (a separate, real architecture decision —
  not silently bundled in here).
- `V0011` itself — immutable, already-applied Flyway migration. Never edited (checksum-verified on apply).
- Any other tenant's client row or the `community-service-client` / `wms-internal-services-client` etc. rows —
  untouched; the new migration's `UPDATE` is scoped to exactly `client_id = 'fan-platform-user-flow-client'`.
- Standing up a live docker stack to click through the browser flow end-to-end — this environment's iam.local +
  fan-platform full-stack bring-up is documented as resource-heavy (Hyper-V socket buffer exhaustion under
  multi-container `up`, see `platform-console`/monorepo memory precedent). A live click-through is **recommended
  as best-effort** (AC-5) but must not block merge if infeasible in-session; the DB-level fix is independently
  verifiable via a Testcontainers-backed migration test or direct SQL inspection.

---

# Acceptance Criteria

- [ ] **AC-0 (re-verify gate)** — At implementation start, re-confirm both sides of the mismatch still hold:
      (a) `auth.ts`'s `scope:` string still requests exactly `openid profile email offline_access
      fan-platform.community.read fan-platform.community.write fan-platform.artist.read`, and (b) no migration
      after `V0011` has already granted these to the client. If either has changed, **STOP** and re-scope — do not
      proceed on the filing-time snapshot.
- [ ] **AC-1** — `V0030` adds exactly three `oauth_scopes` rows (`fan-platform.community.read/write`,
      `fan-platform.artist.read`) and updates exactly one `oauth_clients` row's `scopes` column
      (`fan-platform-user-flow-client`). No other row in either table is touched.
- [ ] **AC-2** — `auth-api.md` § Registered Clients' `fan-platform-user-flow-client` row lists the full new scope
      set.
- [ ] **AC-3** — Existing auth-service test suite (unit + Testcontainers-backed integration/migration tests) passes
      with `V0030` applied.
- [ ] **AC-4** — No other client's row, no other tenant's scopes, and no `fan-platform`-project file (`auth.ts`,
      community-service, artist-service) is touched by this task.
- [ ] **AC-5 (best-effort, non-blocking)** — If a local Testcontainers/DB run is available, confirm by direct query
      that `fan-platform-user-flow-client`'s `scopes` column, post-migration, is a strict superset containing all
      four newly-added scopes plus the original four. A full browser click-through against a live iam.local +
      fan-platform-web stack is a nice-to-have, not required for merge (see Out of Scope).

---

# Related Specs

- `projects/fan-platform/specs/integration/iam-integration.md` § Scopes — the source of truth this migration is
  catching the client registration up to.
- `projects/iam-platform/PROJECT.md`

---

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` § Registered Clients

---

# Target Service

- `auth-service` (iam-platform)

---

# Architecture

Follow `projects/iam-platform/specs/services/auth-service/architecture.md`.

---

# Implementation Notes

- `oauth_clients.scopes` is a per-row JSON array column (see `V0011` — `'["openid","profile","email","tenant.read"]'`),
  not a join table — the migration needs a `JSON_ARRAY_APPEND` (or equivalent full-column `UPDATE`) against that one
  row. `V0023__add_erp_write_scope_to_platform_console.sql` is the established precedent for appending a single
  scope to an existing client via `JSON_ARRAY_APPEND` with an idempotency guard (`JSON_SEARCH(...) IS NULL`) — follow
  that pattern for all four appended values.
- `V0011` is never edited — Flyway migrations are immutable once applied (checksum-verified on every startup). The
  fix is a strictly additive migration (`V0030`).
- Tenant-scoped `oauth_scopes` catalog rows follow the `V0010`/`V0012`/`V0013` pattern:
  `INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES (...)`.

---

# Edge Cases

- If AC-0 finds `auth.ts`'s requested scope string has since changed (e.g., a scope removed or added), the
  migration's scope list must match the *current* `auth.ts`, not this task's filing-time snapshot — re-derive, don't
  copy blindly.
- MySQL `JSON_ARRAY_APPEND` is not portable to the H2-backed SAS slice tests (see `V0011`'s own header comment on
  this exact issue) — if any auth-service slice test runs against H2 rather than Testcontainers MySQL, verify the
  migration doesn't break it, or follow `V0011`'s workaround (embed the full literal array rather than using a
  MySQL-only JSON function) if H2 compatibility is required for this migration's own application.

---

# Failure Scenarios

- **F1 — the migration accidentally widens another client's scopes or another tenant's catalog.** Guarded by AC-1 —
  `UPDATE`/`INSERT` statements must be scoped to exactly `client_id = 'fan-platform-user-flow-client'` and
  `tenant_id = 'fan-platform'` respectively.
- **F2 — scope creep**: adding `fan-platform.artist.write` / `membership.*` / `notification.write` "while we're at
  it" even though nothing requests them yet. Guarded by Out of Scope — grant exactly what `auth.ts` currently
  requests, nothing more.
- **F3 — silently bundling resource-server enforcement**: assuming that because the scope is now grantable, it
  should also be enforced by `community-service`/`artist-service`. That is a separate, real authorization-model
  decision (should these services 403 on missing scope, or continue to rely on tenant-claim-only gating?) and must
  not be decided implicitly inside this DB-migration task.

---

# Test Requirements

- Re-run auth-service's existing test suite (unit + Testcontainers-backed migration/integration tests) with `V0030`
  present. No new test file is strictly required (this is data-seed only, matching the precedent of `V0023`/`V0029`,
  neither of which added a dedicated migration test) — but if a Flyway checksum/count test exists and asserts on
  content, that assertion must be updated in the same PR.

---

# Definition of Done

- [ ] `V0030` migration added.
- [ ] `auth-api.md` updated.
- [ ] Existing tests passing with the new migration applied.
- [ ] No `fan-platform`-project file touched.

---

# Provenance

Surfaced during `TASK-MONO-502` (memory-audit-promotions) session, re-investigating a deferred finding from monorepo
memory `project_fan_platform_local_bringup.md` (originally discovered 2026-07-21, live-verified fixable 2026-07-29
via an **uncommitted** local `auth.ts` scope-reduction workaround + a runtime, non-persisted DB scope grant — both
since lost, as expected for uncommitted/runtime-only changes). This task re-diagnoses the same defect from first
principles against current `main` (2026-08-04): confirmed via static migration/spec grep, not by repeating the prior
live-DB verification. Root cause re-framed here as a **registration gap on the IAM side** (matching
`iam-integration.md`'s own documented scope contract) rather than an over-request on the `fan-platform` side — the
prior session's local workaround took the opposite direction (reducing `auth.ts`'s request) as a faster unblock, but
that loses `offline_access`/refresh-token capability and the two resource scopes the spec says the client should
have; registering them in IAM instead makes the client conform to its own contract.

분석=Sonnet 5 / 구현 권장=Sonnet 5 (scoped Flyway seed-data migration + one contract-table-row edit, established
patterns exist in V0010/V0012/V0013/V0023 to follow directly; no architecture judgment left open — resource-server
enforcement, the one real architecture question, is explicitly deferred as a separate task in Out of Scope).
