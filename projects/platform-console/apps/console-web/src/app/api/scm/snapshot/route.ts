import { NextResponse } from 'next/server';
import { getSnapshot } from '@/features/scm-ops/api/scm-api';
import type { SnapshotQueryParams } from '@/features/scm-ops/api/types';
import { mapScmError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm inventory-visibility snapshot read proxy (read-only —
 * GET). The HttpOnly IAM OIDC access token is attached server-side in
 * `getSnapshot()` (§ 2.4.6 reusing § 2.4.5 — NOT the operator token). The
 * full `{ data, meta }` envelope is forwarded so the REQUIRED S5
 * `meta.warning` reaches the client and is rendered prominently — it is
 * NEVER stripped here.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: SnapshotQueryParams = {
    nodeId: sp.get('nodeId') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await getSnapshot(params);
    // Forward the FULL envelope (data + meta) — the S5 meta.warning MUST
    // survive to the client (§ 2.4.6, never stripped).
    return NextResponse.json(result.data);
  } catch (err) {
    return mapScmError(err, requestId);
  }
}
