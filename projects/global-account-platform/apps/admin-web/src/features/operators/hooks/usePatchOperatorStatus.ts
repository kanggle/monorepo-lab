'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/shared/api/client';
import {
  PatchStatusResponseSchema,
  type PatchStatusResponse,
  type OperatorStatus,
} from '@/shared/api/admin-api';

export interface PatchStatusVariables {
  operatorId: string;
  status: OperatorStatus;
  /** Operator-provided audit reason for X-Operator-Reason header. */
  reason: string;
}

/**
 * PATCH /api/admin/operators/{operatorId}/status — transitions ACTIVE ↔ SUSPENDED.
 * Required permission: operator.manage (SUPER_ADMIN).
 */
export function usePatchOperatorStatus() {
  const qc = useQueryClient();
  return useMutation<PatchStatusResponse, Error, PatchStatusVariables>({
    mutationFn: async ({ operatorId, status, reason }) => {
      const data = await apiFetch<unknown>(`/api/admin/operators/${operatorId}/status`, {
        method: 'PATCH',
        body: { status },
        operatorReason: reason,
      });
      return PatchStatusResponseSchema.parse(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['operators'] });
    },
  });
}
