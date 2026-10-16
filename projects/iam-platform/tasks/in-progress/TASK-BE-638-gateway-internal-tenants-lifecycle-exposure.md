# Task ID

TASK-BE-638

# Title

The iam gateway exposes account-service tenant lifecycle writes: `PATCH /internal/tenants/{id}` passes with that tenant's own token, and `POST` / `GET /internal/tenants` get no tenant-scope check at all

# Status

in-progress

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

- [ ] **AC-0**: the live or IT-measured answers to the In Scope questions (file:line), the chosen fix, and why.
- [ ] **AC-1**: with a tenant's own workload token, `PATCH /internal/tenants/{self}` → refused, and the status is unchanged.
- [ ] **AC-2**: `POST /internal/tenants` and `GET /internal/tenants` with a non-admin workload token → refused.
- [ ] **AC-3** (control): admin-service's tenant create / status change → unchanged.

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
