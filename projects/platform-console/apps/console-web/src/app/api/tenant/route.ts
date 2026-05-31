import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { fetchRegistry } from '@/shared/api/registry-client';
import {
  RegistryUnavailableError,
  ApiError,
  AssumeTenantError,
} from '@/shared/api/errors';
import {
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  tokenCookieOpts,
  getAccessToken,
} from '@/shared/lib/session';
import { exchangeForAssumedToken } from '@/shared/lib/assume-tenant-exchange';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Tenant switcher endpoint (multi-tenant trait + ADR-MONO-020 D4 / § 2.7
 * active-tenant switcher → assume-tenant flow).
 *
 * On selection the route (1) keeps the existing defence-in-depth registry
 * allow-check (the requested tenant MUST appear in some product's `tenants`
 * array in the operator's own GAP-scoped registry response —
 * console-registry-api.md § Multi-tenant isolation; M2 layer 2), then (2)
 * drives the server-side **assume-tenant exchange** (subject = the operator's
 * base GAP OIDC access token, audience = the selected tenant) to mint a
 * short-lived domain-facing token re-scoped to the selected customer
 * (`tenant_id=<selected>` + `entitled_domains=<selected's subs>`), and (3)
 * stores the assumed token + `TENANT_COOKIE` **atomically**. Setting
 * X-Tenant-Id alone does nothing — the domain gates trust the SIGNED claims,
 * so the switch MUST mint the assumed token (ADR-020 D4 / the A↔B proof).
 *
 * Fail-closed switch (§ 2.7 / AC-3): assume-tenant `denied` (the D2
 * assignment gate / subject invalid) → 403, NO cookie change (prior selection
 * + assumed token preserved); `invalid` → 422; `unavailable` → 503; missing
 * base token → 401. Never logs the token; never falls back to the base token
 * on the selected-tenant boundary.
 *
 * `tenant=''` clears the selection (single/zero-tenant operators) — deletes
 * BOTH the tenant cookie AND the assumed-token cookie (they are coupled).
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
    // Clearing the active tenant clears the coupled assumed token (the
    // assumed token is only ever valid for the current active tenant).
    jar.delete(TENANT_COOKIE);
    jar.delete(ASSUMED_TOKEN_COOKIE);
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

  // ── Assume-tenant exchange (ADR-MONO-020 D4 / § 2.7) ────────────────────
  // Re-scope the domain-facing credential to the selected customer. The base
  // GAP OIDC access token is the `subject_token` only (never logged/returned).
  const baseToken = await getAccessToken();
  if (!baseToken) {
    // No GAP session ⇒ cannot assume; the caller must re-login (no partial
    // state, no cookie change).
    logger.warn('tenant_switch_no_base_token', { requestId });
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'session not authenticated' },
      { status: 401 },
    );
  }

  let assumed;
  try {
    assumed = await exchangeForAssumedToken(baseToken, tenant);
  } catch (err) {
    if (err instanceof AssumeTenantError) {
      // Fail-closed: the prior selection + assumed token are PRESERVED on
      // every failure (no cookie change). Never fall back to the base token
      // on the selected-tenant boundary.
      if (err.reason === 'denied') {
        logger.warn('tenant_switch_assume_denied', { requestId, tenant });
        return NextResponse.json(
          { code: 'TENANT_FORBIDDEN', message: 'tenant not selectable' },
          { status: 403 },
        );
      }
      if (err.reason === 'invalid') {
        return NextResponse.json(
          { code: 'VALIDATION_ERROR', message: 'invalid tenant selection' },
          { status: 422 },
        );
      }
      logger.warn('tenant_switch_assume_unavailable', { requestId, tenant });
      return NextResponse.json(
        { code: 'DOWNSTREAM_ERROR', message: 'assume-tenant unavailable' },
        { status: 503 },
      );
    }
    logger.error('tenant_switch_assume_error', { requestId });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: 'assume-tenant failed' },
      { status: 503 },
    );
  }

  // Atomic: set BOTH the active tenant and the assumed token (scoped to that
  // tenant by construction). The assumed token's lifetime tracks `expiresIn`.
  jar.set(TENANT_COOKIE, tenant, tokenCookieOpts);
  jar.set(ASSUMED_TOKEN_COOKIE, assumed.accessToken, {
    ...tokenCookieOpts,
    maxAge: assumed.expiresIn,
  });
  logger.info('tenant_switched', { requestId, tenant });
  return NextResponse.json({ ok: true, activeTenant: tenant });
}
