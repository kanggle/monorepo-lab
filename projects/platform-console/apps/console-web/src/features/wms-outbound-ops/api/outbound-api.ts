import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, WmsOutboundUnavailableError } from '@/shared/api/errors';
import {
  OutboundOrderPageSchema,
  type OutboundOrderPage,
  OutboundOrderDetailSchema,
  type OutboundOrderDetail,
  OutboundSagaSchema,
  type OutboundSaga,
  PickingRequestListSchema,
  type PickingRequestList,
  PickConfirmationSchema,
  type PickConfirmation,
  PackingUnitSchema,
  type PackingUnit,
  ShipmentSchema,
  type Shipment,
  CancelResultSchema,
  type CancelResult,
  TmsRetryResultSchema,
  type TmsRetryResult,
  AdminShipmentRefPageSchema,
  type OutboundListParams,
  OUTBOUND_DEFAULT_PAGE_SIZE,
  OUTBOUND_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side wms `outbound-service` operations client (TASK-PC-FE-057 —
 * the SECOND wms surface, ADR-MONO-022 § D7 operator leg). Drives an
 * ecommerce-originated outbound order PICKING → PICKED → PACKING/PACKED →
 * SHIPPED from inside the console.
 *
 * Server-only by construction (same posture as `wms-api.ts`): imported
 * exclusively from server components and the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/wms/outbound/**` proxy routes, which attach the HttpOnly credential
 * here server-side.
 *
 * ── THE AUTH-MODEL DIVERGENCE (the crux — console-integration-contract
 *    § 2.4.5.1, inherited verbatim from § 2.4.5) ───────────────────────────
 *
 * wms's `outbound-service-api.md` § Global Conventions requires
 * `Authorization: Bearer <IAM OIDC access token>` DIRECTLY (RS256, ADR-001;
 * the wms gateway + outbound-service validate it against IAM JWKS and enforce
 * `tenant_id=wms` from the JWT claim itself). wms has NO token-exchange.
 *
 * Therefore this client uses `getDomainFacingToken()` (the assumed
 * tenant-scoped IAM OIDC token when the operator switched to a customer, else
 * the base access token — net-zero; ADR-MONO-020 D4) and NEVER
 * `getOperatorToken()`. This is the EXACT INVERSE of the IAM
 * `features/{accounts,audit,operators,dashboards}` clients — the #569
 * trust-boundary invariant is GAP-domain-scoped and does NOT generalise to
 * wms (the wms gateway *requires* the IAM OIDC token). A test pins this (the
 * `getOperatorToken` path MUST be absent).
 *
 * Tenant invariant (§ 2.4.5.1): wms resolves the tenant from the JWT
 * `tenant_id=wms` claim — NOT an `X-Tenant-Id` header. The console therefore
 * does NOT send `X-Tenant-Id`; wms rejects cross-tenant producer-side.
 *
 * Mutation discipline (§ 2.4.5.1): every POST/PATCH (ops 5–8) carries an
 * `Idempotency-Key` (caller-supplied, stable per a confirmed action, fresh
 * per a new attempt) and is reason-free — wms does NOT define
 * `X-Operator-Reason` (carrying IAM's § 2.4.1 reason header over is a
 * header-matrix-drift defect; a test asserts its absence). All reads carry
 * NO mutation artifacts.
 *
 * Error envelope (§ 2.4.5.1 / § 2.5): wms uses the NESTED shape
 * `{ "error": { "code", "message", "timestamp", … } }` — DISTINCT from
 * GAP's flat `{ code, message, timestamp }`. `parseOutboundError()` reads the
 * wms shape (and tolerates an absent/flat body without crashing).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * `401` → `ApiError` (whole-session re-login); `403` → `ApiError` (inline
 * "not available to your role"); `404`/`400`/`422`/`409` → `ApiError` (inline
 * actionable — the `409 CONFLICT` path drives a refetch + retry-prompt, never
 * a silent auto-retry); `503`/timeout/network → `WmsOutboundUnavailableError`
 * (ONLY this section degrades).
 *
 * Logging: structured, server-side only; the IAM access token and any wms
 * data are NEVER logged (redacted).
 */

interface CallOptions {
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
async function callOutbound<T>(
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

function clampSize(size?: number): number {
  return Math.min(
    OUTBOUND_MAX_PAGE_SIZE,
    Math.max(1, size ?? OUTBOUND_DEFAULT_PAGE_SIZE),
  );
}

// ===========================================================================
// READS (OUTBOUND_READ — no mutation artifacts)
// ===========================================================================

/** 1.3 — GET /orders (paginated order summaries). */
export function listOrders(
  params: OutboundListParams = {},
): Promise<OutboundOrderPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.orderNo) qs.set('orderNo', params.orderNo);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callOutbound({ method: 'GET', path: `/orders?${qs.toString()}` }, (j) =>
    OutboundOrderPageSchema.parse(j),
  );
}

/** 1.2 — GET /orders/{id} (lines + status + version). */
export function getOrder(orderId: string): Promise<OutboundOrderDetail> {
  return callOutbound(
    { method: 'GET', path: `/orders/${encodeURIComponent(orderId)}` },
    (j) => OutboundOrderDetailSchema.parse(j),
  );
}

/** 5.1 — GET /orders/{id}/saga. */
export function getSaga(orderId: string): Promise<OutboundSaga> {
  return callOutbound(
    { method: 'GET', path: `/orders/${encodeURIComponent(orderId)}/saga` },
    (j) => OutboundSagaSchema.parse(j),
  );
}

/** 2.4 — GET /orders/{id}/picking-requests (planned lines; may be `[]`). */
export function listPickingRequests(
  orderId: string,
): Promise<PickingRequestList> {
  return callOutbound(
    {
      method: 'GET',
      path: `/orders/${encodeURIComponent(orderId)}/picking-requests`,
    },
    (j) => PickingRequestListSchema.parse(j),
  );
}

// ===========================================================================
// MUTATIONS (OUTBOUND_WRITE — each Idempotency-Key, reason-free, confirm-gated)
// ===========================================================================

/** 2.3 line shape for the pick confirmation body (built from planned lines). */
export interface ConfirmPickLine {
  orderLineId: string;
  skuId: string;
  lotId?: string | null;
  actualLocationId: string;
  qtyConfirmed: number;
}

/** 2.3 — POST /picking-requests/{id}/confirmations. */
export function confirmPick(
  pickingRequestId: string,
  lines: ConfirmPickLine[],
  idempotencyKey: string,
  notes?: string,
): Promise<PickConfirmation> {
  return callOutbound(
    {
      method: 'POST',
      path: `/picking-requests/${encodeURIComponent(pickingRequestId)}/confirmations`,
      idempotencyKey,
      body: notes ? { notes, lines } : { lines },
    },
    (j) => PickConfirmationSchema.parse(j),
  );
}

/** 3.1 line shape for the packing-unit create body. */
export interface PackingUnitLine {
  orderLineId: string;
  skuId: string;
  lotId?: string | null;
  qty: number;
}

/** 3.1 — POST /orders/{id}/packing-units (create unit). */
export function createPackingUnit(
  orderId: string,
  cartonNo: string,
  lines: PackingUnitLine[],
  idempotencyKey: string,
): Promise<PackingUnit> {
  return callOutbound(
    {
      method: 'POST',
      path: `/orders/${encodeURIComponent(orderId)}/packing-units`,
      idempotencyKey,
      body: { cartonNo, packingType: 'BOX', lines },
    },
    (j) => PackingUnitSchema.parse(j),
  );
}

/** 3.2 — PATCH /packing-units/{id} (seal, version-checked). */
export function sealPackingUnit(
  packingUnitId: string,
  version: number,
  idempotencyKey: string,
): Promise<PackingUnit> {
  return callOutbound(
    {
      method: 'PATCH',
      path: `/packing-units/${encodeURIComponent(packingUnitId)}`,
      idempotencyKey,
      body: { seal: true, version },
    },
    (j) => PackingUnitSchema.parse(j),
  );
}

/** 4.1 — POST /orders/{id}/shipments (confirm shipping, version-checked). */
export function confirmShipping(
  orderId: string,
  version: number,
  idempotencyKey: string,
  carrierCode = 'DEMO-CARRIER',
): Promise<Shipment> {
  return callOutbound(
    {
      method: 'POST',
      path: `/orders/${encodeURIComponent(orderId)}/shipments`,
      idempotencyKey,
      body: { carrierCode, version },
    },
    (j) => ShipmentSchema.parse(j),
  );
}

/**
 * 1.4 — POST /orders/{id}:cancel (cancel order, reason-required,
 * version-checked). TASK-PC-FE-085.
 *
 * Diverges from the reason-free forward mutations: a **REQUIRED `reason`**
 * (3..500, producer § 1.4) rides in the JSON body (NOT a header — the wms
 * surface still has no `X-Operator-Reason`). Role is producer-enforced
 * (`OUTBOUND_WRITE` for PICKING / `OUTBOUND_ADMIN` post-pick) — a 403 maps
 * inline; the console never pre-gates on role. Note the `:cancel` action
 * suffix on the path (not a `/cancel` sub-resource).
 */
export function cancelOrder(
  orderId: string,
  version: number,
  reason: string,
  idempotencyKey: string,
): Promise<CancelResult> {
  return callOutbound(
    {
      method: 'POST',
      path: `/orders/${encodeURIComponent(orderId)}:cancel`,
      idempotencyKey,
      body: { reason, version },
    },
    (j) => CancelResultSchema.parse(j),
  );
}

// ===========================================================================
// TMS RETRY (OUTBOUND_ADMIN — reason-free; TASK-PC-FE-087, op 10)
// ===========================================================================

/**
 * Resolves the `shipmentId` for an order from the wms admin read-model
 * (admin-service-api.md § 1.3 `GET /api/v1/admin/dashboard/shipments?orderId`).
 *
 * The TMS-retry producer endpoint (§ 4.3) is shipment-keyed, but the
 * outbound order-centric reads carry no `shipmentId` (§ 1.2 order detail =
 * create-response shape; there is NO `GET /orders/{id}/shipments`). So the id
 * is read from the admin projection (the `orderId` filter is contracted). This
 * hits `WMS_ADMIN_BASE_URL` with the SAME IAM-OIDC domain-facing credential —
 * same wms gateway, distinct `/api/v1/admin` path prefix (§ 2.4.5 / § 2.4.5.1).
 * `503`/timeout/network still map to `WmsOutboundUnavailableError` (the
 * outbound section degrade), NOT a crash. Returns `null` when the order has no
 * projected shipment (→ the proxy maps that to a `404 SHIPMENT_NOT_FOUND`).
 */
export async function resolveShipmentIdForOrder(
  orderId: string,
): Promise<string | null> {
  const env = getServerEnv();
  const page = await callOutbound(
    {
      method: 'GET',
      path: `/dashboard/shipments?orderId=${encodeURIComponent(orderId)}&size=1`,
      baseUrl: env.WMS_ADMIN_BASE_URL,
      timeoutMs: env.WMS_TIMEOUT_MS,
    },
    (j) => AdminShipmentRefPageSchema.parse(j),
  );
  return page.content[0]?.shipmentId ?? null;
}

/**
 * 4.3 — POST /shipments/{id}:retry-tms-notify (manual TMS retry).
 * TASK-PC-FE-087, console-integration-contract § 2.4.5.1 op 10.
 *
 * Reason-free (re-notifies the carrier only; stock already consumed — UNLIKE
 * the reason-required cancel). Empty `{}` body + `Idempotency-Key`. Role is
 * producer-enforced (`OUTBOUND_ADMIN`) — a 403 maps inline; the console never
 * pre-gates. Note the `:retry-tms-notify` action suffix on the path.
 */
export function retryTmsNotify(
  shipmentId: string,
  idempotencyKey: string,
): Promise<TmsRetryResult> {
  return callOutbound(
    {
      method: 'POST',
      path: `/shipments/${encodeURIComponent(shipmentId)}:retry-tms-notify`,
      idempotencyKey,
      body: {},
    },
    (j) => TmsRetryResultSchema.parse(j),
  );
}
