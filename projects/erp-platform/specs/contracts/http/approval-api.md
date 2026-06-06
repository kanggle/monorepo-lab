# API Contract — approval-service

Base path: `/api/erp/approval` (rewritten by the gateway from
`/api/v1/erp/approval` once `gateway-service` is introduced; v1 = direct
JWT to the service).

Authoritative architecture:
[`approval-service/architecture.md`](../../services/approval-service/architecture.md).
Domain rules: [`rules/domains/erp.md`](../../../../../rules/domains/erp.md)
(E3 — 상태기계 + 권한 있는 결재자만 전이; E4 — 멱등 + 불변 감사; E6/E7/E8).

**First increment scope** — single-stage approval only. The state machine is
`DRAFT → SUBMITTED → APPROVED`, with branches `REJECTED` / `WITHDRAWN`. The
intermediate `IN_REVIEW` state, multi-stage routes, delegation (대결), and
inbox filtering are **v2 deferred** (see § v2 deferred). Each request carries
exactly one approver (`approverId`).

> **v2.0 AMENDMENT (TASK-ERP-BE-012 — multi-stage routing + `IN_REVIEW`,
> additive + backward-compatible).** Multi-stage routes (1~N ordered stages) and
> the `IN_REVIEW` status are now realised (architecture.md § v2.0 amendment).
> Additive changes to this contract:
> - **Create** accepts an ordered **`approverIds: ["emp-a","emp-b",…]`** (1~N
>   stages) **OR** the legacy **`approverId`** (= a 1-stage route); exactly one is
>   required. The platform-console (TASK-PC-FE-051) keeps sending `approverId` and
>   continues to work unchanged (a 1-stage route).
> - **`status`** enum gains **`IN_REVIEW`** (non-terminal). A multi-stage request
>   is `SUBMITTED` while stage 0 is pending, `IN_REVIEW` while a later stage is
>   pending, then `APPROVED` once the final stage approves.
> - **Detail / Summary** gain **`stages`** (`[{ "stageIndex", "approverId",
>   "status": "PENDING|APPROVED" }]`), **`currentStage`** (0-based index of the
>   pending stage; ABSENT once finalized), and **`totalStages`**. The legacy
>   **`approverId`** is retained = the **current stage's** approver (read
>   back-compat). `history` entries gain an optional **`stage`** field.
> - **`approve`** advances the route: from `SUBMITTED|IN_REVIEW` → `APPROVED` if
>   the current stage is the last, else → `IN_REVIEW` (next stage pending). Only
>   the **current stage's** approver may `approve`/`reject` (an earlier/later
>   stage's approver → 403 `APPROVAL_NOT_AUTHORIZED_APPROVER` — sequential order
>   enforced). `withdraw` stays submitter-only; `reject` from any stage → terminal
>   `REJECTED`.
> - **Route-validity** (`APPROVAL_ROUTE_INVALID`) extends to a **duplicate
>   approver across stages** (`details.cause = "duplicate_stage_approver"`) and
>   `submitter ∈ any stage` (self-approval). **No new error code** — the existing
>   approval codes cover it.
>
> Where a v1.0 endpoint below says "single-stage" / `approverId`, read it through
> this amendment: the single approver is the N=1 special case. 대결/위임 (delegation)
> remains **v2.1-deferred** (§ v2 deferred).

> **v2.1 AMENDMENT (TASK-ERP-BE-013 — 대결/위임 delegation, additive).** A standing
> windowed delegation grant lets an absent stage approver's delegate act for them
> (architecture.md § v2.1 amendment). Additive changes to this contract:
> - **New delegation endpoints** (all under `/api/erp/approval/delegations`):
>   - `POST /api/erp/approval/delegations` — create a grant `{ delegateId,
>     validFrom, validTo? , reason }` (delegator = caller `sub`; `Idempotency-Key`
>     required). 201 with the grant. Self-delegation (`delegateId == caller`) or
>     `validTo < validFrom` → 422 `DELEGATION_INVALID`.
>   - `POST /api/erp/approval/delegations/{id}/revoke` — `{ reason }` →
>     ACTIVE→REVOKED (idempotent; unknown id → 404 `DELEGATION_NOT_FOUND`).
>   - `GET /api/erp/approval/delegations` — the caller's grants (as delegator +
>     as delegate), scope-aware; `?role=DELEGATOR|DELEGATE` optional filter.
>   Auth: create/revoke = `erp.write` (own grants / admin); list = `erp.read`.
> - **Transition acceptance** — `approve` / `reject` accept **the current stage's
>   approver OR an active delegate of that approver**. When a delegate acts, the
>   transition succeeds exactly as if the approver acted (state machine + stage
>   advance unchanged), the audit records `actor` (delegate) + `onBehalfOf`
>   (approver), and the response/detail + the emitted event carry an additive
>   **`actingForApproverId`** (= the approver; ABSENT when the approver acted
>   themselves — NON_NULL). A non-approver with **no** active grant → 403
>   `APPROVAL_NOT_AUTHORIZED_APPROVER` (unchanged). **Delegation cannot bypass
>   Separation of Duties**: a delegate who is the request's `submitterId` is
>   refused. `withdraw` stays **submitter-only** (never delegable).
> - **New error codes** — `DELEGATION_INVALID` (422), `DELEGATION_NOT_FOUND` (404),
>   registered in `platform/error-handling.md` erp section.
>
> This completes the ADR-MONO-016 §D3 approval forward-declaration (단계/대결/위임).
> v2.2-deferred: per-request/per-route delegation, auto-absence detection,
> transitive chaining (§ v2 deferred).

> **v2.3 AMENDMENT (TASK-ERP-BE-017 — per-request delegation scoping, additive).**
> A delegation grant can be narrowed to ONE approval request (the v2.1 grant is the
> `GLOBAL` default — blanket over A's whole queue). Additive changes:
> - **Create body** — `POST /api/erp/approval/delegations` accepts two optional
>   fields: `scope` (`"GLOBAL"` | `"REQUEST"`, default `GLOBAL`) + `scopeRequestId`
>   (the target `approvalRequestId` string, ≤64). Coherence: `scope="REQUEST"`
>   requires a non-blank `scopeRequestId`; `scope="GLOBAL"` forbids it — a violation
>   → 422 `DELEGATION_INVALID`. An unparseable `scope` string → 400
>   `VALIDATION_ERROR`. The full create body is now `{ delegateId, validFrom,
>   validTo?, reason?, scope?, scopeRequestId? }`.
> - **Grant view** — create + `GET /delegations` responses carry `scope` (always)
>   + `scopeRequestId` (present only when `REQUEST`; ABSENT when `GLOBAL` —
>   `@JsonInclude(NON_NULL)`).
> - **Transition acceptance honors scope** — `approve`/`reject` accept a delegate
>   only when the delegate holds an active grant that **covers the request being
>   acted on**: a `GLOBAL` grant covers every request where A is the approver; a
>   `REQUEST` grant covers ONLY its `scopeRequestId`. A delegate with a
>   `REQUEST`-scoped grant for a *different* request → 403
>   `APPROVAL_NOT_AUTHORIZED_APPROVER` (fail-closed). SoD (delegate ≠ submitter) +
>   `withdraw` submitter-only are unchanged.
> - **No new error codes.** Reuses `DELEGATION_INVALID` (422) / `VALIDATION_ERROR`
>   (400) / `APPROVAL_NOT_AUTHORIZED_APPROVER` (403).
>
> Clients that send neither field behave **identically to the v2.1 contract**
> (every grant is `GLOBAL`). v2.2-still-deferred: **per-route** scoping (a
> route-template identity does not yet exist), auto-absence, transitive chaining.

All endpoints:
- Require `Authorization: Bearer <token>` with `tenant_id ∈ {erp, *}`
  (RS256, GAP JWKS — [`iam-integration.md`](../../integration/iam-integration.md)).
  Cross-tenant → 403 `TENANT_FORBIDDEN`.
- Enforce the **authorization matrix + data scope** (E6, fail-closed) — the
  caller must hold `erp.write` for mutations / `erp.read` for reads, and the
  request's subject (`subjectType`/`subjectId`) must fall inside the caller's
  data scope; insufficient role → 403 `PERMISSION_DENIED`; subject outside the
  caller's organization subtree → 403 `DATA_SCOPE_FORBIDDEN`. Both checks happen
  inside the single application path (`AuthorizationPort.evaluate(...)`) BEFORE
  any repository call.
- **Transition authorization** (E3) — `approve` / `reject` may be performed
  **only by the request's `approverId`**; any other caller → 403
  `APPROVAL_NOT_AUTHORIZED_APPROVER`. `withdraw` may be performed only by the
  request's `submitterId`. Self-approval (`approverId == submitterId`) is
  refused at `submit` time → `APPROVAL_ROUTE_INVALID`.
- **Mutating** endpoints (`POST` create + the 4 transitions) require
  `Idempotency-Key: <client-generated>` (erp E4, transactional T1). Missing →
  400 `IDEMPOTENCY_KEY_REQUIRED`. Same key + identical payload → first stored
  response replayed (no re-transition). Same key + different payload → 409
  `IDEMPOTENCY_KEY_CONFLICT`. Key scope = `(idempotency_key, endpoint,
  tenant_id)`. Idempotent replay of a transition is **not** an
  `APPROVAL_ALREADY_FINALIZED` error — the original stored response is returned
  (E4 멱등).
- **Operator reason** — transition endpoints that record a reason
  (`approve` optional, `reject` / `withdraw` required) accept the reason in the
  request body **and** echo it via the `X-Operator-Reason` header for the
  audit trail (E4/E8). On `reject` / `withdraw` a missing/blank reason → 400
  `VALIDATION_ERROR`.
- Success envelope: `{ "data": <payload>, "meta": { "timestamp":
  "<ISO-8601>" } }`. List responses extend `meta` with
  `page` / `size` / `totalElements`.
- Error envelope: `{ "code": "<ERROR_CODE>", "message": "<human>",
  "details": <object?>, "timestamp": "<ISO-8601>" }`. Codes per
  [`platform/error-handling.md`](../../../../../platform/error-handling.md)
  erp section.
- **`@JsonInclude(NON_NULL)` absent-field convention** — nullable response
  fields (e.g. `reason`, `finalizedAt`, `submittedAt`) are **ABSENT** from the
  JSON when unset, never serialized as `null`. Consumers/tests assert absence
  (`.doesNotExist()`), not a null value — same convention as
  [`masterdata-api.md`](masterdata-api.md).
- No webhook / public-callback surface in v1 (erp is internal-only, E7).
  Only `/actuator/{health,info}` are unauthenticated.

---

## Common shapes

`ApprovalHistoryEntry` (one immutable audit row per transition, E4):
```json
{ "transition": "SUBMITTED|APPROVED|REJECTED|WITHDRAWN",
  "actor": "<JWT sub / approver / submitter id>",
  "at": "<ISO-8601 UTC>",
  "reason": "<≤512; ABSENT when none>" }
```

`ApprovalRequest` (detail payload — nullable fields ABSENT per NON_NULL):
```json
{ "id": "appr-9b1d4a8c-...",
  "status": "DRAFT|SUBMITTED|APPROVED|REJECTED|WITHDRAWN",
  "subjectType": "DEPARTMENT|EMPLOYEE",
  "subjectId": "dept-... | emp-...",
  "title": "2026 영업본부 조직개편 결재",
  "approverId": "emp-approver-...",
  "submitterId": "emp-submitter-...",
  "reason": "<creation reason; ABSENT when none>",
  "history": [ <ApprovalHistoryEntry>, ... ],
  "createdAt": "<ISO-8601 UTC>",
  "submittedAt": "<ISO-8601 UTC; ABSENT until SUBMITTED>",
  "finalizedAt": "<ISO-8601 UTC; ABSENT until APPROVED/REJECTED/WITHDRAWN>" }
```

`ApprovalSummary` (list / inbox item — trimmed, no `history`):
```json
{ "id", "status", "subjectType", "subjectId", "title",
  "approverId", "submitterId", "createdAt",
  "submittedAt": "<ABSENT until SUBMITTED>" }
```

`PageMeta` (list responses): `{ "page": 0, "size": 20, "totalElements":
123, "timestamp": "<ISO-8601>" }`.

---

## State transition × endpoint

| From state | Endpoint | To state | Allowed actor | On invalid current state |
|---|---|---|---|---|
| `DRAFT` | `POST /requests` (creates) | `DRAFT` | submitter (caller) | — |
| `DRAFT` | `POST /requests/{id}/submit` | `SUBMITTED` | submitter (caller) | 409 `APPROVAL_STATUS_TRANSITION_INVALID` |
| `SUBMITTED` | `POST /requests/{id}/approve` | `APPROVED` | `approverId` only | 409 `APPROVAL_STATUS_TRANSITION_INVALID` / `APPROVAL_ALREADY_FINALIZED` |
| `SUBMITTED` | `POST /requests/{id}/reject` | `REJECTED` | `approverId` only | 409 `APPROVAL_STATUS_TRANSITION_INVALID` / `APPROVAL_ALREADY_FINALIZED` |
| `SUBMITTED` | `POST /requests/{id}/withdraw` | `WITHDRAWN` | `submitterId` only | 409 `APPROVAL_STATUS_TRANSITION_INVALID` / `APPROVAL_ALREADY_FINALIZED` |

`APPROVED` / `REJECTED` / `WITHDRAWN` are **terminal** (immutable) — any
transition attempt on a terminal request → 409 `APPROVAL_ALREADY_FINALIZED`
(E3 — 완료/반려된 결재는 새 요청으로만 재처리). A transition from a state that
is non-terminal but simply not the legal predecessor (e.g. `approve` on a
`DRAFT`) → 409 `APPROVAL_STATUS_TRANSITION_INVALID`.

---

## Endpoints

### POST /api/erp/approval/requests

Create an approval request in initial state `DRAFT`. The caller is recorded as
`submitterId` (from the JWT `sub`). No master validation or route validation
occurs at create time — those are deferred to `submit` (E3).

**Headers**: `Authorization` (req), `Idempotency-Key` (req),
`Content-Type: application/json`

**Request**:
```json
{ "subjectType": "DEPARTMENT|EMPLOYEE",
  "subjectId": "dept-... | emp-...",
  "title": "2026 영업본부 조직개편 결재",
  "reason": "조직개편 사전 승인 요청",
  "approverId": "emp-approver-..." }
```
- `subjectType` — required, enum `DEPARTMENT | EMPLOYEE`
- `subjectId` — required, the referenced master aggregate id
- `title` — required, ≤ 256 chars
- `reason` — optional, ≤ 512 chars
- `approverId` — required, the single-stage approver (employee id)

**201**:
```json
{ "data": { "id": "appr-...", "status": "DRAFT",
            "subjectType": "DEPARTMENT", "subjectId": "dept-...",
            "title": "...", "approverId": "emp-approver-...",
            "submitterId": "emp-submitter-...",
            "reason": "...", "history": [],
            "createdAt": "<ISO-8601>" },
  "meta": { "timestamp": "<ISO-8601>" } }
```
(`submittedAt` / `finalizedAt` ABSENT in `DRAFT`.)

**Errors**: 400 `VALIDATION_ERROR`, 400 `IDEMPOTENCY_KEY_REQUIRED`,
409 `IDEMPOTENCY_KEY_CONFLICT`, 401 `UNAUTHORIZED`,
403 `PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN`, 403 `TENANT_FORBIDDEN`.

> Note: a bad `subjectId` / `approverId` is **not** validated here — create is a
> draft. Reference + route validation is enforced at `submit`.

### GET /api/erp/approval/requests

List requests, **scope-aware** (E6) — the caller sees requests whose subject is
inside their data scope, plus requests where they are `submitterId` or
`approverId`.

**Headers**: `Authorization` (req)

**Query**:
- `status` — optional, enum `DRAFT | SUBMITTED | APPROVED | REJECTED | WITHDRAWN`
- `role` — optional, enum `SUBMITTER | APPROVER` (filter to requests where the
  caller plays that role; absent = both)
- `page` — optional, default `0`
- `size` — optional, default `20`, ≤ `100`

**200**:
```json
{ "data": [ <ApprovalSummary>, ... ],
  "meta": { "page": 0, "size": 20, "totalElements": 42,
            "timestamp": "<ISO-8601>" } }
```

**Errors**: 400 `VALIDATION_ERROR` (bad status/role/page/size),
401 `UNAUTHORIZED`, 403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

### GET /api/erp/approval/requests/{id}

Detail, including the immutable transition `history` (E4).

**Headers**: `Authorization` (req)

**Path**: `id` — approval request aggregate id (UUID).

**200**:
```json
{ "data": <ApprovalRequest>,
  "meta": { "timestamp": "<ISO-8601>" } }
```

**Errors**: 404 `APPROVAL_REQUEST_NOT_FOUND`, 401 `UNAUTHORIZED`,
403 `PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN`, 403 `TENANT_FORBIDDEN`.

### POST /api/erp/approval/requests/{id}/submit

Transition `DRAFT → SUBMITTED`. At this point the service:
1. **Validates the referenced master** (E1) — `subjectId` must resolve to a
   live (non-retired) master of `subjectType` (resolved via the masterdata read
   API / consumed `erp.masterdata.*.changed.v1` projection, `asOf` = now). Miss
   → `APPROVAL_ROUTE_INVALID` (`details.cause = "subject_unresolved"`).
2. **Validates the route** (E3) — `approverId` must resolve to a live employee
   and must **not** equal `submitterId` (no self-approval). Self-approval →
   `APPROVAL_ROUTE_INVALID` (`details.cause = "self_approval"`); approver
   unresolvable / not an eligible approver → `APPROVAL_NOT_AUTHORIZED_APPROVER`.

**Headers**: `Authorization` (req), `Idempotency-Key` (req)

**Request**: `{}` (no body fields; the route was fixed at create time)

**200**:
```json
{ "data": { "id": "appr-...", "status": "SUBMITTED",
            "submittedAt": "<ISO-8601>",
            "history": [ { "transition": "SUBMITTED", "actor": "emp-submitter-...",
                           "at": "<ISO-8601>" } ], ... },
  "meta": { "timestamp": "<ISO-8601>" } }
```

**Errors**: 404 `APPROVAL_REQUEST_NOT_FOUND`,
409 `APPROVAL_STATUS_TRANSITION_INVALID` (not in `DRAFT`),
409 `APPROVAL_ALREADY_FINALIZED` (already terminal),
422 `APPROVAL_ROUTE_INVALID` (self-approval / unresolved subject),
403 `APPROVAL_NOT_AUTHORIZED_APPROVER` (approver ineligible),
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
401 `UNAUTHORIZED`, 403 `PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN`,
403 `TENANT_FORBIDDEN`.

### POST /api/erp/approval/requests/{id}/approve

Transition `SUBMITTED → APPROVED`. **Only the request's `approverId`** may call
this (E3) — any other caller → 403 `APPROVAL_NOT_AUTHORIZED_APPROVER`.

**Headers**: `Authorization` (req), `Idempotency-Key` (req),
`X-Operator-Reason` (optional — echoes the body `reason`)

**Request**: `{ "reason": "<optional, ≤512>" }`

**200**:
```json
{ "data": { "id": "appr-...", "status": "APPROVED",
            "finalizedAt": "<ISO-8601>",
            "history": [ ..., { "transition": "APPROVED", "actor": "emp-approver-...",
                                "at": "<ISO-8601>", "reason": "<ABSENT when none>" } ],
            ... },
  "meta": { "timestamp": "<ISO-8601>" } }
```

**Errors**: 404 `APPROVAL_REQUEST_NOT_FOUND`,
403 `APPROVAL_NOT_AUTHORIZED_APPROVER` (caller ≠ `approverId`),
409 `APPROVAL_STATUS_TRANSITION_INVALID` (not in `SUBMITTED`),
409 `APPROVAL_ALREADY_FINALIZED` (already terminal),
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
401 `UNAUTHORIZED`, 403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

### POST /api/erp/approval/requests/{id}/reject

Transition `SUBMITTED → REJECTED`. **Only `approverId`** (E3). Reason
**REQUIRED** (E4 — 반려 시 사유 필수).

**Headers**: `Authorization` (req), `Idempotency-Key` (req),
`X-Operator-Reason` (echoes the body `reason`)

**Request**: `{ "reason": "<required, ≤512>" }`

**200**:
```json
{ "data": { "id": "appr-...", "status": "REJECTED",
            "finalizedAt": "<ISO-8601>",
            "history": [ ..., { "transition": "REJECTED", "actor": "emp-approver-...",
                                "at": "<ISO-8601>", "reason": "예산 근거 부족" } ],
            ... },
  "meta": { "timestamp": "<ISO-8601>" } }
```

**Errors**: 400 `VALIDATION_ERROR` (reason missing/blank),
404 `APPROVAL_REQUEST_NOT_FOUND`,
403 `APPROVAL_NOT_AUTHORIZED_APPROVER` (caller ≠ `approverId`),
409 `APPROVAL_STATUS_TRANSITION_INVALID` (not in `SUBMITTED`),
409 `APPROVAL_ALREADY_FINALIZED`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
401 `UNAUTHORIZED`, 403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

### POST /api/erp/approval/requests/{id}/withdraw

Transition `SUBMITTED → WITHDRAWN`. **Only the request's `submitterId`** may
withdraw their own submitted request. Reason **REQUIRED** (E4).

**Headers**: `Authorization` (req), `Idempotency-Key` (req),
`X-Operator-Reason` (echoes the body `reason`)

**Request**: `{ "reason": "<required, ≤512>" }`

**200**:
```json
{ "data": { "id": "appr-...", "status": "WITHDRAWN",
            "finalizedAt": "<ISO-8601>",
            "history": [ ..., { "transition": "WITHDRAWN", "actor": "emp-submitter-...",
                                "at": "<ISO-8601>", "reason": "기안 내용 수정 필요" } ],
            ... },
  "meta": { "timestamp": "<ISO-8601>" } }
```

**Errors**: 400 `VALIDATION_ERROR` (reason missing/blank),
404 `APPROVAL_REQUEST_NOT_FOUND`,
403 `APPROVAL_NOT_AUTHORIZED_APPROVER` (caller ≠ `submitterId` — the
not-authorized code is reused for the submitter-only constraint; `details.role
= "submitter"` discriminates),
409 `APPROVAL_STATUS_TRANSITION_INVALID` (not in `SUBMITTED`),
409 `APPROVAL_ALREADY_FINALIZED`,
400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
401 `UNAUTHORIZED`, 403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

### GET /api/erp/approval/inbox

The current approver's **pending** queue — `SUBMITTED` requests whose
`approverId` equals the caller (basic role/approver-based selection). First
increment is intentionally minimal: no due-date / priority / delegation
filtering (v2 deferred).

**Headers**: `Authorization` (req)

**Query**:
- `page` — optional, default `0`
- `size` — optional, default `20`, ≤ `100`

**200**:
```json
{ "data": [ <ApprovalSummary with status="SUBMITTED">, ... ],
  "meta": { "page": 0, "size": 20, "totalElements": 7,
            "timestamp": "<ISO-8601>" } }
```

**Errors**: 400 `VALIDATION_ERROR` (bad page/size), 401 `UNAUTHORIZED`,
403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

---

## Error code → HTTP status (approval)

| Code | HTTP | Trigger |
|---|---|---|
| `VALIDATION_ERROR` | 400 | bean-validation failure (incl. missing required `reason` on reject/withdraw) |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | mutating call without header (Platform-Common Transactional Trait; create + 4 transitions) |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | same key, different payload |
| `APPROVAL_REQUEST_NOT_FOUND` | 404 | unknown approval request id |
| `APPROVAL_STATUS_TRANSITION_INVALID` | 409 | transition from a state that is not the legal (non-terminal) predecessor (E3) |
| `APPROVAL_ALREADY_FINALIZED` | 409 | transition attempted on a terminal request (`APPROVED`/`REJECTED`/`WITHDRAWN`) (E3) |
| `APPROVAL_NOT_AUTHORIZED_APPROVER` | 403 | `approve`/`reject` caller ≠ `approverId`, or `withdraw` caller ≠ `submitterId`, or approver ineligible at submit (E3) |
| `APPROVAL_ROUTE_INVALID` | 422 | route construction error at submit: self-approval (`approverId == submitterId`) or unresolved subject master (E3/E1) |
| `PERMISSION_DENIED` | 403 | required role/scope not present (E6) |
| `DATA_SCOPE_FORBIDDEN` | 403 | request subject outside caller's data scope (E6) |
| `TENANT_FORBIDDEN` | 403 | `tenant_id ∉ {erp, *}` |
| `UNAUTHORIZED` | 401 | missing / invalid / expired JWT (Platform-Common Authentication) |
| `EXTERNAL_TRAFFIC_REJECTED` | 403 | external (non-internal-network) ingress (E7) — primarily enforced at Traefik / network layer; application-layer fallback surface |

The 5 approval-specific codes (`APPROVAL_REQUEST_NOT_FOUND`,
`APPROVAL_STATUS_TRANSITION_INVALID`, `APPROVAL_NOT_AUTHORIZED_APPROVER`,
`APPROVAL_ROUTE_INVALID`, `APPROVAL_ALREADY_FINALIZED`) are defined verbatim in
[`rules/domains/erp.md`](../../../../../rules/domains/erp.md) § Standard Error
Codes → Approval Workflow, and are registered in
[`platform/error-handling.md`](../../../../../platform/error-handling.md) under
the `Approval Workflow  [domain: erp]` section (this PR appends them). The
shared codes (`VALIDATION_ERROR`, `IDEMPOTENCY_*`, `PERMISSION_DENIED`,
`DATA_SCOPE_FORBIDDEN`, `TENANT_FORBIDDEN`, `UNAUTHORIZED`,
`EXTERNAL_TRAFFIC_REJECTED`) are already registered by the masterdata /
authorization sections.

---

## Auth

Identical to [`masterdata-api.md`](masterdata-api.md) and
[`iam-integration.md`](../../integration/iam-integration.md):

- **Authentication** — `Authorization: Bearer <RS256 JWT>` verified against GAP
  JWKS. `tenant_id ∈ {erp, *}`; else 403 `TENANT_FORBIDDEN`. `entitlement-trust`
  dual-accept applies wherever the platform-console operator token reaches this
  service (same `RoleScopeAuthorizationAdapter` semantics as the read surfaces).
- **Authorization (E6, fail-closed)** — reads require `erp.read`; mutations
  require `erp.write`. The authorization matrix + data scope are evaluated on
  every request before any repository access.
- **Transition authorization (E3)** — beyond the scope check, `approve`/`reject`
  require caller == `approverId`, `withdraw` requires caller == `submitterId`,
  and `submit` refuses self-approval — all surfaced as
  `APPROVAL_NOT_AUTHORIZED_APPROVER` / `APPROVAL_ROUTE_INVALID`.
- **internal-only (E7)** — no public/self-signup/anonymous path; external
  ingress rejected at the network layer (`EXTERNAL_TRAFFIC_REJECTED` fallback).
- **Audit (E4/E8)** — every transition writes an immutable `history` /
  `audit_log` row (actor + timestamp + transition + before/after + reason)
  in the same transaction as the state change (see § Outbox in
  [`erp-approval-events.md`](../events/erp-approval-events.md)).

---

## v2 deferred

The following are **explicitly out of scope** for this first increment and
deferred to v2 (ADR-MONO-016 § D3 v2 Service Map; `rules/domains/erp.md` E3 UL):

- **`IN_REVIEW` state** — the full UL state machine is
  `DRAFT → SUBMITTED → (IN_REVIEW →) APPROVED`. The first increment collapses
  `SUBMITTED → APPROVED` directly (single stage); `IN_REVIEW` is v2.
- **Multi-stage routes (결재선 1~N단계)** — the first increment fixes exactly
  one `approverId` per request. Ordered multi-stage routing is v2.
- **Delegation / 대결 (delegate approver)** — absentee delegation is v2;
  the first increment recognizes only the literal `approverId`.
- **Inbox filtering** — due-date, priority, processed (기결) history, and
  delegated-to-me views are v2. The first increment's `/inbox` returns only
  the caller's own `SUBMITTED` pending items.
- **Notification fan-out** — `erp.approval.*` events are published in this
  increment (forward interface), but `notification-service` consumes them in
  v2 (see [`erp-approval-events.md`](../events/erp-approval-events.md)).

When any of the above lands, this contract is amended additively (new states /
fields / endpoints), and breaking shape changes follow the platform versioning
policy (`.v2` topic + dual-publish window for events; `/api/v2/...` for HTTP).
