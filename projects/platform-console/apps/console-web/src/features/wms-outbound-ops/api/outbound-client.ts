import { WmsOutboundUnavailableError } from '@/shared/api/errors';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  callWmsGateway,
  type WmsGatewayProfile,
} from '@/shared/api/wms-gateway';
import {
  OUTBOUND_DEFAULT_PAGE_SIZE,
  OUTBOUND_MAX_PAGE_SIZE,
} from './types';

/**
 * Internal wms outbound call infrastructure shared by all domain sub-modules
 * (TASK-PC-FE-147 — behavior-preserving split of `outbound-api.ts`).
 *
 * NOT part of the public API surface — only the sibling domain modules
 * (`outbound-order-api.ts`, `outbound-fulfillment-api.ts`,
 * `outbound-tms-api.ts`) import from here. External code must continue to
 * import from `outbound-api.ts` (the barrel).
 *
 * Auth model, error envelope, resilience taxonomy, and mutation discipline are
 * documented on `callOutbound` and in the barrel's top-level JSDoc (preserved
 * verbatim from the original `outbound-api.ts`).
 */

export interface CallOptions {
  method: 'GET' | 'POST' | 'PATCH';
  /** Path relative to `baseUrl` (default `WMS_OUTBOUND_BASE_URL`, e.g.
   *  `/orders`). */
  path: string;
  /** Stable per a single confirmed action → `Idempotency-Key` (mutation). */
  idempotencyKey?: string;
  /** Typed mutation body; `undefined` for reads. */
  body?: unknown;
  /**
   * Base URL override. Defaults to `WMS_OUTBOUND_BASE_URL`. The TMS-retry
   * shipment-id resolver (TASK-PC-FE-087) overrides this to
   * `WMS_ADMIN_BASE_URL` to read the admin read-model
   * (`GET /dashboard/shipments?orderId`) — SAME wms gateway + SAME IAM-OIDC
   * domain-facing credential, a DISTINCT `/api/v1/admin` path prefix
   * (console-integration-contract § 2.4.5 / § 2.4.5.1). The token + abort +
   * nested-envelope error mapping below are reused verbatim.
   */
  baseUrl?: string;
  /** Timeout override (ms). Defaults to `WMS_OUTBOUND_TIMEOUT_MS`; the admin
   *  read uses `WMS_TIMEOUT_MS`. */
  timeoutMs?: number;
}

/**
 * wms-outbound-ops profile for the shared {@link callWmsGateway} core: the WMS
 * outbound-service surface (`WMS_OUTBOUND_BASE_URL`, `WMS_OUTBOUND_TIMEOUT_MS`;
 * a request may override baseUrl/timeout — the TMS-retry admin read does) that
 * degrades via {@link WmsOutboundUnavailableError} and logs `wms_outbound_*`
 * events.
 */
const WMS_OUTBOUND_PROFILE: WmsGatewayProfile = {
  logPrefix: 'wms_outbound',
  requestFailedLabel: 'wms outbound request failed',
  resolveDefaults: (env) => ({
    baseUrl: env.WMS_OUTBOUND_BASE_URL,
    timeoutMs: env.WMS_OUTBOUND_TIMEOUT_MS,
  }),
  makeUnavailable: (reason, code, message) =>
    new WmsOutboundUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof WmsOutboundUnavailableError,
  messages: {
    degraded: 'wms outbound-service unavailable',
    timeout: 'wms outbound-service call timed out',
    network: 'wms outbound-service call failed',
  },
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callWmsGateway} core with the {@link WMS_OUTBOUND_PROFILE}. Passes the
 * method + optional body + baseUrl/timeout overrides through; returns the parsed
 * body (`lagSeconds` is not surfaced on the outbound surface).
 */
export async function callOutbound<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const { data } = await callWmsGateway(
    {
      method: opts.method,
      path: opts.path,
      idempotencyKey: opts.idempotencyKey,
      body: opts.body,
      baseUrl: opts.baseUrl,
      timeoutMs: opts.timeoutMs,
    },
    parse,
    WMS_OUTBOUND_PROFILE,
  );
  return data;
}

export const clampSize = (size?: number): number =>
  clampPageSize(size, OUTBOUND_DEFAULT_PAGE_SIZE, OUTBOUND_MAX_PAGE_SIZE);
