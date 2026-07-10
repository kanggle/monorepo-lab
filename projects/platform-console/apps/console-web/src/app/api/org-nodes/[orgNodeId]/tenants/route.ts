import { NextResponse } from 'next/server';
import { getOrgNodeTenants } from '@/features/org-hierarchy/api/org-nodes-api';
import { mapError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin org-node SUBTREE-TENANTS proxy (GET) (TASK-PC-FE-237 / ADR-047).
 * Returns `{ tenantIds }` for the node + ALL descendants. READ; no mutation
 * headers. Consumed by the detail's "소속 테넌트" section and (server-side, not
 * via this route) by the layout's tenant-switcher grouping.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ orgNodeId: string }> },
) {
  const requestId = newRequestId();
  const { orgNodeId } = await params;
  try {
    const result = await getOrgNodeTenants(orgNodeId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
