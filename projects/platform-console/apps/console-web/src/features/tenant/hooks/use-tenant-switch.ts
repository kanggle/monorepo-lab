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
      // TASK-PC-FE-044 — tenant-scoped LIST queries (`['operators']` MONO-175,
      // `['audit']` PC-FE-043) are seeded from the server render (initialData +
      // staleTime 30s + refetchOnMount false) and keyed WITHOUT a tenant slot,
      // so router.refresh() alone leaves the previous tenant's cached list in
      // place (React Query ignores the new initialData for an existing key).
      // Invalidating forces the mounted query to refetch through the proxy with
      // the now-current active-tenant cookie -> the list re-scopes. Omitting
      // them was the stale-list defect (운영자 관리 / 감사·보안 showed identical
      // rows across tenants).
      qc.invalidateQueries({ queryKey: ['operators'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
      router.refresh();
    },
  });
}
