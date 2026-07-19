import { NextResponse } from 'next/server';
import {
  getGroup,
  updateGroup,
  deleteGroup,
} from '@/features/operator-groups/api/operator-groups-api';
import {
  UpdateGroupBodySchema,
  GroupReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin group GET (detail) + PATCH (rename / describe) + DELETE (remove)
 * proxy (TASK-PC-FE-250). Next 15 dynamic params are a Promise — `await params`.
 *
 * DELETE returns 204 no content → `new NextResponse(null, { status: 204 })`
 * (NEVER `NextResponse.json(...)` on a 204 — that throws
 * `TypeError: Response with null body status cannot have body` → Next 500). The
 * audit reason travels in the REQUEST body (`apiClient.delete` forwards
 * `opts.body` — the org-nodes precedent), never in the query string: it is
 * operator-authored free text that would otherwise be captured by access logs,
 * browser history and `Referer` headers. A 204 RESPONSE still carries no body.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ groupId: string }> },
) {
  const requestId = newRequestId();
  const { groupId } = await params;
  try {
    const result = await getGroup(groupId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ groupId: string }> },
) {
  const requestId = newRequestId();
  const { groupId } = await params;
  let body;
  try {
    body = UpdateGroupBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await updateGroup(
      groupId,
      { name: body.name, description: body.description },
      body.reason,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function DELETE(
  req: Request,
  { params }: { params: Promise<{ groupId: string }> },
) {
  const requestId = newRequestId();
  const { groupId } = await params;
  let body;
  try {
    body = GroupReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await deleteGroup(groupId, body.reason);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
