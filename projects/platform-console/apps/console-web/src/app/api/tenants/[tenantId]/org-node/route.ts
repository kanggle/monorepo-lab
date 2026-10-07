import { NextResponse } from 'next/server';
import { placeTenant } from '@/features/org-hierarchy/api/org-nodes-api';
import {
  PlaceTenantBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../org-nodes/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin tenant PLACEMENT proxy (PUT) (TASK-PC-FE-312 / TASK-BE-625 /
 * admin-api.md § «테넌트 소속»). Put a tenant under a node, move it, or take it
 * out (`orgNodeId: null`). The client sends `{ orgNodeId, reason }`; the api
 * layer sends `{ orgNodeId }` as the producer body and the reason as
 * `X-Operator-Reason`.
 *
 * The route lives under `/api/tenants/{tenantId}/…` because the producer path
 * does, but it is an `org.manage` org-hierarchy write — so it uses the
 * org-nodes `_proxy` mapping (the api call raises `OrgNodesUnavailableError`
 * on 503/timeout, which only that mapping knows). 404 `TENANT_NOT_FOUND` /
 * `ORG_NODE_NOT_FOUND` and 409 `TENANT_ORG_NODE_CONFLICT` pass through
 * verbatim; the screen decides the copy (it never guesses existence).
 */
export async function PUT(
  req: Request,
  { params }: { params: Promise<{ tenantId: string }> },
) {
  const requestId = newRequestId();
  const { tenantId } = await params;
  let body;
  try {
    body = PlaceTenantBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await placeTenant(tenantId, body.orgNodeId, body.reason);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
