import { NextResponse } from 'next/server';
import { bulkLockAccounts } from '@/features/accounts/api/accounts-api';
import {
  BulkLockBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin bulk-lock proxy → IAM `POST /api/admin/accounts/bulk-lock`.
 * Partial-failure aware: the producer returns 200 with per-account
 * `results[]`; the client renders each outcome (no all-or-nothing
 * implication). Reason-gated (≥ 8 chars producer-side) + idempotency-keyed
 * (the same key reused only for an exact same confirmed retry).
 */
export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = BulkLockBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await bulkLockAccounts(
      body.accountIds,
      { reason: body.reason, ticketId: body.ticketId },
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
