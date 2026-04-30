'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { AccountPageSchema, type AccountPage } from '@/shared/api/admin-api';

export function useAccountList(page: number, size: number, enabled: boolean) {
  return useQuery<AccountPage>({
    queryKey: ['accounts', 'list', page, size],
    enabled,
    queryFn: async () => {
      const data = await apiClient.get<unknown>(
        `/api/admin/accounts?page=${page}&size=${size}`,
      );
      return AccountPageSchema.parse(data);
    },
  });
}
