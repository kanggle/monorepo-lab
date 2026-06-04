import { NextResponse } from 'next/server';
import { moveDepartmentParent } from '@/features/erp-ops/api/erp-api';
import { MoveDepartmentParentBodySchema } from '@/features/erp-ops/api/types';
import { mapErpError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp department MOVE-PARENT proxy (write PILOT —
 * TASK-PC-FE-046 / § 2.4.8). Forwards to the UNCHANGED producer
 * `POST /api/erp/masterdata/departments/{id}/move-parent` via
 * `moveDepartmentParent()`. `newParentId` may be `null` (promote to
 * root). `effectiveFrom` required; `reason` optional (≤256, producer
 * slot — rides in the BODY, never an `X-Operator-Reason` header).
 * `Idempotency-Key` required. A `409 MASTERDATA_PARENT_CYCLE` /
 * `422 MASTERDATA_EFFECTIVE_PERIOD_INVALID` / `404` /
 * `403 PERMISSION_DENIED` passes through inline-actionably.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  let body: ReturnType<typeof MoveDepartmentParentBodySchema.parse>;
  try {
    body = MoveDepartmentParentBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      {
        code: 'VALIDATION_ERROR',
        message: 'invalid move-parent body',
      },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await moveDepartmentParent(id, input, idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
