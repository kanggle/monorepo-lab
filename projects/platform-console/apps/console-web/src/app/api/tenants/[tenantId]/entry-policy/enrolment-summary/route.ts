import { NextResponse } from 'next/server';
import { getEnrolmentSummary } from '@/features/tenant-entry-policy/api/entry-policy-api';
import { mapError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin proxy for the entry-policy PRE-CHECK (TASK-MONO-771 S5 —
 * console-integration-contract § 2.4.3.3; producer admin-api.md § Tenant Entry
 * Policy › enrolment-summary). Read-only, advisory: the «켜기» confirm shows its
 * copy without a number when this answers 403 / 503 — the proxy passes the
 * status through and never substitutes a count.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ tenantId: string }> },
) {
  const requestId = newRequestId();
  const { tenantId } = await params;
  try {
    return NextResponse.json(await getEnrolmentSummary(tenantId));
  } catch (err) {
    return mapError(err, requestId);
  }
}
