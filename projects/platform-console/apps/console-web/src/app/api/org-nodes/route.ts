import { NextResponse } from 'next/server';
import {
  listOrgNodes,
  createOrgNode,
} from '@/features/org-hierarchy/api/org-nodes-api';
import {
  CreateOrgNodeBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin org-node hierarchy LIST proxy (GET) + CREATE proxy (POST) for
 * client components (TASK-PC-FE-237 / ADR-047) — the typed api-client's single
 * backend entry point (no browser-direct IAM call, architecture.md § Forbidden
 * Dependencies). The HttpOnly operator token + active tenant are attached
 * server-side in the api layer; the reason rides as `X-Operator-Reason`.
 *
 * GET → `listOrgNodes` (flat, reach-scoped by the server). POST →
 * `createOrgNode` (201). 401 → 401 (re-login); 503/timeout → 503
 * (org-hierarchy section degrades only); 400 NO_ACTIVE_TENANT → 400;
 * 403/404/422 producer → inline actionable.
 */
export async function GET() {
  const requestId = newRequestId();
  try {
    const result = await listOrgNodes();
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = CreateOrgNodeBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await createOrgNode(
      { name: body.name, parentId: body.parentId, ceiling: body.ceiling },
      body.reason,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
