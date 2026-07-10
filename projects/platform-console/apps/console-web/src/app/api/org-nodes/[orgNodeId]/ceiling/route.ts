import { NextResponse } from 'next/server';
import { setOrgNodeCeiling } from '@/features/org-hierarchy/api/org-nodes-api';
import {
  SetCeilingBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin org-node CEILING proxy (PUT) (TASK-PC-FE-237 / ADR-047). The
 * client sends `{ ceiling, reason }`; the api layer unwraps it (the producer
 * body IS the ceiling) and attaches the reason as `X-Operator-Reason`. A
 * server 422 `ORG_NODE_CEILING_NOT_SUBSET` / 403 `ORG_NODE_SELF_CEILING_DENIED`
 * is surfaced verbatim via `mapError` (client-side subset validation is UX
 * assistance only — the server is the authority).
 */
export async function PUT(
  req: Request,
  { params }: { params: Promise<{ orgNodeId: string }> },
) {
  const requestId = newRequestId();
  const { orgNodeId } = await params;
  let body;
  try {
    body = SetCeilingBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await setOrgNodeCeiling(orgNodeId, body.ceiling, body.reason);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
