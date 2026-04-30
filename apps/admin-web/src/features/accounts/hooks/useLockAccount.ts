'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { LockResponseSchema, UnlockResponseSchema, type LockResponse, type UnlockResponse } from '@/shared/api/admin-api';
import { newIdempotencyKey } from '@/shared/lib/idempotency';

export interface LockVariables {
  accountId: string;
  reason: string;
  ticketId?: string;
}

export function useLockAccount() {
  const qc = useQueryClient();
  return useMutation<LockResponse, Error, LockVariables>({
    mutationFn: async ({ accountId, reason, ticketId }) => {
      const data = await apiClient.post<unknown>(
        `/api/admin/accounts/${accountId}/lock`,
        { reason, ticketId },
        { idempotencyKey: newIdempotencyKey(), operatorReason: reason },
      );
      return LockResponseSchema.parse(data);
    },
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['account', vars.accountId] });
    },
  });
}

export function useUnlockAccount() {
  const qc = useQueryClient();
  return useMutation<UnlockResponse, Error, LockVariables>({
    mutationFn: async ({ accountId, reason, ticketId }) => {
      const data = await apiClient.post<unknown>(
        `/api/admin/accounts/${accountId}/unlock`,
        { reason, ticketId },
        { idempotencyKey: newIdempotencyKey(), operatorReason: reason },
      );
      return UnlockResponseSchema.parse(data);
    },
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['account', vars.accountId] });
    },
  });
}
