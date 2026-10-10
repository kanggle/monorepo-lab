import { NextResponse } from 'next/server';
import { cancelOperatorInvitation } from '@/features/operators/api/operators-api';
import {
  InvitationReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../operators/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin cancel-invitation proxy (TASK-MONO-772 S5 — § 2.4.3 row 13).
 * → producer `POST /api/admin/operator-invitations/{id}:cancel`.
 *
 * PER-ENDPOINT HEADER MATRIX: `X-Operator-Reason` ONLY — **NO
 * `Idempotency-Key`** (a re-cancel is a 200 no-op producer-side). The body
 * carries only the operator-entered reason; it is never fabricated here.
 * `409 OPERATOR_INVITATION_NOT_PENDING` / `404 OPERATOR_INVITATION_NOT_FOUND`
 * pass through (the screen refreshes its list on them).
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
    const result = await cancelOperatorInvitation(invitationId, body.reason);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
