import { NextResponse } from 'next/server';
import { setOperatorProfile } from '@/features/operators/api/operators-api';
import {
  AdminUpdateProfileBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin admin-on-behalf-of profile-edit proxy
 * (`PATCH /api/admin/operators/{operatorId}/profile` — TASK-PC-FE-017 /
 * TASK-BE-307 producer). A SUPER_ADMIN sets another operator's
 * `operatorContext.defaultAccountId` with explicit `X-Operator-Reason`.
 * This is the cross-operator counterpart of the self route
 * `me/profile/route.ts` (BE-306 producer / PC-FE-016 consumer).
 *
 * PER-ENDPOINT HEADER MATRIX (§ 2.4.3 row 7): the api layer attaches
 * `X-Operator-Reason` ONLY — there is **NO `Idempotency-Key`** (producer
 * matrix mirrors rows 13 + 14 `/roles` + `/status` non-uniformity;
 * full-replace PATCH on the profile column is idempotent). This proxy
 * carries no idempotency key and never fabricates a reason.
 *
 * SELF VIA THIS PATH: the producer returns
 * `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`
 * (self-serve must use `me/profile`, BE-306). The per-row UI button is
 * disabled when the row is self — UX layer; this proxy honors whatever
 * the producer returns (passthrough via `mapError`).
 *
 * BODY shape: `{ defaultAccountId: string | null, reason: string }` (the
 * proxy-layer wire shape; the api fn reconstructs the GAP-side body
 * `{ operatorContext: { defaultAccountId } }` server-side). Explicit
 * `null` clears the column; a string is opaque to IAM (no cross-service
 * verification — TASK-BE-304 § Decision authority).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ operatorId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId } = await params;
  let body;
  try {
    body = AdminUpdateProfileBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await setOperatorProfile(operatorId, body.defaultAccountId, body.reason);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
