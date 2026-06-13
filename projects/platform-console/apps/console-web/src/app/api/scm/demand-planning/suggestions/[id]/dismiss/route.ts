import { NextResponse } from 'next/server';
import { dismissSuggestion } from '@/features/scm-replenishment/api/demand-planning-api';
import {
  ActionBodySchema,
  mapReplenishmentError,
  badRequest,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm demand-planning suggestion **dismiss** mutation proxy
 * (console-integration-contract § 2.4.6.1). The operator dismisses a reorder
 * suggestion (`* → DISMISSED`; releases the open-suggestion guard).
 *
 * The domain-facing IAM OIDC token is attached server-side in
 * `dismissSuggestion()` (NOT the operator token — § 2.4.6.1). The OPTIONAL
 * `reason` rides in the request BODY — NO `Idempotency-Key`, NO
 * `X-Operator-Reason` header (demand-planning-api defines neither; the producer
 * is idempotent by suggestion state — re-dismiss is a no-op). `INVALID_
 * SUGGESTION_STATE` (422, dismiss of a MATERIALIZED one) passes through inline.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let reason: string | undefined;
  try {
    const raw = await req.text();
    if (raw.trim()) {
      const parsed = ActionBodySchema.parse(JSON.parse(raw));
      reason = parsed.reason;
    }
  } catch {
    return badRequest();
  }

  try {
    const result = await dismissSuggestion(id, reason);
    return NextResponse.json(result);
  } catch (err) {
    return mapReplenishmentError(err, requestId);
  }
}
