import { NextResponse } from 'next/server';
import { getUser } from '@/features/ecommerce-ops/api/users-api';
import { mapEcommerceError, newRequestId } from '../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **user detail** read proxy
 * (console-integration-contract § 2.4.10 — users READ surface):
 * `GET /admin/users/{userId}`.
 *
 * Used by the `useUser` client hook for refetch. The domain-facing IAM OIDC
 * token is attached server-side in `users-api.ts`. No tenant header —
 * ecommerce resolves tenant from the JWT claim (§ 2.4.10).
 * READ-ONLY: NO POST/PATCH/DELETE handlers.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getUser(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
