# Internal HTTP Contract: Tenant Provisioning API (account-service)

Enterprise tenants (e.g. `wms`) manage their users through this internal provisioning API.
WMS backends call account-service directly over an internal network path; the gateway does not
expose `/internal/**` to the public internet.

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
| `roles` | string[] | No | May be empty; each role ≤ 50 chars |

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

Replace all roles for the account. An empty array removes all roles.

> **Audit**: Records `OPERATOR_PROVISIONING_ROLES_REPLACE` in account_status_history (or equivalent audit table).

**Request**:
```json
{
  "roles": ["INBOUND_OPERATOR", "INVENTORY_VIEWER"]
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `roles` | string[] | Yes | May be empty (removes all roles); each role ≤ 50 chars |

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
| `operatorId` | string | No | Caller identifier for audit |

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
| Replace roles | `OPERATOR_PROVISIONING_ROLES_REPLACE` |
| Change status | `OPERATOR_PROVISIONING_STATUS_CHANGE` |
| Issue password-reset | `OPERATOR_PROVISIONING_PASSWORD_RESET` |

Audit entry includes: `actor_type=provisioning_system`, `actor_id={operatorId or tenantId}`, `target_tenant_id`, `target_account_id`, `occurred_at`.

---

## Outbox Events

| Event | Trigger |
|---|---|
| `account.created` | POST create — payload includes `tenant_id` |
| `account.status.changed` | PATCH status — payload includes `tenant_id` |
| `account.roles.changed` | PATCH roles — payload includes `tenant_id`, `roles` |

All payloads must include `tenant_id` per `specs/contracts/events/account-events.md`.
