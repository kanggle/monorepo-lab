'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { AccountDetailSchema, type AccountDetail } from '@/shared/api/admin-api';

export function useAccountDetail(accountId: string) {
  return useQuery<AccountDetail>({
    queryKey: ['account', accountId],
    queryFn: async () => {
      const data = await apiClient.get<unknown>(`/api/admin/accounts/${accountId}`);
      return AccountDetailSchema.parse(data);
    },
  });
}
