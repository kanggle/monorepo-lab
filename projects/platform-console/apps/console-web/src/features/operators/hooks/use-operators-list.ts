'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  OperatorPageSchema,
  type OperatorPage,
  type OperatorListParams,
} from '../api/types';
import { listKey } from './operators-keys';

// --- read: list -----------------------------------------------------------

async function queryList(
  params: OperatorListParams,
): Promise<OperatorPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampPageSize(params.size, 20, 100)));
  const raw = await apiClient.get<unknown>(
    `/api/operators?${qs.toString()}`,
  );
  return OperatorPageSchema.parse(raw);
}

export function useOperatorsList(
  params: OperatorListParams,
  initial?: OperatorPage,
) {
  return useQuery({
    queryKey: listKey(params),
    queryFn: () => queryList(params),
    initialData: initial,
    // Seeded from the server render ⇒ that page is fresh (the server
    // already fetched it with the operator token). A filter / page change
    // is a new queryKey → one fresh proxy call. NO auto-refetch interval.
    staleTime: initial ? 30_000 : 0,
    refetchOnMount: initial ? false : true,
    ...READ_QUERY_REFETCH,
  });
}
