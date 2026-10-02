import { NextResponse } from 'next/server';
import { inviteSellerMember } from '@/features/ecommerce-ops/api/sellers-api';
import { InviteSellerMemberBodySchema } from '@/features/ecommerce-ops/api/seller-types';
import {
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce seller INVITE proxy (TASK-MONO-752 — ADR-MONO-079 D5):
 *   POST /api/ecommerce/sellers/{id}/invitations { email } → 201
 *        { invitationId, email, expiresAt, token }
 *
 * The token is the only copy the operator will ever see (the producer stores its
 * hash). Zod-validated before the upstream; domain-facing IAM OIDC token
 * server-side; NO X-Tenant-Id; NO Idempotency-Key (a second invite is a second,
 * independent invitation — the confirm-free form is the operator's own action).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(InviteSellerMemberBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await inviteSellerMember(id, body);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
