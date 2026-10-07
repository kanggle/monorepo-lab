import { NextResponse } from 'next/server';
import { listMyAccountLinkProposals } from '@/features/erp-ops/api/erp-api';
import { mapErpError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp «proposals addressed to me» read proxy (GET —
 * TASK-PC-FE-318). The producer filters to the caller's own `sub` (no data
 * scope); the console sends nothing that names the caller.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  try {
    const result = await listMyAccountLinkProposals({
      page: sp.has('page') ? Number(sp.get('page')) : undefined,
      size: sp.has('size') ? Number(sp.get('size')) : undefined,
    });
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
