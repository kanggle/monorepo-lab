'use client';

import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';

interface SwitchResult {
  ok: boolean;
  activeTenant: string | null;
}

/**
 * Shared **active-tenant switch** mutation hook (TASK-PC-FE-040 /
 * TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * Switching the active tenant is a console-wide concern, not a `tenant`-feature
 * private one: it is driven from the top-bar `TenantSwitcher`
 * (`features/tenant`) **and** from every product card's tenant row in
 * `CatalogGrid` (`features/catalog`), which previously imported it across the
 * feature boundary. Per `architecture.md` § Forbidden Dependencies —
 *
 *   > 같은 계층 `features/A → features/B` 상호 참조 금지
 *   > (공유 가치는 `shared/` 로 승격).
 *
 * `features/tenant` still re-exports it from its barrel (the switcher remains
 * part of that feature's public surface); both call sites import this module.
 * `shared/api/` (not `shared/lib/`) because the hook's whole body is a backend
 * call through the shared `apiClient` — the console's single backend entry
 * point.
 *
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

/**
 * TASK-MONO-780 — the wms sections' client query roots. Literals, not imports:
 * `shared/` may not import `features/` (architecture.md § Forbidden
 * Dependencies). `tests/unit/use-tenant-switch.test.tsx` pins them against the
 * features' own key builders, so a rename there turns that test red instead of
 * silently reopening the stale-list defect below.
 *
 * WHY THESE: the wms lists (`wms-ops` inventory/alerts/shipments/asns/refs,
 * `wms-outbound-ops` orders) are seeded from the server render (initialData +
 * staleTime 30s + `refetchOnMount: false`) and keyed WITHOUT a tenant slot —
 * the same shape PC-FE-044 fixed for `['operators']`/`['audit']`. The server
 * side already re-scopes on a switch (the wms gateway core sends
 * `getDomainFacingToken()` = the assumed token of the new tenant), but React
 * Query ignores the refreshed initialData for an existing key, so the previous
 * tenant's rows stay on screen. That is exactly «the WMS 출고 list still shows
 * demo-corp's `SO-DEMO-0001` after switching to ecommerce».
 *
 * Inactive entries are REMOVED (not just invalidated): with `refetchOnMount:
 * false` an invalidated-but-inactive entry is shown as-is on the next visit,
 * so the old tenant's rows would come back the moment the operator navigates
 * to the section. Removed, the next mount seeds from the new server render.
 */
export const WMS_TENANT_SCOPED_QUERY_ROOTS = ['wms-ops', 'wms-outbound-ops'] as const;

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
      for (const root of WMS_TENANT_SCOPED_QUERY_ROOTS) {
        qc.removeQueries({ queryKey: [root], type: 'inactive' });
        qc.invalidateQueries({ queryKey: [root] });
      }
      router.refresh();
    },
  });
}
