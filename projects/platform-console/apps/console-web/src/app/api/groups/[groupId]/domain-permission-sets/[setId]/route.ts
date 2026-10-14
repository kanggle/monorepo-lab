import { NextResponse } from 'next/server';
import { removeGroupDomainSet } from '@/features/operator-groups/api/group-domain-sets-api';
import {
  GroupReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin group ADR-MONO-083 「업무 권한 세트」 REVOKE proxy (DELETE, 204)
 * (TASK-PC-FE-338 slice b — admin-api.md § 도메인 업무 권한 세트 row 11).
 * Returns `204` → `new NextResponse(null, { status: 204 })` (NEVER
 * `NextResponse.json(...)` on a 204 → Next 500 trap). The reason travels in
 * the REQUEST body (never the query string — the org-nodes/groups DELETE
 * precedent). Removes ONLY this group's `GROUP:<this group>` rows across
 * every member — `DIRECT` / `MIGRATION` / another group's rows survive. A
 * server `404 GROUP_NOT_FOUND` / `404 DOMAIN_PERMISSION_SET_NOT_FOUND` (this
 * group never assigned the set) is surfaced verbatim via `mapError`. Next 15
 * dynamic params are a Promise.
 */
export async function DELETE(
  req: Request,
  { params }: { params: Promise<{ groupId: string; setId: string }> },
) {
  const requestId = newRequestId();
  const { groupId, setId } = await params;
  let body;
  try {
    body = GroupReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await removeGroupDomainSet(groupId, setId, body.reason);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
