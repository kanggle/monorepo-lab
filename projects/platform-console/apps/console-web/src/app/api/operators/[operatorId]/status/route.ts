import { NextResponse } from 'next/server';
import { changeOperatorStatus } from '@/features/operators/api/operators-api';
import {
  ChangeStatusBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin change-status proxy (ACTIVE ↔ SUSPENDED). Suspending is
 * high-impact (the producer immediately invalidates the target operator's
 * sessions) — the client gates it behind a reason + elevated confirm. The
 * client supplies an operator-entered `reason`; the operator token +
 * tenant are attached server-side.
 *
 * PER-ENDPOINT HEADER MATRIX (§ 2.4.3): the api layer attaches
 * `X-Operator-Reason` ONLY — there is **NO `Idempotency-Key`** (the
 * producer does not list it; idempotent PATCH). This proxy carries no
 * idempotency key and never fabricates a reason.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ operatorId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId } = await params;
  let body;
  try {
    body = ChangeStatusBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await changeOperatorStatus(
      operatorId,
      body.status,
      body.reason,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
