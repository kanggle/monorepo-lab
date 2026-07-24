import { ApiError, WmsOutboundUnavailableError } from '@/shared/api/errors';
import {
  callScmGateway,
  type ScmGatewayProfile,
} from '@/shared/api/scm-gateway';
import {
  DispatchRefSchema,
  type DispatchRef,
  DispatchRetryResultSchema,
  type DispatchRetryResult,
} from './types';

/**
 * Server-side `logistics-service` carrier-dispatch client (TASK-PC-FE-258 —
 * the console half of ADR-MONO-053 §D8). Carrier dispatch moved from the wms
 * TMS side-channel to `logistics-service` (ADR-053 Phase 1, live). The operator
 * "발송 재시도" action now re-drives a failed carrier dispatch here instead of
 * the retired wms TMS notify side-channel.
 *
 * ── SAME GATEWAY + SAME CREDENTIAL AS scm DEMAND-PLANNING (the crux — NOT the
 *    wms `callOutbound` client) ─────────────────────────────────────────────
 *
 * `logistics-service` sits behind the **scm gateway** (`/api/v1/logistics/**`,
 * `SCM_GATEWAY_BASE_URL` default `http://scm.local`) — the SAME gateway + SAME
 * domain-facing IAM OIDC credential the console already uses for the scm
 * demand-planning approve/dismiss mutations (§ 2.4.6 / § 2.4.6.1). So these two
 * calls REUSE the shared {@link callScmGateway} helper (the demand-planning
 * mutation path), NOT the wms `callOutbound` client (a distinct gateway/base +
 * nested error envelope). No new credential, no new env var, no IAM
 * entitlement: the scm gateway validates the IAM RS256 token +
 * `tenant_id ∈ {scm,*}` claim producer-side, exactly as § 2.4.6 states.
 *
 * The degrade signal reuses {@link WmsOutboundUnavailableError} so a logistics
 * outage degrades ONLY the wms-outbound section (where this action lives) via
 * the existing `mapOutboundError` — the console shell + every other section
 * stay intact.
 *
 * Server-only by construction: imported exclusively from the `runtime='nodejs'`
 * retry-dispatch route handler; the token + any data never reach client JS.
 *
 * External code must import from `outbound-api.ts` (the barrel), not directly.
 */

/**
 * logistics profile for the shared {@link callScmGateway} core: the carrier
 * dispatch surface (by-shipment resolve + `:retry`) degrades via
 * {@link WmsOutboundUnavailableError} (the wms-outbound section signal) and logs
 * `wms_outbound_logistics_*` events. The scm gateway's flat error envelope +
 * domain-facing credential + bounded-429 policy are supplied by
 * {@link callScmGateway} itself.
 */
const LOGISTICS_PROFILE: ScmGatewayProfile = {
  logPrefix: 'wms_outbound_logistics',
  requestFailedLabel: 'logistics dispatch request failed',
  makeUnavailable: (reason, code, message) =>
    new WmsOutboundUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof WmsOutboundUnavailableError,
  messages: {
    degraded: 'logistics dispatch unavailable',
    timeout: 'logistics dispatch call timed out',
    network: 'logistics dispatch call failed',
  },
};

/** Unwrap the logistics `DispatchResponse` envelope `{ data: {...} }`
 *  (tolerant: an already-unwrapped body still parses). */
function unwrapDispatch(json: unknown): unknown {
  const env = (json ?? {}) as { data?: unknown };
  return env.data ?? json;
}

/**
 * Resolves the carrier dispatch for a shipment (logistics
 * `GET /api/v1/logistics/dispatches/by-shipment/{shipmentId}` — the dispatch-id-free
 * entry point BE-045 added for this relocation). Returns the dispatch record
 * (`id` + `status` + …). A `404 DISPATCH_NOT_FOUND` (no dispatch yet — the wms
 * `outbound.shipping.confirmed` seam event has not been consumed / logistics is
 * lagging) is an inline actionable state, NOT an error: it maps to `null` so the
 * proxy renders "아직 발송 접수 전" and fires NO `:retry`.
 */
export async function resolveDispatchIdForShipment(
  shipmentId: string,
): Promise<DispatchRef | null> {
  try {
    const { raw } = await callScmGateway(
      {
        method: 'GET',
        path: `/api/v1/logistics/dispatches/by-shipment/${encodeURIComponent(shipmentId)}`,
      },
      (json) => DispatchRefSchema.parse(unwrapDispatch(json)),
      LOGISTICS_PROFILE,
    );
    return raw;
  } catch (err) {
    // No dispatch for this shipment yet → inline actionable (the proxy maps it
    // to a `404 DISPATCH_NOT_FOUND`); every other error propagates.
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

/**
 * Re-drives a failed carrier dispatch (logistics
 * `POST /api/v1/logistics/dispatches/{id}:retry`). Reason-free — empty `{}` body
 * plus a stable `Idempotency-Key` (naturally idempotent: an already-`DISPATCHED`
 * dispatch returns a cached ack with no vendor call). Role is producer-enforced
 * (a valid scm-tenant JWT); the console does NOT pre-gate — a `403` maps inline.
 */
export function retryDispatch(
  dispatchId: string,
  idempotencyKey: string,
): Promise<DispatchRetryResult> {
  return callScmGateway(
    {
      method: 'POST',
      path: `/api/v1/logistics/dispatches/${encodeURIComponent(dispatchId)}:retry`,
      body: {},
      idempotencyKey,
    },
    (json) => DispatchRetryResultSchema.parse(unwrapDispatch(json)),
    LOGISTICS_PROFILE,
  ).then(({ raw }) => raw);
}
