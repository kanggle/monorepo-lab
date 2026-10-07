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
  `page` / `size` / `totalElements` / `totalPages` (ADR-MONO-058 § D3 —
  `com.example.common.page.PageResult` adoption added `totalPages`, additive).
- Error envelope: `{ "code": "<ERROR_CODE>", "message": "<human>",
  "details": <object?>, "timestamp": "<ISO-8601>" }`. Codes per
  [`platform/error-handling.md`](../../../../../platform/error-handling.md)
  erp section. `details` follows the `@JsonInclude(NON_NULL)` absent-field
  convention — it is **omitted** for every code that does not document it, never
  serialized as `null`. The one code that carries it today is
  `MASTERDATA_REFERENCE_VIOLATION` (see below); `message` is human-readable prose
  and is **not** a machine-matched value.
- **`MASTERDATA_REFERENCE_VIOLATION` → `details` shape** (TASK-ERP-BE-038 —
  the field was documented here from the start but was not populated by the
  service until then):
  ```json
  { "code": "MASTERDATA_REFERENCE_VIOLATION",
    "message": "Department dept-1 is referenced by active child departments, employees — cannot retire",
    "details": { "referencers": ["childDepartments", "employees", "costCenters"] },
    "timestamp": "<ISO-8601>" }
  ```
  `details.referencers` is a string array of the referencer **kinds** that are
  currently blocking the retire, drawn from the closed set
  `childDepartments` / `employees` / `costCenters`, in that order. **Every** kind is
  evaluated (no short-circuit), so the array lists all blockers at once rather than
  only the first one found. JobGrade / CostCenter retire can only be blocked by
  `employees`, so their array always has exactly one element.
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
123, "totalPages": 7, "timestamp": "<ISO-8601>" }`. `totalPages =
ceil(totalElements / size)` (0 when `totalElements == 0`).

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
`details.referencers` enumerates every blocking referencer kind — shape at the
top of this file), 400 `IDEMPOTENCY_KEY_REQUIRED`,
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

## Employee ↔ IAM account link (TASK-MONO-774 — `ADR-MONO-080` D7 = E1, additive)

> **v1.1 AMENDMENT (contract-first; 구현은 후속 슬라이스).** 직원 마스터가 그 직원으로
> 로그인하는 IAM 계정을 안다. 결재(`approval-api.md` § v2.4)와 알림(`notification-api.md`
> § v1.1)은 «내 `sub` → 나와 연결된 직원» 을 이 표면으로 푼다.
>
> 🔴 **연결을 누가 쓰나 = ⓑ 인사 제안 + 본인 수락** (소유자 결정 2026-10-08 UTC,
> `TASK-MONO-774` AC-0). `erp.write` 보유자(직원의 부서 data scope 안 — 다른 직원 쓰기와 같은
> 규칙)가 «직원 E ↔ 계정 A» 를 **제안**하고, 계정 A 의 주인(JWT `sub` = A)이 **수락**해야만
> `accountId` 가 쓰인다. 🔴 **두 사람 규칙**: 제안자와 수락자는 달라야 한다 — 같으면
> 인사 권한자가 CFO 직원에 자기 계정을 제안하고 스스로 수락해 CFO 의 결재함을 가져간다(ⓑ 가
> ⓐ 로 무너진다).

### Data model (invariants)

- `employees.account_id` — NULL 허용. **테넌트 안 유니크** `(tenant_id, account_id)`: 한
  계정은 한 테넌트에서 직원 **최대 하나**에 연결된다(NULL 은 여럿 허용). IAM 으로의 FK 없음
  (교차 컨텍스트 참조).
- 퇴사(`RETIRED`) 직원의 연결은 **남긴다** — 결재 쪽 E3 가 그 직원을 승인자로 받지 않는다.
  계정 삭제·잠금도 연결을 지우지 않는다 — 그 계정은 토큰을 못 받으므로 결재함에 들어올 수 없다.
- `employee_account_link_proposals` — 제안 한 건 = 한 행. 열: `id`, `tenant_id`,
  `employee_id`, `account_id`, `status` (`PENDING|ACCEPTED|DECLINED|REVOKED`),
  `proposed_by`(제안자 JWT `sub`), `proposed_at`, `reason?`, `decided_by?`, `decided_at?`,
  `decision_reason?`, `version`. **직원당 `PENDING` 최대 하나**(DB 유니크로 강제 — MySQL 에
  부분 인덱스가 없으므로 `status='PENDING'` 일 때만 값을 갖는 생성 열 + 유니크).
  `PENDING` 이 아닌 행은 불변(감사 이력).
- 모든 상태 변화는 append-only `audit_log` 행 + 직원 쪽 변화(`accountId` 설정·해제)는
  `erp.masterdata.employee.changed.v1` 을 같은 Tx 에서 낸다(E8 · A7 — 기존 규약 그대로).

### Employee 응답의 `accountId`

- `GET /employees` 목록 원소와 `GET /employees/{id}` 상세에 **`accountId`** 추가 —
  연결되지 않았으면 **ABSENT**(`@JsonInclude(NON_NULL)`).
- `POST /employees` · `PATCH /employees/{id}` 는 `accountId` 를 **받지 않는다** — 연결의
  쓰기 경로는 아래 «수락» 하나뿐이다(요청 DTO 에 그 필드가 없다 — 실려 와도 쓰이지 않는다.
  이 서비스의 기존 규약대로 모르는 필드는 무시된다).

### GET /api/erp/masterdata/employees/me

호출자 `sub` 와 연결된 직원(호출자 토큰의 테넌트). 결재·알림 서비스가 호출자 토큰을 그대로
전달해 «내 직원 id» 를 푼다.

- Auth: `erp.read`. **부서 data scope 를 적용하지 않는다** — 자기 자신의 연결을 읽는 것이다.
- **200**: Employee 상세 봉투(상태 무관 — `RETIRED` 도 돌려준다. 판정은 호출자 몫).
- **404** `MASTERDATA_NOT_FOUND` — 호출자 `sub` 와 연결된 직원이 이 테넌트에 없다(«연결 없음»
  은 답이지 장애가 아니다).

### GET /api/erp/masterdata/employees/{id}/approver-ref

결재선 해소용 최소 조회 — `{ "id", "status", "accountId"? }` (이름 등 PII 없음).

- Auth: `erp.read`. **부서 data scope 를 적용하지 않는다** — 승인자는 보통 상신자의 data
  scope 밖(상위 부서)에 있고, 기존 `GET /employees/{id}` 는 그 직원의 부서로 scope 를 건다
  (`MasterdataApplicationService` 의 employee detail read). 그 상세로 E3 를 풀면 정상적인
  결재선이 403 으로 «승인자 확인 불가» 가 된다.
- **404** `MASTERDATA_NOT_FOUND` — 그런 직원이 없다.

### POST /api/erp/masterdata/employees/{id}/account-link-proposals

제안. **Headers**: `Idempotency-Key` (req).

**Request**: `{ "accountId": "<IAM account UUID>", "reason": "<≤256, optional>" }`

검사 순서(앞이 실패하면 뒤는 안 본다):
1. Auth — `erp.write` + 직원 부서 data scope(다른 직원 쓰기와 같은 `AuthorizationPort` 경로).
2. 직원 존재 → 없으면 404 `MASTERDATA_NOT_FOUND`. `ACTIVE` 아님 → 422
   `EMPLOYEE_LINK_INVALID` (`details.cause = "employee_not_active"`).
3. `accountId == 호출자 sub` → 403 `EMPLOYEE_LINK_SELF_ACCEPT` (두 사람 규칙 — 그 제안은
   어차피 수락될 수 없으므로 일찍 거절한다. 권위 있는 검사는 «수락» 쪽이다).
4. 직원이 이미 연결됨 / 계정이 이 테넌트의 다른 직원에 연결됨 / 이 직원에 `PENDING` 제안이
   이미 있음 → 409 `EMPLOYEE_LINK_CONFLICT` (`details.cause ∈ { "employee_already_linked",
   "account_already_linked", "proposal_pending" }`).

🔵 **제안 시점에 IAM 계정 존재를 확인하지 않는다** (소유자 결정 2026-10-08 UTC,
`TASK-MONO-774` AC-0). `accountId` 는 형식만 본다(빈 값 · 64자 초과 → 400
`VALIDATION_ERROR`). 연결이 실제로 쓰이는 순간(수락)에는 IAM 이 서명한 토큰의 `sub` 가
계정 실재의 증거이고, 존재하지 않는 계정 앞 제안은 아무도 수락할 수 없어 «철회» 로만
치운다. erp 는 IAM 워크로드 자격을 갖지 않는다(FK 없음 · 동기 호출 없음).

**201**: `EmployeeAccountLinkProposal` —
`{ "id", "employeeId", "accountId", "status": "PENDING", "proposedBy", "proposedAt", "reason"? }`.

### GET /api/erp/masterdata/account-link-proposals/mine

호출자 `sub` 앞으로 온 `PENDING` 제안(수락 화면). Auth: `erp.read`, data scope 없음(자기 앞
제안). **200**: list 봉투(`EmployeeAccountLinkProposal[]` + 직원 표시용 `employeeName`,
`employeeNumber`).

### GET /api/erp/masterdata/employees/{id}/account-link-proposals

한 직원의 제안 이력(전 상태). Auth: `erp.read` + 직원 부서 data scope. **200**: list 봉투.

### POST /api/erp/masterdata/account-link-proposals/{proposalId}/accept

수락 — 이 호출이 `employees.account_id` 를 쓰는 **유일한** 경로다. **Headers**:
`Idempotency-Key` (req). **Request**: `{}`.

1. 제안 없음 → 404 `EMPLOYEE_LINK_PROPOSAL_NOT_FOUND`.
2. 호출자 `sub ≠ proposal.accountId` → 403 `EMPLOYEE_LINK_NOT_ADDRESSEE`.
3. 🔴 호출자 `sub == proposal.proposedBy` → 403 `EMPLOYEE_LINK_SELF_ACCEPT` (두 사람 규칙).
4. `status ≠ PENDING` → 409 `EMPLOYEE_LINK_CONFLICT` (`details.cause = "proposal_not_pending"`).
5. 직원이 `ACTIVE` 아님 → 422 `EMPLOYEE_LINK_INVALID` (`employee_not_active`). 직원이 그새
   연결됨 / 계정이 그새 다른 직원에 연결됨 → 409 `EMPLOYEE_LINK_CONFLICT`
   (`employee_already_linked` / `account_already_linked`).

성공: `employees.account_id = accountId` · 제안 `ACCEPTED`(`decidedBy = 호출자 sub`) — 한 Tx.
**200**: 직원 상세 봉투(`accountId` 포함).

🔵 수락에 부서 data scope 를 걸지 않는다 — 수락자는 인사 권한자가 아니라 **계정 주인**이다.
`erp.read` 이상(이 테넌트의 erp 참여자)은 요구한다.

🔵 ADR-MONO-080 R1(회사 권한이 붙는 쓰기에 인증된 이메일)은 수락에 **직접 걸지 않는다**
(소유자 결정 2026-10-08 UTC) — 간접 게이트로 충분하다: 수락자는 이미 이 테넌트의 erp
참여자여야 하고(`erp.read` 이상), 그 운영자 권한이 붙을 때 `ADR-MONO-080` D3 가 인증된
이메일을 본다. 🔴 그 간접 게이트는 `TASK-MONO-772` 가 `done/` 이 된 뒤에 완성된다.

### POST /api/erp/masterdata/account-link-proposals/{proposalId}/decline

거절 — 계정 주인만(2 와 같은 403). `{ "reason": "<≤256, optional>" }`. `PENDING` 아님 → 409
`EMPLOYEE_LINK_CONFLICT` (`proposal_not_pending`). **200**: 제안(`DECLINED`).

### POST /api/erp/masterdata/account-link-proposals/{proposalId}/revoke

철회 — `erp.write` + 직원 부서 data scope(제안자 본인이 아니어도 된다). `{ "reason":
"<≤256, required>" }`. `PENDING` 아님 → 409 `EMPLOYEE_LINK_CONFLICT`
(`proposal_not_pending`). **200**: 제안(`REVOKED`).

### POST /api/erp/masterdata/employees/{id}/account-link/unlink

연결 해제 — `erp.write` + 직원 부서 data scope, **또는** 연결된 계정 주인 본인.
`{ "reason": "<≤256, required>" }`. 연결이 없으면 409 `EMPLOYEE_LINK_CONFLICT`
(`details.cause = "not_linked"`). **200**: 직원 상세 봉투(`accountId` ABSENT). 다시 연결하려면
새 제안 → 수락.

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
reference this grade; `details.referencers = ["employees"]`),
400 `IDEMPOTENCY_KEY_REQUIRED`,
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
reference this cost center; `details.referencers = ["employees"]`),
400 `IDEMPOTENCY_KEY_REQUIRED`,
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
| `ILLEGAL_STATE` | 422 | aggregate invariant violated at the controller boundary — the unclassified `IllegalStateException` fallback (Platform-Common General). Prefer a domain code above where the failure is a known one |
| `EMPLOYEE_LINK_PROPOSAL_NOT_FOUND` | 404 | unknown account-link proposal id (TASK-MONO-774) |
| `EMPLOYEE_LINK_CONFLICT` | 409 | link state collision — `details.cause ∈ { employee_already_linked, account_already_linked, proposal_pending, proposal_not_pending, not_linked }` (TASK-MONO-774) |
| `EMPLOYEE_LINK_INVALID` | 422 | link target not eligible — the employee is not `ACTIVE` (`details.cause = "employee_not_active"`) (TASK-MONO-774) |
| `EMPLOYEE_LINK_NOT_ADDRESSEE` | 403 | accept/decline by a caller whose `sub` is not the proposal's `accountId` (TASK-MONO-774) |
| `EMPLOYEE_LINK_SELF_ACCEPT` | 403 | two-person rule — the acceptor's `sub` equals the proposer's `sub` (or a proposal names the proposer's own account) (TASK-MONO-774) |

> `IDEMPOTENCY_STORE_UNAVAILABLE` (503) is **v1-emittable but rare** — v1 uses
> the DB-table primary inside the mutation Tx (see architecture.md
> § Idempotency), so the common path never raises it; however the fail-CLOSED
> store (`DbIdempotencyStore`) does throw it in v1 on the claim path's
> `DataAccessException` (store-down) or unresolved-insert-race branch. The same
> code is also reserved for the future Redis-primary swap.

All erp codes registered in `platform/error-handling.md` under the
`Master Data  [domain: erp]` and `Authorization  [domain: erp]` sections
(this PR appends them). The five `EMPLOYEE_LINK_*` codes (TASK-MONO-774) are
registered there and in `rules/domains/erp.md` § Master Data before any emitter
exists (contract-first).
