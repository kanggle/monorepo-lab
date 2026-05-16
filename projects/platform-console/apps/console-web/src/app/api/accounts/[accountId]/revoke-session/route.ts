import { NextResponse } from 'next/server';
import { revokeSessions } from '@/features/accounts/api/accounts-api';
import {
  MutationBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin revoke-session proxy → GAP
 * `POST /api/admin/sessions/{accountId}/revoke`. Reason-gated +
 * idempotency-keyed (force-logout is destructive).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ accountId: string }> },
) {
  const requestId = newRequestId();
  const { accountId } = await params;
  let body;
  try {
    body = MutationBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await revokeSessions(
      accountId,
      { reason: body.reason },
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
