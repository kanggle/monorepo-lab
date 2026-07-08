import { NextResponse } from 'next/server';
import { getProjectionStatus } from '@/features/wms-ops/api/wms-refs-api';
import { mapWmsError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms **프로젝션 상태**(projection-status) read proxy for
 * client components (TASK-PC-FE-224 — `getProjectionStatus()` already
 * existed in `wms-refs-api.ts` § 6.2 since the ref-client split but had
 * ZERO consumers/proxy route until this task's `/wms/operations` screen).
 * The typed API client's single backend entry point — no browser-direct
 * wms call (architecture.md § Forbidden Dependencies / contract § 2.3).
 * The HttpOnly **IAM OIDC access token** is attached server-side in
 * `getProjectionStatus()` (NOT the IAM operator token — § 2.4.5 per-domain
 * credential divergence). READ-ONLY: GET only, no query params, no
 * mutation branch, no Idempotency-Key, no X-Operator-Reason.
 */
export async function GET() {
  const requestId = newRequestId();
  try {
    const result = await getProjectionStatus();
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
