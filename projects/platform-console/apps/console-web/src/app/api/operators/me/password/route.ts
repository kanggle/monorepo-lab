import { NextResponse } from 'next/server';
import { changeOwnPassword } from '@/features/operators/api/operators-api';
import {
  ChangePasswordBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin SELF change-password proxy
 * (`PATCH /api/admin/operators/me/password`). This is the logged-in
 * operator's OWN password — there is NO admin-set-other-password endpoint
 * in the parity line (the console does not invent one). A static `me`
 * segment takes precedence over the `[operatorId]` dynamic segment in
 * Next.js routing, so this never collides with roles/status.
 *
 * PER-ENDPOINT HEADER MATRIX (§ 2.4.3): the api layer sends NO
 * `X-Operator-Reason` and NO `Idempotency-Key` — this is the self auth
 * flow (valid operator token only, no `operator.manage`). The producer
 * returns 204 No Content; the proxy normalises that to a 204.
 *
 * SECURITY: the current/new passwords are forwarded server-side only and
 * are NEVER logged / echoed (no token / password in any structured log).
 */
export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = ChangePasswordBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await changeOwnPassword({
      currentPassword: body.currentPassword,
      newPassword: body.newPassword,
    });
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
