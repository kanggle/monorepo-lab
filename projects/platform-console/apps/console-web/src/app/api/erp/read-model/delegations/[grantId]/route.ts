import { NextResponse } from 'next/server';
import { getDelegationFact } from '@/features/erp-ops/api/erp-api';
import { mapErpError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp read-model delegation-fact DETAIL proxy (GET only —
 * TASK-PC-FE-055). READ-ONLY (E5). Returns the latest state of a single
 * delegation grant (`DelegationFact`). For the full audit history,
 * consumers use `approval-service` (source of record).
 *
 * 404 `MASTERDATA_NOT_FOUND` — no delegation-fact projection for the
 * given `grantId` (a projection miss is not fabricated; out-of-scope
 * delegator also surfaces as 404 to avoid existence leak — read-model
 * detail rule). Passes through as an `ApiError` inline actionable.
 *
 * No POST / PATCH / DELETE handler — this route file intentionally
 * exports only `GET` (a test pins that absence: read-model write
 * surface = 0, AC-3).
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ grantId: string }> },
) {
  const requestId = newRequestId();
  try {
    const { grantId } = await params;
    const result = await getDelegationFact(grantId);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
