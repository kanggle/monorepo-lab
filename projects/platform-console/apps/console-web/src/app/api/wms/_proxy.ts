import { NextResponse } from 'next/server';
import { z } from 'zod';
import { WmsUnavailableError } from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the wms-ops same-origin proxy routes
 * (console-integration-contract § 2.4.5 / § 2.5). The bearer is attached
 * server-side by the shared wms core (`shared/api/wms-gateway.ts`
 * `prepareWmsHeaders`) and it is the **domain-facing IAM OIDC token**
 * (`getDomainFacingToken()`): the ACTIVE tenant's assumed token
 * (`tenant_id=<active>` + that tenant's `entitled_domains`, minted by
 * `POST /api/tenant`), else the login token. It is NEVER the exchanged
 * operator token (`iss=admin-service`, `token_type=admin`) — wms validates
 * the IAM OIDC issuer, and the #569 invariant is IAM-`/api/admin/**`-scoped
 * (§ 2.4.5).
 *
 * 🔴 TASK-MONO-780: this paragraph used to say «the IAM OIDC access token,
 *    NOT the exchanged token» without naming WHICH IAM OIDC token, and the
 *    24th demo window read that as «wms gets the login token, so a tenant
 *    switch cannot reach it» — a ticket was filed against a defect the code
 *    did not have (the switch has re-scoped the wms bearer since
 *    TASK-MONO-158). Name the accessor, not the token family.
 *    Pinned by `tests/unit/wms-active-tenant-token.test.ts`.
 *
 * Mirrors the FE-002 `_proxy` shape but for the wms (nested) envelope.
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION re-login;
 *     no partial authed state — NOT a per-section degrade).
 *   - 403 → 403 (role-insufficient → inline "not available to your role").
 *   - 400 / 404 / 422 STATE_TRANSITION_INVALID / 409 DUPLICATE_REQUEST →
 *     passthrough (inline actionable, no crash).
 *   - 503 / timeout / network → 503 (ONLY the wms section degrades; the
 *     console shell + IAM sections stay intact).
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
