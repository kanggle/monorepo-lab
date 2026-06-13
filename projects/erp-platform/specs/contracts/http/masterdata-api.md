# API Contract — masterdata-service

Base path: `/api/erp/masterdata` (rewritten by the gateway from
`/api/v1/erp/masterdata` once `gateway-service` is introduced; v1 = direct
JWT to the service).

Authoritative architecture:
[`masterdata-service/architecture.md`](../../services/masterdata-service/architecture.md).
Domain rules: [`rules/domains/erp.md`](../../../../../rules/domains/erp.md) (E1–E8).

All endpoints:
- Require `Authorization: Bearer <token>` with `tenant_id ∈ {erp, *}`
  (RS256, IAM JWKS). Cross-tenant → 403 `TENANT_FORBIDDEN`.
- Enforce the **authorization matrix + data scope** (E6) — insufficient
  role → 403 `PERMISSION_DENIED`; target row outside the caller's
  organization subtree → 403 `DATA_SCOPE_FORBIDDEN`. Both checks happen
  inside the single application path (`AuthorizationPort.evaluate(...)`)
  BEFORE any repository call.
- **Mutating** endpoints require `Idempotency-Key: <client-generated>`
  (erp E1, transactional T1). Missing → 400 `IDEMPOTENCY_KEY_REQUIRED`.
  Same key + identical payload → first stored response replayed (no
  re-mutation). Same key + different payload → 409
  `IDEMPOTENCY_KEY_CONFLICT`. Key scope =
  `(idempotency_key, endpoint, tenant_id)`.
- **Effective-dating** (E2) — read endpoints accept an optional
  `?asOf=<ISO-8601 DATE>` query parameter. Without `asOf`, the read
  resolves to "today" (UTC). With `asOf`, the read returns the single
  revision whose `[effectiveFrom, effectiveTo)` contains `asOf` (or
  open-ended at `effectiveTo`). Required for the E2 point-in-time
  reproducibility AC.
- Success envelope: `{ "data": <payload>, "meta": { "timestamp":
  "<ISO-8601>" } }`. List responses extend `meta` with
  `page` / `size` / `totalElements`.
- Error envelope: `{ "code": "<ERROR_CODE>", "message": "<human>",
  "details": <object?>, "timestamp": "<ISO-8601>" }`. Codes per
  [`platform/error-handling.md`](../../../../../platform/error-handling.md)
  erp section.
- No webhook / public-callback surface in v1 (erp is internal-only, E7).
  Only `/actuator/{health,info}` are unauthenticated.

---

## Common shapes

`EffectivePeriod`:
```json
{ "effectiveFrom": "2026-01-01", "effectiveTo": "2026-12-31" }
```
`effectiveTo` may be `null` (open-ended).

`Audit` (in detail responses):
```json
{ "createdAt": "<ISO-8601>", "createdBy": "<actor>",
  "updatedAt": "<ISO-8601>", "updatedBy": "<actor>" }
```

`PageMeta` (in list responses): `{ "page": 0, "size": 20, "totalElements":
123, "timestamp": "<ISO-8601>" }`.

---

## Department

### POST /api/erp/masterdata/departments

Create a department. Initial state `ACTIVE`. Optional `parentId` (root if
absent).

**Headers**: `Authorization` (req), `Idempotency-Key` (req),
`Content-Type: application/json`

**Request**:
```json
{ "code": "DEPT-001", "name": "Sales",
  "parentId": "dept-9b1d4a8c-...",
  "effectiveFrom": "2026-01-01" }
```
- `code` — required, ≤ 64 chars, natural key (unique per tenant)
- `name` — required, ≤ 256 chars
- `parentId` — optional UUID (root if absent)
- `effectiveFrom` — optional ISO-8601 DATE (default: today)

**201**: `{ "data": { "id", "code", "name", "parentId", "status": "ACTIVE",
"effectivePeriod": {...}, "audit": {...} }, "meta": {...} }`

**Errors**: 400 `VALIDATION_ERROR`, 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 409 `MASTERDATA_DUPLICATE_KEY` (code in
use), 404 `MASTERDATA_NOT_FOUND` (parentId unknown), 422
`MASTERDATA_EFFECTIVE_PERIOD_INVALID`, 403 `PERMISSION_DENIED` /
`DATA_SCOPE_FORBIDDEN`, 403 `TENANT_FORBIDDEN`.

### GET /api/erp/masterdata/departments

List (scope-aware).

**Query**: `?asOf=&active=true|false&parentId=&page=&size=`

**200**: `{ "data": [ { "id", "code", "name", "parentId", "status",
"effectivePeriod" } ], "meta": <PageMeta> }`

**Errors**: 403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

### GET /api/erp/masterdata/departments/{id}

Detail (point-in-time).

**Query**: `?asOf=`

**200**: `{ "data": { "id", "code", "name", "parentId", "status",
"effectivePeriod", "audit" }, "meta": {...} }`

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 403 `PERMISSION_DENIED` /
`DATA_SCOPE_FORBIDDEN`.

### PATCH /api/erp/masterdata/departments/{id}

Append an effective-dated revision. The PATCH does NOT overwrite the
current row — it creates a new revision with `effectiveFrom = now` (or
the operator-supplied future date, validated for non-overlap with
existing revisions of this id).

**Headers**: `Idempotency-Key` (req)

**Request**:
```json
{ "name": "Sales (renamed)",
  "effectiveFrom": "2026-04-01" }
```

**200**: `{ "data": { "id", "effectivePeriod": {...}, ... }, "meta": {...} }`

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 422
`MASTERDATA_EFFECTIVE_PERIOD_INVALID`, 409 `CONCURRENT_MODIFICATION`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
403 `PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN`.

### POST /api/erp/masterdata/departments/{id}/retire

Logical retire. Blocked if any live reference points at this department.

**Headers**: `Idempotency-Key` (req)

**Request**: `{ "reason": "<≤256, required>" }`

**200**: `{ "data": { "id", "status": "RETIRED", "retiredAt", ... }, ... }`

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 409 `MASTERDATA_REFERENCE_VIOLATION`
(employees / cost-centers / child departments still reference this row;
`details` enumerates the referencer kinds), 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 403 `PERMISSION_DENIED`.

### POST /api/erp/masterdata/departments/{id}/move-parent

Move the department to a new parent in the hierarchy. Refused if the move
would close a cycle (i.e. the candidate new parent is a descendant of, or
equal to, this department).

**Headers**: `Idempotency-Key` (req)

**Request**: `{ "newParentId": "dept-...|null", "effectiveFrom":
"<ISO-8601 DATE>", "reason": "<≤256>" }`

**200**: `{ "data": { "id", "parentId", "effectivePeriod", ... }, ... }`

**Errors**: 404 `MASTERDATA_NOT_FOUND` (id or newParentId unknown),
409 `MASTERDATA_PARENT_CYCLE`, 422 `MASTERDATA_EFFECTIVE_PERIOD_INVALID`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
403 `PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN`.

---

## Employee

### POST /api/erp/masterdata/employees

**Request**:
```json
{ "employeeNumber": "EMP-001",
  "name": "홍길동",
  "departmentId": "dept-...",
  "costCenterId": "cc-...",
  "jobGradeId": "jg-...",
  "effectiveFrom": "2026-01-01" }
```

**201**: detail envelope (same shape pattern as Department).

**Errors**: 400 `VALIDATION_ERROR`, 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 409 `MASTERDATA_DUPLICATE_KEY`
(`employeeNumber` in use), 404 `MASTERDATA_NOT_FOUND` (referenced
department/costCenter/jobGrade unknown), 422
`MASTERDATA_EFFECTIVE_PERIOD_INVALID`, 403 `PERMISSION_DENIED` /
`DATA_SCOPE_FORBIDDEN`.

### GET /api/erp/masterdata/employees

**Query**: `?asOf=&active=&departmentId=&costCenterId=&page=&size=`

**200**: list envelope.

### GET /api/erp/masterdata/employees/{id}

**Query**: `?asOf=`

**200**: detail envelope.

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 403 `PERMISSION_DENIED` /
`DATA_SCOPE_FORBIDDEN`.

### PATCH /api/erp/masterdata/employees/{id}

Append revision. Typical use: change `departmentId` / `costCenterId` /
`jobGradeId` (organization reassignment).

**Headers**: `Idempotency-Key` (req)

**Request**:
```json
{ "departmentId": "dept-...?", "costCenterId": "cc-...?",
  "jobGradeId": "jg-...?", "name": "...?",
  "effectiveFrom": "2026-04-01" }
```
(All business fields optional; at least one required.)

**200**: detail envelope.

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 422
`MASTERDATA_EFFECTIVE_PERIOD_INVALID`, 409 `CONCURRENT_MODIFICATION`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
403 `PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN`.

### POST /api/erp/masterdata/employees/{id}/retire

**Headers**: `Idempotency-Key` (req)
**Request**: `{ "reason": "<≤256, required>" }`
**200**: detail envelope.

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 403 `PERMISSION_DENIED`.
(`MASTERDATA_REFERENCE_VIOLATION` not emitted for Employee — Employee is
a leaf in the v1 reference graph; only forward integrations reference it,
and those are not enforced here per E5 read-only boundary.)

---

## JobGrade

### POST /api/erp/masterdata/job-grades

**Request**:
```json
{ "code": "G3", "name": "사원-3년차", "displayOrder": 30,
  "effectiveFrom": "2026-01-01" }
```

**201**: detail envelope.

**Errors**: 400 `VALIDATION_ERROR`, 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 409 `MASTERDATA_DUPLICATE_KEY` (`code`
in use), 422 `MASTERDATA_EFFECTIVE_PERIOD_INVALID`,
403 `PERMISSION_DENIED`.

### GET /api/erp/masterdata/job-grades

**Query**: `?asOf=&active=&page=&size=`

**200**: list envelope ordered by `displayOrder` ascending.

### GET /api/erp/masterdata/job-grades/{id}

**Query**: `?asOf=`

**200**: detail envelope. **Errors**: 404 `MASTERDATA_NOT_FOUND`.

### PATCH /api/erp/masterdata/job-grades/{id}

**Headers**: `Idempotency-Key` (req)
**Request**: `{ "name": "?", "displayOrder": "?",
"effectiveFrom": "..." }`
**200**: detail envelope.

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 422
`MASTERDATA_EFFECTIVE_PERIOD_INVALID`, 409 `CONCURRENT_MODIFICATION`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
403 `PERMISSION_DENIED`.

### POST /api/erp/masterdata/job-grades/{id}/retire

**Headers**: `Idempotency-Key` (req)
**Request**: `{ "reason": "<≤256, required>" }`
**200**: detail envelope.

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 409
`MASTERDATA_REFERENCE_VIOLATION` (active Employee revisions still
reference this grade), 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 403 `PERMISSION_DENIED`.

---

## CostCenter

### POST /api/erp/masterdata/cost-centers

**Request**:
```json
{ "code": "CC-100", "name": "영업본부 비용센터",
  "departmentId": "dept-...",
  "effectiveFrom": "2026-01-01" }
```

**201**: detail envelope.

**Errors**: 400 `VALIDATION_ERROR`, 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 409 `MASTERDATA_DUPLICATE_KEY`,
404 `MASTERDATA_NOT_FOUND` (`departmentId` unknown), 422
`MASTERDATA_EFFECTIVE_PERIOD_INVALID`, 403 `PERMISSION_DENIED` /
`DATA_SCOPE_FORBIDDEN`.

### GET /api/erp/masterdata/cost-centers

**Query**: `?asOf=&active=&departmentId=&page=&size=`

**200**: list envelope.

### GET /api/erp/masterdata/cost-centers/{id}

**Query**: `?asOf=`

**200**: detail envelope. **Errors**: 404 `MASTERDATA_NOT_FOUND`.

### PATCH /api/erp/masterdata/cost-centers/{id}

**Headers**: `Idempotency-Key` (req)
**Request**: `{ "name": "?", "departmentId": "?",
"effectiveFrom": "..." }`
**200**: detail envelope.

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 422
`MASTERDATA_EFFECTIVE_PERIOD_INVALID`, 409 `CONCURRENT_MODIFICATION`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
403 `PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN`.

### POST /api/erp/masterdata/cost-centers/{id}/retire

**Headers**: `Idempotency-Key` (req)
**Request**: `{ "reason": "<≤256, required>" }`
**200**: detail envelope.

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 409
`MASTERDATA_REFERENCE_VIOLATION` (active Employee revisions still
reference this cost center), 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 403 `PERMISSION_DENIED`.

---

## BusinessPartner

### POST /api/erp/masterdata/business-partners

**Request**:
```json
{ "code": "BP-001", "name": "ACME Corp",
  "partnerType": "CUSTOMER|SUPPLIER|BOTH",
  "paymentTerms": { "termDays": 30, "method": "BANK_TRANSFER" },
  "effectiveFrom": "2026-01-01" }
```

**201**: detail envelope.

**Errors**: 400 `VALIDATION_ERROR`, 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 409 `MASTERDATA_DUPLICATE_KEY`,
422 `MASTERDATA_EFFECTIVE_PERIOD_INVALID`, 403 `PERMISSION_DENIED`.

### GET /api/erp/masterdata/business-partners

**Query**: `?asOf=&active=&partnerType=&page=&size=`

**200**: list envelope.

### GET /api/erp/masterdata/business-partners/{id}

**Query**: `?asOf=`

**200**: detail envelope. **Errors**: 404 `MASTERDATA_NOT_FOUND`.

### PATCH /api/erp/masterdata/business-partners/{id}

**Headers**: `Idempotency-Key` (req)
**Request**: `{ "name": "?", "partnerType": "?",
"paymentTerms": {...}?, "effectiveFrom": "..." }`
**200**: detail envelope.

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 422
`MASTERDATA_EFFECTIVE_PERIOD_INVALID`, 409 `CONCURRENT_MODIFICATION`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
403 `PERMISSION_DENIED`.

### POST /api/erp/masterdata/business-partners/{id}/retire

**Headers**: `Idempotency-Key` (req)
**Request**: `{ "reason": "<≤256, required>" }`
**200**: detail envelope.

**Errors**: 404 `MASTERDATA_NOT_FOUND`, 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 403 `PERMISSION_DENIED`.
(`MASTERDATA_REFERENCE_VIOLATION` not emitted for BusinessPartner in v1 —
cross-domain references from procurement / finance are read-only per
E5; v1 has no inbound enforcement surface here.)

---

## Error code → HTTP status (erp)

| Code | HTTP | Trigger |
|---|---|---|
| `VALIDATION_ERROR` | 400 | bean-validation failure |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | mutating call without header (Platform-Common Transactional Trait) |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | same key, different payload |
| `MASTERDATA_NOT_FOUND` | 404 | unknown aggregate id (incl. referenced parent / department / cost-center / job-grade) |
| `MASTERDATA_DUPLICATE_KEY` | 409 | natural-key collision on create |
| `MASTERDATA_REFERENCE_VIOLATION` | 409 | retire blocked by live referencer (E1) |
| `MASTERDATA_PARENT_CYCLE` | 409 | `Department.moveParent` would close a cycle (E1) |
| `MASTERDATA_EFFECTIVE_PERIOD_INVALID` | 422 | period overlap on same natural key, or `effectiveTo ≤ effectiveFrom` (E2) |
| `PERMISSION_DENIED` | 403 | required role not present (E6) |
| `DATA_SCOPE_FORBIDDEN` | 403 | target row's owning department outside caller's data scope (E6) |
| `TENANT_FORBIDDEN` | 403 | `tenant_id ∉ {erp, *}` |
| `EXTERNAL_TRAFFIC_REJECTED` | 403 | external (non-internal-network) ingress (E7) — primarily enforced at Traefik / network layer; this code is the application-layer fallback surface |
| `UNAUTHORIZED` | 401 | missing / invalid / expired JWT (Platform-Common Authentication) |
| `CONCURRENT_MODIFICATION` | 409 | optimistic-lock conflict on revision append (Platform-Common Transactional Trait `CONFLICT` semantic; this surface uses the erp-specific name) |

> `IDEMPOTENCY_STORE_UNAVAILABLE` (503) is **v1-emittable but rare** — v1 uses
> the DB-table primary inside the mutation Tx (see architecture.md
> § Idempotency), so the common path never raises it; however the fail-CLOSED
> store (`DbIdempotencyStore`) does throw it in v1 on the claim path's
> `DataAccessException` (store-down) or unresolved-insert-race branch. The same
> code is also reserved for the future Redis-primary swap.

All erp codes registered in `platform/error-handling.md` under the
`Master Data  [domain: erp]` and `Authorization  [domain: erp]` sections
(this PR appends them).
