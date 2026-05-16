'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';

interface SwitchResult {
  ok: boolean;
  activeTenant: string | null;
}

/**
 * Tenant switch mutation. Posts to the same-origin `/api/tenant` route which
 * validates the requested tenant against the operator's own registry scope
 * server-side and rejects cross-tenant selections with 403 (multi-tenant
 * M2/M3; task Failure Scenario). On success, invalidates catalog/session so
 * tenant-scoped views refetch.
 */
export function useTenantSwitch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (tenant: string) =>
      apiClient.post<SwitchResult>('/api/tenant', { tenant }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['catalog'] });
      qc.invalidateQueries({ queryKey: ['session'] });
    },
  });
}
