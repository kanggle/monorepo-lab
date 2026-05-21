import { NextResponse } from 'next/server';
import { updateOwnProfile } from '@/features/operators/api/operators-api';
import {
  UpdateProfileBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin SELF update-profile proxy
 * (`PATCH /api/admin/operators/me/profile`) — TASK-PC-FE-016. This is the
 * logged-in operator's OWN profile carrier — there is NO
 * admin-set-other-profile endpoint in the parity line (the console does
 * not invent one). A static `me` segment takes precedence over the
 * `[operatorId]` dynamic segment in Next.js routing, so this never
 * collides with roles/status.
 *
 * PER-ENDPOINT HEADER MATRIX (§ 2.4.3, row 6): the api layer sends NO
 * `X-Operator-Reason` and NO `Idempotency-Key` — this is the self auth
 * flow (valid operator token only, no `operator.manage`). Mirrors
 * `me/password` exactly. The producer returns 204 No Content; the proxy
 * normalises that to a 204.
 *
 * BODY shape mirrors the read shape verbatim (TASK-BE-304 → BE-306 →
 * PC-FE-014 → THIS): `{ operatorContext: { defaultAccountId: string | null } }`.
 * Explicit `null` clears the column; a string is opaque to GAP (no
 * cross-service verification — TASK-BE-304 § Decision authority). The
 * effective value is re-read via `GET /api/admin/console/registry`.
 */
export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = UpdateProfileBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await updateOwnProfile({
      defaultAccountId: body.operatorContext.defaultAccountId,
    });
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
