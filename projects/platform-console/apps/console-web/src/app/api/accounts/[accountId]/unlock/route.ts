import { NextResponse } from 'next/server';
import { unlockAccount } from '@/features/accounts/api/accounts-api';
import {
  MutationBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/** Same-origin unlock proxy (reason-gated, idempotency-keyed). */
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
    const result = await unlockAccount(
      accountId,
      { reason: body.reason, ticketId: body.ticketId },
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
