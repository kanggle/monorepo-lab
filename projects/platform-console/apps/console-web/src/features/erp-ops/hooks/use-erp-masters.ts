'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  DepartmentListResponseSchema,
  type DepartmentListResponse,
  DepartmentSchema,
  type Department,
  EmployeeListResponseSchema,
  type EmployeeListResponse,
  EmployeeSchema,
  type Employee,
  JobGradeListResponseSchema,
  type JobGradeListResponse,
  JobGradeSchema,
  type JobGrade,
  CostCenterListResponseSchema,
  type CostCenterListResponse,
  CostCenterSchema,
  type CostCenter,
  BusinessPartnerListResponseSchema,
  type BusinessPartnerListResponse,
  BusinessPartnerSchema,
  type BusinessPartner,
  EmployeeOrgViewListResponseSchema,
  type EmployeeOrgViewListResponse,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type OrgViewListQueryParams,
  type CreateDepartmentInput,
  type UpdateDepartmentInput,
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
} from '../api/types';
import {
  ERP_KEY,
  businessPartnerDetailKey,
  businessPartnersListKey,
  costCenterDetailKey,
  costCentersListKey,
  departmentDetailKey,
  departmentsListKey,
  employeeDetailKey,
  employeesListKey,
  jobGradeDetailKey,
  jobGradesListKey,
  employeeOrgViewsListKey,
} from '../api/erp-keys';
import {
  clampSize,
  buildListQs,
  buildDetailQs,
  useThreadedAsOf,
} from './use-erp-shared';

// ---------------------------------------------------------------------------
// 1. departments — list + detail
// ---------------------------------------------------------------------------

async function fetchDepartmentsList(
  params: ErpListQueryParams,
): Promise<DepartmentListResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/departments?${buildListQs(params)}`,
  );
  return DepartmentListResponseSchema.parse(raw);
}

export function useDepartments(
  paramsIn: ErpListQueryParams = {},
  initial?: DepartmentListResponse,
) {
  const asOf = useThreadedAsOf(paramsIn.asOf);
  const params: ErpListQueryParams = { ...paramsIn, asOf };
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.filters &&
    params.active === undefined;
  return useQuery({
    queryKey: departmentsListKey(
      asOf,
      params.page ?? 0,
      clampSize(params.size),
      params.filters,
    ),
    queryFn: () => fetchDepartmentsList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

async function fetchDepartmentDetail(
  id: string,
  params: ErpDetailQueryParams,
): Promise<Department> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/departments/${encodeURIComponent(id)}${buildDetailQs(params)}`,
  );
  return DepartmentSchema.parse(raw);
}

export function useDepartment(id: string | null, asOfExplicit?: string | null) {
  const asOf = useThreadedAsOf(asOfExplicit);
  return useQuery({
    queryKey: departmentDetailKey(id ?? '', asOf),
    queryFn: () => fetchDepartmentDetail(id as string, { asOf }),
    enabled: Boolean(id && id.trim()),
    staleTime: 15_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// ---------------------------------------------------------------------------
// 2. employees — list + detail (PII; never logged)
// ---------------------------------------------------------------------------

async function fetchEmployeesList(
  params: ErpListQueryParams,
): Promise<EmployeeListResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/employees?${buildListQs(params)}`,
  );
  return EmployeeListResponseSchema.parse(raw);
}

export function useEmployees(
  paramsIn: ErpListQueryParams = {},
  initial?: EmployeeListResponse,
) {
  const asOf = useThreadedAsOf(paramsIn.asOf);
  const params: ErpListQueryParams = { ...paramsIn, asOf };
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.filters &&
    params.active === undefined;
  return useQuery({
    queryKey: employeesListKey(
      asOf,
      params.page ?? 0,
      clampSize(params.size),
      params.filters,
    ),
    queryFn: () => fetchEmployeesList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

async function fetchEmployeeDetail(
  id: string,
  params: ErpDetailQueryParams,
): Promise<Employee> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/employees/${encodeURIComponent(id)}${buildDetailQs(params)}`,
  );
  return EmployeeSchema.parse(raw);
}

export function useEmployee(id: string | null, asOfExplicit?: string | null) {
  const asOf = useThreadedAsOf(asOfExplicit);
  return useQuery({
    queryKey: employeeDetailKey(id ?? '', asOf),
    queryFn: () => fetchEmployeeDetail(id as string, { asOf }),
    enabled: Boolean(id && id.trim()),
    staleTime: 15_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// ---------------------------------------------------------------------------
// 3. job-grades — list (producer orders by displayOrder asc) + detail
// ---------------------------------------------------------------------------

async function fetchJobGradesList(
  params: ErpListQueryParams,
): Promise<JobGradeListResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/job-grades?${buildListQs(params)}`,
  );
  return JobGradeListResponseSchema.parse(raw);
}

export function useJobGrades(
  paramsIn: ErpListQueryParams = {},
  initial?: JobGradeListResponse,
) {
  const asOf = useThreadedAsOf(paramsIn.asOf);
  const params: ErpListQueryParams = { ...paramsIn, asOf };
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.filters &&
    params.active === undefined;
  return useQuery({
    queryKey: jobGradesListKey(asOf, params.page ?? 0, clampSize(params.size)),
    queryFn: () => fetchJobGradesList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

async function fetchJobGradeDetail(
  id: string,
  params: ErpDetailQueryParams,
): Promise<JobGrade> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}${buildDetailQs(params)}`,
  );
  return JobGradeSchema.parse(raw);
}

export function useJobGrade(id: string | null, asOfExplicit?: string | null) {
  const asOf = useThreadedAsOf(asOfExplicit);
  return useQuery({
    queryKey: jobGradeDetailKey(id ?? '', asOf),
    queryFn: () => fetchJobGradeDetail(id as string, { asOf }),
    enabled: Boolean(id && id.trim()),
    staleTime: 15_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// ---------------------------------------------------------------------------
// 4. cost-centers — list + detail
// ---------------------------------------------------------------------------

async function fetchCostCentersList(
  params: ErpListQueryParams,
): Promise<CostCenterListResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/cost-centers?${buildListQs(params)}`,
  );
  return CostCenterListResponseSchema.parse(raw);
}

export function useCostCenters(
  paramsIn: ErpListQueryParams = {},
  initial?: CostCenterListResponse,
) {
  const asOf = useThreadedAsOf(paramsIn.asOf);
  const params: ErpListQueryParams = { ...paramsIn, asOf };
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.filters &&
    params.active === undefined;
  return useQuery({
    queryKey: costCentersListKey(
      asOf,
      params.page ?? 0,
      clampSize(params.size),
      params.filters,
    ),
    queryFn: () => fetchCostCentersList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

async function fetchCostCenterDetail(
  id: string,
  params: ErpDetailQueryParams,
): Promise<CostCenter> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}${buildDetailQs(params)}`,
  );
  return CostCenterSchema.parse(raw);
}

export function useCostCenter(id: string | null, asOfExplicit?: string | null) {
  const asOf = useThreadedAsOf(asOfExplicit);
  return useQuery({
    queryKey: costCenterDetailKey(id ?? '', asOf),
    queryFn: () => fetchCostCenterDetail(id as string, { asOf }),
    enabled: Boolean(id && id.trim()),
    staleTime: 15_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// ---------------------------------------------------------------------------
// 5. business-partners — list + detail (confidential paymentTerms;
//   never logged)
// ---------------------------------------------------------------------------

async function fetchBusinessPartnersList(
  params: ErpListQueryParams,
): Promise<BusinessPartnerListResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/business-partners?${buildListQs(params)}`,
  );
  return BusinessPartnerListResponseSchema.parse(raw);
}

export function useBusinessPartners(
  paramsIn: ErpListQueryParams = {},
  initial?: BusinessPartnerListResponse,
) {
  const asOf = useThreadedAsOf(paramsIn.asOf);
  const params: ErpListQueryParams = { ...paramsIn, asOf };
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.filters &&
    params.active === undefined;
  return useQuery({
    queryKey: businessPartnersListKey(
      asOf,
      params.page ?? 0,
      clampSize(params.size),
      params.filters,
    ),
    queryFn: () => fetchBusinessPartnersList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

async function fetchBusinessPartnerDetail(
  id: string,
  params: ErpDetailQueryParams,
): Promise<BusinessPartner> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}${buildDetailQs(params)}`,
  );
  return BusinessPartnerSchema.parse(raw);
}

export function useBusinessPartner(
  id: string | null,
  asOfExplicit?: string | null,
) {
  const asOf = useThreadedAsOf(asOfExplicit);
  return useQuery({
    queryKey: businessPartnerDetailKey(id ?? '', asOf),
    queryFn: () => fetchBusinessPartnerDetail(id as string, { asOf }),
    enabled: Boolean(id && id.trim()),
    staleTime: 15_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// ---------------------------------------------------------------------------
// 6. read-model — employee org-view (TASK-PC-FE-049, READ-ONLY)
//   GET /api/erp/read-model/employees
// No mutation hook exists (E5 — read-model has no write surface).
// ---------------------------------------------------------------------------

async function fetchEmployeeOrgViewsList(
  params: OrgViewListQueryParams,
): Promise<EmployeeOrgViewListResponse> {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampPageSize(params.size, ERP_DEFAULT_PAGE_SIZE, ERP_MAX_PAGE_SIZE)));
  if (params.departmentId) qs.set('departmentId', params.departmentId);
  if (params.status) qs.set('status', params.status);
  const raw = await apiClient.get<unknown>(
    `/api/erp/read-model/employees?${qs.toString()}`,
  );
  return EmployeeOrgViewListResponseSchema.parse(raw);
}

export function useEmployeeOrgViews(
  paramsIn: OrgViewListQueryParams = {},
  initial?: EmployeeOrgViewListResponse,
) {
  const asOf = useThreadedAsOf(paramsIn.asOf);
  const params: OrgViewListQueryParams = { ...paramsIn, asOf };
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.departmentId &&
    !params.status;
  return useQuery({
    queryKey: employeeOrgViewsListKey(
      asOf,
      params.page ?? 0,
      clampSize(params.size),
      {
        departmentId: params.departmentId,
        status: params.status,
      },
    ),
    queryFn: () => fetchEmployeeOrgViewsList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// ---------------------------------------------------------------------------
// Department WRITE PILOT mutations (TASK-PC-FE-046 / § 2.4.8 *Department
// write binding (PILOT)*). The ONLY erp mutation hooks — the other four
// masters stay read-only (no mutation hook exists for them; a test pins
// that). Each goes to a same-origin POST proxy route (the typed client
// has only get/post; the route forwards with the correct UPSTREAM method
// — POST create / PATCH update / POST retire / POST move-parent). The
// `Idempotency-Key` is generated by the calling component per attempt
// (the operators `crypto.randomUUID()` pattern) and threaded through the
// body → the proxy → the api-client header. On success every department
// list/detail query is invalidated by prefix (any asOf / page).
// ---------------------------------------------------------------------------

function invalidateDepartments(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [ERP_KEY, 'departments'] });
}

export interface CreateDepartmentArgs {
  input: CreateDepartmentInput;
  idempotencyKey: string;
}

export function useCreateDepartment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ input, idempotencyKey }: CreateDepartmentArgs) => {
      const raw = await apiClient.post<unknown>(
        '/api/erp/masterdata/departments',
        { ...input, idempotencyKey },
      );
      return DepartmentSchema.parse(raw);
    },
    onSuccess: () => invalidateDepartments(qc),
  });
}

export interface UpdateDepartmentArgs {
  id: string;
  input: UpdateDepartmentInput;
  idempotencyKey: string;
}

export function useUpdateDepartment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, input, idempotencyKey }: UpdateDepartmentArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/departments/${encodeURIComponent(id)}`,
        { ...input, idempotencyKey },
      );
      return DepartmentSchema.parse(raw);
    },
    onSuccess: () => invalidateDepartments(qc),
  });
}

export interface RetireDepartmentArgs {
  id: string;
  reason: string;
  idempotencyKey: string;
}

export function useRetireDepartment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, reason, idempotencyKey }: RetireDepartmentArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/departments/${encodeURIComponent(id)}/retire`,
        { reason, idempotencyKey },
      );
      return DepartmentSchema.parse(raw);
    },
    onSuccess: () => invalidateDepartments(qc),
  });
}

export interface MoveDepartmentParentArgs {
  id: string;
  input: MoveDepartmentParentInput;
  idempotencyKey: string;
}

export function useMoveDepartmentParent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      input,
      idempotencyKey,
    }: MoveDepartmentParentArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/departments/${encodeURIComponent(id)}/move-parent`,
        { ...input, idempotencyKey },
      );
      return DepartmentSchema.parse(raw);
    },
    onSuccess: () => invalidateDepartments(qc),
  });
}

// ---------------------------------------------------------------------------
// Additional masters WRITE mutations (TASK-PC-FE-048 — employees / job-grades /
// cost-centers / business-partners create/update/retire). Same shape as the
// department mutation hooks: same-origin POST proxy (the route forwards with
// the correct upstream method), entity-schema parse, prefix invalidation on
// success. retire takes a bare reason string (the producer's only reason slot
// for these masters).
// ---------------------------------------------------------------------------

function invalidateMaster(
  qc: ReturnType<typeof useQueryClient>,
  master: string,
) {
  qc.invalidateQueries({ queryKey: [ERP_KEY, master] });
}

// employees ------------------------------------------------------------------
export function useCreateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      input: CreateEmployeeInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        '/api/erp/masterdata/employees',
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return EmployeeSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'employees'),
  });
}
export function useUpdateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      input: UpdateEmployeeInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/employees/${encodeURIComponent(args.id)}`,
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return EmployeeSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'employees'),
  });
}
export function useRetireEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      reason: string;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/employees/${encodeURIComponent(args.id)}/retire`,
        { reason: args.reason, idempotencyKey: args.idempotencyKey },
      );
      return EmployeeSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'employees'),
  });
}

// job-grades -----------------------------------------------------------------
export function useCreateJobGrade() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      input: CreateJobGradeInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        '/api/erp/masterdata/job-grades',
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return JobGradeSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'job-grades'),
  });
}
export function useUpdateJobGrade() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      input: UpdateJobGradeInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/job-grades/${encodeURIComponent(args.id)}`,
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return JobGradeSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'job-grades'),
  });
}
export function useRetireJobGrade() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      reason: string;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/job-grades/${encodeURIComponent(args.id)}/retire`,
        { reason: args.reason, idempotencyKey: args.idempotencyKey },
      );
      return JobGradeSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'job-grades'),
  });
}

// cost-centers ---------------------------------------------------------------
export function useCreateCostCenter() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      input: CreateCostCenterInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        '/api/erp/masterdata/cost-centers',
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return CostCenterSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'cost-centers'),
  });
}
export function useUpdateCostCenter() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      input: UpdateCostCenterInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/cost-centers/${encodeURIComponent(args.id)}`,
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return CostCenterSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'cost-centers'),
  });
}
export function useRetireCostCenter() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      reason: string;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/cost-centers/${encodeURIComponent(args.id)}/retire`,
        { reason: args.reason, idempotencyKey: args.idempotencyKey },
      );
      return CostCenterSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'cost-centers'),
  });
}

// business-partners ----------------------------------------------------------
export function useCreateBusinessPartner() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      input: CreateBusinessPartnerInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        '/api/erp/masterdata/business-partners',
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return BusinessPartnerSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'business-partners'),
  });
}
export function useUpdateBusinessPartner() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      input: UpdateBusinessPartnerInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/business-partners/${encodeURIComponent(args.id)}`,
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return BusinessPartnerSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'business-partners'),
  });
}
export function useRetireBusinessPartner() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      reason: string;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/business-partners/${encodeURIComponent(args.id)}/retire`,
        { reason: args.reason, idempotencyKey: args.idempotencyKey },
      );
      return BusinessPartnerSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'business-partners'),
  });
}
