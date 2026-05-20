'use client';

import { useCallback, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
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
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from '../api/types';
import {
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
  normaliseAsOf,
} from '../api/erp-keys';

/**
 * Client-side erp-ops hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the
 * same-origin `/api/erp/**` proxy (the typed API client's single
 * backend entry point); the proxy attaches the HttpOnly **GAP OIDC
 * access token** server-side — the browser never reads a token or
 * calls erp directly (contract § 2.3). The § 2.4.5 per-domain
 * credential rule is reused, NOT re-derived (§ 2.4.8).
 *
 * READ-ONLY: there are NO mutation hooks at all (erp v1 has no
 * operator-mutation parity at the console; the section is
 * list-driven reads with E3 first-class asOf). The hooks below are
 * pure reads.
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
