import { NextResponse } from 'next/server';
import { getStaleness } from '@/features/scm-ops/api/scm-api';
import { mapScmError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm inventory-visibility node-staleness read proxy
 * (read-only — GET). GAP OIDC access token attached server-side
 * (§ 2.4.6 reusing § 2.4.5 — NOT the operator token). The full
 * `{ data, meta }` envelope is forwarded so the REQUIRED S5
 * `meta.warning` reaches the client (never stripped). Node
 * FRESH/STALE/UNREACHABLE status is surfaced honestly by the UI.
 */
export async function GET() {
  const requestId = newRequestId();
  try {
    const result = await getStaleness();
    return NextResponse.json(result.data);
  } catch (err) {
    return mapScmError(err, requestId);
  }
}
