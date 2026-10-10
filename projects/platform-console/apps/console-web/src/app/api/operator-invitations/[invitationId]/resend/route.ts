import { NextResponse } from 'next/server';
import { resendOperatorInvitation } from '@/features/operators/api/operators-api';
import {
  InvitationReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../operators/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin resend-invitation proxy (TASK-MONO-772 S5 — § 2.4.3 row 14).
 * → producer `POST /api/admin/operator-invitations/{id}:resend` — a NEW token
 * on the SAME invitation; the previously mailed link stops working.
 *
 * PER-ENDPOINT HEADER MATRIX: `X-Operator-Reason` ONLY — **NO
 * `Idempotency-Key`** (a retried resend just rotates the token again). `200`
 * even when the mail failed (`delivery.status = FAILED_*`). The response never
 * carries a token or link (OD-4).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ invitationId: string }> },
) {
  const requestId = newRequestId();
  const { invitationId } = await params;
  let body;
  try {
    body = InvitationReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await resendOperatorInvitation(invitationId, body.reason);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
