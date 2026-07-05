import { NextResponse } from 'next/server';
import { reactivatePartnership } from '@/features/partnerships/api/partnerships-api';
import {
  ReasonBodySchema,
  IdParamSchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin partnership REACTIVATE proxy (POST) — REST segment mapping to the
 * producer colon verb `{id}:reactivate` (TASK-PC-FE-187). Either party may
 * reactivate a SUSPENDED partnership (D2 mutuality). The derived participant
 * reach resumes on the next assume-tenant issuance.
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
    const result = await reactivatePartnership(idOk.data, body.reason);
    return NextResponse.json(result, { status: 200 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
