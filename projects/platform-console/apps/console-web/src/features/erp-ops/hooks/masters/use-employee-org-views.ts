'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  EmployeeOrgViewListResponseSchema,
  type EmployeeOrgViewListResponse,
  type OrgViewListQueryParams,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from '../../api/types';
import { employeeOrgViewsListKey } from '../../api/erp-keys';
import { clampSize, useThreadedAsOf } from '../use-erp-shared';

/**
 * erp-ops masters — employee org-view read hook (TASK-PC-FE-049; TASK-PC-FE-107
 * split). READ-ONLY read-model surface (`GET /api/erp/read-model/employees`).
 * No mutation hook exists (E5 — the read-model has no write surface).
 * Behavior-preserving; re-exported through the masters barrel.
 */

// ---------------------------------------------------------------------------
// read-model — employee org-view (TASK-PC-FE-049, READ-ONLY)
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
