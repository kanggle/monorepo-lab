import { NextResponse } from 'next/server';
import { listSellerMembers } from '@/features/ecommerce-ops/api/sellers-api';
import { mapEcommerceError, newRequestId } from '../../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce seller MEMBERS proxy (TASK-MONO-752 — ADR-MONO-079 D5):
 *   GET /api/ecommerce/sellers/{id}/members → { members, invitations }
 *
 * Domain-facing IAM OIDC token attached server-side; NO X-Tenant-Id; NO
 * Idempotency-Key. The producer never returns invitation tokens here.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    return NextResponse.json(await listSellerMembers(id));
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
