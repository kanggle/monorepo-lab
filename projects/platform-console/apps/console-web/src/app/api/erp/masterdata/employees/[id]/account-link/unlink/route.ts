import { NextResponse } from 'next/server';
import { unlinkEmployeeAccount } from '@/features/erp-ops/api/erp-api';
import { UnlinkAccountBodySchema } from '@/features/erp-ops/api/types';
import { mapErpError, newRequestId } from '../../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp employee ↔ IAM account UNLINK proxy (POST —
 * TASK-PC-FE-318). `erp.write` + department data scope, OR the linked account
 * owner (producer-enforced). `reason` (≤256) required in the BODY;
 * `Idempotency-Key` required. 409 `EMPLOYEE_LINK_CONFLICT`
 * (`details.cause = "not_linked"`) passes through with its `details`.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  let body: ReturnType<typeof UnlinkAccountBodySchema.parse>;
  try {
    body = UnlinkAccountBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid unlink body' },
      { status: 400 },
    );
  }
  try {
    const result = await unlinkEmployeeAccount(
      id,
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
