import { NextResponse } from 'next/server';
import {
  listOrgNodeAdmins,
  grantOrgNodeAdmin,
} from '@/features/org-hierarchy/api/org-nodes-api';
import {
  GrantOrgAdminBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin org-node ADMINS proxy — GET (list grants) + POST (grant, 201)
 * (TASK-PC-FE-237 / ADR-047). GET is a READ (no mutation headers); POST
 * carries the reason as `X-Operator-Reason` server-side. A server 403
 * `ROLE_GRANT_FORBIDDEN` / 422 `ORG_ADMIN_GRANT_OUT_OF_CEILING` is surfaced
 * verbatim via `mapError`.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ orgNodeId: string }> },
) {
  const requestId = newRequestId();
  const { orgNodeId } = await params;
  try {
    const result = await listOrgNodeAdmins(orgNodeId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function POST(
  req: Request,
  { params }: { params: Promise<{ orgNodeId: string }> },
) {
  const requestId = newRequestId();
  const { orgNodeId } = await params;
  let body;
  try {
    body = GrantOrgAdminBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await grantOrgNodeAdmin(
      orgNodeId,
      { operatorId: body.operatorId, roleName: body.roleName },
      body.reason,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
