import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, WmsOutboundUnavailableError } from '@/shared/api/errors';
import { clampPageSize } from '@/shared/lib/pagination';
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
 * Parses the wms NESTED error envelope
 * (`{ error: { code, message, timestamp } }`). Defensive: a missing /
 * flat / non-JSON body degrades to a synthetic code rather than throwing
 * (the producer is the authority for the real code; this never crashes the
 * console on a malformed error body).
 */
async function parseOutboundError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `wms outbound request failed (${res.status})`;
  let timestamp: string | undefined;
  try {
    const body = (await res.json()) as {
      error?: { code?: string; message?: string; timestamp?: string };
    };
    if (body && typeof body === 'object' && body.error) {
      code = body.error.code ?? code;
      message = body.error.message ?? message;
      timestamp = body.error.timestamp;
    }
  } catch {
    /* keep the synthetic defaults — never throw on a bad error body */
  }
  return { code, message, timestamp };
}

/**
 * Single hardened call site. Resolves the domain-facing IAM OIDC token,
 * applies the timeout, and maps the wms error envelope to the § 2.5
 * resilience taxonomy.
 */
export async function callOutbound<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // ── Per-domain credential selection (§ 2.4.5.1, inherited from § 2.4.5):
  //    wms requires the IAM OIDC ACCESS token directly. NEVER
  //    getOperatorToken() — that is the IAM-domain (§ 2.6 exchanged)
  //    credential; wms would reject it (wrong issuer/type). The credential
  //    is the DOMAIN-FACING token (assumed-when-switched, else the base
  //    access token — net-zero; ADR-MONO-020 D4). Still NEVER the operator
  //    token (the #569 boundary holds, GAP-domain-scoped).
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('wms_outbound_no_gap_session', {
      requestId,
      path: opts.path,
    });
    // No IAM OIDC session ⇒ whole-session re-login (no partial authed state).
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    // wms gateway echoes/generates X-Request-Id; X-Actor-Id is set by the
    // wms gateway from the JWT — the console does NOT forge it.
    'X-Request-Id': requestId,
  };
  // NOTE: deliberately NO `X-Tenant-Id` — wms resolves tenant from the JWT
  // `tenant_id=wms` claim (§ 2.4.5 tenant-model divergence).

  if (opts.method === 'POST' || opts.method === 'PATCH') {
    if (!opts.idempotencyKey) {
      throw new ApiError(
        400,
        'VALIDATION_ERROR',
        'An idempotency key is required for this action',
      );
    }
    headers['Idempotency-Key'] = opts.idempotencyKey;
    // NO `X-Operator-Reason` — the wms outbound surface does not define it;
    // carrying IAM's § 2.4.1 reason header over is a drift defect.
  }
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  const baseUrl = opts.baseUrl ?? env.WMS_OUTBOUND_BASE_URL;
  const timeoutMs = opts.timeoutMs ?? env.WMS_OUTBOUND_TIMEOUT_MS;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const res = await fetch(`${baseUrl}${opts.path}`, {
      method: opts.method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseOutboundError(res);
      logger.warn('wms_outbound_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      // IAM OIDC session expired → whole-session re-login (NOT a per-section
      // degrade — no partial authed state).
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseOutboundError(res);
      logger.warn('wms_outbound_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      // Role-insufficient (e.g. lacking OUTBOUND_WRITE) → inline, no crash,
      // no re-login loop.
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseOutboundError(res);
      logger.warn('wms_outbound_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      // ONLY the wms outbound section degrades — shell + other sections intact.
      throw new WmsOutboundUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'wms outbound-service unavailable',
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR, 404 *_NOT_FOUND, 422 STATE_TRANSITION_INVALID /
      // PICKING_INCOMPLETE / PACKING_INCOMPLETE, 409 CONFLICT (optimistic
      // lock) / DUPLICATE_REQUEST → inline actionable (no crash). The 409
      // CONFLICT path drives a refetch + retry-prompt in the UI (never a
      // silent auto-retry).
      const e = await parseOutboundError(res);
      logger.warn('wms_outbound_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    logger.info('wms_outbound_ok', {
      requestId,
      status: res.status,
      path: opts.path,
    });
    return parse(json);
  } catch (err) {
    if (err instanceof ApiError || err instanceof WmsOutboundUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('wms_outbound_timeout', {
        requestId,
        timeoutMs,
        path: opts.path,
      });
      throw new WmsOutboundUnavailableError(
        'timeout',
        'TIMEOUT',
        'wms outbound-service call timed out',
      );
    }
    logger.error('wms_outbound_error', { requestId, path: opts.path });
    throw new WmsOutboundUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'wms outbound-service call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

export const clampSize = (size?: number): number =>
  clampPageSize(size, OUTBOUND_DEFAULT_PAGE_SIZE, OUTBOUND_MAX_PAGE_SIZE);
