import { NextResponse } from 'next/server';
import { removeGrant } from '@/features/operator-groups/api/operator-groups-api';
import {
  GroupReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin group grant REVOKE proxy (DELETE, 204) (TASK-PC-FE-250 /
 * ADR-MONO-046). Returns 204 no content → `new NextResponse(null, { status: 204 })`
 * (NEVER `NextResponse.json(...)` on a 204 → Next 500 trap).
 *
 * The audit reason travels in the REQUEST body (`apiClient.delete` forwards
 * `opts.body` — the org-nodes precedent), never in the query string. The 204
 * RESPONSE still carries no body. Next 15 dynamic params are a Promise. A server
 * 404 `GROUP_GRANT_NOT_FOUND` is surfaced verbatim via `mapError`.
 */
export async function DELETE(
  req: Request,
  { params }: { params: Promise<{ groupId: string; grantId: string }> },
) {
  const requestId = newRequestId();
  const { groupId, grantId } = await params;
  let body;
  try {
    body = GroupReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await removeGrant(groupId, grantId, body.reason);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
