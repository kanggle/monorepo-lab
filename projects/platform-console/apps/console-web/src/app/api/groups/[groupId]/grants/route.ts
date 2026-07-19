import { NextResponse } from 'next/server';
import {
  listGrants,
  addGrants,
} from '@/features/operator-groups/api/operator-groups-api';
import {
  AddGrantsBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin group GRANTS proxy — GET (list templates) + POST (add, 201)
 * (TASK-PC-FE-250 / ADR-MONO-046). GET is a READ (no mutation headers); POST
 * carries the reason as `X-Operator-Reason` + the idempotency key as
 * `Idempotency-Key` server-side. A server 403 `ROLE_GRANT_FORBIDDEN` /
 * 400 `ROLE_NOT_FOUND` / 409 `GROUP_GRANT_ALREADY_EXISTS` /
 * 422 `GROUP_GRANT_NO_ESCALATION` is surfaced verbatim via `mapError`.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ groupId: string }> },
) {
  const requestId = newRequestId();
  const { groupId } = await params;
  try {
    const result = await listGrants(groupId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function POST(
  req: Request,
  { params }: { params: Promise<{ groupId: string }> },
) {
  const requestId = newRequestId();
  const { groupId } = await params;
  let body;
  try {
    body = AddGrantsBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await addGrants(
      groupId,
      { roles: body.roles, tenantAssignments: body.tenantAssignments },
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
