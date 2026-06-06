import { NextResponse } from 'next/server';
import { getApprovalRequest } from '@/features/erp-ops/api/approval-api';
import { mapErpError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp approval DETAIL proxy (read — GET only). Forwards to the
 * UNCHANGED producer `GET /api/erp/approval/requests/{id}` via
 * `getApprovalRequest()` (domain-facing IAM token server-side). Returns the
 * full request incl. the immutable transition `history` (E4). A
 * `404 APPROVAL_REQUEST_NOT_FOUND` / `403 DATA_SCOPE_FORBIDDEN` passes
 * through `mapErpError` inline-actionably (no crash).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getApprovalRequest(id);
    return NextResponse.json({ data: result });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
