# HTTP Contract — admin-service API

All `admin-service` REST endpoints. This contract is authoritative —
implementation must match it. Changes here precede code changes (per
`CLAUDE.md` Contract Rule).

`admin-service` is dual-typed (`rest-api` + `event-consumer`). This document
covers only the REST surface. Event publication / consumption schemas live in
[`specs/contracts/events/admin-events.md`](../events/admin-events.md).

Base path: `/api/v1/admin`
Service: `admin-service`
Base URL (via gateway): `https://{gateway}/api/v1/admin`

The endpoints split into three logical surfaces:

| Surface | Path prefix | Nature | Idempotency-Key |
|---|---|---|---|
| **Dashboard / Read-Model** | `/api/v1/admin/dashboard/**` | Pure reads on CQRS read-model | not required |
| **User / Role / Assignment** | `/api/v1/admin/users/**`, `/roles/**`, `/assignments/**` | Write paths with outbox | required for POST / PATCH / DELETE |
| **Settings** | `/api/v1/admin/settings/**` | Write path with outbox + downstream propagation | required for PUT |

---

## Global Conventions

### Headers

Every request:

| Header | Required | Notes |
|---|---|---|
| `Authorization` | yes | `Bearer <oauth2-access-token>` issued by GAP (OIDC, ADR-001). RS256 JWT validated against GAP JWKS by both gateway and admin-service; `tenant_id=wms` enforced. See [`specs/integration/gap-integration.md`](../../integration/gap-integration.md). |
| `X-Request-Id` | yes | Generated/echoed by gateway. Surfaced in logs + traces |
| `X-Actor-Id` | yes | User id from JWT claim, set by gateway |
| `Idempotency-Key` | yes for POST / PATCH / PUT / DELETE on `/users`, `/roles`, `/assignments`, `/settings` | UUID. TTL 24h. Scope `(Idempotency-Key, method, path)` |
| `If-Match` | optional on `PATCH /users/{id}`, `PATCH /roles/{id}`, `PUT /settings/{key}` | Carries the prior `version` (e.g., `If-Match: "v3"`) for explicit optimistic-lock check |
| `Content-Type` | yes on body | `application/json` |
| `Accept` | no | Defaults to `application/json` |

Responses:

| Header | Notes |
|---|---|
| `X-Request-Id` | Echoed |
| `ETag` | Single-resource GET responses (`"v{version}"` for write-side resources). Read-model resources do NOT carry `ETag` because their `version` reflects projection state, not a client-acknowledged revision |
| `X-Read-Model-Lag-Seconds` | Optional on dashboard responses — observed lag of the slowest contributing projection at response time. Set when `> 5s` |

### Authorization

Roles are seeded in `admin_role` (see `domain-model.md` § 2). Each endpoint
declares required role / permission below. Two enforcement layers:

1. **Coarse guard at controller** — `@PreAuthorize("hasAnyRole(...)")` to reject
   the obviously-unauthorized fast.
2. **Fine guard at application service** — re-checks role + warehouse scope
   from JWT claims (e.g., a `WMS_OPERATOR` scoped to `WH-A` cannot acknowledge
   alerts from `WH-B`). Per `architecture.md § Security`.

Role → permission mapping (read from `admin_role.permissions_json`, applied
when JWTs are minted; this contract uses the role names as a stable shorthand):

| Role | Read | User / Role / Assignment write | Settings write | Force / superadmin overrides |
|---|---|---|---|---|
| `WMS_VIEWER` | yes | no | no | no |
| `WMS_OPERATOR` | yes | no | no | no |
| `WMS_ADMIN` | yes | yes | yes | no |
| `WMS_SUPERADMIN` | yes | yes | yes | yes (force-deactivate, force-revoke, role-deletion bypass) |

`WMS_OPERATOR` is intentionally listed as non-writer on this admin surface.
Operators perform inventory / inbound / outbound writes; admin-service writes
are restricted to `WMS_ADMIN` and above.

### Error Envelope

Per [`platform/error-handling.md`](../../../../../platform/error-handling.md).
Every error response:

```json
{
  "error": {
    "code": "USER_EMAIL_DUPLICATE",
    "message": "email already taken: alice@example.com",
    "timestamp": "2026-05-09T10:00:00.000Z",
    "details": { "email": "alice@example.com" },
    "traceId": "abc123",
    "requestId": "req-xyz"
  }
}
```

Domain error → HTTP status mapping (admin-specific only; platform-common codes
reused as documented in `error-handling.md`):

| Code | HTTP | Notes |
|---|---|---|
| `USER_NOT_FOUND` | 404 | Generic — uses platform `NOT_FOUND` semantics, code is admin-scoped for clarity |
| `ROLE_NOT_FOUND` | 404 | |
| `ASSIGNMENT_NOT_FOUND` | 404 | |
| `SETTING_NOT_FOUND` | 404 | Setting key + scope tuple not found |
| `USER_EMAIL_DUPLICATE` | 409 | (admin-domain) See `error-handling.md § Admin` |
| `ROLE_CODE_DUPLICATE` | 409 | (admin-domain) |
| `USER_HAS_ACTIVE_ASSIGNMENTS` | 422 | (admin-domain) Deactivation blocked unless `force=true` and caller is `WMS_SUPERADMIN` |
| `ROLE_IN_USE` | 422 | (admin-domain) Deactivation blocked unless `force=true` and caller is `WMS_SUPERADMIN` |
| `ROLE_BUILTIN_IMMUTABLE` | 422 | Built-in roles (`WMS_VIEWER` / `WMS_OPERATOR` / `WMS_ADMIN` / `WMS_SUPERADMIN`) cannot be deleted; only their `permissions_json` may be updated. Maps to `IMMUTABLE_FIELD` semantics with admin-specific naming for clarity |
| `SETTING_VALIDATION_ERROR` | 400 | (admin-domain) `value_json` does not satisfy `schema_json` |
| `SETTING_IMMUTABLE_FIELD` | 422 | Reuses platform `IMMUTABLE_FIELD` — `key` / `scope` / `warehouse_id` mutation rejected |
| `STATE_TRANSITION_INVALID` | 422 | Platform-common — User / Role status transitions outside `ACTIVE ↔ INACTIVE` |
| `CONFLICT` | 409 | Platform-common — version mismatch |
| `DUPLICATE_REQUEST` | 409 | Platform-common — same Idempotency-Key, different body |
| `VALIDATION_ERROR` | 400 | Platform-common |
| `UNAUTHORIZED` / `FORBIDDEN` | 401 / 403 | Platform-common |

### Pagination

All list endpoints support:

| Param | Type | Default | Max | Notes |
|---|---|---|---|---|
| `page` | int | 0 | | 0-indexed |
| `size` | int | 20 | 100 | |
| `sort` | string | varies | | `field,{asc\|desc}` (default per endpoint) |

Response envelope:

```json
{
  "content": [ /* items */ ],
  "page": { "number": 0, "size": 20, "totalElements": 137, "totalPages": 7 },
  "sort": "updatedAt,desc"
}
```

### Idempotency Semantics

- `Idempotency-Key` absent on a mutating endpoint (`POST` / `PATCH` / `PUT` /
  `DELETE` under `/users` / `/roles` / `/assignments` / `/settings`) → 400
  `VALIDATION_ERROR`
- Same key, same method+path, **same body hash** → cached response replayed
- Same key, same method+path, **different body** → 409 `DUPLICATE_REQUEST`
- TTL: 24 hours
- Full strategy: [`specs/services/admin-service/idempotency.md`](../../services/admin-service/idempotency.md)

Read-model / dashboard endpoints are pure reads — no idempotency required.

### Optimistic Locking

Mutation endpoints rely on version-checked `UPDATE` internally.
`If-Match: "v{version}"` is **optional** but recommended on `PATCH` / `PUT`
mutations of write-side resources. If supplied, the application service compares
to the persisted version before applying the change:

- Match → proceed
- Mismatch → 409 `CONFLICT` immediately (no DB round-trip past the read)

If `If-Match` is omitted, the version-checked `UPDATE` is the only safety net,
and a `CONFLICT` may surface only after the use-case touched several rows.

Read-model rows carry `version` for diagnostic purposes only — clients must not
send `If-Match` on dashboard endpoints; the value would be meaningless because
projections re-write the row asynchronously.

---

## 1. Dashboard / Read-Model Queries

All endpoints under `/api/v1/admin/dashboard/**` are read-only projections of
other services' events. They are eventually consistent (typically < 5 s lag
under normal load; see `architecture.md § Observability` for the
`admin.projection.lag.seconds` SLI).

### 1.1 Inventory Snapshot

#### `GET /api/v1/admin/dashboard/inventory` — List inventory snapshot rows

Auth: `WMS_VIEWER` or higher (`hasAnyRole('WMS_VIEWER','WMS_OPERATOR','WMS_ADMIN','WMS_SUPERADMIN')`).

Query parameters:

| Param | Type | Notes |
|---|---|---|
| `warehouseId` | UUID | Filter by warehouse |
| `locationId` | UUID | |
| `skuId` | UUID | |
| `lotId` | UUID | |
| `lowStockOnly` | boolean | If `true`, only rows where `low_stock_flag = true` |
| `minOnHand` | int | Only rows where `on_hand_qty >= minOnHand` |
| pagination | | default sort `lastEventAt,desc` |

Response `200`:

```json
{
  "content": [
    {
      "locationId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "warehouseId": "uuid",
      "locationCode": "WH01-A-01-01-01",
      "skuCode": "SKU-APPLE-001",
      "lotNo": "L-20260418-A",
      "availableQty": 80,
      "reservedQty": 20,
      "damagedQty": 0,
      "onHandQty": 100,
      "lowStockFlag": false,
      "lastAdjustedAt": "2026-05-09T10:00:00Z",
      "lastEventAt": "2026-05-09T10:00:00Z",
      "version": 5
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 },
  "sort": "lastEventAt,desc"
}
```

`lowStockFlag` is read-model convenience, recomputed at projection time using
the current `inventory.low_stock.default_threshold_qty` setting. Authoritative
alerts are in `AlertLog`.

#### `GET /api/v1/admin/dashboard/inventory/by-key` — Lookup by composite key

Auth: `WMS_VIEWER` or higher.

Query parameters:

| Param | Type | Required | Notes |
|---|---|---|---|
| `locationId` | UUID | yes | |
| `skuId` | UUID | yes | |
| `lotId` | UUID | no | Required iff SKU is LOT-tracked |

Response `200`: same shape as a single content item above.
Response `404` (`NOT_FOUND`): no row exists for this combination — equivalent
to zero stock.

### 1.2 Throughput

#### `GET /api/v1/admin/dashboard/throughput` — Daily inbound + outbound counters

Auth: `WMS_VIEWER` or higher.

Query parameters:

| Param | Type | Required | Notes |
|---|---|---|---|
| `warehouseId` | UUID | yes | |
| `from` | LocalDate | yes | Inclusive |
| `to` | LocalDate | yes | Inclusive; max 90-day range |

Response `200`:

```json
{
  "warehouseId": "uuid",
  "from": "2026-05-01",
  "to": "2026-05-09",
  "days": [
    {
      "date": "2026-05-01",
      "inbound": { "putawayCount": 12, "qtyReceived": 480 },
      "outbound": { "shipmentCount": 9, "qtyShipped": 312 }
    }
  ],
  "totals": {
    "inbound": { "putawayCount": 95, "qtyReceived": 3210 },
    "outbound": { "shipmentCount": 88, "qtyShipped": 2945 }
  }
}
```

Errors: `VALIDATION_ERROR` (400) if range > 90 days or `to < from`.

### 1.3 Order / Shipment Summary

#### `GET /api/v1/admin/dashboard/orders` — List outbound orders

Auth: `WMS_VIEWER` or higher.

Query parameters: `warehouseId`, `customerPartnerId`, `status`,
`requiredShipDateFrom/To`, `sagaState`, pagination (default
`receivedAt,desc`).

Response `200`: paginated list of `OrderSummary` rows (see
[`domain-model.md § 8`](../../services/admin-service/domain-model.md)).

#### `GET /api/v1/admin/dashboard/shipments` — List shipments

Auth: `WMS_VIEWER` or higher.

Query parameters: `warehouseId`, `orderId`, `carrierCode`, `shippedAtFrom/To`,
pagination (default `shippedAt,desc`).

Response `200`: paginated list of `ShipmentSummary` rows.

### 1.4 Inbound (ASN) Summary

#### `GET /api/v1/admin/dashboard/asns` — List ASN summaries

Auth: `WMS_VIEWER` or higher.

Query parameters: `warehouseId`, `supplierPartnerId`, `status`, `source`,
`expectedArriveDateFrom/To`, pagination (default `receivedAt,desc`).

Response `200`: paginated list of `AsnSummary` rows.

#### `GET /api/v1/admin/dashboard/asns/{asnId}/inspection` — Inspection summary

Auth: `WMS_VIEWER` or higher.

Response `200`: single `InspectionSummary` row.
Errors: `NOT_FOUND` (404) if no inspection has been projected yet.

### 1.5 Adjustment Audit

#### `GET /api/v1/admin/dashboard/adjustments` — List adjustments

Auth: `WMS_VIEWER` or higher.

Query parameters: `warehouseId`, `locationId`, `skuId`, `bucket`, `reasonCode`,
`occurredAtFrom/To`, pagination (default `occurredAt,desc`).

Response `200`: paginated list of `AdjustmentAudit` rows.

This endpoint is the dashboard view onto the appended audit log. It is
**append-only** from the projection — there is no PATCH / DELETE here.

### 1.6 Alerts

#### `GET /api/v1/admin/dashboard/alerts` — List alert log

Auth: `WMS_VIEWER` or higher.

Query parameters: `alertType`, `warehouseId`, `acknowledged` (boolean),
`detectedAtFrom/To`, pagination (default `detectedAt,desc`).

Response `200`: paginated list of `AlertLog` rows.

#### `POST /api/v1/admin/dashboard/alerts/{alertId}/acknowledge` — Acknowledge alert

Auth: `WMS_OPERATOR` or higher.
Requires `Idempotency-Key`.

Request: empty body (the endpoint sets `acknowledged_at = now()` and
`acknowledged_by = X-Actor-Id`).

Response `200`: the updated `AlertLog` row.
Errors: `NOT_FOUND` (404) — no alert with that id;
`STATE_TRANSITION_INVALID` (422) — alert already acknowledged;
`DUPLICATE_REQUEST` (409).

This is the **only** application-layer write path on a read-model table. It
mutates `acknowledged_at` / `acknowledged_by` on `admin_alert_log` only.
Justification: alert acknowledgement is operational state owned by admin-service
itself, not derived from any other service's event. Treated as a domain-tier
write, but persisted into the projection table for join-free dashboard queries.

### 1.7 Master Reference Tables

#### `GET /api/v1/admin/dashboard/refs/{type}` — List a master reference projection

`{type}` ∈ `warehouses | zones | locations | skus | lots | partners`.

Auth: `WMS_VIEWER` or higher.

Query parameters vary by `{type}`. Pagination supported (default
`lastEventAt,desc`).

Response `200`: paginated list of the matching `*Ref` row shape from
`domain-model.md § 5`.

These endpoints exist primarily for admin-UI dropdowns / autocomplete. For
authoritative master data, callers should use `master-service-api.md` directly.

---

## 2. User Management

Write paths fire the corresponding `admin.user.*` event via outbox in the same
`@Transactional` boundary (T3).

### 2.1 `POST /api/v1/admin/users` — Create user

Auth: `WMS_ADMIN` or higher.
Requires `Idempotency-Key`.

Request:

```json
{
  "userCode": "USR-0001",
  "email": "alice@example.com",
  "name": "Alice",
  "phone": "+82-10-1234-5678",
  "defaultWarehouseId": "uuid-or-null"
}
```

Validation:

- `userCode`: required, length 1–40, immutable after creation
- `email`: required, RFC 5322, length ≤ 200, lowercased before persistence,
  globally unique (case-insensitive) → `USER_EMAIL_DUPLICATE` on conflict
- `name`: required, length 1–200
- `phone`: optional, length ≤ 30
- `defaultWarehouseId`: optional, soft-ref to `warehouse_ref`

Response `201`:

```json
{
  "id": "uuid",
  "userCode": "USR-0001",
  "email": "alice@example.com",
  "name": "Alice",
  "phone": "+82-10-1234-5678",
  "defaultWarehouseId": "uuid",
  "status": "ACTIVE",
  "version": 0,
  "createdAt": "2026-05-09T10:00:00Z",
  "createdBy": "admin-uuid",
  "updatedAt": "2026-05-09T10:00:00Z",
  "updatedBy": "admin-uuid"
}
```

Errors: `VALIDATION_ERROR` (400), `USER_EMAIL_DUPLICATE` (409),
`DUPLICATE_REQUEST` (409).

### 2.2 `GET /api/v1/admin/users` — List users

Auth: `WMS_ADMIN` or higher.

Query parameters: `status`, `warehouseId` (matches `defaultWarehouseId`),
`q` (substring match on `name` / `email` / `userCode`), pagination (default
`updatedAt,desc`).

Response `200`: paginated list of user rows in the same shape as the create
response.

### 2.3 `GET /api/v1/admin/users/{id}` — Get user

Auth: `WMS_ADMIN` or higher (`WMS_VIEWER` may also use this for self-lookup —
implementation enforces `id == X-Actor-Id` in that case).

Response `200`: single user row.
Errors: `USER_NOT_FOUND` (404).
Headers: `ETag: "v{version}"`.

### 2.4 `PATCH /api/v1/admin/users/{id}` — Update user profile

Auth: `WMS_ADMIN` or higher.
Requires `Idempotency-Key`.

Request (all fields optional; only fields present are updated):

```json
{
  "name": "Alice Liddell",
  "phone": "+82-10-1234-5679",
  "defaultWarehouseId": "uuid-or-null",
  "email": "alice.l@example.com"
}
```

`userCode` is immutable. Email change validates uniqueness; on conflict
returns `USER_EMAIL_DUPLICATE` (409).

Response `200`: the updated user row.
Errors: `USER_NOT_FOUND` (404), `USER_EMAIL_DUPLICATE` (409),
`CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 2.5 `POST /api/v1/admin/users/{id}/deactivate` — Deactivate user

Auth: `WMS_ADMIN` (`force=false` only); `WMS_SUPERADMIN` (`force=true`).
Requires `Idempotency-Key`.

Request:

```json
{ "force": false }
```

Business rules:

- `force=false`: if the user has any `ACTIVE` `UserRoleAssignment`, returns
  `USER_HAS_ACTIVE_ASSIGNMENTS` (422). Otherwise transitions the user to
  `INACTIVE` and emits `admin.user.deactivated`.
- `force=true` (requires `WMS_SUPERADMIN`): cascade-revokes all `ACTIVE`
  assignments and transitions the user to `INACTIVE`. Emits one
  `admin.assignment.revoked` event per assignment **and** one
  `admin.user.deactivated` event, all in the same TX via the outbox. Per T3
  no in-memory side effects leak before commit.

Response `200`: updated user row + revoked assignment ids (if any):

```json
{
  "user": { /* user row, status=INACTIVE */ },
  "revokedAssignmentIds": ["uuid", "uuid"]
}
```

Errors: `USER_NOT_FOUND` (404), `USER_HAS_ACTIVE_ASSIGNMENTS` (422),
`STATE_TRANSITION_INVALID` (422 — already INACTIVE),
`FORBIDDEN` (403 — `force=true` requested by non-`WMS_SUPERADMIN`),
`CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 2.6 `POST /api/v1/admin/users/{id}/reactivate` — Reactivate user

Auth: `WMS_ADMIN` or higher.
Requires `Idempotency-Key`.

Request: empty body.

Response `200`: updated user row (`status=ACTIVE`).
Errors: `USER_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422 — already
ACTIVE), `CONFLICT` (409), `DUPLICATE_REQUEST` (409).

Reactivation does **not** restore previously-revoked assignments. Operators
must re-grant.

---

## 3. Role Management

### 3.1 `POST /api/v1/admin/roles` — Create role

Auth: `WMS_ADMIN` or higher.
Requires `Idempotency-Key`.

Request:

```json
{
  "roleCode": "WMS_SHIFT_LEAD",
  "name": "Shift Lead",
  "description": "Floor supervisor — read everywhere + acknowledge alerts",
  "permissionsJson": ["INVENTORY_READ", "ALERT_ACKNOWLEDGE"]
}
```

Validation:

- `roleCode`: required, length 1–40, immutable, globally unique →
  `ROLE_CODE_DUPLICATE` on conflict
- `name`: required, length 1–100
- `description`: optional, length ≤ 500
- `permissionsJson`: required, non-empty JSON array of known permission
  strings; unknown values → `SETTING_VALIDATION_ERROR` (400)

Response `201`: created role row with `status=ACTIVE`, `version=0`.
Errors: `VALIDATION_ERROR` (400), `ROLE_CODE_DUPLICATE` (409),
`SETTING_VALIDATION_ERROR` (400 — unknown permission string),
`DUPLICATE_REQUEST` (409).

### 3.2 `GET /api/v1/admin/roles` — List roles

Auth: `WMS_ADMIN` or higher.

Query: `status`, pagination (default `updatedAt,desc`).
Response `200`: paginated list of role rows.

### 3.3 `GET /api/v1/admin/roles/{id}` — Get role

Auth: `WMS_ADMIN` or higher.
Response `200`: single role row. `ETag: "v{version}"`.
Errors: `ROLE_NOT_FOUND` (404).

### 3.4 `PATCH /api/v1/admin/roles/{id}` — Update role

Auth: `WMS_ADMIN` or higher.
Requires `Idempotency-Key`.

Request (all optional):

```json
{
  "name": "Shift Lead (Updated)",
  "description": "...",
  "permissionsJson": ["INVENTORY_READ", "INBOUND_READ", "ALERT_ACKNOWLEDGE"]
}
```

`roleCode` is immutable. Updating `permissionsJson` does **not** retroactively
change permissions on existing JWTs — token re-mint required (out of v1
scope; documented as a known gap in `architecture.md § Security`).

Response `200`: updated role row.
Errors: `ROLE_NOT_FOUND` (404), `SETTING_VALIDATION_ERROR` (400),
`CONFLICT` (409), `DUPLICATE_REQUEST` (409).

### 3.5 `POST /api/v1/admin/roles/{id}/deactivate` — Deactivate role

Auth: `WMS_ADMIN` (`force=false`); `WMS_SUPERADMIN` (`force=true`).
Requires `Idempotency-Key`.

Built-in roles (`WMS_VIEWER`, `WMS_OPERATOR`, `WMS_ADMIN`, `WMS_SUPERADMIN`)
cannot be deactivated. Returns `ROLE_BUILTIN_IMMUTABLE` (422).

Request: `{ "force": false }`.

Business rules:

- `force=false`: if any `ACTIVE` `UserRoleAssignment` references the role,
  returns `ROLE_IN_USE` (422). Otherwise transitions to `INACTIVE`.
- `force=true` (`WMS_SUPERADMIN`): cascade-revokes all referencing
  assignments and transitions the role to `INACTIVE`. Emits
  `admin.assignment.revoked` per assignment + `admin.role.deactivated`.

Response `200`:

```json
{
  "role": { /* role row, status=INACTIVE */ },
  "revokedAssignmentIds": ["uuid"]
}
```

Errors: `ROLE_NOT_FOUND` (404), `ROLE_BUILTIN_IMMUTABLE` (422),
`ROLE_IN_USE` (422), `STATE_TRANSITION_INVALID` (422),
`FORBIDDEN` (403 — non-superadmin force), `CONFLICT` (409),
`DUPLICATE_REQUEST` (409).

### 3.6 `POST /api/v1/admin/roles/{id}/reactivate` — Reactivate role

Auth: `WMS_ADMIN` or higher.
Requires `Idempotency-Key`.

Response `200`: role row (`status=ACTIVE`).
Errors: `ROLE_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422).

---

## 4. User-Role Assignments

### 4.1 `POST /api/v1/admin/assignments` — Grant assignment

Auth: `WMS_ADMIN` or higher.
Requires `Idempotency-Key`.

Request:

```json
{
  "userId": "uuid",
  "roleId": "uuid",
  "warehouseId": "uuid-or-null"
}
```

`warehouseId = null` grants global scope.

Business rules:

- Both `User` and `Role` must be `ACTIVE`. Otherwise `STATE_TRANSITION_INVALID` (422).
- If `(userId, roleId, warehouseId)` already has an `ACTIVE` assignment, returns
  the existing row with HTTP `200` (idempotent grant). Does **not** count as a
  `DUPLICATE_REQUEST` because the body matches the existing state.
- Emits `admin.assignment.granted` via outbox.

Response `201` (new) or `200` (existing-active):

```json
{
  "id": "uuid",
  "userId": "uuid",
  "roleId": "uuid",
  "warehouseId": "uuid-or-null",
  "status": "ACTIVE",
  "grantedAt": "2026-05-09T10:00:00Z",
  "grantedBy": "admin-uuid",
  "version": 0,
  "createdAt": "2026-05-09T10:00:00Z",
  "updatedAt": "2026-05-09T10:00:00Z"
}
```

Errors: `USER_NOT_FOUND` (404), `ROLE_NOT_FOUND` (404),
`STATE_TRANSITION_INVALID` (422 — User/Role inactive), `DUPLICATE_REQUEST` (409).

### 4.2 `GET /api/v1/admin/assignments` — List assignments

Auth: `WMS_ADMIN` or higher.

Query: `userId`, `roleId`, `warehouseId`, `status`, pagination (default
`grantedAt,desc`).
Response `200`: paginated list.

### 4.3 `DELETE /api/v1/admin/assignments/{id}` — Revoke assignment

Auth: `WMS_ADMIN` or higher.
Requires `Idempotency-Key`.

Performs soft-delete: `status=REVOKED`, `revoked_at=now()`,
`revoked_by=X-Actor-Id`. Emits `admin.assignment.revoked`.

Response `204`: no body.
Errors: `ASSIGNMENT_NOT_FOUND` (404), `STATE_TRANSITION_INVALID` (422 —
already REVOKED), `CONFLICT` (409), `DUPLICATE_REQUEST` (409).

`REVOKED` is terminal. To re-grant, create a new assignment via 4.1.

---

## 5. Settings

### 5.1 `GET /api/v1/admin/settings` — List settings

Auth: `WMS_VIEWER` or higher (settings are operationally readable; values may
be cached by consumers via `admin.settings.changed`).

Query: `keyPrefix`, `scope`, `warehouseId`, pagination (default `key,asc`).

Response `200`:

```json
{
  "content": [
    {
      "key": "inventory.reservation.ttl_hours",
      "scope": "GLOBAL",
      "warehouseId": null,
      "valueJson": 24,
      "schemaJson": { "type": "integer", "minimum": 1, "maximum": 168 },
      "description": "Reservation TTL in hours",
      "version": 3,
      "updatedAt": "2026-05-09T10:00:00Z",
      "updatedBy": "admin-uuid"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 4, "totalPages": 1 },
  "sort": "key,asc"
}
```

### 5.2 `GET /api/v1/admin/settings/{key}` — Get a setting

Auth: `WMS_VIEWER` or higher.

Query parameter `warehouseId` is required for `WAREHOUSE`-scoped keys; omitted
for `GLOBAL`.

Response `200`: single setting row.
`ETag: "v{version}"`.
Errors: `SETTING_NOT_FOUND` (404).

### 5.3 `PUT /api/v1/admin/settings/{key}` — Upsert setting value

Auth: `WMS_ADMIN` or higher.
Requires `Idempotency-Key`.

Request:

```json
{
  "warehouseId": null,
  "valueJson": 36
}
```

For `WAREHOUSE`-scoped keys, `warehouseId` must be supplied. For `GLOBAL`,
it must be `null`.

Business rules:

- `key` and `scope` are immutable (declared at seed time). Mismatched scope
  returns `SETTING_IMMUTABLE_FIELD` (422).
- `valueJson` is validated against the persisted `schema_json`. Failure →
  `SETTING_VALIDATION_ERROR` (400).
- Successful write fires `admin.settings.changed` via outbox in the same TX.
  Consuming services react asynchronously — the API does not wait for them.
- If the setting key does not exist (e.g., a new key not in seed), returns
  `SETTING_NOT_FOUND` (404). v1 does **not** create new keys via API; only the
  seed migration registers keys with their `schema_json`.

Response `200`: updated setting row.
Errors: `SETTING_NOT_FOUND` (404), `SETTING_VALIDATION_ERROR` (400),
`SETTING_IMMUTABLE_FIELD` (422), `CONFLICT` (409),
`DUPLICATE_REQUEST` (409).

`DELETE` is **not** offered in v1 (per `domain-model.md § 4`).

---

## 6. Service Health

### 6.1 `GET /actuator/health` — Liveness/readiness

Public (per `gateway-service/public-routes.md`). Standard Spring Boot Actuator.

### 6.2 `GET /api/v1/admin/operations/projection-status` — Projection lag report

Auth: `WMS_ADMIN` or higher.

Response `200`:

```json
{
  "projections": [
    {
      "topic": "wms.inventory.adjusted.v1",
      "consumerGroup": "admin-projection",
      "lagSeconds": 1.4,
      "lastEventAt": "2026-05-09T10:00:00Z",
      "lastProjectedAt": "2026-05-09T10:00:01.400Z",
      "lifetimeApplied": 12048,
      "lifetimeIgnoredDuplicate": 17,
      "lifetimeFailed": 0
    }
  ],
  "worstLagSeconds": 4.8
}
```

Used by ops dashboards / runbook (see
[`runbooks/read-model-rebuild.md`](../../services/admin-service/runbooks/read-model-rebuild.md))
to verify catch-up after a rebuild. Pure read — no idempotency.

---

## References

- [`specs/services/admin-service/architecture.md`](../../services/admin-service/architecture.md)
- [`specs/services/admin-service/domain-model.md`](../../services/admin-service/domain-model.md)
- [`specs/services/admin-service/idempotency.md`](../../services/admin-service/idempotency.md)
- [`specs/services/admin-service/runbooks/read-model-rebuild.md`](../../services/admin-service/runbooks/read-model-rebuild.md)
- [`specs/contracts/events/admin-events.md`](../events/admin-events.md)
- [`specs/integration/gap-integration.md`](../../integration/gap-integration.md)
- [`platform/error-handling.md`](../../../../../platform/error-handling.md) § Admin
- [`platform/api-gateway-policy.md`](../../../../../platform/api-gateway-policy.md)
- `rules/traits/transactional.md` — T1, T3, T4, T5, T8
- `rules/domains/wms.md` — Admin / Operations bounded context
