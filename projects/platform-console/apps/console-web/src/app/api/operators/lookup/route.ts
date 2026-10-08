import { NextResponse } from 'next/server';
import { lookupOperatorsByEmail } from '@/shared/api/iam-operator-lookup';
import { mapError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin IAM operator e-mail lookup proxy (GET) — TASK-MONO-777,
 * console contract § 2.4.8 «Account selection». Forwards to
 * `GET /api/admin/operators/lookup?email=&tenantId=<active>` with the HttpOnly
 * operator token attached server-side (no browser-direct IAM call).
 *
 * READ; no mutation headers. Errors go through the shared operators
 * `mapError`: 🔴 a producer `403` stays `403` (never rewritten to `401`, so the
 * client never mistakes it for an expired session); 401 → 401; 503/timeout →
 * 503. A blank `email` is refused here (400) without an upstream call.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const email = new URL(req.url).searchParams.get('email')?.trim() ?? '';
  if (email === '') {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'email is required' },
      { status: 400 },
    );
  }
  try {
    return NextResponse.json(await lookupOperatorsByEmail(email));
  } catch (err) {
    return mapError(err, requestId);
  }
}
