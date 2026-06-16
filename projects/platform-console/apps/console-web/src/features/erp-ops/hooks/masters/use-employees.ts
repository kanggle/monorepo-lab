'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  EmployeeListResponseSchema,
  type EmployeeListResponse,
  EmployeeSchema,
  type Employee,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateEmployeeInput,
  type UpdateEmployeeInput,
} from '../../api/types';
import { employeeDetailKey, employeesListKey } from '../../api/erp-keys';
import {
  clampSize,
  buildListQs,
  buildDetailQs,
  useThreadedAsOf,
  invalidateMaster,
} from '../use-erp-shared';

/**
 * erp-ops masters — employees hooks (TASK-PC-FE-107 split; PII — never logged).
 * List + detail reads + create/update/retire mutations (TASK-PC-FE-048).
 * Behavior-preserving; re-exported verbatim through the masters barrel.
 */

// ---------------------------------------------------------------------------
// employees — list + detail (PII; never logged)
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
// employees — WRITE mutations (TASK-PC-FE-048). Same shape as the department
// mutation hooks: same-origin POST proxy (the route forwards with the correct
// upstream method), entity-schema parse, prefix invalidation on success.
// retire takes a bare reason string (the producer's only reason slot).
// ---------------------------------------------------------------------------

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
