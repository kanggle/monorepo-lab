import { NextResponse } from 'next/server';
import { gdprDeleteAccount } from '@/features/accounts/api/accounts-api';
import {
  MutationBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin GDPR-delete proxy. This is irreversible: the client UI MUST
 * have double-confirmed + collected a typed confirmation + a non-empty
 * operator reason before reaching here (the reason-capture + double-confirm
 * gate). The server is the fail-safe — an empty reason is rejected in the
 * api layer (`REASON_REQUIRED`) and never fabricated.
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
    const result = await gdprDeleteAccount(
      accountId,
      { reason: body.reason, ticketId: body.ticketId },
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
