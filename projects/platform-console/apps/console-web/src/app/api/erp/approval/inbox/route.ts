import { NextResponse } from 'next/server';
import { listApprovalInbox } from '@/features/erp-ops/api/approval-api';
import type { ApprovalInboxQueryParams } from '@/features/erp-ops/api/approval-types';
import { mapErpError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp approval INBOX proxy (read — GET only). Forwards to the
 * UNCHANGED producer `GET /api/erp/approval/inbox` via `listApprovalInbox()`
 * (domain-facing IAM token server-side). Returns the caller's PENDING
 * (SUBMITTED) requests where they are the `approverId`. First increment is
 * minimal — no due-date / priority / delegation filtering (v2 deferred); only
 * page / size are forwarded.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: ApprovalInboxQueryParams = {};
  if (sp.has('page')) params.page = Number(sp.get('page'));
  if (sp.has('size')) params.size = Number(sp.get('size'));
  try {
    const result = await listApprovalInbox(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
