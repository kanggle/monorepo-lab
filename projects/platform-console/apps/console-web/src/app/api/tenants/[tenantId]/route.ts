import { NextResponse } from 'next/server';
import { getTenant, updateTenant } from '@/features/tenants/api/tenants-api';
import {
  UpdateTenantBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin tenant DETAIL read proxy (GET) + UPDATE proxy (PATCH)
 * (TASK-PC-FE-226 / admin-api.md § "Tenant Lifecycle (TASK-BE-256)"). Used by
 * the `useTenant` client hook (detail refetch after the SSR seed) and the
 * `useUpdateTenant` mutation. The operator token + active tenant +
 * `X-Operator-Reason` (update only) are attached server-side in the api
 * layer; the proxy never re-derives the header set.
 *
 * 401 → 401 (re-login); 403 → inline (not SUPER_ADMIN); 404
 * TENANT_NOT_FOUND → inline; 503/timeout → 503 (tenants section degrades
 * only).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ tenantId: string }> },
) {
  const requestId = newRequestId();
  const { tenantId } = await params;
  try {
    const result = await getTenant(tenantId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ tenantId: string }> },
) {
  const requestId = newRequestId();
  const { tenantId } = await params;
  let body;
  try {
    body = UpdateTenantBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await updateTenant(
      tenantId,
      { displayName: body.displayName, status: body.status },
      body.reason,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
