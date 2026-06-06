import { NextResponse } from 'next/server';
import { revokeDelegation } from '@/features/erp-ops/api/delegation-api';
import { RevokeDelegationBodySchema } from '@/features/erp-ops/api/delegation-types';
import { mapErpError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp delegation REVOKE proxy (write — POST only). Forwards the
 * `reason` (required) + console-generated `idempotencyKey` to the producer
 * `POST /api/erp/approval/delegations/{id}/revoke` via `revokeDelegation()`,
 * which attaches the domain-facing IAM token + `Idempotency-Key` server-side.
 *
 * POST only — no GET / PUT / PATCH / DELETE handler. The reason rides in the
 * BODY (NOT `X-Operator-Reason`). The producer error 404
 * `DELEGATION_NOT_FOUND` passes through `mapErpError` inline-actionably (the
 * console never pre-judges whether the grant still exists).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body: ReturnType<typeof RevokeDelegationBodySchema.parse>;
  try {
    body = RevokeDelegationBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      {
        code: 'VALIDATION_ERROR',
        message: 'invalid revoke-delegation body (reason required)',
      },
      { status: 400 },
    );
  }

  try {
    const result = await revokeDelegation(id, body.reason, body.idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
