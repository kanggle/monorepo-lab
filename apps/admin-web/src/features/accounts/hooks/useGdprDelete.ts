'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { GdprDeleteResponseSchema, type GdprDeleteResponse } from '@/shared/api/admin-api';
import { newIdempotencyKey } from '@/shared/lib/idempotency';

export interface GdprDeleteVariables {
  accountId: string;
  reason: string;
  ticketId?: string;
}

export function useGdprDelete() {
  const qc = useQueryClient();
  return useMutation<GdprDeleteResponse, Error, GdprDeleteVariables>({
    mutationFn: async ({ accountId, reason, ticketId }) => {
      const data = await apiClient.post<unknown>(
        `/api/admin/accounts/${accountId}/gdpr-delete`,
        { reason, ticketId },
        { idempotencyKey: newIdempotencyKey(), operatorReason: reason },
      );
      return GdprDeleteResponseSchema.parse(data);
    },
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['account', vars.accountId] });
    },
  });
}
