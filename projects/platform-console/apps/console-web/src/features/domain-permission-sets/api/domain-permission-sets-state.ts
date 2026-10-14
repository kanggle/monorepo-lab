import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, DomainPermissionSetsUnavailableError } from '@/shared/api/errors';
import { listDomainRoles, listDomainPermissionSets } from './domain-permission-sets-api';
import type { DomainRole, DomainPermissionSet } from './types';

/**
 * Server-side ADR-MONO-083 「업무 권한 세트」 SSR state for the
 * `(console)/domain-permission-sets` route (TASK-PC-FE-338). Mirrors
 * `operator-groups-state.ts` `getOperatorGroupsState()` branch-for-branch —
 * the pattern-of-record for an IAM admin surface whose LIST read itself
 * requires an elevated capability (here: `domain_access.manage`).
 *
 * Resilience boundary (admin-api.md § 도메인 업무 권한 세트 / § 2.5):
 *   - 401 → `redirect('/login')` (clean re-login; no partial authed state).
 *   - no active tenant → a distinct "select a tenant" gate (never an empty
 *     `X-Tenant-Id`; a SUPER_ADMIN selects `*` via the tenant switcher).
 *   - 403 (`PERMISSION_DENIED` / `TENANT_SCOPE_DENIED` — lacks
 *     `domain_access.manage`) → a `permissionError` state carrying the
 *     producer code so the page renders an inline "domain_access.manage
 *     required" section (no crash, no re-login loop).
 *   - `DomainPermissionSetsUnavailableError` (503/timeout/network) / anything
 *     else → DEGRADED (the 업무 권한 세트 section renders a degraded notice;
 *     the console shell + every other IAM surface stay intact).
 *
 * `subscribedDomains` (the create/edit form's "구독 없음" disable source) is
 * NOT computed in here — `architecture.md` § Allowed Dependencies bars a
 * `features/*` → `features/catalog` import, so the caller (`page.tsx`, the
 * `app/` layer, which may import any feature) resolves it the SAME way
 * `(console)/layout.tsx` does for the sidebar badge and threads it through
 * this function's `subscribedDomains` parameter. `null` ⇒ unknown (registry
 * failure / no active tenant / `'*'` scope) — "don't know ⇒ don't disable";
 * the producer's `422 DOMAIN_NOT_SUBSCRIBED` stays the final authority either
 * way.
 */
export interface DomainPermissionSetsState {
  roles: DomainRole[] | null;
  sets: DomainPermissionSet[] | null;
  degraded: boolean;
  noTenant: boolean;
  permissionError: { code: string; message: string } | null;
  subscribedDomains: string[] | null;
}

export async function getDomainPermissionSetsState(
  subscribedDomains: string[] | null = null,
): Promise<DomainPermissionSetsState> {
  const tenant = await getActiveTenant();
  if (!tenant) {
    return {
      roles: null,
      sets: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
      subscribedDomains: null,
    };
  }

  try {
    const [roles, sets] = await Promise.all([
      listDomainRoles(),
      listDomainPermissionSets(),
    ]);
    return {
      roles: roles.items,
      sets: sets.items,
      degraded: false,
      noTenant: false,
      permissionError: null,
      subscribedDomains,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login?error=session_expired');
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return {
        roles: null,
        sets: null,
        degraded: false,
        noTenant: true,
        permissionError: null,
        subscribedDomains: null,
      };
    }
    if (err instanceof ApiError && err.status === 403) {
      return {
        roles: null,
        sets: null,
        degraded: false,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
        subscribedDomains,
      };
    }
    if (err instanceof DomainPermissionSetsUnavailableError) {
      return {
        roles: null,
        sets: null,
        degraded: true,
        noTenant: false,
        permissionError: null,
        subscribedDomains,
      };
    }
    return {
      roles: null,
      sets: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
      subscribedDomains,
    };
  }
}
