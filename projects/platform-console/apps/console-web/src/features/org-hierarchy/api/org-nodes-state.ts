import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, OrgNodesUnavailableError } from '@/shared/api/errors';
import { listOrgNodes } from './org-nodes-api';
import type { OrgNode } from './types';

/**
 * Server-side org-hierarchy SSR state for the `(console)/org-hierarchy` route
 * (TASK-PC-FE-237 / ADR-047). Mirrors `tenants-state.ts` `getTenantsListState()`
 * branch-for-branch — the pattern-of-record for an IAM admin surface whose
 * LIST read itself requires an elevated capability (here: `org.manage`).
 *
 * Resilience boundary (admin-api.md § org-node / § 2.5):
 *   - 401 → `redirect('/login')` (clean re-login; no partial authed state).
 *   - `NO_ACTIVE_TENANT` → a distinct "select a tenant" gate (never an empty
 *     `X-Tenant-Id`; a SUPER_ADMIN selects `*` via the tenant switcher).
 *   - 403 (`PERMISSION_DENIED` — lacks `org.manage`) → a `permissionError`
 *     state carrying the producer code so the page renders an inline
 *     "org.manage required" section (no crash, no re-login loop). Since the
 *     LIST read is `org.manage`-gated, this single check covers both view and
 *     mutate authority.
 *   - `OrgNodesUnavailableError` (503 / timeout / network) / anything else →
 *     DEGRADED (the org-hierarchy section renders a degraded notice; the
 *     console shell + every other IAM surface stay intact).
 */
export interface OrgHierarchyState {
  nodes: OrgNode[];
  degraded: boolean;
  noTenant: boolean;
  permissionError: { code: string; message: string } | null;
}

export async function getOrgHierarchyState(): Promise<OrgHierarchyState> {
  const tenant = await getActiveTenant();
  if (!tenant) {
    return {
      nodes: [],
      degraded: false,
      noTenant: true,
      permissionError: null,
    };
  }

  try {
    const list = await listOrgNodes();
    return {
      nodes: list.items,
      degraded: false,
      noTenant: false,
      permissionError: null,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return {
        nodes: [],
        degraded: false,
        noTenant: true,
        permissionError: null,
      };
    }
    if (err instanceof ApiError && err.status === 403) {
      return {
        nodes: [],
        degraded: false,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
      };
    }
    if (err instanceof OrgNodesUnavailableError) {
      return {
        nodes: [],
        degraded: true,
        noTenant: false,
        permissionError: null,
      };
    }
    return {
      nodes: [],
      degraded: true,
      noTenant: false,
      permissionError: null,
    };
  }
}
