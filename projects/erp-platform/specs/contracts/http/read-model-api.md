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
  RS256, IAM JWKS). Both branches fail → 403 `TENANT_FORBIDDEN`.
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

## Approval facts (v1.1 — TASK-ERP-BE-010)

The integrated read model also projects **approval facts** — the latest state of
each approval request (consumed from `erp.approval.*.v1`,
[`read-model-subscriptions.md`](../events/read-model-subscriptions.md)). These are
**read-only projections** (E5): the latest fact only — the authoritative full
transition `history` lives on `approval-service`
(`GET /api/erp/approval/requests/{id}`). The read-model surface exists so an
operator can query/report approval state **alongside** the org-view in one read
store, without per-request fan-out to approval-service.

`ApprovalFact`:
```json
{ "approvalRequestId": "appr-...",
  "status": "SUBMITTED|APPROVED|REJECTED|WITHDRAWN",
  "subjectType": "DEPARTMENT|EMPLOYEE",
  "subjectId": "dept-... | emp-...",
  "subject": <DepartmentRef> | <{ id, employeeNumber, name }> | null,
  "approverId": "emp-approver-...",
  "submitterId": "emp-submitter-...",
  "submittedAt": "<ISO-8601 UTC; ABSENT if the submitted event was not seen>",
  "finalizedAt": "<ISO-8601 UTC; ABSENT until a terminal event>",
  "lastReason":  "<≤512; ABSENT when none (submitted / reasonless approve)>" }
```
- `subject` is the read-time-resolved master ref (`DepartmentRef` for
  `DEPARTMENT`, a trimmed employee ref for `EMPLOYEE`) — `null` when the
  referenced master projection is not yet consumed (eventually-consistent;
  surfaced in `meta.unresolved`, never fabricated, E5).
- Only `DRAFT` requests never appear (a draft emits no event — the first
  projected fact is `SUBMITTED`).

### GET /api/erp/read-model/approvals

Paginated approval-fact list, **scope-aware** (E6) — same READ gate +
**org_scope subtree filter** as the org-view: a caller bounded to a department
subtree (TASK-ERP-BE-008 `org_scope`) sees only facts whose **subject department**
(the `DEPARTMENT` subject itself, or an `EMPLOYEE` subject's resolved department)
is in scope; `["*"]`/unset = all (net-zero).

**Headers**: `Authorization` (req)

**Query**:
- `status` — optional, enum `SUBMITTED | APPROVED | REJECTED | WITHDRAWN`
- `subjectType` — optional, enum `DEPARTMENT | EMPLOYEE`
- `subjectId` — optional, filter to one subject
- `approverId` / `submitterId` — optional, filter by route role
- `page` — optional, default `0`
- `size` — optional, default `20`, ≤ `100`

**200**:
```json
{ "data": [ <ApprovalFact>, ... ],
  "meta": { "page": 0, "size": 20, "totalElements": 12,
            "timestamp": "<ISO-8601>",
            "warning": "Eventually-consistent read-model" } }
```

**Errors**: 400 `VALIDATION_ERROR`, 401 `UNAUTHORIZED`, 403 `PERMISSION_DENIED`,
403 `TENANT_FORBIDDEN`.

### GET /api/erp/read-model/approvals/{approvalRequestId}

Single approval fact (latest state). For the full immutable `history`, consumers
use `approval-service` `GET /api/erp/approval/requests/{id}` (source of record).

**Headers**: `Authorization` (req)

**Path**: `approvalRequestId` — the approval request id.

**200**:
```json
{ "data": <ApprovalFact>,
  "meta": { "timestamp": "<ISO-8601>",
            "warning": "Eventually-consistent read-model",
            "unresolved": ["subject"] } }
```
(`meta.unresolved` present only when the subject ref is not yet resolved.)

**Errors**: 404 `MASTERDATA_NOT_FOUND` (no approval-fact projection for the id — a
projection miss is not fabricated; out-of-scope subject also surfaces as 404 to
avoid existence leak, mirroring the org-view detail rule), 401 `UNAUTHORIZED`,
403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

---

## Delegation facts (v1.2 — TASK-ERP-BE-015)

The integrated read model also projects **delegation facts** — the latest state of
each delegation grant (consumed from `erp.approval.delegated.v1` +
`erp.approval.delegation.revoked.v1`,
[`read-model-subscriptions.md`](../events/read-model-subscriptions.md) § v1.2).
**Read-only projections** (E5): latest fact only — the authoritative grant state +
audit history live on `approval-service`. The surface answers "who may act for
whom" (delegation) read-only, alongside the org-view + approval facts.

`DelegationFact`:
```json
{ "grantId": "dgr-...",
  "status": "ACTIVE|REVOKED",
  "delegatorId": "emp-A-...",
  "delegateId": "emp-D-...",
  "validFrom": "<ISO-8601 UTC; ABSENT if only a revoke (out-of-order) was seen>",
  "validTo":   "<ISO-8601 UTC; ABSENT = open-ended, or revoke-before-grant>",
  "reason":    "<≤512; ABSENT when none>",
  "scope":     "GLOBAL | REQUEST; ABSENT if only a revoke (out-of-order) was seen — TASK-ERP-BE-018",
  "scopeRequestId": "<approvalRequestId; present only when scope=REQUEST; ABSENT for GLOBAL>",
  "revokedAt": "<ISO-8601 UTC; ABSENT while ACTIVE>" }
```
- `delegatorId` / `delegateId` are opaque employee ids (no subject ref is resolved —
  a console drill-down resolves display names via the org-view if needed).
- `validFrom`/`validTo`/`revokedAt`/`reason` are ABSENT (NON_NULL) when not
  applicable — an out-of-order revoke-before-grant leaves the validity window
  ABSENT (never fabricated, E5).
- **`scope`/`scopeRequestId` (TASK-ERP-BE-018)** project the per-request scoping
  added by the producer (`erp-approval-events.md` § v2.3 / approval-service v2.3):
  `scope=GLOBAL` (blanket — D acts for A on any request) or `scope=REQUEST`
  (narrowed to the one `scopeRequestId`). They are grant-time immutable, projected
  from the `delegated` event and preserved across a later `revoke`; both are ABSENT
  (NON_NULL) when only a revoke was seen out-of-order (scope unknown — never
  fabricated, E5). `scopeRequestId` is ABSENT for a `GLOBAL` grant.

### GET /api/erp/read-model/delegations

Paginated delegation-fact list, **scope-aware** (E6) — same READ gate +
**org_scope subtree filter** as the org-view / approval facts, here on the
**delegator's department** (the `delegatorId` is an employee id resolved to its
department; `["*"]`/unset = all, net-zero).

**Headers**: `Authorization` (req)

**Query**:
- `delegatorId` — optional, filter to one delegator (A)
- `delegateId` — optional, filter to one delegate (D)
- `status` — optional, enum `ACTIVE | REVOKED`
- `activeAt` — optional ISO-8601 UTC instant; matches grants ACTIVE at that
  instant (`status=ACTIVE ∧ validFrom ≤ t ∧ (validTo null ∨ t ≤ validTo)`)
- `page` — optional, default `0`
- `size` — optional, default `20`, ≤ `100`

**200**:
```json
{ "data": [ <DelegationFact>, ... ],
  "meta": { "page": 0, "size": 20, "totalElements": 7,
            "timestamp": "<ISO-8601>",
            "warning": "Eventually-consistent read-model" } }
```

**Errors**: 400 `VALIDATION_ERROR` (bad status/activeAt/page/size), 401
`UNAUTHORIZED`, 403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

### GET /api/erp/read-model/delegations/{grantId}

Single delegation fact (latest state). For the full grant audit history, consumers
use `approval-service` (source of record).

**Headers**: `Authorization` (req)

**Path**: `grantId` — the delegation grant id.

**200**:
```json
{ "data": <DelegationFact>,
  "meta": { "timestamp": "<ISO-8601>",
            "warning": "Eventually-consistent read-model" } }
```

**Errors**: 404 `MASTERDATA_NOT_FOUND` (no delegation-fact projection for the id — a
projection miss is not fabricated; out-of-scope delegator also surfaces as 404 to
avoid existence leak, mirroring the approval-fact / org-view detail rule), 401
`UNAUTHORIZED`, 403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

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
