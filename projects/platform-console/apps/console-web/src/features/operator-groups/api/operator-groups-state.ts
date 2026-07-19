import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, GroupsUnavailableError } from '@/shared/api/errors';
import { listGroups, getGroup } from './operator-groups-api';
import type { Group, GroupPage, GroupListParams } from './types';

/**
 * Server-side operator-groups SSR state for the `(console)/operator-groups`
 * route(s) (TASK-PC-FE-250 / ADR-MONO-046). Mirrors `tenants-state.ts`
 * `getTenantsListState()` / `org-nodes-state.ts` `getOrgHierarchyState()`
 * branch-for-branch — the pattern-of-record for an IAM admin surface whose LIST
 * read itself requires an elevated capability (here: `group.manage`).
 *
 * Resilience boundary (admin-api.md § Operator Group Management / § 2.5):
 *   - 401 → `redirect('/login')` (clean re-login; no partial authed state).
 *   - `NO_ACTIVE_TENANT` → a distinct "select a tenant" gate (never an empty
 *     `X-Tenant-Id`; a SUPER_ADMIN selects `*` via the tenant switcher).
 *   - 403 (`PERMISSION_DENIED` / `TENANT_SCOPE_DENIED` — lacks `group.manage`)
 *     → a `permissionError` state carrying the producer code so the page
 *     renders an inline "group.manage required" section (no crash, no re-login
 *     loop). Since the LIST read is `group.manage`-gated, this single check
 *     covers both view and mutate authority.
 *   - `GroupsUnavailableError` (503 / timeout / network) / anything else →
 *     DEGRADED (the 운영자 그룹 section renders a degraded notice; the console
 *     shell + every other IAM surface stay intact).
 */
export interface OperatorGroupsState {
  page: GroupPage | null;
  degraded: boolean;
  noTenant: boolean;
  permissionError: { code: string; message: string } | null;
  query: GroupListParams;
}

export async function getOperatorGroupsState(
  query: GroupListParams = {},
): Promise<OperatorGroupsState> {
  const tenant = await getActiveTenant();
  if (!tenant) {
    return {
      page: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
      query,
    };
  }

  try {
    const page = await listGroups(query);
    return {
      page,
      degraded: false,
      noTenant: false,
      permissionError: null,
      query,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return {
        page: null,
        degraded: false,
        noTenant: true,
        permissionError: null,
        query,
      };
    }
    if (err instanceof ApiError && err.status === 403) {
      return {
        page: null,
        degraded: false,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
        query,
      };
    }
    if (err instanceof GroupsUnavailableError) {
      return {
        page: null,
        degraded: true,
        noTenant: false,
        permissionError: null,
        query,
      };
    }
    return {
      page: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
      query,
    };
  }
}

/**
 * Server-side group DETAIL SSR state for `(console)/operator-groups/[groupId]`.
 * Sibling of {@link getOperatorGroupsState}; additionally distinguishes
 * `404 GROUP_NOT_FOUND` so the page can call `notFound()` (mirrors the tenant
 * detail precedent). The members / grants panels load client-side (React Query)
 * from the same-origin proxy once the shell renders.
 */
export interface OperatorGroupDetailState {
  group: Group | null;
  degraded: boolean;
  noTenant: boolean;
  permissionError: { code: string; message: string } | null;
  notFound: boolean;
}

export async function getOperatorGroupDetailState(
  groupId: string,
): Promise<OperatorGroupDetailState> {
  const tenant = await getActiveTenant();
  if (!tenant) {
    return {
      group: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
      notFound: false,
    };
  }

  try {
    const group = await getGroup(groupId);
    return {
      group,
      degraded: false,
      noTenant: false,
      permissionError: null,
      notFound: false,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return {
        group: null,
        degraded: false,
        noTenant: true,
        permissionError: null,
        notFound: false,
      };
    }
    if (err instanceof ApiError && err.status === 404) {
      return {
        group: null,
        degraded: false,
        noTenant: false,
        permissionError: null,
        notFound: true,
      };
    }
    if (err instanceof ApiError && err.status === 403) {
      return {
        group: null,
        degraded: false,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
        notFound: false,
      };
    }
    if (err instanceof GroupsUnavailableError) {
      return {
        group: null,
        degraded: true,
        noTenant: false,
        permissionError: null,
        notFound: false,
      };
    }
    return {
      group: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
      notFound: false,
    };
  }
}
