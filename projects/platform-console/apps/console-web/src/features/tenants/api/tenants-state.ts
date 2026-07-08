import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, TenantsUnavailableError } from '@/shared/api/errors';
import { listTenants, getTenant } from './tenants-api';
import type { Tenant, TenantPage, TenantListParams } from './types';

/**
 * Server-side tenant-management SSR state for the `(console)/tenants`
 * route(s) (TASK-PC-FE-226). Mirrors `operators-state.ts`
 * `getOperatorsListState()` — the pattern-of-record for an IAM admin surface
 * whose LIST read itself requires an elevated role (here: SUPER_ADMIN for
 * every one of the 4 tenant endpoints, not just the mutations).
 *
 * Resilience boundary (admin-api.md § Tenant Lifecycle / § 2.5):
 *   - 401 → `redirect('/login')` (clean re-login; no partial authed state).
 *   - `NO_ACTIVE_TENANT` → a distinct "select a tenant" gate (never an empty
 *     `X-Tenant-Id` — a SUPER_ADMIN selects `*` via the tenant switcher).
 *   - 403 (`PERMISSION_DENIED` / `TENANT_SCOPE_DENIED` — not SUPER_ADMIN) → a
 *     `permissionError` state carrying the producer code so the page renders
 *     an inline "not permitted" section (no crash, no re-login loop). Since
 *     the LIST read itself is SUPER_ADMIN-gated, this single check covers
 *     BOTH the "may I view tenants" AND "may I mutate tenants" questions —
 *     a non-SUPER_ADMIN operator never reaches a partially-functional screen.
 *   - 503 / timeout / network → DEGRADED (the tenants section renders a
 *     degraded notice; the console shell + every other IAM surface stay
 *     intact).
 */
export interface TenantsListState {
  page: TenantPage | null;
  degraded: boolean;
  noTenant: boolean;
  permissionError: { code: string; message: string } | null;
  query: TenantListParams;
}

export async function getTenantsListState(
  query: TenantListParams = {},
): Promise<TenantsListState> {
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
    const page = await listTenants(query);
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
    if (err instanceof TenantsUnavailableError) {
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
 * Server-side tenant DETAIL SSR state for `(console)/tenants/[tenantId]`.
 * Sibling of {@link getTenantsListState}; additionally distinguishes
 * `404 TENANT_NOT_FOUND` so the page can call `notFound()` (mirrors the
 * ecommerce seller-detail precedent).
 */
export interface TenantDetailState {
  tenant: Tenant | null;
  degraded: boolean;
  noTenant: boolean;
  permissionError: { code: string; message: string } | null;
  notFound: boolean;
}

export async function getTenantDetailState(
  tenantId: string,
): Promise<TenantDetailState> {
  const tenant = await getActiveTenant();
  if (!tenant) {
    return {
      tenant: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
      notFound: false,
    };
  }

  try {
    const detail = await getTenant(tenantId);
    return {
      tenant: detail,
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
        tenant: null,
        degraded: false,
        noTenant: true,
        permissionError: null,
        notFound: false,
      };
    }
    if (err instanceof ApiError && err.status === 404) {
      return {
        tenant: null,
        degraded: false,
        noTenant: false,
        permissionError: null,
        notFound: true,
      };
    }
    if (err instanceof ApiError && err.status === 403) {
      return {
        tenant: null,
        degraded: false,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
        notFound: false,
      };
    }
    if (err instanceof TenantsUnavailableError) {
      return {
        tenant: null,
        degraded: true,
        noTenant: false,
        permissionError: null,
        notFound: false,
      };
    }
    return {
      tenant: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
      notFound: false,
    };
  }
}
