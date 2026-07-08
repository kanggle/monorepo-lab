import { clampPageSize } from '@/shared/lib/pagination';
import {
  TenantSchema,
  type Tenant,
  TenantPageSchema,
  type TenantPage,
  type TenantListParams,
  type CreateTenantInput,
  type UpdateTenantInput,
} from './types';
import { callGapTenants, TENANTS_PREFIX } from './tenants-client';

/**
 * Server-side IAM tenant-management API functions (TASK-PC-FE-226,
 * admin-api.md § "Tenant Lifecycle (TASK-BE-256)"). Every call goes through
 * {@link callGapTenants} (the hardened `callAdminGateway` core) — server-only
 * by construction (imported exclusively from server components and
 * `runtime = 'nodejs'` route handlers).
 */

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 100;

// ---------------------------------------------------------------------------
// 1. list — GET /api/admin/tenants (SUPER_ADMIN only)
// ---------------------------------------------------------------------------

export async function listTenants(
  params: TenantListParams = {},
): Promise<TenantPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.tenantType) qs.set('tenantType', params.tenantType);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set(
    'size',
    String(clampPageSize(params.size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE)),
  );
  return callGapTenants(
    { method: 'GET', path: `${TENANTS_PREFIX}?${qs.toString()}` },
    (json) => TenantPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 2. get — GET /api/admin/tenants/{tenantId}
// ---------------------------------------------------------------------------

export async function getTenant(tenantId: string): Promise<Tenant> {
  return callGapTenants(
    {
      method: 'GET',
      path: `${TENANTS_PREFIX}/${encodeURIComponent(tenantId)}`,
    },
    (json) => TenantSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 3. create — POST /api/admin/tenants
//    HEADERS: X-Operator-Reason (general /api/admin/* audit rule).
//    Idempotency-Key is producer-RECOMMENDED (not required) — forwarded only
//    when the caller supplies one.
// ---------------------------------------------------------------------------

export async function createTenant(
  input: CreateTenantInput,
  reason: string,
  idempotencyKey?: string,
): Promise<Tenant> {
  return callGapTenants(
    {
      method: 'POST',
      path: TENANTS_PREFIX,
      reason,
      idempotencyKey,
      body: {
        tenantId: input.tenantId,
        displayName: input.displayName,
        tenantType: input.tenantType,
      },
    },
    (json) => TenantSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 4. update — PATCH /api/admin/tenants/{tenantId}
//    HEADERS: X-Operator-Reason ONLY (no Idempotency-Key — partial PATCH is
//    naturally idempotent, mirroring the operators roles/status precedent).
// ---------------------------------------------------------------------------

export async function updateTenant(
  tenantId: string,
  input: UpdateTenantInput,
  reason: string,
): Promise<Tenant> {
  const body: Record<string, unknown> = {};
  if (input.displayName !== undefined) body.displayName = input.displayName;
  if (input.status !== undefined) body.status = input.status;
  return callGapTenants(
    {
      method: 'PATCH',
      path: `${TENANTS_PREFIX}/${encodeURIComponent(tenantId)}`,
      reason,
      body,
    },
    (json) => TenantSchema.parse(json),
  );
}
