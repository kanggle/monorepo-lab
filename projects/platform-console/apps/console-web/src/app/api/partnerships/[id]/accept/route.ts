import { NextResponse } from 'next/server';
import { acceptPartnership } from '@/features/partnerships/api/partnerships-api';
import {
  ReasonBodySchema,
  IdParamSchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin partnership ACCEPT proxy (POST) — REST segment mapping to the
 * producer colon verb `{id}:accept` (TASK-PC-FE-187). partner-side accept only
 * (the producer enforces the D2 side rule). `X-Operator-Reason` is attached
 * server-side from the body `reason`; the tenant is the server active tenant.
 *
 * 401 → 401; 403 PARTNERSHIP_SCOPE_DENIED → inline; 404 → inline;
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
    const result = await acceptPartnership(idOk.data, body.reason);
    return NextResponse.json(result, { status: 200 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
