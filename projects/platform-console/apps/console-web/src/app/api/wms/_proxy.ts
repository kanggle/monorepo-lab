import { NextResponse } from 'next/server';
import { z } from 'zod';
import { WmsUnavailableError } from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the wms-ops same-origin proxy routes
 * (console-integration-contract § 2.4.5 / § 2.5). The HttpOnly **GAP OIDC
 * access token** is attached server-side in `wms-api.ts` — NOT the GAP
 * exchanged operator token (the wms gateway requires the GAP OIDC token;
 * the #569 invariant is GAP-domain-scoped — § 2.4.5). Mirrors the FE-002
 * `_proxy` shape but for the wms (nested) envelope + GAP-token credential.
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION re-login;
 *     no partial authed state — NOT a per-section degrade).
 *   - 403 → 403 (role-insufficient → inline "not available to your role").
 *   - 400 / 404 / 422 STATE_TRANSITION_INVALID / 409 DUPLICATE_REQUEST →
 *     passthrough (inline actionable, no crash).
 *   - 503 / timeout / network → 503 (ONLY the wms section degrades; the
 *     console shell + GAP sections stay intact).
 *
 * No token / wms data is ever logged.
 */

/** Alert-ack request body: ONLY an idempotency key (the wms alert-ack is
 *  reason-free — NO `X-Operator-Reason`; confirm-gated in the UI). */
export const AckBodySchema = z.object({
  idempotencyKey: z.string().min(1),
});
export type AckBody = z.infer<typeof AckBodySchema>;

export const mapWmsError = makeProxyErrorMapper('wms', WmsUnavailableError);

export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

export { newRequestId };
