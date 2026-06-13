import { NextResponse } from 'next/server';
import { getSuggestion } from '@/features/scm-replenishment/api/demand-planning-api';
import { mapReplenishmentError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm demand-planning suggestion-detail read proxy (read-only —
 * GET). The HttpOnly domain-facing IAM OIDC access token is attached
 * server-side in `getSuggestion()` (§ 2.4.6.1 reusing the § 2.4.5/§ 2.4.6
 * per-domain credential rule — NOT the operator token). No mutation artifacts.
 * `404 SUGGESTION_NOT_FOUND` passes through inline (no crash).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const suggestion = await getSuggestion(id);
    return NextResponse.json(suggestion);
  } catch (err) {
    return mapReplenishmentError(err, requestId);
  }
}
