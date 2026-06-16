'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  CostCenterListResponseSchema,
  type CostCenterListResponse,
  CostCenterSchema,
  type CostCenter,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateCostCenterInput,
  type UpdateCostCenterInput,
} from '../../api/types';
import { costCenterDetailKey, costCentersListKey } from '../../api/erp-keys';
import {
  clampSize,
  buildListQs,
  buildDetailQs,
  useThreadedAsOf,
  invalidateMaster,
} from '../use-erp-shared';

/**
 * erp-ops masters — cost-centers hooks (TASK-PC-FE-107 split). List + detail
 * reads + create/update/retire mutations (TASK-PC-FE-048). Behavior-preserving;
 * re-exported through the masters barrel.
 */

// ---------------------------------------------------------------------------
// cost-centers — list + detail
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
// cost-centers — WRITE mutations (TASK-PC-FE-048).
// ---------------------------------------------------------------------------

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
