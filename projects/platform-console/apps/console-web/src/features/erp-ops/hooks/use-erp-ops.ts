'use client';

import { useCallback, useMemo } from 'react';
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {
  usePathname,
  useRouter,
  useSearchParams,
} from 'next/navigation';
import { apiClient } from '@/shared/api/client';
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
  normaliseAsOf,
  APPROVAL_PREFIX,
  approvalListKey,
  approvalDetailKey,
  approvalInboxKey,
  DELEGATION_PREFIX,
  delegationListKey,
  delegationFactsListKey,
  delegationFactDetailKey,
} from '../api/erp-keys';
import {
  ApprovalListResponseSchema,
  type ApprovalListResponse,
  ApprovalRequestSchema,
  type ApprovalRequest,
  type ApprovalListQueryParams,
  type ApprovalInboxQueryParams,
  type CreateApprovalInput,
  APPROVAL_DEFAULT_PAGE_SIZE,
  APPROVAL_MAX_PAGE_SIZE,
} from '../api/approval-types';
import {
  DelegationListResponseSchema,
  type DelegationListResponse,
  type CreateDelegationInput,
} from '../api/delegation-types';
import {
  DelegationFactListResponseSchema,
  type DelegationFactListResponse,
  type DelegationFact,
  type DelegationFactListQueryParams,
} from '../api/types';

/**
 * Client-side erp-ops hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the
 * same-origin `/api/erp/**` proxy (the typed API client's single
 * backend entry point); the proxy attaches the HttpOnly **IAM OIDC
 * access token** server-side — the browser never reads a token or
 * calls erp directly (contract § 2.3). The § 2.4.5 per-domain
 * credential rule is reused, NOT re-derived (§ 2.4.8).
 *
 * READ + DEPARTMENT WRITE PILOT: the list/detail hooks are pure
 * reads. The department master additionally has four mutation hooks
 * (TASK-PC-FE-046 — `useCreateDepartment` / `useUpdateDepartment` /
 * `useRetireDepartment` / `useMoveDepartmentParent`; § 2.4.8
 * *Department write binding (PILOT)*). The OTHER FOUR masters have NO
 * mutation hook (a test pins that absence). Department writes go to a
 * same-origin POST proxy that forwards with the correct upstream
 * method; on success the department queries are invalidated by prefix.
 *
 * E3 FIRST-CLASS ASOF (§ 2.4.8 / `<AsOfPicker>` thread-through):
 * `useAsOf()` reads the `?asOf=` URL search-param (it is the SINGLE
 * source of truth — no separate component state to drift); list
 * and detail hooks pull from `useAsOf()` and inject it into the
 * proxy URL so the producer receives `asOf=<value>` verbatim. The
 * queryKey is bound to the asOf so React Query refetches on
 * change. NO tight refetch loop / `refetchInterval` /
 * `refetchOnWindowFocus`. **No 429 / Retry-After / backoff branch**
 * (§ 2.4.8 — erp has no documented 429; React Query `retry: false`
 * means an erp failure surfaces immediately, no client retry).
 */

function clampSize(size?: number): number {
  return Math.min(
    ERP_MAX_PAGE_SIZE,
    Math.max(1, size ?? ERP_DEFAULT_PAGE_SIZE),
  );
}

// ---------------------------------------------------------------------------
// useAsOf — URL-param-bound E3 first-class hook (the single source
// of truth for the section's effective-instant).
// ---------------------------------------------------------------------------

export interface UseAsOfResult {
  /** Current asOf (ISO-8601 DATE string) from the URL — `null`
   *  when absent (producer resolves to "today" UTC). */
  asOf: string | null;
  /** Sets / clears the asOf URL param (replaces the current
   *  history entry, preserves other query params). */
  setAsOf: (next: string | null) => void;
}

/**
 * Reads the `?asOf=` URL search-param and exposes a setter that
 * writes it back to the URL. This is the SINGLE source of truth
 * for the section's effective-instant — every list / detail hook
 * pulls from here and threads `asOf` to the producer verbatim
 * (the E3 core invariant; the asOf URL → query refetch → producer
 * pass-through chain).
 */
export function useAsOf(): UseAsOfResult {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const asOf = search?.get('asOf');
  const setAsOf = useCallback(
    (next: string | null) => {
      const params = new URLSearchParams(search?.toString() ?? '');
      const norm = normaliseAsOf(next);
      if (norm) params.set('asOf', norm);
      else params.delete('asOf');
      const qs = params.toString();
      // App-router replace (no history push so the back-button
      // path stays clean); preserves other params.
      router.replace(`${pathname ?? ''}${qs ? `?${qs}` : ''}`);
    },
    [router, pathname, search],
  );
  return { asOf: asOf || null, setAsOf };
}

/**
 * Builds the `asOf=…&active=…&page=…&size=…` query string the
 * proxy forwards to the producer. CORE E3 — the asOf threads
 * through verbatim. NO Number coercion of the wire string; page /
 * size arithmetic is on the integer page-index NUMBERS only (not
 * money — there is no F5 here).
 */
function buildListQs(params: ErpListQueryParams): string {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  if (params.active !== undefined) qs.set('active', String(params.active));
  if (params.filters) {
    for (const [k, v] of Object.entries(params.filters)) {
      if (v !== undefined && v !== null && v !== '') qs.set(k, v);
    }
  }
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

function buildDetailQs(params: ErpDetailQueryParams): string {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  const s = qs.toString();
  return s ? `?${s}` : '';
}

/** Internal — picks the asOf threaded into the hook (explicit
 *  param wins over the URL hook). When the caller omits `asOf` we
 *  read from `useAsOf()` so a single asOf change re-renders every
 *  list / detail subscribed under this section. */
function useThreadedAsOf(explicit?: string | null): string | undefined {
  const { asOf } = useAsOf();
  return useMemo(() => {
    const chosen = explicit !== undefined ? explicit : asOf;
    return normaliseAsOf(chosen);
  }, [explicit, asOf]);
}

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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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
  qs.set('size', String(Math.min(ERP_MAX_PAGE_SIZE, Math.max(1, params.size ?? ERP_DEFAULT_PAGE_SIZE))));
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
    refetchOnWindowFocus: false,
    refetchInterval: false,
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

// ---------------------------------------------------------------------------
// approval workflow (TASK-PC-FE-051 — ADR-MONO-016 § D3.1 parity slice).
// Read queries (list / detail / inbox) + 5 mutations (create + 4 state-machine
// transitions). Every call goes to the same-origin `/api/erp/approval/**`
// proxy; the proxy attaches the domain-facing IAM OIDC token + Idempotency-Key
// + X-Operator-Reason server-side (the browser never reads a token). On a
// successful mutation the entire `approval` prefix is invalidated so list +
// detail + inbox all refetch (the state machine moved). The Idempotency-Key is
// generated by the calling component per attempt (the masterdata write pattern
// reused) and threaded through the body → the proxy → the api-client header.
// ---------------------------------------------------------------------------

function clampApprovalSize(size?: number): number {
  return Math.min(
    APPROVAL_MAX_PAGE_SIZE,
    Math.max(1, size ?? APPROVAL_DEFAULT_PAGE_SIZE),
  );
}

function invalidateApproval(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [ERP_KEY, APPROVAL_PREFIX] });
}

async function fetchApprovalList(
  params: ApprovalListQueryParams,
): Promise<ApprovalListResponse> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.role) qs.set('role', params.role);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampApprovalSize(params.size)));
  const raw = await apiClient.get<unknown>(
    `/api/erp/approval/requests?${qs.toString()}`,
  );
  return ApprovalListResponseSchema.parse(raw);
}

export function useApprovalRequests(
  paramsIn: ApprovalListQueryParams = {},
  initial?: ApprovalListResponse,
) {
  const params = paramsIn;
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.status &&
    !params.role;
  return useQuery({
    queryKey: approvalListKey(
      params.status,
      params.role,
      params.page ?? 0,
      clampApprovalSize(params.size),
    ),
    queryFn: () => fetchApprovalList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

async function fetchApprovalInbox(
  params: ApprovalInboxQueryParams,
): Promise<ApprovalListResponse> {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampApprovalSize(params.size)));
  const raw = await apiClient.get<unknown>(
    `/api/erp/approval/inbox?${qs.toString()}`,
  );
  return ApprovalListResponseSchema.parse(raw);
}

export function useApprovalInbox(
  paramsIn: ApprovalInboxQueryParams = {},
  initial?: ApprovalListResponse,
) {
  const params = paramsIn;
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: approvalInboxKey(params.page ?? 0, clampApprovalSize(params.size)),
    queryFn: () => fetchApprovalInbox(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

/** Detail / mutation proxy responses are `{ data: <ApprovalRequest> }`. */
function parseApprovalDetail(raw: unknown): ApprovalRequest {
  const env = (raw ?? {}) as { data?: unknown };
  return ApprovalRequestSchema.parse(env.data);
}

async function fetchApprovalDetail(id: string): Promise<ApprovalRequest> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/approval/requests/${encodeURIComponent(id)}`,
  );
  return parseApprovalDetail(raw);
}

export function useApprovalRequest(id: string | null) {
  return useQuery({
    queryKey: approvalDetailKey(id ?? ''),
    queryFn: () => fetchApprovalDetail(id as string),
    enabled: Boolean(id && id.trim()),
    staleTime: 0,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

// mutations -----------------------------------------------------------------

export interface CreateApprovalArgs {
  input: CreateApprovalInput;
  idempotencyKey: string;
}

export function useCreateApproval() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ input, idempotencyKey }: CreateApprovalArgs) => {
      const raw = await apiClient.post<unknown>(
        '/api/erp/approval/requests',
        { ...input, idempotencyKey },
      );
      return parseApprovalDetail(raw);
    },
    onSuccess: () => invalidateApproval(qc),
  });
}

/** Shared transition args — `reason` required for reject / withdraw, optional
 *  for approve, absent for submit (the dialog enforces the per-transition
 *  rule; the proxy route is the authoritative guard). */
export interface ApprovalTransitionArgs {
  id: string;
  idempotencyKey: string;
  reason?: string;
}

function useApprovalTransition(transition: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, idempotencyKey, reason }: ApprovalTransitionArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/approval/requests/${encodeURIComponent(id)}/${transition}`,
        { idempotencyKey, ...(reason ? { reason } : {}) },
      );
      return parseApprovalDetail(raw);
    },
    onSuccess: () => invalidateApproval(qc),
  });
}

export function useSubmitApproval() {
  return useApprovalTransition('submit');
}
export function useApproveApproval() {
  return useApprovalTransition('approve');
}
export function useRejectApproval() {
  return useApprovalTransition('reject');
}
export function useWithdrawApproval() {
  return useApprovalTransition('withdraw');
}

// ---------------------------------------------------------------------------
// delegation grant management (TASK-PC-FE-054 — PC-FE-053 follow-up).
// Read query (list) + 2 mutations (create + revoke). Every call goes to the
// same-origin `/api/erp/approval/delegations/**` proxy; the proxy attaches
// the domain-facing IAM OIDC token server-side. On a successful mutation the
// entire `delegation` prefix is invalidated so both DELEGATOR + DELEGATE
// lists refetch. The Idempotency-Key is generated by the calling component
// per attempt (same pattern as approval mutations). The delegation reason
// rides in the body (NOT X-Operator-Reason — the delegation surface does not
// define an operator-reason audit header).
// ---------------------------------------------------------------------------

function invalidateDelegations(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [ERP_KEY, DELEGATION_PREFIX] });
}

async function fetchDelegationList(
  role: 'DELEGATOR' | 'DELEGATE' | undefined,
): Promise<DelegationListResponse> {
  const qs = role ? `?role=${encodeURIComponent(role)}` : '';
  const raw = await apiClient.get<unknown>(
    `/api/erp/approval/delegations${qs}`,
  );
  return DelegationListResponseSchema.parse(raw);
}

export function useDelegations(role?: 'DELEGATOR' | 'DELEGATE') {
  return useQuery({
    queryKey: delegationListKey(role),
    queryFn: () => fetchDelegationList(role),
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

export interface CreateDelegationArgs {
  input: CreateDelegationInput;
  idempotencyKey: string;
}

export function useCreateDelegation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ input, idempotencyKey }: CreateDelegationArgs) => {
      const raw = await apiClient.post<unknown>(
        '/api/erp/approval/delegations',
        {
          delegateId: input.delegateId,
          validFrom: input.validFrom,
          ...(input.validTo ? { validTo: input.validTo } : {}),
          ...(input.reason ? { reason: input.reason } : {}),
          idempotencyKey,
        },
      );
      return raw;
    },
    onSuccess: () => invalidateDelegations(qc),
  });
}

export interface RevokeDelegationArgs {
  id: string;
  reason: string;
  idempotencyKey: string;
}

export function useRevokeDelegation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, reason, idempotencyKey }: RevokeDelegationArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/approval/delegations/${encodeURIComponent(id)}/revoke`,
        { reason, idempotencyKey },
      );
      return raw;
    },
    onSuccess: () => invalidateDelegations(qc),
  });
}

// ---------------------------------------------------------------------------
// read-model — delegation facts (TASK-PC-FE-055, READ-ONLY)
//   GET /api/erp/read-model/delegations
//   GET /api/erp/read-model/delegations/{grantId}
// No mutation hook exists (E5 — read-model has no write surface).
// ---------------------------------------------------------------------------

async function fetchDelegationFactsList(
  params: DelegationFactListQueryParams,
): Promise<DelegationFactListResponse> {
  const qs = new URLSearchParams();
  if (params.delegatorId) qs.set('delegatorId', params.delegatorId);
  if (params.delegateId) qs.set('delegateId', params.delegateId);
  if (params.status) qs.set('status', params.status);
  if (params.activeAt) qs.set('activeAt', params.activeAt);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(Math.min(ERP_MAX_PAGE_SIZE, Math.max(1, params.size ?? ERP_DEFAULT_PAGE_SIZE))));
  const raw = await apiClient.get<unknown>(
    `/api/erp/read-model/delegations?${qs.toString()}`,
  );
  return DelegationFactListResponseSchema.parse(raw);
}

export function useDelegationFacts(
  paramsIn: DelegationFactListQueryParams = {},
  initial?: DelegationFactListResponse,
) {
  const params = paramsIn;
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.delegatorId &&
    !params.delegateId &&
    !params.status &&
    !params.activeAt;
  return useQuery({
    queryKey: delegationFactsListKey(
      params.delegatorId,
      params.delegateId,
      params.status,
      params.activeAt,
      params.page ?? 0,
      clampSize(params.size),
    ),
    queryFn: () => fetchDelegationFactsList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

async function fetchDelegationFactDetail(grantId: string): Promise<DelegationFact> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/read-model/delegations/${encodeURIComponent(grantId)}`,
  );
  const env = (raw ?? {}) as { data?: unknown };
  return DelegationFactListResponseSchema.shape.data.element.parse(env.data ?? raw);
}

export function useDelegationFact(grantId: string | null) {
  return useQuery({
    queryKey: delegationFactDetailKey(grantId ?? ''),
    queryFn: () => fetchDelegationFactDetail(grantId as string),
    enabled: Boolean(grantId && grantId.trim()),
    staleTime: 0,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}
