'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { OperatorSessionSchema, type OperatorSession } from '@/shared/api/admin-api';

export function useOperatorSession() {
  return useQuery<OperatorSession>({
    queryKey: ['operator-session'],
    queryFn: async () => {
      const data = await apiClient.get<unknown>('/api/admin/me');
      return OperatorSessionSchema.parse(data);
    },
    staleTime: 60_000,
    retry: false,
  });
}
