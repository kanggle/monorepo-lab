import { NextResponse } from 'next/server';
import {
  listGroupDomainSets,
  addGroupDomainSet,
} from '@/features/operator-groups/api/group-domain-sets-api';
import {
  AddGroupDomainSetBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin group ADR-MONO-083 「업무 권한 세트」 LIST + ADD proxy
 * (TASK-PC-FE-338 slice b — admin-api.md § 도메인 업무 권한 세트 rows 9-10).
 * GET is a READ (no mutation headers); POST carries the reason as
 * `X-Operator-Reason` + the idempotency key as `Idempotency-Key`
 * server-side (BOTH required — the group add-member/add-grants matrix, NOT
 * the operator-personal-grant matrix). A server `404 GROUP_NOT_FOUND` /
 * `404 DOMAIN_PERMISSION_SET_NOT_FOUND` /
 * `409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED` /
 * `409 IDEMPOTENCY_KEY_CONFLICT` / `422 DOMAIN_NOT_SUBSCRIBED` is surfaced
 * verbatim via `mapError`.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ groupId: string }> },
) {
  const requestId = newRequestId();
  const { groupId } = await params;
  try {
    const result = await listGroupDomainSets(groupId);
    return NextResponse.json({ items: result });
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
    body = AddGroupDomainSetBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await addGroupDomainSet(
      groupId,
      body.setId,
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
