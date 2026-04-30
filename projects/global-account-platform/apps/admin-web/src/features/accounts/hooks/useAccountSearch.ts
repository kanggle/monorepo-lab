'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { AccountSummarySchema, type AccountSummary } from '@/shared/api/admin-api';
import { z } from 'zod';

const ResponseSchema = z.object({
  content: z.array(AccountSummarySchema),
  totalElements: z.number().int().nonnegative().optional(),
});

export function useAccountSearch(email: string | undefined) {
  return useQuery<AccountSummary[]>({
    queryKey: ['accounts', 'search', email],
    enabled: Boolean(email),
    queryFn: async () => {
      const data = await apiClient.get<unknown>(`/api/admin/accounts?email=${encodeURIComponent(email!)}`);
      return ResponseSchema.parse(data).content;
    },
  });
}
