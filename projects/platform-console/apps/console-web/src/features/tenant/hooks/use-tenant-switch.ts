'use client';

import { useRouter } from 'next/navigation';
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
 * M2/M3; task Failure Scenario).
 *
 * On success (TASK-PC-FE-040): the assume-tenant exchange has re-scoped the
 * session's signed token to the selected customer. The console's tenant-scoped
 * views are rendered by **server components** keyed on the active-tenant cookie
 * + assumed token, so invalidating react-query caches alone (client queries)
 * leaves the current page stale. `router.refresh()` re-runs the current route's
 * server components with the new token → **the view the operator is on
 * re-applies the new tenant's entitlement gate in place** (entitled → live
 * data; not entitled → the section's forbidden/not-eligible state). The query
 * invalidations cover any client-side tenant-scoped queries on the page.
 */
export function useTenantSwitch() {
  const qc = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: (tenant: string) =>
      apiClient.post<SwitchResult>('/api/tenant', { tenant }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['catalog'] });
      qc.invalidateQueries({ queryKey: ['session'] });
      router.refresh();
    },
  });
}
