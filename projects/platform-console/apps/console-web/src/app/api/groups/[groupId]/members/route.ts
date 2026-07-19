import { NextResponse } from 'next/server';
import {
  listMembers,
  addMember,
} from '@/features/operator-groups/api/operator-groups-api';
import {
  AddMemberBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin group MEMBERS proxy — GET (list) + POST (add, 201)
 * (TASK-PC-FE-250 / ADR-MONO-046). GET is a READ (no mutation headers); POST
 * carries the reason as `X-Operator-Reason` + the idempotency key as
 * `Idempotency-Key` server-side. A server 403 `ROLE_GRANT_FORBIDDEN` /
 * 422 `GROUP_MEMBER_TENANT_MISMATCH` / `GROUP_GRANT_NO_ESCALATION` /
 * 409 `GROUP_MEMBER_ALREADY_EXISTS` is surfaced verbatim via `mapError`.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ groupId: string }> },
) {
  const requestId = newRequestId();
  const { groupId } = await params;
  try {
    const result = await listMembers(groupId);
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
    body = AddMemberBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await addMember(
      groupId,
      { operatorId: body.operatorId },
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
