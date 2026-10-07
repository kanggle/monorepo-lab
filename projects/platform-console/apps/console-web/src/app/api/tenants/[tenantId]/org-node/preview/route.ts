import { NextResponse } from 'next/server';
import { previewTenantPlacement } from '@/features/org-hierarchy/api/org-nodes-api';
import { mapError, newRequestId } from '../../../../org-nodes/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin tenant PLACEMENT PREVIEW proxy (GET) (TASK-PC-FE-312 /
 * TASK-BE-625). Returns the placement effect (`lostDomains` …) the
 * confirmation dialog shows BEFORE the write. READ; no mutation headers.
 *
 * `?orgNodeId=` absent or empty ⇒ detach (the api layer then omits the
 * parameter upstream — the producer reads «absent» as «no node»).
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ tenantId: string }> },
) {
  const requestId = newRequestId();
  const { tenantId } = await params;
  const raw = new URL(req.url).searchParams.get('orgNodeId');
  const toOrgNodeId = raw === null || raw === '' ? null : raw;
  try {
    const result = await previewTenantPlacement(tenantId, toOrgNodeId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
