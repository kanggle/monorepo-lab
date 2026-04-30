'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  CreateOperatorResponseSchema,
  type CreateOperatorResponse,
  type OperatorRole,
} from '@/shared/api/admin-api';
import { newIdempotencyKey } from '@/shared/lib/idempotency';

export interface CreateOperatorVariables {
  email: string;
  displayName: string;
  password: string;
  roles: OperatorRole[];
  /** Operator-provided audit reason for X-Operator-Reason header. */
  reason: string;
}

/**
 * POST /api/admin/operators — create a new operator account.
 * Required permission: operator.manage (SUPER_ADMIN).
 */
export function useCreateOperator() {
  const qc = useQueryClient();
  return useMutation<CreateOperatorResponse, Error, CreateOperatorVariables>({
    mutationFn: async ({ email, displayName, password, roles, reason }) => {
      const data = await apiClient.post<unknown>(
        '/api/admin/operators',
        { email, displayName, password, roles },
        { idempotencyKey: newIdempotencyKey(), operatorReason: reason },
      );
      return CreateOperatorResponseSchema.parse(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['operators'] });
    },
  });
}
