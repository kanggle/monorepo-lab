# Internal HTTP Contract: Tenant Provisioning API (account-service)

Enterprise tenants (e.g. `wms`) manage their users through this internal provisioning API.
WMS backends call account-service directly over an internal network path; the gateway does not
expose `/internal/**` to the public internet.

> Onboarding a new B2B consumer? Start with the single-entry
> [consumer-integration-guide.md](../../../features/consumer-integration-guide.md). This contract
> is the source-of-truth for the provisioning endpoints; the guide sequences them with tenant
> registration, OAuth client issuance, JWKS setup, and event subscription.

**Path prefix**: `/internal/tenants/{tenantId}/accounts`
**Authentication**: `X-Internal-Token` header (service-to-service token) or mTLS
**Authorization**: Path `{tenantId}` must match the caller's JWT `tenant_id` claim,
or the caller must hold a platform-scope `SUPER_ADMIN` role.

> **Defense-in-depth**: Even when the gateway validates the scope (TASK-BE-230), this service
> re-validates the `X-Tenant-Id` header (or JWT claim) against the path `{tenantId}`.

---

## Common Request Headers

| Header | Required | Description |
|---|---|---|
| `X-Internal-Token` | Yes | Shared service-to-service token (`INTERNAL_API_TOKEN` env var) |
| `X-Tenant-Id` | Conditional | Caller's tenant scope. Required when no JWT is present. |

---

## Common Error Codes

| Code | HTTP | Condition |
|---|---|---|
| `TENANT_SCOPE_DENIED` | 403 | Path `{tenantId}` does not match caller's tenant scope |
| `TENANT_NOT_FOUND` | 404 | `{tenantId}` is not registered in the tenants table |
| `TENANT_SUSPENDED` | 409 | `{tenantId}` exists but its status is `SUSPENDED` |
| `ACCOUNT_ALREADY_EXISTS` | 409 | Email already exists within the same tenant (`tenant_id`, `email` uniqueness) |
| `ACCOUNT_NOT_FOUND` | 404 | `{accountId}` does not exist within this tenant |
| `STATE_TRANSITION_INVALID` | 409 | Requested status transition is not permitted by `AccountStatusMachine` |
| `VALIDATION_ERROR` | 400 | Request body fails Bean Validation |
| `UNAUTHORIZED` | 401 | `X-Internal-Token` is missing or invalid |
| `BULK_LIMIT_EXCEEDED` | 400 | `items` array exceeds the maximum of 1 000 entries (TASK-BE-257) |

---

## POST /internal/tenants/{tenantId}/accounts:bulk

> **TASK-BE-257**: Google AIP-136 verb path. Bulk-create up to 1 000 accounts for a tenant
> in a single request. Partial-success model — per-row failures do not roll back other rows.

**Path Parameters**:

| Parameter | Type | Description |
|---|---|---|
| `tenantId` | string (slug) | Target tenant. Pattern `^[a-z][a-z0-9-]{1,31}$` |

**Request**:
```json
{
  "items": [
    {
      "externalId": "erp-user-001",
      "email": "user@example.com",
      "phone": "+821012345678",
      "displayName": "홍길동",
      "roles": ["WAREHOUSE_ADMIN"],
      "status": "ACTIVE"
    }
  ]
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `items` | array | Yes | 1–1 000 items (`@Size(max=1000)`). Empty array returns 200 with `{requested:0, created:0, failed:0}` |
| `items[].externalId` | string | No | Caller-side dedup key; no server-side uniqueness constraint |
| `items[].email` | string | Yes | Valid email format |
| `items[].phone` | string | No | Free-form phone string |
| `items[].displayName` | string | No | Max 100 chars |
| `items[].roles` | string[] | No | Each role matches `^[A-Z][A-Z0-9_]*$`, ≤ 64 chars |
| `items[].status` | string | No | `ACTIVE` (default) or `DORMANT` |

**Response 200 OK** (partial-success):
```json
{
  "created": [
    { "externalId": "erp-user-001", "accountId": "01923abc-def0-7890-abcd-ef0123456789" }
  ],
  "failed": [
    { "externalId": "erp-user-002", "errorCode": "EMAIL_DUPLICATE", "message": "Email already exists within the tenant" }
  ],
  "summary": { "requested": 1000, "created": 950, "failed": 50 }
}
```

> Always returns 200 — the HTTP status code indicates the request was processed. Check `summary.failed` for per-row errors.

**Per-row error codes** (returned in `failed[].errorCode`):

| Code | Condition |
|---|---|
| `EMAIL_DUPLICATE` | Email already exists within the same tenant (DB uniqueness violation on `(tenant_id, email)`) |
| `INVALID_ROLE` | A role name fails `^[A-Z][A-Z0-9_]*$` validation |
| `VALIDATION_ERROR` | Other row-level validation failure |

**Errors** (request-level — fail before per-row processing):

| Code | HTTP | Condition |
|---|---|---|
| `BULK_LIMIT_EXCEEDED` | 400 | `items` array has more than 1 000 entries |
| `TENANT_SCOPE_DENIED` | 403 | Caller's `X-Tenant-Id` does not match path `{tenantId}` |
| `TENANT_NOT_FOUND` | 404 | `{tenantId}` is not registered |
| `TENANT_SUSPENDED` | 409 | `{tenantId}` is SUSPENDED |
| `VALIDATION_ERROR` | 400 | Request body fails top-level Bean Validation |

**Outbox events**: Each successfully created row publishes one `account.created` event. N created rows → N events.

**Audit**: One `account_status_history` row per bulk call with `reason_code=OPERATOR_PROVISIONING_CREATE` and `details=action=ACCOUNT_BULK_CREATE,targetCount={N}`.

> **Admin-service audit obligation**: The `admin_actions` table in admin-service is not written by account-service directly. An `ACCOUNT_BULK_CREATE` action code should be added to admin-service's audit when the admin-service audit emission pattern for provisioning flows is established. As of TASK-BE-257, account-service records the audit in `account_status_history` only. See code TODO comment in `BulkAccountController`.

---

## POST /internal/tenants/{tenantId}/accounts

Create a new user account under the given tenant.
Persists Account + Profile + role mapping in a single transaction.
Publishes outbox `account.created` event with `tenant_id` in the payload.

**Path Parameters**:

| Parameter | Type | Description |
|---|---|---|
| `tenantId` | string (slug) | Target tenant. Pattern `^[a-z][a-z0-9-]{1,31}$` |

**Request**:
```json
{
  "email": "user@example.com",
  "password": "Password1!",
  "displayName": "홍길동",
  "locale": "ko-KR",
  "timezone": "Asia/Seoul",
  "roles": ["WAREHOUSE_ADMIN", "INBOUND_OPERATOR"]
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `email` | string | Yes | Valid email format |
| `password` | string | Yes | Min 8 chars |
| `displayName` | string | No | Max 100 chars |
| `locale` | string | No | Default `ko-KR` |
| `timezone` | string | No | Default `Asia/Seoul` |
| `roles` | string[] | No | May be empty; each role ≤ 64 chars |
| `operatorId` | string | No | Caller identifier for audit; ≤ 36 chars |

**Response 201 Created**:
```json
{
  "accountId": "01923abc-def0-7890-abcd-ef0123456789",
  "tenantId": "wms",
  "email": "user@example.com",
  "status": "ACTIVE",
  "roles": ["WAREHOUSE_ADMIN", "INBOUND_OPERATOR"],
  "createdAt": "2026-04-30T10:00:00Z"
}
```

> Sensitive fields (`password_hash`, `deleted_at`, `email_hash`) are excluded from the response.

**Errors**: 403 `TENANT_SCOPE_DENIED`, 404 `TENANT_NOT_FOUND`, 409 `TENANT_SUSPENDED`, 409 `ACCOUNT_ALREADY_EXISTS`

---

## GET /internal/tenants/{tenantId}/accounts

List accounts belonging to the tenant with pagination.

**Query Parameters**:

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | int | 0 | Zero-based page number |
| `size` | int | 20 | Page size (max 100) |
| `status` | string | (none) | Filter by account status: `ACTIVE`, `LOCKED`, `DORMANT`, `DELETED` |
| `role` | string | (none) | Filter by role name (exact match) |

**Response 200 OK**:
```json
{
  "content": [
    {
      "accountId": "01923abc-def0-7890-abcd-ef0123456789",
      "tenantId": "wms",
      "email": "user@example.com",
      "status": "ACTIVE",
      "roles": ["WAREHOUSE_ADMIN"],
      "createdAt": "2026-04-30T10:00:00Z"
    }
  ],
  "totalElements": 50,
  "page": 0,
  "size": 20,
  "totalPages": 3
}
```

**Errors**: 403 `TENANT_SCOPE_DENIED`, 404 `TENANT_NOT_FOUND`, 400 `VALIDATION_ERROR` (size > 100)

---

## GET /internal/tenants/{tenantId}/accounts/{accountId}

Retrieve a single account within the tenant.

**Path Parameters**:

| Parameter | Type | Description |
|---|---|---|
| `tenantId` | string | Target tenant |
| `accountId` | string (UUID) | Account identifier |

**Response 200 OK**:
```json
{
  "accountId": "01923abc-def0-7890-abcd-ef0123456789",
  "tenantId": "wms",
  "email": "user@example.com",
  "status": "ACTIVE",
  "roles": ["WAREHOUSE_ADMIN"],
  "displayName": "홍길동",
  "createdAt": "2026-04-30T10:00:00Z",
  "emailVerifiedAt": null
}
```

> `password_hash`, `deleted_at` are excluded.

**Errors**: 403 `TENANT_SCOPE_DENIED`, 404 `TENANT_NOT_FOUND`, 404 `ACCOUNT_NOT_FOUND`

---

## PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles

Replace **all** roles for the account. An empty array removes all roles.

> **Audit**: Records `OPERATOR_PROVISIONING_ROLES_REPLACE` in account_status_history (or equivalent audit table).

**Request**:
```json
{
  "roles": ["INBOUND_OPERATOR", "INVENTORY_VIEWER"],
  "operatorId": "sys-wms-backend"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `roles` | string[] | Yes | May be empty (removes all roles); each role matches `^[A-Z][A-Z0-9_]*$`, ≤ 64 chars |
| `operatorId` | string | No | Caller identifier for audit + outbox event `changed_by` field; ≤ 36 chars |

**Response 200 OK**:
```json
{
  "accountId": "01923abc-def0-7890-abcd-ef0123456789",
  "tenantId": "wms",
  "roles": ["INBOUND_OPERATOR", "INVENTORY_VIEWER"],
  "updatedAt": "2026-04-30T10:05:00Z"
}
```

**Errors**: 403 `TENANT_SCOPE_DENIED`, 404 `TENANT_NOT_FOUND`, 404 `ACCOUNT_NOT_FOUND`, 400 `VALIDATION_ERROR`

> Cross-tenant safety: if path `{tenantId}` differs from the account's actual `tenant_id` (i.e., the accountId belongs to a different tenant), the response is **404 `ACCOUNT_NOT_FOUND`** (not 403). The repository's tenant-scoped `findById(tenantId, accountId)` returns empty in that case, so the rest of the flow never sees the foreign account.

---

## PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles:add

> **TASK-BE-255**: Single-role add operation. Idempotent — if the role is already assigned, the call returns 200 with the existing role set unchanged (no event fired).

Append a single role to the account. This avoids the TOCTOU race that the `replaceAll` endpoint has when two operators concurrently mutate the same account's roles.

> **Audit**: Records `OPERATOR_PROVISIONING_ROLES_REPLACE` in `account_status_history` with `details=action=ROLE_ADD,role={roleName}`. The audit code is shared with replaceAll because the underlying fact ("operator changed this account's roles") is the same.

**Request**:
```json
{
  "roleName": "INBOUND_OPERATOR",
  "operatorId": "sys-wms-backend"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `roleName` | string | Yes | Matches `^[A-Z][A-Z0-9_]*$`, ≤ 64 chars |
| `operatorId` | string | No | Caller identifier for audit + outbox event `changed_by` field; ≤ 36 chars |

**Response 200 OK**:
```json
{
  "accountId": "01923abc-def0-7890-abcd-ef0123456789",
  "tenantId": "wms",
  "roles": ["WAREHOUSE_ADMIN", "INBOUND_OPERATOR"],
  "updatedAt": "2026-04-30T10:05:00Z"
}
```

`roles` is the **complete** role list after the add operation, not just the newly added role.

**Outbox event**: `account.roles.changed` is published only when the role set actually changed (i.e., the role was not already assigned). Idempotent re-adds do not emit events.

**Errors**: 403 `TENANT_SCOPE_DENIED`, 404 `TENANT_NOT_FOUND`, 404 `ACCOUNT_NOT_FOUND`, 400 `VALIDATION_ERROR`

---

## PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles:remove

> **TASK-BE-255**: Single-role remove operation. Idempotent — if the role is not currently assigned, the call returns 200 with the existing role set unchanged (no event fired).

Remove a single role from the account.

> **Audit**: Records `OPERATOR_PROVISIONING_ROLES_REPLACE` in `account_status_history` with `details=action=ROLE_REMOVE,role={roleName}`.

**Request**:
```json
{
  "roleName": "INBOUND_OPERATOR",
  "operatorId": "sys-wms-backend"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `roleName` | string | Yes | Matches `^[A-Z][A-Z0-9_]*$`, ≤ 64 chars |
| `operatorId` | string | No | Caller identifier for audit + outbox event `changed_by` field; ≤ 36 chars |

**Response 200 OK**:
```json
{
  "accountId": "01923abc-def0-7890-abcd-ef0123456789",
  "tenantId": "wms",
  "roles": ["WAREHOUSE_ADMIN"],
  "updatedAt": "2026-04-30T10:05:00Z"
}
```

**Outbox event**: `account.roles.changed` is published only when the role set actually changed.

**Errors**: 403 `TENANT_SCOPE_DENIED`, 404 `TENANT_NOT_FOUND`, 404 `ACCOUNT_NOT_FOUND`, 400 `VALIDATION_ERROR`

---

## PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status

Change the account status. Follows `AccountStatusMachine` transition rules.

> **Audit**: Records `OPERATOR_PROVISIONING_STATUS_CHANGE` in account_status_history.

**Request**:
```json
{
  "status": "LOCKED",
  "reason": "ADMIN_LOCK",
  "operatorId": "sys-wms-backend"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `status` | string | Yes | `ACTIVE`, `LOCKED`, `DELETED` |
| `reason` | string | Yes | Must be a valid `StatusChangeReason` enum value |
| `operatorId` | string | No | Caller identifier for audit; ≤ 36 chars |

**Response 200 OK**:
```json
{
  "accountId": "01923abc-def0-7890-abcd-ef0123456789",
  "tenantId": "wms",
  "previousStatus": "ACTIVE",
  "currentStatus": "LOCKED",
  "changedAt": "2026-04-30T10:10:00Z"
}
```

**Errors**: 403 `TENANT_SCOPE_DENIED`, 404 `TENANT_NOT_FOUND`, 404 `ACCOUNT_NOT_FOUND`, 409 `STATE_TRANSITION_INVALID`, 400 `VALIDATION_ERROR`

---

## POST /internal/tenants/{tenantId}/accounts/{accountId}/password-reset

Issue a password-reset token for the account (operator-initiated).
The token is stored in the auth-service credential store for the account's next reset.

> **Audit**: Records `OPERATOR_PROVISIONING_PASSWORD_RESET` in account_status_history.

**Request**: Empty body or:
```json
{
  "operatorId": "sys-wms-backend"
}
```

**Response 200 OK**:
```json
{
  "accountId": "01923abc-def0-7890-abcd-ef0123456789",
  "tenantId": "wms",
  "resetTokenIssuedAt": "2026-04-30T10:15:00Z",
  "message": "Password reset token issued. Delivery via existing notification channel."
}
```

**Errors**: 403 `TENANT_SCOPE_DENIED`, 404 `TENANT_NOT_FOUND`, 404 `ACCOUNT_NOT_FOUND`

---

## Audit Action Codes

All mutations record an audit entry in `account_status_history` (or equivalent table) with the following action codes:

| Operation | `reason_code` / `action_code` |
|---|---|
| Create account | `OPERATOR_PROVISIONING_CREATE` |
| Bulk create accounts (TASK-BE-257) | `OPERATOR_PROVISIONING_CREATE` (`details=action=ACCOUNT_BULK_CREATE,targetCount={N}`) |
| Replace roles | `OPERATOR_PROVISIONING_ROLES_REPLACE` |
| Add a single role (TASK-BE-255) | `OPERATOR_PROVISIONING_ROLES_REPLACE` (`details=action=ROLE_ADD,...`) |
| Remove a single role (TASK-BE-255) | `OPERATOR_PROVISIONING_ROLES_REPLACE` (`details=action=ROLE_REMOVE,...`) |
| Change status | `OPERATOR_PROVISIONING_STATUS_CHANGE` |
| Issue password-reset | `OPERATOR_PROVISIONING_PASSWORD_RESET` |

Audit entry includes: `actor_type=provisioning_system`, `actor_id={operatorId or tenantId}`, `target_tenant_id`, `target_account_id`, `occurred_at`.

For bulk create: one audit row per call with `target_count=N` (where N = number of successfully created rows). Individual row results are tracked via outbox events.

---

## Outbox Events

| Event | Trigger |
|---|---|
| `account.created` | POST create — payload includes `tenant_id` |
| `account.created` (×N) | POST accounts:bulk — N events emitted for N successfully created rows; downstream consumers process each event individually (TASK-BE-257) |
| `account.status.changed` | PATCH status — payload includes `tenant_id` |
| `account.roles.changed` | PATCH roles, PATCH roles:add, PATCH roles:remove — payload includes `tenant_id`, `roles`, `before_roles`, `after_roles`, `changed_by` (TASK-BE-255). Add/remove only emit when the role set actually changed (idempotent calls are silent). |

All payloads must include `tenant_id` per `specs/contracts/events/account-events.md`.
