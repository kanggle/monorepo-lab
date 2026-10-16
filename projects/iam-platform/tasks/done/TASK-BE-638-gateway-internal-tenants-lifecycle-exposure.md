# Task ID

TASK-BE-638

# Title

The iam gateway exposes account-service tenant lifecycle writes: `PATCH /internal/tenants/{id}` passes with that tenant's own token, and `POST` / `GET /internal/tenants` get no tenant-scope check at all

# Status

done

# Owner

backend

# Task Tags

- code
- test
- security

---

> **Analysis model:** Opus 5.5 / **Recommended implementation model:** Opus (gateway predicate · who may change tenant status)
> Source: `TASK-MONO-773` S2 (2026-10-15 UTC), S1 open question Q3, measured by reading the code (not live).

# Dependency Markers

- None before it. 🔴 It becomes more urgent once `TASK-MONO-773` S2 ships the `PENDING` status: a tenant that could set its own status could skip «관리자 대기» (`PENDING → ACTIVE`), or reverse a suspension (`SUSPENDED → ACTIVE`).

# Goal

What the code says (to be confirmed live in AC-0):

1. The gateway route is `Path=/internal/tenants/**` (`gateway-service/src/main/resources/application.yml:58-63`). The pattern also matches the bare `/internal/tenants/{tenantId}` and `/internal/tenants`.
2. The gateway tenant-scope check is the regex `^/internal/tenants/([^/]+)(/.*)?$` (`JwtAuthenticationFilter.java:48-49`, applied `:165-173`).
   - The trailing segment is optional. So `PATCH /internal/tenants/acme` only needs the token's `tenant_id == acme`, and that tenant's own token passes.
3. `POST /internal/tenants` and `GET /internal/tenants` have no path tenant. `extractPathTenantId` (`:288-294`) returns null, so **no tenant-scope check runs**.
4. account-service `/internal/**` requires scope `internal.invoke` (`SecurityConfig.java:53-59, 101, 128`). That scope is seeded to the workload clients (V0019). `TenantLifecycleController` (`:42-60`) never reads `X-Tenant-Id`.

⇒ A tenant's workload `client_credentials` token might be able to change its own tenant status, create tenants, or list every tenant, through the public gateway.

# Scope

## In Scope

- **AC-0 (measure first):**
  - Does a workload token carry `tenant_id`, and does it pass the gateway on these paths?
  - Which callers legitimately use `/internal/tenants/**` through the gateway, as opposed to service-to-service inside the network? The only intended caller of lifecycle writes is admin-service (`admin-to-account.md`).
- **Fix:** close the lifecycle writes (`POST /internal/tenants`, `PATCH /internal/tenants/{id}`) and the list (`GET /internal/tenants`) to anyone except the intended caller. Candidates, decided in AC-0:
  - remove them from the public gateway route;
  - an explicit gateway deny;
  - a client-id allow-list on `TenantLifecycleController`.
- An IT for each closed path:
  - a tenant's own workload token → refused;
  - the intended caller → unchanged (control).

## Out of Scope

- The other `/internal/tenants/{id}/**` sub-resources that legitimately need tenant-scoped access. AC-0 lists them; they stay as they are unless shown unsafe.

# Acceptance Criteria

- [x] **AC-0**: the live or IT-measured answers to the In Scope questions (file:line), the chosen fix, and why. — IT-measured, § AC-0 below (live → next demo window).
- [x] **AC-1**: with a tenant's own workload token, `PATCH /internal/tenants/{self}` → refused, and the status is unchanged. — 403, 0 requests reach account-service (IT).
- [x] **AC-2**: `POST /internal/tenants` and `GET /internal/tenants` with a non-admin workload token → refused. — 403 for every token incl. `'*'` (IT + unit), and no route (route test).
- [x] **AC-3** (control): admin-service's tenant create / status change → unchanged. — no admin-service or account-service code touched; it never crosses the gateway; admin-service ITs 332/0 failed.

# Related Specs

- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` (tenant lifecycle — its caller)
- `projects/iam-platform/specs/contracts/http/gateway-api.md`

# Related Contracts

- Same as above. The contract may need a sentence naming the only allowed caller.

# Edge Cases

- A workload client whose `tenant_id` is `'*'`, or which has no `tenant_id` claim.

# Failure Scenarios

1. Fixing only the `PATCH` path while `POST` and `GET` stay open.
2. Closing the route in a way that also breaks the legitimate tenant-scoped sub-resources (for example subscriptions). Measure those first.

---

# AC-0 — measurement (2026-10-16 UTC, Opus 5.5)

Measured by IT (Testcontainers Redis + WireMock account-service, CI lane `Integration (iam B)`) and by reading code. Not measured live — that goes to the next demo window (see the end of this section).

## Q1 — does a workload token carry `tenant_id`, and does it pass the gateway on these paths?

- **It carries one, always.** `TenantClaimTokenCustomizer` (auth-service) mints `tenant_id` for `client_credentials` from the client's registered tenant and fails closed on a blank one (`TenantClaimTokenCustomizer.java:239-274, 399-411`). `sub` stays the client id, so the gateway's `extractAccountId` is non-null (`JwtAuthenticationFilter.java` `extractAccountId`).
- The `internal.invoke` holders (the scope account-service requires, `SecurityConfig.java:59, 104, 128`): `admin-service-client`, `auth-service-client`, `security-service-client`, `account-service-client` (V0019), `product-service-client` (V0036), `ecommerce-internal-services-client` (V0038). All are registered to `global-account-platform`. `product-service-client` may also **assume** `ecommerce` and `demo-corp` (`WorkloadTenantCatalog.java:115`).
- **It passed, on all four lifecycle paths.** Red run on `ecef8f91e` (job `116941092655`), `GatewayIntegrationTest`, a `client_credentials`-shaped token with `tenant_id=wms`:

  | request | before the fix |
  |---|---|
  | `PATCH /internal/tenants/wms` (own tenant) | **200**, forwarded |
  | `GET /internal/tenants/wms` (own tenant) | **200**, forwarded |
  | `POST /internal/tenants` | **201**, forwarded |
  | `GET /internal/tenants?status=PENDING` | **200**, forwarded |
  | `POST /internal/tenants` with `tenant_id='*'` | **201**, forwarded |

  Two causes, both measured: the route `Path=/internal/tenants/**` matches the bare paths (`GatewayRouteResolutionTest`, 3 red), and the scope regex `^/internal/tenants/([^/]+)(/.*)?$` made the sub-path optional (`JwtAuthenticationFilterUnitTest`, 13 red).
- So concretely: `product-service-client`, assuming `ecommerce`, could have suspended or re-activated the `ecommerce` tenant, created tenants, and listed every tenant through `iam.local`.
- `tenant_id` missing → already 401 (`legacy-fallback.enabled=false` default). `PATCH /internal/tenants/wms` with `tenant_id='*'` → already 403 (`'*' != 'wms'`). The `*` hole was `POST`/`GET /internal/tenants`, which had no path tenant.

## Q2 — who uses `/internal/tenants/**` through the gateway?

- **Lifecycle (`POST`/`GET /internal/tenants`, `GET`/`PATCH /internal/tenants/{id}`):** nobody. admin-service calls account-service directly (`AccountServiceTenantClient.java:93, 133, 169, 204`, base URL `admin.account-service.base-url = ${ACCOUNT_SERVICE_URL}` — `http://account-service:8082` in `docker-compose.e2e.yml`). auth-service reads `GET /internal/tenants/{tid}` directly too (`AccountServiceClient.java:1020, 1057`). No script, e2e spec or workflow calls these paths (grep over `*.ts|js|sh|py|ps1`, `.github/`, `infra/`: 0 hits apart from a comment in `infra/demo/demo.env`).
- **Sub-resources (the legitimate gateway traffic):** only ecommerce product-service via `iam.local` (`ACCOUNT_SERVICE_BASE_URL`, ecommerce `docker-compose.yml:722`, `infra/demo/demo.env:112-118`): `POST …/{t}/accounts`, `POST …/{t}/identities:resolveOrCreate`, `PATCH …/{t}/accounts/{id}/status` (`AccountServiceSellerProvisioner.java:171, 199, 221`), `PATCH …/{t}/accounts/{id}/site-roles:grant|revoke` (`AccountServiceSellerSiteRoleClient.java:107`).
- Other sub-resources the route still carries (account-service mappings): `accounts` (GET/POST), `accounts/{id}`, `accounts/{id}/roles` (PATCH/GET), `accounts/{id}/status`, `accounts/{id}/password-reset`, `accounts:bulk`, `accounts/{id}/roles:add|remove`, `accounts/{id}/site-roles:grant|revoke`, `accounts/{id}/identity`, `identities:resolveOrCreate`, `entitled-domains`, `consumer-members/{id}` (GET/PUT). All are inside one tenant and keep the `{tenantId} == tenant_id` check. Out of scope; not shown unsafe here.

## Chosen fix, and why

Two refusals in the gateway, each independent of the other:

1. **Route** — `Path=/internal/tenants/**` → `Path=/internal/tenants/*/*/**` (`application.yml`). At least one segment after `{tenantId}` is required, so the four lifecycle paths have no route (gateway default 404). This removes them from the public edge, which is what the contract always meant (`/internal/tenants/{tenantId}/**`, "a tenant workload opens the paths *under* the tenant").
2. **Filter** — `JwtAuthenticationFilter.isInternalPathOutsideTenantScope`: any `/internal/**` path that is not `^/internal/tenants/([^/]+)/[^/].*$`, or that contains a dot segment, `%2e`, `%2f`, `%5c`, `\` or `;`, gets **403 `TENANT_SCOPE_DENIED`** for every token, `'*'` included. This holds if the route is widened again, and the IT keeps the wide route on purpose so it measures this layer alone. The scope regex itself now requires the sub-path.

No new error code: the existing `TENANT_SCOPE_DENIED` (gateway-api.md § 403) with a different message — the lifecycle resource is outside every tenant's scope.

Not chosen:
- **A client-id allow-list on `TenantLifecycleController`.** It would also close direct calls that bypass the gateway, but it changes account-service, needs admin-service's client id as config (a wrong value in the demo env would break tenant create/suspend there), and must not catch auth-service's `GET /{id}`. ⚪ **Owner question:** in the demo account-service is also on `traefik-net` (`infra/demo/iam-traefik.override.yml:327-331`, alias `iam-account-service`). Any container on that network holding an `internal.invoke` token can call the lifecycle API directly. By reading the overrides, the non-IAM containers on `traefik-net` are ecommerce notification-service and the erp/finance/scm/wms/fan backends that join for JWKS. None of them holds an `internal.invoke` client as far as the seeds show. `product-service-client` and the ecommerce internal client run on `ecommerce-net` only (measured live in `TASK-MONO-717`/`721`). So the bypass is open today but has no holder. Closing it is a separate ticket if wanted.
- **Explicit deny with route unchanged.** It was layered in as (2) rather than being the only layer.

## Red → green (PR #4390, counted from CI job logs)

| lane | red (`ecef8f91e`, tests only) | green (`3c807a9a9`, fix) |
|---|---|---|
| `Build & Test` — iam `gateway-service:test` | job `116941091652`: 158 run, **16 failed**, all `AssertionFailedError` (13 `JwtAuthenticationFilterUnitTest` status assertions · 3 `GatewayRouteResolutionTest` "no route") | job `116945363225`: 158 run, 0 failed (task executed, not FROM-CACHE) |
| `Integration (iam B)` — `GatewayIntegrationTest` | job `116941092655`: 43 run, **5 failed**, all `Status expected:<403> but was:<200 OK>` (×3) / `<201 CREATED>` (×2) — the exposure itself | job `116945365039`: 43 run, 0 failed (5 × BE-638 `PASSED`); same lane auth 158 / account 157, 0 failed |
| `Integration (iam A)` (AC-3 control — admin-service ITs incl. tenant create/suspend) | job `116941092590`: green | job `116945365058`: admin 332 / security 24, 0 failed |

Expected-green-on-red (the current behaviour was already right, kept as guards): `PATCH /internal/tenants/wms` with `tenant_id='*'`, and the matrix-parameter shape `wms;x=1`. Both were already 403 via the path ≠ claim check. The 8 sub-resource controls in each of the unit and route tests passed on both commits.

AC-1 "status unchanged" is measured as `accountServiceMock.verify(0, patchRequestedFor(/internal/tenants/wms))`: the request never reaches account-service.

**Branch e2e (gateway path changed):**
- `federation-hardening-e2e.yml` run `38892564503`, job `116945887037`: **21 passed**, 0 flaky.
- `nightly-e2e.yml` run `38892569380`:
  - iam docker-compose full (job `116945342031`): 3/3.
  - Platform Console full-stack (job `116946011408`): 11 passed, **1 flaky**. The flaky one is `overview-consolidation.spec.ts:131`: the IAM overview card was not rendered within 5 s on the first try and passed on retry. That is the overview-leg family of `TASK-PC-FE-341`, but ⚪ the `degraded reason=TIMEOUT` text is not in the job log, so the cause is not verified. No console path calls `/internal/tenants` (grep of `*.ts`: 0).
  - web-store full-stack, ecommerce×wms, wms gateway-master, web-store GAP logout: all success.
  - scm (job `116945341991`) and fan (job `116945342012`): failed at `compileJava` with `Could not find resilience4j-annotations-2.2.0.jar` (2 hits each, `tests=0`). That is the known main-side CI cause, not this change.

## Sibling gateways (CLAUDE.md step 10, grep by symptom)

Searched `projects/*/apps/gateway-service` for an `/internal` route predicate, for a tenant/path scope regex with an optional tail, and for `/**` routes that also cover a bare collection or row on an internal API:
- erp, finance, scm, wms, fan: no `/internal` route at all, and no path-scope regex (`Pattern.compile` appears only in iam).
- ecommerce: `Path=/internal/sellers/**` (application.yml:78). Not the same exposure: `AccountTypeEnforcementFilter` admits GET/HEAD + scope `store.seller.read` + `tenant_id=ecommerce` only, and product-service maps a single `GET /internal/sellers/{id}` (`ProductSecurityConfig.java:127` `GET /internal/sellers/*`). There is no write or list behind it.
- The ecommerce `/api/internal/**` endpoints (order, promotion, auth) sit under no ecommerce gateway route prefix.
- ⇒ No sibling carries this pattern. Nothing filed.

## Verification plan for the next demo window (rides the next AMI bake, owner decision 2026-10-16)

Live check against `iam.local` with a `product-service-client` token assumed into `ecommerce`: `PATCH /internal/tenants/ecommerce` → 404 or 403, never 200, and the tenant stays `ACTIVE`. `POST /internal/tenants` → 404 or 403. `GET /internal/tenants` → 404 or 403. Control: seller provisioning (`POST /internal/tenants/ecommerce/accounts`) still 201, and console tenant create/suspend through admin-service still works.

## 28th window live (2026-10-16 UTC)

- AMI `ami-00ef8cc1057cca59b` (`RepoCommit` `8905d2c29`; `a3eef4249` is an ancestor) — `iam-gateway-service` from this commit.
- A `product-service-client` workload token (client credentials, tenant `global-account-platform`), and the same token assumed into `ecommerce`, against `iam.local`:
  - `PATCH /internal/tenants/ecommerce {"status":"ACTIVE"}` → **404** · `GET /internal/tenants/ecommerce` → **404** · `GET /internal/tenants` → **404** · `POST /internal/tenants` → **404** · dot-segment `/internal/tenants/ecommerce/../x` → **404**. Never 200. `ecommerce` is still `ACTIVE` afterwards.
  - Control: `GET /internal/tenants/ecommerce/entitled-domains` (assumed) → **200** `{"tenantId":"ecommerce","domainKeys":["ecommerce","wms"],"tenantStatus":"ACTIVE"}`.
  - Control: the admin-service tenant create / suspend path still works — the 28th created and suspended three throwaway tenants through it (`TASK-MONO-672` § 28th window — harvest, 773 AC-4 row).
  - ⚪ The seller-provisioning control (`POST /internal/tenants/ecommerce/accounts` 201) was not exercised separately; `seed-ecommerce` ran green on the fresh volume. This was a verification-plan line, not an AC — AC-3's control is admin-service, measured above.
- Recorded also in `TASK-MONO-672` § 28th window — harvest.

## Close (2026-10-16 UTC) — review → done

- (a) `gh pr view 4390`: `state=MERGED`, `mergedAt` 2026-10-16T11:50:01Z, merge commit `a3eef4249`. (b) An ancestor of `origin/main` (`1c8ee22cb`). (c) 0 failing checks at merge (18 pass / 49 skipped).
- (d) AC section read against each verb: AC-0 «measured answers + chosen fix + why» ✅ · AC-1 «own workload token PATCH refused, status unchanged» — IT 403 and live 404, `ACTIVE` unchanged ✅ · AC-2 «POST / GET with a non-admin workload token refused» — IT 403 and live 404 ✅ · AC-3 (control) «admin-service create / status change unchanged» — no admin-service / account-service code touched, admin ITs green, and live tenant create / suspend worked ✅.
