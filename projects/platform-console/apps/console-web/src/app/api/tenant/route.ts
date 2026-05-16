import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { fetchRegistry } from '@/shared/api/registry-client';
import { RegistryUnavailableError, ApiError } from '@/shared/api/errors';
import { TENANT_COOKIE, tokenCookieOpts } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Tenant switcher endpoint (multi-tenant trait).
 *
 * Sets the active session `tenant_id` cookie. Defence-in-depth: the requested
 * tenant MUST appear in some product's `tenants` array in the operator's own
 * registry response (which GAP already scopes to the operator —
 * console-registry-api.md § Multi-tenant isolation). The console therefore
 * never sends a tenant the operator lacks; GAP remains the final authority
 * for cross-tenant rejection (architecture.md § Boundary Rules; M2 layer 2).
 *
 * `tenant=''` clears the selection (single/zero-tenant operators).
 */

const BodySchema = z.object({ tenant: z.string() });

export async function POST(req: Request) {
  const requestId = newRequestId();
  const jar = await cookies();

  let tenant: string;
  try {
    tenant = BodySchema.parse(await req.json()).tenant;
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'tenant is required' },
      { status: 422 },
    );
  }

  if (tenant === '') {
    jar.delete(TENANT_COOKIE);
    return NextResponse.json({ ok: true, activeTenant: null });
  }

  let allowed: Set<string>;
  try {
    const registry = await fetchRegistry();
    allowed = new Set(
      registry.products.flatMap((p) => (p.available ? p.tenants : [])),
    );
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      return NextResponse.json(
        { code: 'TOKEN_INVALID', message: 'session expired' },
        { status: 401 },
      );
    }
    if (
      err instanceof RegistryUnavailableError &&
      err.reason === 'unauthorized'
    ) {
      return NextResponse.json(
        { code: 'TOKEN_INVALID', message: 'session expired' },
        { status: 401 },
      );
    }
    logger.warn('tenant_switch_registry_unavailable', { requestId });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: 'registry unavailable' },
      { status: 503 },
    );
  }

  if (!allowed.has(tenant)) {
    // Cross-tenant write intent → explicit 403 (multi-tenant M3).
    logger.warn('tenant_switch_forbidden', { requestId, tenant });
    return NextResponse.json(
      { code: 'TENANT_FORBIDDEN', message: 'tenant not selectable' },
      { status: 403 },
    );
  }

  jar.set(TENANT_COOKIE, tenant, tokenCookieOpts);
  logger.info('tenant_switched', { requestId, tenant });
  return NextResponse.json({ ok: true, activeTenant: tenant });
}
