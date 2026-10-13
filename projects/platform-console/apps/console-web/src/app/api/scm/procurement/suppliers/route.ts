import { NextResponse } from 'next/server';
import { listSuppliers } from '@/shared/api/scm-suppliers-read';
import type { SupplierQueryParams } from '@/shared/api/scm-suppliers-types';
import { mapScmError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm procurement **supplier list** read proxy for client
 * components (TASK-PC-FE-337 — the typed API client's single backend entry
 * point; no browser-direct scm call, architecture.md § Forbidden
 * Dependencies / contract § 2.3). The HttpOnly domain-facing **IAM OIDC
 * access token** is attached server-side in `listSuppliers()` (NOT the IAM
 * operator token — § 2.4.6 reusing the § 2.4.5 per-domain credential rule).
 * READ-ONLY: GET only, forwards `page`/`size`/`status`, no mutation branch.
 *
 * Shared by two feature proxies' consumers (`features/scm-ops` PO filter +
 * `features/scm-config` supplier-code suggestions) — the same promotion
 * reason as `shared/api/scm-suppliers-read.ts` itself.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: SupplierQueryParams = {
    status: sp.get('status') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listSuppliers(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapScmError(err, requestId);
  }
}
