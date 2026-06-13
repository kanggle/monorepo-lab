import { NextResponse } from 'next/server';
import { approveSuggestion } from '@/features/scm-replenishment/api/demand-planning-api';
import {
  ActionBodySchema,
  mapReplenishmentError,
  badRequest,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm demand-planning suggestion **approve** mutation proxy
 * (console-integration-contract § 2.4.6.1). The operator approves a reorder
 * suggestion → the producer resolves `sku_supplier_map` → procurement creates
 * a **DRAFT** PO → the suggestion is `MATERIALIZED` with `materializedPoId`.
 *
 * The domain-facing IAM OIDC token is attached server-side in
 * `approveSuggestion()` (NOT the operator token — § 2.4.6.1). The OPTIONAL
 * `note` rides in the request BODY — there is NO `Idempotency-Key` and NO
 * `X-Operator-Reason` header (demand-planning-api defines neither; the producer
 * is idempotent by suggestion state — re-approve returns the existing `poId`).
 * This route NEVER submits the PO (DRAFT-only invariant — ADR-MONO-027 D5).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  // The body is OPTIONAL ({ note } or empty). Tolerate an absent / empty body.
  let note: string | undefined;
  try {
    const raw = await req.text();
    if (raw.trim()) {
      const parsed = ActionBodySchema.parse(JSON.parse(raw));
      note = parsed.note;
    }
  } catch {
    return badRequest();
  }

  try {
    const result = await approveSuggestion(id, note);
    return NextResponse.json(result);
  } catch (err) {
    return mapReplenishmentError(err, requestId);
  }
}
