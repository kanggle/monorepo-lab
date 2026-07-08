import { NextResponse } from 'next/server';
import { listTenants, createTenant } from '@/features/tenants/api/tenants-api';
import type { TenantStatus, TenantType } from '@/features/tenants';
import {
  CreateTenantBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin tenant-management LIST proxy (GET) + CREATE proxy (POST) for
 * client components (TASK-PC-FE-226) — the typed API client's single backend
 * entry point (no browser-direct IAM call, architecture.md § Forbidden
 * Dependencies / contract § 2.3). The HttpOnly operator token + active tenant
 * are attached server-side in the api layer.
 *
 * - GET  → `listTenants` (read; SUPER_ADMIN only — a non-SUPER_ADMIN gets
 *   403 here, same as every other tenant endpoint).
 * - POST → `createTenant` (mutation; `X-Operator-Reason` attached
 *   server-side; `Idempotency-Key` forwarded only when the client supplies
 *   one — producer-recommended, not required).
 *
 * 401 → 401 (re-login); 503/timeout → 503 (tenants section degrades only);
 * 400 NO_ACTIVE_TENANT → 400 (tenant gate); 403/404/409/400 producer →
 * inline actionable.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const url = new URL(req.url);
  const statusParam = url.searchParams.get('status');
  const status =
    statusParam === 'ACTIVE' || statusParam === 'SUSPENDED'
      ? (statusParam as TenantStatus)
      : undefined;
  const tenantTypeParam = url.searchParams.get('tenantType');
  const tenantType =
    tenantTypeParam === 'B2C_CONSUMER' || tenantTypeParam === 'B2B_ENTERPRISE'
      ? (tenantTypeParam as TenantType)
      : undefined;
  const page = url.searchParams.has('page')
    ? Number(url.searchParams.get('page'))
    : undefined;
  const size = url.searchParams.has('size')
    ? Number(url.searchParams.get('size'))
    : undefined;

  try {
    const result = await listTenants({ status, tenantType, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = CreateTenantBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await createTenant(
      {
        tenantId: body.tenantId,
        displayName: body.displayName,
        tenantType: body.tenantType,
      },
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
