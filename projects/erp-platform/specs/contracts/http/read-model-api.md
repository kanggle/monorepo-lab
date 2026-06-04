# API Contract — read-model-service

Base path: `/api/erp/read-model` (rewritten by the gateway from
`/api/v1/erp/read-model` once `gateway-service` is introduced; v1 = direct
JWT to the service).

Authoritative architecture:
[`read-model-service/architecture.md`](../../services/read-model-service/architecture.md).
Domain rules: [`rules/domains/erp.md`](../../../../../rules/domains/erp.md)
(E5 — integrated read model holds no domain logic; E6/E7).

All endpoints:
- Require `Authorization: Bearer <token>` satisfying the **entitlement-trust
  dual-accept** gate (`tenant_id ∈ {erp, *}` ∪ signed `entitled_domains ∋ erp`,
  RS256, GAP JWKS). Both branches fail → 403 `TENANT_FORBIDDEN`.
- Enforce the **READ authorization gate** (E6, fail-closed): `erp.read` scope ∨
  `isOperator()` ∨ entitled; else 403 `PERMISSION_DENIED`. The platform-console
  operator token that already reads `masterdata-service` satisfies this gate
  (same `RoleScopeAuthorizationAdapter` READ semantics; TASK-ERP-BE-004).
- Are **read-only** (E5) — there are no mutating endpoints, so no
  `Idempotency-Key` header applies (contrast masterdata-api.md).
- **Effective-dating** (E2 parity) — accept an optional `?asOf=<ISO-8601 DATE>`.
  Without `asOf`, resolves to "today" (UTC). With `asOf`, each projected master
  is resolved to the revision whose `[effectiveFrom, effectiveTo)` contains
  `asOf` (open-ended at `effectiveTo`); a master `RETIRED` after `asOf` still
  resolves as it was at `asOf`.
- Success envelope: `{ "data": <payload>, "meta": { "timestamp": "<ISO-8601>",
  "warning": "Eventually-consistent read-model" } }`. List responses extend
  `meta` with `page` / `size` / `totalElements`. An org-view with an unresolved
  reference adds `meta.unresolved: ["department"|"costCenter"|"jobGrade", ...]`.
- Error envelope: `{ "code": "<ERROR_CODE>", "message": "<human>", "details":
  <object?>, "timestamp": "<ISO-8601>" }`. Codes per
  [`platform/error-handling.md`](../../../../../platform/error-handling.md)
  erp section.
- No webhook / public-callback surface (erp is internal-only, E7). Only
  `/actuator/{health,info}` are unauthenticated.

---

## Common shapes

`DepartmentRef` (resolved department, with ancestry path root→leaf):
```json
{ "id": "dept-...", "code": "SALES", "name": "영업본부",
  "path": [ { "id": "dept-root", "code": "HQ", "name": "본사" },
            { "id": "dept-...", "code": "SALES", "name": "영업본부" } ] }
```

`CostCenterRef`: `{ "id": "cc-...", "code": "CC-100", "name": "영업원가센터" }`

`JobGradeRef`: `{ "id": "jg-...", "code": "G3", "name": "사원", "displayOrder": 30 }`

`EmployeeOrgView`:
```json
{ "id": "emp-...", "employeeNumber": "E-1001", "name": "홍길동",
  "status": "ACTIVE",
  "effectivePeriod": { "effectiveFrom": "2026-01-01", "effectiveTo": null },
  "department": <DepartmentRef> | null,
  "costCenter":  <CostCenterRef> | null,
  "jobGrade":    <JobGradeRef> | null }
```
Any of `department` / `costCenter` / `jobGrade` is `null` when the referenced
master's `*.changed` event has not yet been consumed (eventually-consistent) —
the field is **never fabricated** (E5; `READ_MODEL_SOURCE_UNAVAILABLE`
semantics). Unresolved references are also listed in `meta.unresolved`.

`PageMeta` (list responses): `{ "page": 0, "size": 20, "totalElements": 123,
"timestamp": "<ISO-8601>", "warning": "Eventually-consistent read-model" }`.

---

## GET /api/erp/read-model/employees

Paginated employee org-view list (employee enriched with resolved department
path + cost center + job grade).

**Headers**: `Authorization` (req)

**Query**:
- `page` — optional, default `0`
- `size` — optional, default `20`, ≤ `100`
- `asOf` — optional ISO-8601 DATE (default: today, UTC)
- `departmentId` — optional UUID; when present, filters to employees whose
  resolved department is the given department **or a descendant** (subtree
  filter, read-time over `department_proj.parent_id`)
- `status` — optional `ACTIVE | RETIRED` (default: `ACTIVE`)

**200**:
```json
{ "data": [ <EmployeeOrgView>, ... ],
  "meta": { "page": 0, "size": 20, "totalElements": 42,
            "timestamp": "<ISO-8601>",
            "warning": "Eventually-consistent read-model" } }
```

**Errors**: 400 `VALIDATION_ERROR` (bad page/size/asOf), 401 `UNAUTHORIZED`,
403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

---

## GET /api/erp/read-model/employees/{id}

Single employee org-view.

**Headers**: `Authorization` (req)

**Path**: `id` — employee aggregate id (UUID).

**Query**: `asOf` — optional ISO-8601 DATE (default: today, UTC).

**200**:
```json
{ "data": <EmployeeOrgView>,
  "meta": { "timestamp": "<ISO-8601>",
            "warning": "Eventually-consistent read-model",
            "unresolved": ["costCenter"] } }
```
(`meta.unresolved` present only when ≥ 1 reference is unresolved.)

**Errors**: 404 `MASTERDATA_NOT_FOUND` (no employee projection for `id` — a
projection miss is not a fabricated row), 401 `UNAUTHORIZED`, 403
`PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

---

## Notes

- The read-model is **eventually consistent** with `masterdata-service`. Every
  response carries `meta.warning` so a consumer cannot mistake the projection
  for the authoritative master (`masterdata-service` is the source of record,
  E5). For authoritative single-master reads, consumers use the
  [`masterdata-api.md`](masterdata-api.md) GET endpoints.
- `businesspartner` is **not** part of the employee org-view and is not projected
  in this increment (read-model first increment, TASK-ERP-BE-007). A
  business-partner / full integrated-view surface is a follow-up.
- No mutating endpoint exists or will exist on the read-model (E5). Master
  changes flow only through the consumed Kafka topics
  ([`read-model-subscriptions.md`](../events/read-model-subscriptions.md)).
