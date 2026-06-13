import { NextResponse } from 'next/server';
import { listSuggestions } from '@/features/scm-replenishment/api/demand-planning-api';
import type { SuggestionQueryParams } from '@/features/scm-replenishment/api/types';
import { mapReplenishmentError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm demand-planning suggestion-list read proxy for client
 * components (TASK-PC-FE-077 / § 2.4.6.1). The HttpOnly **domain-facing IAM
 * OIDC access token** is attached server-side in `listSuggestions()` (NOT the
 * IAM operator token — § 2.4.6.1 reusing the § 2.4.5/§ 2.4.6 per-domain
 * credential rule). READ-ONLY: GET only, no mutation artifacts.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: SuggestionQueryParams = {
    status: sp.get('status') ?? undefined,
    skuCode: sp.get('skuCode') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const page = await listSuggestions(params);
    return NextResponse.json(page);
  } catch (err) {
    return mapReplenishmentError(err, requestId);
  }
}
