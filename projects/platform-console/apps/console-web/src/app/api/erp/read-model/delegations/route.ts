import { NextResponse } from 'next/server';
import { listDelegationFacts } from '@/features/erp-ops/api/erp-api';
import { mapErpError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp read-model delegation-fact LIST proxy (GET only —
 * TASK-PC-FE-055). READ-ONLY (E5 — the read-model holds no domain
 * logic and has NO mutation surface). IAM OIDC domain-facing token
 * attached server-side (same pattern as the employees read-model
 * proxy — PC-FE-049 precedent; NEVER `getOperatorToken()`).
 *
 * Filters threaded: `delegatorId` / `delegateId` / `status` /
 * `activeAt` / `page` / `size`. The producer is the authority for
 * filter semantics — the proxy passes them verbatim.
 *
 * No POST / PATCH / DELETE handler — this route file intentionally
 * exports only `GET` (a test pins that absence: read-model write
 * surface = 0, AC-3).
 *
 * NO `X-Tenant-Id` (erp resolves tenant from JWT `tenant_id` claim).
 * NO `X-Operator-Reason` (read-only; the read-model has no reason slot).
 * NO `Idempotency-Key` (read-only; no mutation).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const sp = new URL(req.url).searchParams;
    const params: Parameters<typeof listDelegationFacts>[0] = {};

    const delegatorId = sp.get('delegatorId');
    if (delegatorId) params.delegatorId = delegatorId;
    const delegateId = sp.get('delegateId');
    if (delegateId) params.delegateId = delegateId;
    const status = sp.get('status');
    if (status) params.status = status;
    const activeAt = sp.get('activeAt');
    if (activeAt) params.activeAt = activeAt;
    if (sp.has('page')) params.page = Number(sp.get('page'));
    if (sp.has('size')) params.size = Number(sp.get('size'));

    const result = await listDelegationFacts(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
