import { NextResponse } from 'next/server';
import { terminatePartnership } from '@/features/partnerships/api/partnerships-api';
import {
  ReasonBodySchema,
  IdParamSchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin partnership TERMINATE proxy (POST) — REST segment mapping to the
 * producer colon verb `{id}:terminate` (TASK-PC-FE-187). Either party may
 * terminate (host withdraws a PENDING invite / either party ends an ACTIVE or
 * SUSPENDED relationship). TERMINATED is terminal (idempotent no-op 200).
 * Cascade-revoke (D6): a one-shot cascade zeroes every participant's host-reach.
 *
 * 401 → 401; 403 → inline; 404 → inline;
 * 409 PARTNERSHIP_TRANSITION_INVALID → inline; 503/timeout → 503.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  const idOk = IdParamSchema.safeParse(id);
  if (!idOk.success) return badRequest();

  let body;
  try {
    body = ReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await terminatePartnership(idOk.data, body.reason);
    return NextResponse.json(result, { status: 200 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
