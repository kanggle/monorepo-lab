'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { RevokeResponseSchema, type RevokeResponse } from '@/shared/api/admin-api';
import { newIdempotencyKey } from '@/shared/lib/idempotency';

export interface RevokeSessionVariables {
  accountId: string;
  reason: string;
}

export function useRevokeSession() {
  const qc = useQueryClient();
  return useMutation<RevokeResponse, Error, RevokeSessionVariables>({
    mutationFn: async ({ accountId, reason }) => {
      const data = await apiClient.post<unknown>(
        `/api/admin/sessions/${accountId}/revoke`,
        { reason },
        { idempotencyKey: newIdempotencyKey(), operatorReason: reason },
      );
      return RevokeResponseSchema.parse(data);
    },
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['account', vars.accountId] });
    },
  });
}
