# Task ID

TASK-BE-640

# Title

The tenant lifecycle API can still be called around the gateway: in the demo, account-service also sits on `traefik-net`, so any container there holding an `internal.invoke` token can create, list or re-status tenants directly

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- security

---

> **Analysis model:** Opus 5.5 / **Recommended implementation model:** Opus (who may change tenant status · a caller allow-list on an internal API)
> Source: `TASK-BE-638` AC-0, open item ⚪ (2026-10-16 UTC), measured by reading the compose overrides and seeds — not live.

# Dependency Markers

- After `TASK-BE-638` (#4390, merged `a3eef4249`). It closed the **gateway** path to `POST`/`GET /internal/tenants` and `GET`/`PATCH /internal/tenants/{id}`. This ticket is about the **direct** path that 638 deliberately left out.

# Goal

What the files say (to be confirmed live in AC-0):

1. In the demo, account-service joins `traefik-net` with the alias `iam-account-service` (`infra/demo/iam-traefik.override.yml:327-331`), so that other domains can find it by name.
2. account-service `/internal/**` requires a valid IAM JWT carrying the `internal.invoke` scope (`SecurityConfig.java:59, 101-106, 128`). `TenantLifecycleController` has no further check on **who** the caller is.
3. ⇒ Any container on `traefik-net` that holds an `internal.invoke` client secret can call `http://iam-account-service:8082/internal/tenants…` directly, and change any tenant's status (`SUSPENDED → ACTIVE`, `PENDING → ACTIVE` — skipping TASK-MONO-773's «first admin» gate, which lives in admin-service).
4. Today, by reading the seeds and overrides, **no non-IAM container on `traefik-net` holds such a client**. The `internal.invoke` holders are `admin-service-client`, `auth-service-client`, `security-service-client` and `account-service-client` (V0019, IAM's own), plus `product-service-client` (V0036) and `ecommerce-internal-services-client` (V0038). The two ecommerce ones run on `ecommerce-net` only (TASK-MONO-717/721 live measurement). The non-IAM members of `traefik-net` are ecommerce notification-service (`ecommerce-mail.override.yml`) and the erp/finance/scm/wms/fan backends joined for JWKS (`*-identity.override.yml`). ⇒ **open, but no holder** — the next override that puts an `internal.invoke` workload on that network opens it silently.

# Scope

## In Scope

- **AC-0 (measure first):**
  - On a demo host: `docker network inspect traefik-net` membership, and for each non-IAM member whether it holds an `internal.invoke` client (env / secret). Confirm item 4 above or correct it.
  - Who legitimately calls each lifecycle endpoint directly (expected from code: admin-service for all four; auth-service for `GET /internal/tenants/{id}`).
- **Fix candidates, decided in AC-0:**
  - a caller allow-list on `TenantLifecycleController` (JWT `client_id`/`azp`), separately for the writes + list (admin-service only) and for `GET /{id}` (admin-service + auth-service);
  - or take account-service off `traefik-net` if nothing outside IAM needs it by name (AC-0 must list who does — the override comment says «other domains find it by name»);
  - or both.
- An IT per closed caller (a non-allowed `internal.invoke` token → refused, status unchanged) and the controls (admin-service create / suspend / reactivate unchanged; auth-service tenant read unchanged).

## Out of Scope

- The tenant-scoped sub-resources (`/internal/tenants/{id}/accounts` …). Their callers are tenant workloads by design.
- The gateway path (closed by `TASK-BE-638`).

# Acceptance Criteria

- [ ] **AC-0**: the measured `traefik-net` membership × `internal.invoke` holders, the direct callers of each lifecycle endpoint (file:line), the chosen fix, and why.
- [ ] **AC-1**: a non-allowed `internal.invoke` token on `POST /internal/tenants`, `GET /internal/tenants` and `PATCH /internal/tenants/{id}` → refused; the tenant row is unchanged.
- [ ] **AC-2** (control): admin-service tenant create / suspend / reactivate / first-admin activation → unchanged; auth-service `GET /internal/tenants/{id}` → unchanged.
- [ ] **AC-3**: the allowed client ids are configuration with a fail-closed default, and the demo env supplies them (a missing value must not reopen the API nor break admin-service — say which).

# Related Specs

- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` § Tenant lifecycle — «호출자» paragraph (BE-638)
- `projects/iam-platform/specs/contracts/http/internal/auth-to-account.md` § GET /internal/tenants/{tenantId}
- `projects/iam-platform/specs/contracts/http/gateway-api.md` § Internal Provisioning Routes

# Related Contracts

- Same as above. The lifecycle section must name the allowed callers as a rule, not just as a fact.

# Edge Cases

- In the test / standalone profiles, the `InternalApiFilter` bypass authenticates `/internal/**` without a JWT. The allow-list must not turn every slice / IT red, nor be silently skipped in production.
- A client id overridden by env in the demo (`IAM_CLIENT_ID`-style variables) — the allow-list must read the same value the caller sends.
- The circuit breaker / retry in admin-service's `AccountServiceTenantClient` must not be tripped by a 403 caused by a mis-set allow-list (4xx is non-retryable there — confirm).

# Failure Scenarios

1. Closing the writes but leaving `GET /internal/tenants` (the full tenant list) open to every `internal.invoke` holder.
2. An allow-list that blocks auth-service's tenant read: login and signup break (`TASK-BE-407` / `TASK-BE-581` read it non-fail-soft).
3. Taking account-service off `traefik-net` without listing who resolves `iam-account-service` by name — a console or domain leg goes dark with no red test.
