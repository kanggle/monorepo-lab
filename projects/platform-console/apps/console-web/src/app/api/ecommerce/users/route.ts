import { NextResponse } from 'next/server';
import { listUsers } from '@/features/ecommerce-ops/api/users-api';
import { mapEcommerceError, newRequestId } from '../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **user list** read proxy
 * (console-integration-contract § 2.4.10 — users READ surface):
 * `GET /admin/users?status&email&page&size`.
 *
 * Used by the `useUsers` client hook for re-query on filter / pagination.
 * The domain-facing IAM OIDC token is attached server-side in `users-api.ts`.
 * No tenant header — ecommerce resolves tenant from the JWT claim (§ 2.4.10).
 * READ-ONLY: NO POST/PATCH/DELETE handlers.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const status = searchParams.get('status') ?? undefined;
  const email = searchParams.get('email') ?? undefined;
  const page = searchParams.has('page')
    ? Number(searchParams.get('page'))
    : undefined;
  const size = searchParams.has('size')
    ? Number(searchParams.get('size'))
    : undefined;

  try {
    const result = await listUsers({ status, email, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
