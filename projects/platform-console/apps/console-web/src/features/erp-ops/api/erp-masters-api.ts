import {
  DepartmentListResponseSchema,
  type DepartmentListResponse,
  DepartmentDetailResponseSchema,
  type Department,
  EmployeeListResponseSchema,
  type EmployeeListResponse,
  EmployeeDetailResponseSchema,
  type Employee,
  JobGradeListResponseSchema,
  type JobGradeListResponse,
  JobGradeDetailResponseSchema,
  type JobGrade,
  CostCenterListResponseSchema,
  type CostCenterListResponse,
  CostCenterDetailResponseSchema,
  type CostCenter,
  BusinessPartnerListResponseSchema,
  type BusinessPartnerListResponse,
  BusinessPartnerDetailResponseSchema,
  type BusinessPartner,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateDepartmentInput,
  type UpdateDepartmentInput,
  type RetireDepartmentInput,
  type MoveDepartmentParentInput,
  type CreateEmployeeInput,
  type UpdateEmployeeInput,
  type CreateJobGradeInput,
  type UpdateJobGradeInput,
  type CreateCostCenterInput,
  type UpdateCostCenterInput,
  type CreateBusinessPartnerInput,
  type UpdateBusinessPartnerInput,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from './types';
import { callErp } from './erp-client';

// ---------------------------------------------------------------------------
// query-string helpers — `asOf` is the E3 first-class thread-through;
// `active` / `page` / `size` / per-master filters are passthroughs.
// ---------------------------------------------------------------------------

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(
        ERP_MAX_PAGE_SIZE,
        Math.max(1, size ?? ERP_DEFAULT_PAGE_SIZE),
      ),
    ),
  );
}

/**
 * Builds the query string for any erp list read. CORE E3 invariant:
 * when `asOf` is supplied it threads through to the producer
 * verbatim (the producer returns the state-at-that-instant; the
 * console NEVER substitutes current state). When `asOf` is omitted
 * the producer resolves to "today" (UTC) per `masterdata-api.md`.
 */
function listQs(params: ErpListQueryParams): string {
  const qs = new URLSearchParams();
  // E3 thread-through — verbatim, no transformation. This is the
  // single point that pins the asOf-pass-through invariant for
  // every list call.
  if (params.asOf) qs.set('asOf', params.asOf);
  if (params.active !== undefined) qs.set('active', String(params.active));
  if (params.filters) {
    for (const [k, v] of Object.entries(params.filters)) {
      if (v !== undefined && v !== null && v !== '') qs.set(k, v);
    }
  }
  pageParams(qs, params.page, params.size);
  return qs.toString();
}

/**
 * Builds the query string for any erp detail read. Only `asOf` is
 * producer-defined; this is the same E3 thread-through invariant
 * as `listQs`.
 */
function detailQs(params: ErpDetailQueryParams): string {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  const s = qs.toString();
  return s ? `?${s}` : '';
}

// ---------------------------------------------------------------------------
// 1. departments — list + detail
//   GET /api/erp/masterdata/departments
//   GET /api/erp/masterdata/departments/{id}
// ---------------------------------------------------------------------------

export async function listDepartments(
  params: ErpListQueryParams = {},
): Promise<DepartmentListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments?${listQs(params)}`,
      logPath: '/api/erp/masterdata/departments',
    },
    (json) => DepartmentListResponseSchema.parse(json),
  );
}

export async function getDepartmentById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}${detailQs(params)}`,
      // confidential — the log path carries no record id.
      logPath: '/api/erp/masterdata/departments/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return DepartmentDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// 1b. departments — WRITE PILOT (TASK-PC-FE-046 / § 2.4.8 *Department
//   write binding (PILOT)*). The FIRST erp console write. Consumes the
//   UNCHANGED producer `masterdata-api.md` § Department mutations:
//     POST  /api/erp/masterdata/departments              (create)
//     PATCH /api/erp/masterdata/departments/{id}          (update)
//     POST  /api/erp/masterdata/departments/{id}/retire   (retire)
//     POST  /api/erp/masterdata/departments/{id}/move-parent (move-parent)
//   Each carries an `Idempotency-Key` (generated console-side per
//   attempt). `reason` rides in the BODY only where the producer has a
//   slot (retire required / move-parent optional) — NEVER an
//   `X-Operator-Reason` header (erp does not read it). The other four
//   masters have NO write functions (a test pins that absence).
// ---------------------------------------------------------------------------

/** Parses a department mutation response envelope (`{ data, meta }`)
 *  into the `Department` — same tolerant shape as `getDepartmentById`. */
function parseDepartmentData(json: unknown): Department {
  const env = (json ?? {}) as { data?: unknown };
  return DepartmentDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

export async function createDepartment(
  input: CreateDepartmentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: '/api/erp/masterdata/departments',
      logPath: '/api/erp/masterdata/departments',
      method: 'POST',
      idempotencyKey,
      body: {
        code: input.code,
        name: input.name,
        ...(input.parentId !== undefined ? { parentId: input.parentId } : {}),
        ...(input.effectiveFrom ? { effectiveFrom: input.effectiveFrom } : {}),
      },
    },
    parseDepartmentData,
  );
}

export async function updateDepartment(
  id: string,
  input: UpdateDepartmentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/departments/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: {
        ...(input.name !== undefined ? { name: input.name } : {}),
        ...(input.effectiveFrom ? { effectiveFrom: input.effectiveFrom } : {}),
      },
    },
    parseDepartmentData,
  );
}

export async function retireDepartment(
  id: string,
  input: RetireDepartmentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/departments/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason: input.reason },
    },
    parseDepartmentData,
  );
}

export async function moveDepartmentParent(
  id: string,
  input: MoveDepartmentParentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}/move-parent`,
      logPath: '/api/erp/masterdata/departments/{id}/move-parent',
      method: 'POST',
      idempotencyKey,
      body: {
        newParentId: input.newParentId,
        effectiveFrom: input.effectiveFrom,
        ...(input.reason ? { reason: input.reason } : {}),
      },
    },
    parseDepartmentData,
  );
}

// ---------------------------------------------------------------------------
// 2. employees — list + detail
//   GET /api/erp/masterdata/employees
//   GET /api/erp/masterdata/employees/{id}
// ---------------------------------------------------------------------------

export async function listEmployees(
  params: ErpListQueryParams = {},
): Promise<EmployeeListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees?${listQs(params)}`,
      logPath: '/api/erp/masterdata/employees',
    },
    (json) => EmployeeListResponseSchema.parse(json),
  );
}

export async function getEmployeeById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/employees/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return EmployeeDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// 3. job-grades — list (producer orders by displayOrder asc) + detail
//   GET /api/erp/masterdata/job-grades
//   GET /api/erp/masterdata/job-grades/{id}
// ---------------------------------------------------------------------------

export async function listJobGrades(
  params: ErpListQueryParams = {},
): Promise<JobGradeListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades?${listQs(params)}`,
      logPath: '/api/erp/masterdata/job-grades',
    },
    (json) => JobGradeListResponseSchema.parse(json),
  );
}

export async function getJobGradeById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<JobGrade> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/job-grades/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return JobGradeDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// 4. cost-centers — list + detail
//   GET /api/erp/masterdata/cost-centers
//   GET /api/erp/masterdata/cost-centers/{id}
// ---------------------------------------------------------------------------

export async function listCostCenters(
  params: ErpListQueryParams = {},
): Promise<CostCenterListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers?${listQs(params)}`,
      logPath: '/api/erp/masterdata/cost-centers',
    },
    (json) => CostCenterListResponseSchema.parse(json),
  );
}

export async function getCostCenterById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<CostCenter> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/cost-centers/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return CostCenterDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// 5. business-partners — list + detail (confidential paymentTerms;
//   producer enforces F7-equivalent masking — console never logs)
//   GET /api/erp/masterdata/business-partners
//   GET /api/erp/masterdata/business-partners/{id}
// ---------------------------------------------------------------------------

export async function listBusinessPartners(
  params: ErpListQueryParams = {},
): Promise<BusinessPartnerListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners?${listQs(params)}`,
      logPath: '/api/erp/masterdata/business-partners',
    },
    (json) => BusinessPartnerListResponseSchema.parse(json),
  );
}

export async function getBusinessPartnerById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<BusinessPartner> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/business-partners/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return BusinessPartnerDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// 6. additional masters WRITE (TASK-PC-FE-048 — employees / job-grades /
//   cost-centers / business-partners create/update/retire, generalising the
//   department pilot to all 5 masters). Same hardened callErp (method/body/
//   idempotencyKey); `reason` rides in the body on retire only (NEVER an
//   X-Operator-Reason header); credential = IAM OIDC domain-facing token
//   (unchanged). Producer `masterdata-api.md` § <master> is canonical.
// ---------------------------------------------------------------------------

function parseEmployeeData(json: unknown): Employee {
  const env = (json ?? {}) as { data?: unknown };
  return EmployeeDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}
function parseJobGradeData(json: unknown): JobGrade {
  const env = (json ?? {}) as { data?: unknown };
  return JobGradeDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}
function parseCostCenterData(json: unknown): CostCenter {
  const env = (json ?? {}) as { data?: unknown };
  return CostCenterDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}
function parseBusinessPartnerData(json: unknown): BusinessPartner {
  const env = (json ?? {}) as { data?: unknown };
  return BusinessPartnerDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

/** Drops undefined keys so optional fields are omitted from the wire body
 *  (a `PATCH` with `{ name: undefined }` would otherwise serialize nothing
 *  meaningful; the producer wants only the changed fields). */
function compact<T extends Record<string, unknown>>(obj: T): Partial<T> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v !== undefined) out[k] = v;
  }
  return out as Partial<T>;
}

// employees ------------------------------------------------------------------
export async function createEmployee(
  input: CreateEmployeeInput,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: '/api/erp/masterdata/employees',
      logPath: '/api/erp/masterdata/employees',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseEmployeeData,
  );
}
export async function updateEmployee(
  id: string,
  input: UpdateEmployeeInput,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/employees/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseEmployeeData,
  );
}
export async function retireEmployee(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/employees/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseEmployeeData,
  );
}

// job-grades -----------------------------------------------------------------
export async function createJobGrade(
  input: CreateJobGradeInput,
  idempotencyKey: string,
): Promise<JobGrade> {
  return callErp(
    {
      path: '/api/erp/masterdata/job-grades',
      logPath: '/api/erp/masterdata/job-grades',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseJobGradeData,
  );
}
export async function updateJobGrade(
  id: string,
  input: UpdateJobGradeInput,
  idempotencyKey: string,
): Promise<JobGrade> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/job-grades/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseJobGradeData,
  );
}
export async function retireJobGrade(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<JobGrade> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/job-grades/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseJobGradeData,
  );
}

// cost-centers ---------------------------------------------------------------
export async function createCostCenter(
  input: CreateCostCenterInput,
  idempotencyKey: string,
): Promise<CostCenter> {
  return callErp(
    {
      path: '/api/erp/masterdata/cost-centers',
      logPath: '/api/erp/masterdata/cost-centers',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseCostCenterData,
  );
}
export async function updateCostCenter(
  id: string,
  input: UpdateCostCenterInput,
  idempotencyKey: string,
): Promise<CostCenter> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/cost-centers/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseCostCenterData,
  );
}
export async function retireCostCenter(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<CostCenter> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/cost-centers/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseCostCenterData,
  );
}

// business-partners ----------------------------------------------------------
export async function createBusinessPartner(
  input: CreateBusinessPartnerInput,
  idempotencyKey: string,
): Promise<BusinessPartner> {
  return callErp(
    {
      path: '/api/erp/masterdata/business-partners',
      logPath: '/api/erp/masterdata/business-partners',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseBusinessPartnerData,
  );
}
export async function updateBusinessPartner(
  id: string,
  input: UpdateBusinessPartnerInput,
  idempotencyKey: string,
): Promise<BusinessPartner> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/business-partners/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseBusinessPartnerData,
  );
}
export async function retireBusinessPartner(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<BusinessPartner> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/business-partners/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseBusinessPartnerData,
  );
}
