'use client';

import { useMutation } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';

const CHANGE_PASSWORD_REASON = 'operator.password.change';

export function useChangePassword() {
  return useMutation<void, Error, { currentPassword: string; newPassword: string }>({
    mutationFn: async ({ currentPassword, newPassword }) => {
      await apiClient.patch<unknown>(
        '/api/admin/operators/me/password',
        { currentPassword, newPassword },
        { operatorReason: CHANGE_PASSWORD_REASON },
      );
    },
  });
}
