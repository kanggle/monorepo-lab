import { NextResponse } from 'next/server';
import { revokeOrgNodeAdmin } from '@/features/org-hierarchy/api/org-nodes-api';
import {
  OrgNodeReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin org-node admin REVOKE proxy (DELETE, 204) (TASK-PC-FE-237 /
 * ADR-047). Returns 204 no content → `new NextResponse(null, { status: 204 })`
 * (NEVER `NextResponse.json(...)` on a 204 → Next 500 trap).
 *
 * The audit reason travels in the REQUEST body (`apiClient.delete` forwards
 * `opts.body` — the `operators/.../assignments` precedent), never in the query
 * string: it is operator-authored free text that would otherwise be captured by
 * access logs, browser history and `Referer` headers. The 204 RESPONSE still
 * carries no body. Next 15 dynamic params are a Promise.
 */
export async function DELETE(
  req: Request,
  { params }: { params: Promise<{ orgNodeId: string; operatorId: string }> },
) {
  const requestId = newRequestId();
  const { orgNodeId, operatorId } = await params;
  let body;
  try {
    body = OrgNodeReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await revokeOrgNodeAdmin(orgNodeId, operatorId, body.reason);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
