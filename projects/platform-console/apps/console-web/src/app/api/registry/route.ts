import { NextResponse } from 'next/server';
import { fetchRegistry } from '@/shared/api/registry-client';
import { RegistryUnavailableError, ApiError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin registry proxy for client components (the typed API client's
 * single backend entry point — no browser-direct IAM call,
 * architecture.md § Forbidden Dependencies / contract § 2.3). Attaches the
 * HttpOnly operator token server-side via `fetchRegistry()`.
 *
 * 401 → client API client triggers refresh→re-login.
 * 503 → client keeps the last good / degraded catalog (resilience).
 */
export async function GET() {
  const requestId = newRequestId();
  try {
    const registry = await fetchRegistry();
    return NextResponse.json(registry);
  } catch (err) {
    if (
      (err instanceof ApiError && err.status === 401) ||
      (err instanceof RegistryUnavailableError && err.reason === 'unauthorized')
    ) {
      return NextResponse.json(
        { code: 'TOKEN_INVALID', message: 'session expired' },
        { status: 401 },
      );
    }
    logger.warn('registry_proxy_degraded', { requestId });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: 'registry unavailable' },
      { status: 503 },
    );
  }
}
