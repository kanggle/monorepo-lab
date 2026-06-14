import { NextResponse } from 'next/server';
import { z } from 'zod';
import { WmsOutboundUnavailableError } from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the wms-outbound-ops same-origin proxy
 * routes (console-integration-contract § 2.4.5.1 / § 2.5). The HttpOnly
 * **domain-facing IAM OIDC access token** is attached server-side in
 * `outbound-api.ts` — NOT the IAM exchanged operator token (the wms gateway
 * requires the IAM OIDC token; the #569 invariant is GAP-domain-scoped —
 * § 2.4.5). Mirrors the `wms-ops/_proxy` shape for the wms (nested) envelope.
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION re-login).
 *   - 403 → 403 (role-insufficient → inline "not available to your role").
 *   - 400 / 404 / 422 STATE_TRANSITION_INVALID / 409 CONFLICT /
 *     409 DUPLICATE_REQUEST → passthrough (inline actionable, no crash; the
 *     409 CONFLICT path drives a refetch + retry-prompt in the UI).
 *   - 503 / timeout / network → 503 (ONLY the wms outbound section degrades).
 *
 * No token / wms data is ever logged.
 */

/** Action request body: ONLY a stable idempotency key (the wms outbound
 *  surface is reason-free — NO `X-Operator-Reason`; confirm-gated in the UI).
 *  The proxy orchestrates the producer body server-side (Pick = confirm-as-
 *  planned; Pack = create-then-seal) — the client never fabricates it. */
export const ActionBodySchema = z.object({
  idempotencyKey: z.string().min(1),
});
export type ActionBody = z.infer<typeof ActionBodySchema>;

/** Cancel request body (TASK-PC-FE-085 — § 2.4.5.1 op 9): a **REQUIRED reason**
 *  (3..500, producer § 1.4 validation) + the stable idempotency key. UNLIKE the
 *  reason-free forward actions, cancel carries an operator reason — it rides in
 *  the producer JSON body server-side (the wms surface still has no
 *  `X-Operator-Reason` header). The proxy reads the order `version` server-side
 *  (optimistic lock) — the client never supplies it. */
export const CancelBodySchema = z.object({
  reason: z.string().trim().min(3).max(500),
  idempotencyKey: z.string().min(1),
});
export type CancelBody = z.infer<typeof CancelBodySchema>;

export const mapOutboundError = makeProxyErrorMapper(
  'wms-outbound',
  WmsOutboundUnavailableError,
);

export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

export { newRequestId };
