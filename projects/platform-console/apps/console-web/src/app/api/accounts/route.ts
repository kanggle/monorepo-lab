import { NextResponse } from 'next/server';
import { searchAccounts } from '@/features/accounts/api/accounts-api';
import { ApiError, AccountsUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin accounts search proxy for client components (the typed API
 * client's single backend entry point — no browser-direct IAM call,
 * architecture.md § Forbidden Dependencies / contract § 2.3). The HttpOnly
 * operator token + active tenant are attached server-side in
 * `searchAccounts()`.
 *
 * 401/403 → client API client triggers refresh→re-login.
 * 503/timeout → 503 so the client renders a degraded accounts section.
 * 400 NO_ACTIVE_TENANT → 400 so the client renders the tenant gate.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const url = new URL(req.url);
  const email = url.searchParams.get('email') ?? undefined;
  const page = url.searchParams.has('page')
    ? Number(url.searchParams.get('page'))
    : undefined;
  const size = url.searchParams.has('size')
    ? Number(url.searchParams.get('size'))
    : undefined;

  try {
    const result = await searchAccounts({ email, page, size });
    return NextResponse.json(result);
  } catch (err) {
    if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
      return NextResponse.json(
        { code: err.code, message: 'session expired' },
        { status: err.status },
      );
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return NextResponse.json(
        { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
        { status: 400 },
      );
    }
    if (err instanceof ApiError) {
      return NextResponse.json(
        { code: err.code, message: err.message },
        { status: err.status },
      );
    }
    if (err instanceof AccountsUnavailableError) {
      logger.warn('accounts_search_proxy_degraded', {
        requestId,
        reason: err.reason,
      });
      return NextResponse.json(
        { code: err.code, message: 'accounts unavailable' },
        { status: 503 },
      );
    }
    logger.error('accounts_search_proxy_error', { requestId });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: 'accounts unavailable' },
      { status: 503 },
    );
  }
}
