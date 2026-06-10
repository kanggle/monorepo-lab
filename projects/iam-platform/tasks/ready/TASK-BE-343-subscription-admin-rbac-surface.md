# Task ID

TASK-BE-343

# Title

ADR-MONO-023 § 3.3 step 2b (D3) — admin-service operator-facing subscription-management surface gated by a NEW `subscription.manage` permission (DISTINCT from `operator.manage`), delegating the entitlement write to account-service (step 2a internal endpoints). Completes the ADR-023 D2/D3 plane separation at the authorization layer: the IAM plane makes the authz decision (RBAC) + records the operator audit, the entitlement plane performs the write.

# Status

ready

# Owner

backend

# Task Tags

- backend
- admin-service
- adr
- multi-tenant
- rbac

---

# Dependency Markers

- **depends on**: TASK-BE-342 (account-service internal POST/PATCH subscription endpoints, merged #1242 `7cf29a2e`) — this delegates to them.
- **D2 (ADR-023)**: admin-service authorizes with IAM data (RBAC `subscription.manage`), then delegates the entitlement write to account-service. account-service never reads IAM (one-way dependency).
- **D3 (ADR-023)**: `subscription.manage` is a separately-grantable permission distinct from `operator.manage` — the seam a future tenant-admin delegation ADR consumes.

# Goal

Give platform operators (SUPER_ADMIN today) an RBAC-gated, audited surface to subscribe/suspend/resume/cancel tenant↔domain subscriptions, without account-service ever reading the IAM plane — the authorization decision lives in the IAM plane (admin-service), the entitlement write in the entitlement plane (account-service).

# Scope

**Code (admin-service):**
- `domain/rbac/Permission` — NEW `SUBSCRIPTION_MANAGE = "subscription.manage"`.
- `application/ActionCode` — NEW `SUBSCRIPTION_SUBSCRIBE`, `SUBSCRIPTION_CHANGE_STATUS`.
- `application/AdminActionPermissionRegistry` — map both codes → `SUBSCRIPTION_MANAGE` (permission) + `"SUBSCRIPTION"` (target type).
- NEW exceptions `Subscription{NotFound, AlreadyExists, TransitionInvalid}Exception` + `AdminExceptionHandler` handlers (404/409/409).
- `application/port/TenantDomainSubscriptionPort` — NEW `subscribe()` + `changeStatus()`; NEW `application/tenant/SubscriptionMutationSummary`.
- `infrastructure/client/AccountServiceTenantClient` — implement subscribe (POST) + changeStatus (PATCH) to account-service `/internal/tenant-domain-subscriptions`; map 404/409 codes → admin exceptions, 5xx → `DownstreamFailureException`.
- NEW `application/ManageSubscriptionUseCase` (delegate to port + audit-on-success).
- NEW `presentation/SubscriptionAdminController` — `POST /api/admin/subscriptions` + `PATCH /api/admin/subscriptions/{tenantId}/{domainKey}/status` (`@RequiresPermission(SUBSCRIPTION_MANAGE)` + `X-Operator-Reason`).
- NEW `db/migration/V0032__seed_subscription_manage_permission.sql` (SUPER_ADMIN, INSERT IGNORE).

**Contracts:**
- `specs/services/admin-service/rbac.md` — `subscription.manage` row (Permission Keys + Seed Roles SUPER_ADMIN + Seed Matrix).
- `specs/contracts/http/admin-api.md` — `## Subscription Management` (POST + PATCH + error table).

**Tests:**
- NEW `ManageSubscriptionUseCaseTest` (delegation + audit-on-success + no-audit-on-failure).
- NEW `SubscriptionAdminControllerTest` slice (201/200/403/401/400-reason/409-transition).
- `AdminActionPermissionRegistryTest` — assertions for the 2 new codes (targetType SUBSCRIPTION + permission SUBSCRIPTION_MANAGE).

# Acceptance Criteria

- **AC-1** `POST/PATCH /api/admin/subscriptions` gated by `subscription.manage` (403 without it); delegate to account-service; record `admin_actions` (SUBSCRIPTION_SUBSCRIBE / SUBSCRIPTION_CHANGE_STATUS).
- **AC-2** account-service 404/409 surface unchanged (TENANT_NOT_FOUND / SUBSCRIPTION_NOT_FOUND / SUBSCRIPTION_ALREADY_EXISTS / SUBSCRIPTION_TRANSITION_INVALID); 5xx → 503.
- **AC-3** `subscription.manage` ≠ `operator.manage` (separate constant, separate seed row); V0032 seeds it onto SUPER_ADMIN only.
- **AC-4** admin-service reads NO entitlement data to authorize — it reads its own RBAC (IAM plane); the write is delegated (D2).
- **AC-5** Compile clean; ManageSubscriptionUseCaseTest + SubscriptionAdminControllerTest + AdminActionPermissionRegistryTest GREEN; full `:admin-service:test` GREEN (no enumeration/registry regression).

# Related Specs

- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` § D2 / D3 / D6 step 2
- `specs/services/admin-service/rbac.md` (the permission added)
- `specs/contracts/http/admin-api.md` (the endpoints added)
- `specs/contracts/http/internal/account-tenant-domain-subscriptions.md` (the delegated-to surface, BE-342)

# Related Contracts

- `specs/services/admin-service/rbac.md`
- `specs/contracts/http/admin-api.md`

# Edge Cases

- The new ActionCodes MUST be added to BOTH `permissionForActionCode` AND `targetTypeFor` in `AdminActionPermissionRegistry` (the `targetTypeFor_covers_every_action_code` test iterates all codes).
- Audit is recorded after a successful delegation only (mirrors `PatchOperatorRoleUseCase`); a failed downstream call writes no audit row.
- No `@Transactional` over the remote call (must not hold a DB tx across HTTP); the auditor manages its own persistence.

# Failure Scenarios

- If `subscription.manage` is folded into `operator.manage` → D3 violated (no independent delegation).
- If admin-service reads `tenant_domain_subscription` to authorize (instead of delegating the write) → still fine for reads, but the WRITE must go to account-service (D2 — entitlement authority owns its writes).
- If a new ActionCode lacks a `targetTypeFor` entry → `AdminActionPermissionRegistryTest` fails (caught locally before CI).
