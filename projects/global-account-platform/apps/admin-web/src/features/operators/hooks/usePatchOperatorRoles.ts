'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/shared/api/client';
import {
  PatchRolesResponseSchema,
  type PatchRolesResponse,
  type OperatorRole,
} from '@/shared/api/admin-api';

export interface PatchRolesVariables {
  operatorId: string;
  roles: OperatorRole[];
  /** Operator-provided audit reason for X-Operator-Reason header. */
  reason: string;
}

/**
 * PATCH /api/admin/operators/{operatorId}/roles — full replacement of roles.
 * Required permission: operator.manage (SUPER_ADMIN).
 */
export function usePatchOperatorRoles() {
  const qc = useQueryClient();
  return useMutation<PatchRolesResponse, Error, PatchRolesVariables>({
    mutationFn: async ({ operatorId, roles, reason }) => {
      const data = await apiFetch<unknown>(`/api/admin/operators/${operatorId}/roles`, {
        method: 'PATCH',
        body: { roles },
        operatorReason: reason,
      });
      return PatchRolesResponseSchema.parse(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['operators'] });
    },
  });
}
