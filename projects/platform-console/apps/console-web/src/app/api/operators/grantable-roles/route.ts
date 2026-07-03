import { NextResponse } from 'next/server';
import { getGrantableRoles } from '@/features/operators/api/operators-api';
import { mapError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin operators GRANTABLE-ROLES proxy (GET)
 * (feat/iam-grantable-roles-filter). Forwards to
 * `GET /api/admin/operators/grantable-roles` with the HttpOnly operator
 * token + active tenant attached server-side (same pattern as every other
 * operators proxy route — `operators-api.ts` is the single backend entry
 * point; no browser-direct IAM call, architecture.md § Forbidden
 * Dependencies).
 *
 * READ; NO mutation headers. 401 → 401 (re-login); 503/timeout → 503
 * (operators section degrades only); 403 PERMISSION_DENIED / other producer
 * errors → inline actionable (passthrough via the shared `mapError`).
 *
 * This route exists for parity with the rest of the operators surface (a
 * same-origin entry point for any future client-side consumer); the initial
 * `/operators` page SSR render calls the fail-graceful
 * `getGrantableRolesOrNull()` directly (never through this route) so a
 * grantable-roles outage never blocks the page — it just falls back to
 * offering every `KNOWN_OPERATOR_ROLES` checkbox.
 */
export async function GET() {
  const requestId = newRequestId();
  try {
    const roles = await getGrantableRoles();
    return NextResponse.json({ roles });
  } catch (err) {
    return mapError(err, requestId);
  }
}
