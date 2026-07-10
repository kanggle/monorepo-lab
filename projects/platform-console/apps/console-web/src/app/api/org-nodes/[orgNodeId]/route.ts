import { NextResponse } from 'next/server';
import {
  getOrgNode,
  updateOrgNode,
  deleteOrgNode,
} from '@/features/org-hierarchy/api/org-nodes-api';
import {
  UpdateOrgNodeBodySchema,
  OrgNodeReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin org-node GET (detail) + PATCH (rename / re-parent) + DELETE
 * (remove) proxy (TASK-PC-FE-237). Next 15 dynamic params are a Promise —
 * `await params`.
 *
 * DELETE returns 204 no content → `new NextResponse(null, { status: 204 })`
 * (NEVER `NextResponse.json(...)` on a 204 — that throws
 * `TypeError: Response with null body status cannot have body` → Next 500).
 * The audit reason travels in the REQUEST body (`apiClient.delete` forwards
 * `opts.body` — the `operators/.../assignments` precedent), never in the query
 * string: it is operator-authored free text that would otherwise be captured by
 * access logs, browser history and `Referer` headers. A 204 RESPONSE still
 * carries no body.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ orgNodeId: string }> },
) {
  const requestId = newRequestId();
  const { orgNodeId } = await params;
  try {
    const result = await getOrgNode(orgNodeId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ orgNodeId: string }> },
) {
  const requestId = newRequestId();
  const { orgNodeId } = await params;
  let body;
  try {
    body = UpdateOrgNodeBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await updateOrgNode(
      orgNodeId,
      { name: body.name, parentId: body.parentId },
      body.reason,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function DELETE(
  req: Request,
  { params }: { params: Promise<{ orgNodeId: string }> },
) {
  const requestId = newRequestId();
  const { orgNodeId } = await params;
  let body;
  try {
    body = OrgNodeReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await deleteOrgNode(orgNodeId, body.reason);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
