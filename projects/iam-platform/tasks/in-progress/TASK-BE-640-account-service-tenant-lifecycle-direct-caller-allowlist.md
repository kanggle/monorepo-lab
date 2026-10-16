# Task ID

TASK-BE-640

# Title

The tenant lifecycle API can still be called around the gateway: in the demo, account-service also sits on `traefik-net`, so any container there holding an `internal.invoke` token can create, list or re-status tenants directly

# Status

in-progress

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

---

# AC-0 — census (2026-10-17 UTC, Opus 5.5)

Measured by reading code, compose files and seeds in the repository at `9c780001d`. **Not measured live.** `docker network inspect traefik-net` on a demo host belongs to the next demo window (see the end of this section). A repository census sees what the files configure, not what a running host was started with.

## Q1 — who calls each lifecycle endpoint directly (file:line)

Grep for `/internal/tenants` over every `src/main` in `projects/*`, plus compose files, `infra/`, `scripts/`, `.github/` and every e2e directory. The bare lifecycle paths have exactly two callers:

| endpoint | caller | where | token `sub` |
|---|---|---|---|
| `POST /internal/tenants` | admin-service | `AccountServiceTenantClient.java:93` | `admin-service-client` |
| `PATCH /internal/tenants/{id}` | admin-service | `AccountServiceTenantClient.java:133` | `admin-service-client` |
| `GET /internal/tenants/{id}` | admin-service | `AccountServiceTenantClient.java:169` | `admin-service-client` |
| `GET /internal/tenants` (list) | admin-service | `AccountServiceTenantClient.java:204` | `admin-service-client` |
| `GET /internal/tenants/{id}` | auth-service (`getTenantType` · `getTenant`) | `AccountServiceClient.java:1035, 1072` | `auth-service-client` |

- The client ids are `${ADMIN_SERVICE_CLIENT_ID:admin-service-client}` (admin `application.yml:201`) and `${AUTH_SERVICE_CLIENT_ID:auth-service-client}` (auth `application.yml:212`). **Neither variable is set anywhere** (grep over the whole repository: 2 hits, both the defaults themselves). So the demo, the e2e compose and CI all send the default literal.
- A `client_credentials` token's `sub` is the client id. It stays the client id on a workload assume-tenant token too (`TenantClaimTokenCustomizer.java:253-261, 893-896`; `platform/contracts/jwt-standard-claims.md` «Its `sub` remains the client»). An operator assume token's `sub` is an account UUID (`:929`), so it cannot equal a client id.
- security-service makes no lifecycle call. ecommerce product-service calls only sub-resources (`/{t}/accounts…`, `identities:resolveOrCreate`, `site-roles…`) via `iam.local`, as BE-638 measured. The web-store e2e `account-mock.nginx.conf:29` is a **mock** of account-service that answers auth-service — it is not a caller.
- No script, seed, workflow or e2e spec calls the bare paths (0 hits outside comments).

## Q2 — `traefik-net` membership × `internal.invoke` holders (files only)

- `internal.invoke` is seeded in exactly three migrations: V0019 (`admin-service-client`, `auth-service-client`, `security-service-client`, `account-service-client`), V0036 (`product-service-client`), V0038 (`ecommerce-internal-services-client`). V0042 `artist-service-client` (fan artist-service, the newest workload client) holds `store.seller.read` only. This confirms Goal item 4.
- Non-IAM members of `traefik-net` in the demo: each project's gateway and grafana (`projects/*/docker-compose.yml`), ecommerce notification-service (`infra/demo/ecommerce-mail.override.yml`), and the erp/finance/scm/wms/fan backends joined for JWKS (`infra/demo/*-identity.override.yml`). None of them is configured with a client that holds `internal.invoke`. product-service and the ecommerce internal client are on `ecommerce-net` only (TASK-MONO-717/721 live).
- ⇒ By the files, Goal item 4 holds: **open, but no holder today.**

## Q3 — does anything resolve `iam-account-service` by name?

- **No.** The alias appears in the override itself (`iam-traefik.override.yml:331`) and in one contract paragraph (`admin-to-account.md`). No compose environment, code default, seed or script uses it. The override comment «다른 도메인이 이름으로 찾는다» has no consumer for account-service.

## Chosen fix, and why

**A caller allow-list in account-service's security chain, keyed on the token `sub`.** Network membership is **not** changed.

- writes + list (`POST /internal/tenants`, `GET /internal/tenants`, `PATCH /internal/tenants/{id}`) → `admin-service-client` only.
- single read (`GET`/`HEAD /internal/tenants/{id}`) → `admin-service-client`, `auth-service-client`.
- An authenticated caller that is refused gets **403 `FORBIDDEN`** (platform `error-handling.md`: «Authenticated caller lacks the required role/permission»). No new error code. A caller without a valid token keeps today's 401.

Why the allow-list alone:
- It closes the bypass **whatever network the caller is on**. Taking account-service off `traefik-net` would close it for that one network only, and admin-service and auth-service would still reach account-service over `iam-e2e`. So the allow-list is the layer that answers the ticket's question («who may change tenant status»). The network change adds nothing to AC-1.
- A subject allow-list is one of the discriminators that `jwt-standard-claims.md` § JWT Validation rule 5, *Behind the edge*, names for a resource server behind an edge («a required `scope`, a subject allowlist of client ids, or a role»). It has the same shape as ecommerce order-service's `SystemClientSubjectValidator` (TASK-BE-505).
- Not taken: removing account-service from `traefik-net`. Q3 found no consumer of the alias, so it looks safe, but it changes the demo network layout and no AC needs it. ⚪ Left as an owner option, not done here.

Why in the security chain and not in the controller:
- The `test` / `standalone` bypass authenticates `/internal/**` with no JWT. The allow-list must admit that principal (otherwise every IT goes red), and only while the bypass flag is actually on. `SecurityConfig` computes that flag, so the rule lives next to it. A JWT caller is judged by the allow-list even when the bypass is on.
- The controller gets no presentation → infrastructure import.

Configuration (AC-3): `internal.api.tenant-lifecycle.write-callers` / `read-callers`, comma-separated. `application.yml` supplies the default literals that admin-service and auth-service send. The Java side has **no** default: a missing or blank value is an empty list, and an empty list refuses every JWT caller.

Edge case «circuit breaker / retry in `AccountServiceTenantClient`»: create, update and get map a 4xx to `NonRetryableDownstreamException`, which retry and the circuit breaker ignore (admin `application.yml:218-231, 260-281`). **`list()` did not.** It mapped every 4xx to `DownstreamFailureException`, so a mis-set allow-list would have been retried three times and counted against the shared `accountService` breaker. This ticket fixes `list()` the same way as its siblings. The admin API answer does not change (`NonRetryableDownstreamException` extends `DownstreamFailureException` → 503 `DOWNSTREAM_ERROR`).

## Live check for the next demo window (to be copied into `TASK-MONO-672`)

On the demo host, after an AMI that contains this change:
1. `docker network inspect traefik-net` → list the members. For each non-IAM member, check its environment for an `internal.invoke` client (confirms or corrects Q2).
2. From a container on `traefik-net`, send a `product-service-client` token (client credentials, scope `internal.invoke`) **directly** to `http://iam-account-service:8082`: `PATCH /internal/tenants/ecommerce {"status":"ACTIVE"}` → **403**, `POST /internal/tenants` → **403**, `GET /internal/tenants` → **403**, `GET /internal/tenants/ecommerce` → **403**. `ecommerce` stays `ACTIVE`.
3. Control: from the same container, an `admin-service-client` token gets 200 on `GET /internal/tenants/ecommerce` and on `GET /internal/tenants/ecommerce/entitled-domains`. Console tenant create / suspend / reactivate through admin-service still works. An IdP login, which reads `GET /internal/tenants/{id}` through auth-service, still works.
