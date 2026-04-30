'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { BulkLockResponseSchema, type BulkLockResponse } from '@/shared/api/admin-api';
import { newIdempotencyKey } from '@/shared/lib/idempotency';

export interface BulkLockVariables {
  accountIds: string[];
  reason: string;
  ticketId?: string;
}

export function useBulkLock() {
  const qc = useQueryClient();
  return useMutation<BulkLockResponse, Error, BulkLockVariables>({
    mutationFn: async ({ accountIds, reason, ticketId }) => {
      const data = await apiClient.post<unknown>(
        '/api/admin/accounts/bulk-lock',
        { accountIds, reason, ticketId },
        { idempotencyKey: newIdempotencyKey(), operatorReason: reason },
      );
      return BulkLockResponseSchema.parse(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['account'] });
    },
  });
}
