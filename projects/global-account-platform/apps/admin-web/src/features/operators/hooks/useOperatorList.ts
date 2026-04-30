'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  OperatorListResponseSchema,
  type OperatorListResponse,
  type OperatorStatus,
} from '@/shared/api/admin-api';

export interface UseOperatorListOptions {
  status?: OperatorStatus;
  page?: number;
  size?: number;
  enabled?: boolean;
}

/**
 * GET /api/admin/operators — full operator list (paginated).
 * Requires `operator.manage` permission (SUPER_ADMIN).
 */
export function useOperatorList(options: UseOperatorListOptions = {}) {
  const { status, page = 0, size = 20, enabled = true } = options;

  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));
  if (status) params.set('status', status);

  return useQuery<OperatorListResponse>({
    queryKey: ['operators', 'list', { status, page, size }],
    enabled,
    queryFn: async () => {
      const data = await apiClient.get<unknown>(`/api/admin/operators?${params.toString()}`);
      return OperatorListResponseSchema.parse(data);
    },
  });
}
