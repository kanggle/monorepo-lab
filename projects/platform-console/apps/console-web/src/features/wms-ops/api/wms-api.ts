import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';
import {
  InventoryPageSchema,
  type InventoryPage,
  type InventoryQueryParams,
  InventoryRowSchema,
  type InventoryRow,
  ThroughputSchema,
  type Throughput,
  OrderPageSchema,
  type OrderPage,
  ShipmentPageSchema,
  type ShipmentPage,
  type ShipmentQueryParams,
  AsnPageSchema,
  type AsnPage,
  InspectionSchema,
  type Inspection,
  AdjustmentPageSchema,
  type AdjustmentPage,
  AlertPageSchema,
  type AlertPage,
  type AlertQueryParams,
  AckResultSchema,
  type AckResult,
  RefPageSchema,
  type RefPage,
  ProjectionStatusSchema,
  type ProjectionStatus,
  WMS_DEFAULT_PAGE_SIZE,
  WMS_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side wms `admin-service` operations client (TASK-PC-FE-007 —
 * ADR-MONO-013 Phase 4 slice 1, the first NON-IAM federated domain).
 *
 * Server-only by construction (same posture as `accounts-api.ts` /
 * `audit-api.ts`): imported exclusively from server components and the
 * `runtime = 'nodejs'` route handlers; `getServerEnv()` throws outside the
 * server runtime. The token + any data never reach client JS — client
 * components call the same-origin `/api/wms/**` proxy routes, which attach
 * the HttpOnly credential here server-side.
 *
 * ── THE AUTH-MODEL DIVERGENCE (the crux — console-integration-contract
 *    § 2.4.5 "per-domain credential selection") ──────────────────────────
 *
 * wms's `admin-service-api.md` requires `Authorization: Bearer <IAM OIDC
 * access token>` DIRECTLY (RS256, ADR-001; the wms gateway + admin-service
 * validate it against IAM JWKS and enforce `tenant_id=wms` from the JWT
 * claim itself). wms has NO token-exchange.
 *
 * Therefore this client uses `getAccessToken()` (the GAP-session HttpOnly
 * cookie) and NEVER `getOperatorToken()`. This is the EXACT INVERSE of the
 * IAM `features/{accounts,audit,operators,dashboards}` clients — and that is
 * correct: the #569 trust-boundary invariant is GAP-domain-scoped (it
 * forbids the IAM OIDC token on GAP's `/api/admin/**` because IAM requires
 * the § 2.6 exchanged operator token there). wms's gateway *requires* the
 * IAM OIDC token — not a conflict, a different per-domain binding. Carrying
 * the IAM operator-token-exchange to wms would misapply the IAM auth model
 * and be rejected by wms (wrong issuer/type). A test pins this (the
 * `getOperatorToken` path MUST be absent for wms).
 *
 * Tenant invariant (§ 2.4.5): wms resolves the tenant from the JWT
 * `tenant_id` claim (`=wms`) — NOT an `X-Tenant-Id` header (the GAP
 * mechanism) and NOT a producer `admin_operators.tenant_id` lookup. The
 * console therefore does NOT send `X-Tenant-Id` to wms; the tenant rides
 * inside the IAM OIDC token. wms rejects cross-tenant producer-side.
 *
 * Mutation discipline (§ 2.4.5 / alert-ack only): the single mutation
 * (`acknowledgeAlert`) carries `Idempotency-Key` (caller-supplied, stable
 * per a confirmed action, fresh per a new attempt) and an EMPTY body. wms
 * does NOT define `X-Operator-Reason` — it is NEVER sent (carrying GAP's
 * § 2.4.1 reason header over is a header-matrix-drift defect; a test
 * asserts its absence). All read endpoints carry NO mutation artifacts.
 *
 * Error envelope (§ 2.4.5 / § 2.5): wms uses the NESTED shape
 * `{ "error": { "code", "message", "timestamp", … } }` — DISTINCT from
 * GAP's flat `{ code, message, timestamp }`. `parseWmsError()` reads the
 * wms shape (and tolerates an absent/flat body without crashing).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout
 * (no unbounded default); `401` → `ApiError` (forced WHOLE-SESSION GAP
 * re-login — not a per-section degrade); `403` → `ApiError` (inline "not
 * available to your role"); `404`/`400`/`422`/`409` → `ApiError` (inline
 * actionable, no crash); `503`/timeout/network → `WmsUnavailableError`
 * (ONLY the wms section degrades — shell + IAM sections intact).
 *
 * Read-model lag honesty (§ 2.4.5): the `X-Read-Model-Lag-Seconds` response
 * header (set by the producer when the slowest projection lags > 5 s) is
 * surfaced on the result (`lagSeconds`) as a NON-blocking eventual-
 * consistency hint — the section still renders.
 *
 * Logging: structured, server-side only; the IAM access token and any wms
 * data are NEVER logged (redacted) — § 2.6 logging invariant extended.
 */

interface CallOptions {
  method: 'GET' | 'POST';
  /** Path relative to `WMS_ADMIN_BASE_URL` (e.g. `/dashboard/alerts`). */
  path: string;
  /** Stable per a single confirmed action → `Idempotency-Key` (POST only). */
  idempotencyKey?: string;
  /** Alert-ack body is intentionally empty; this stays `undefined`. */
  body?: unknown;
}

/** A read/mutation result + the optional read-model-lag hint. */
export interface WmsResult<T> {
  data: T;
  /** `X-Read-Model-Lag-Seconds` when the producer set it (> 5 s lag);
   *  `null` when absent. NON-blocking eventual-consistency hint. */
  lagSeconds: number | null;
}

/**
 * Parses the wms NESTED error envelope
 * (`{ error: { code, message, timestamp } }`). Defensive: a missing /
 * flat / non-JSON body degrades to a synthetic code rather than throwing
 * (the producer is the authority for the real code; this never crashes the
 * console on a malformed error body).
 */
async function parseWmsError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `wms request failed (${res.status})`;
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

function readLagHeader(res: Response): number | null {
  const raw = res.headers.get('X-Read-Model-Lag-Seconds');
  if (raw === null) return null;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}

/**
 * Single hardened call site. Resolves the IAM OIDC access token, applies
 * the timeout, maps the wms error envelope to the § 2.5 resilience
 * taxonomy, and surfaces the read-model-lag hint.
 */
async function callWmsAdmin<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<WmsResult<T>> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // ── Per-domain credential selection (§ 2.4.5): wms requires the GAP
  //    OIDC ACCESS token directly. NEVER getOperatorToken() — that is the
  //    GAP-domain (§ 2.6 exchanged) credential; wms would reject it
  //    (wrong issuer/type) and it would misapply the IAM auth model. The
  //    #569 invariant is GAP-domain-scoped and does NOT apply here.
  //    ── ADR-MONO-020 D4 / § 2.7: the credential is the DOMAIN-FACING GAP
  //    OIDC token — the ASSUMED (tenant-scoped) token when the operator has
  //    switched to a customer, else the base access token (net-zero). Still
  //    NOT the operator token. The signed `tenant_id`/`entitled_domains`
  //    follow the active-tenant selection (the A↔B re-scope).
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('wms_no_gap_session', { requestId, path: opts.path });
    // No IAM OIDC session ⇒ whole-session re-login (not a per-section
    // degrade — no partial authed state).
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
  // `tenant_id` claim (§ 2.4.5 tenant-model divergence).

  if (opts.method === 'POST') {
    if (!opts.idempotencyKey) {
      throw new ApiError(
        400,
        'VALIDATION_ERROR',
        'An idempotency key is required for this action',
      );
    }
    headers['Idempotency-Key'] = opts.idempotencyKey;
    // NO `X-Operator-Reason` — wms does not define it on this surface;
    // carrying GAP's § 2.4.1 reason header over is a drift defect.
  }
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.WMS_TIMEOUT_MS);

  try {
    const res = await fetch(`${env.WMS_ADMIN_BASE_URL}${opts.path}`, {
      method: opts.method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseWmsError(res);
      logger.warn('wms_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      // IAM OIDC session expired → whole-session re-login (no partial
      // authed state — NOT a per-section degrade).
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseWmsError(res);
      logger.warn('wms_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      // Role-insufficient (e.g. WMS_VIEWER attempting the WMS_OPERATOR+
      // ack, or non-WMS_ADMIN on projection-status) → inline, no crash,
      // no re-login loop.
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseWmsError(res);
      logger.warn('wms_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      // ONLY the wms section degrades — shell + IAM sections intact.
      throw new WmsUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'wms admin-service unavailable',
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR (throughput range), 404 NOT_FOUND,
      // 422 STATE_TRANSITION_INVALID (alert already acknowledged),
      // 409 DUPLICATE_REQUEST → inline actionable (no crash).
      const e = await parseWmsError(res);
      logger.warn('wms_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const lagSeconds = readLagHeader(res);
    const json = await res.json();
    logger.info('wms_ok', {
      requestId,
      status: res.status,
      path: opts.path,
      lagSeconds,
    });
    return { data: parse(json), lagSeconds };
  } catch (err) {
    if (err instanceof ApiError || err instanceof WmsUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('wms_timeout', {
        requestId,
        timeoutMs: env.WMS_TIMEOUT_MS,
        path: opts.path,
      });
      throw new WmsUnavailableError(
        'timeout',
        'TIMEOUT',
        'wms admin-service call timed out',
      );
    }
    logger.error('wms_error', { requestId, path: opts.path });
    throw new WmsUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'wms admin-service call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// pagination helper
// ---------------------------------------------------------------------------

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(WMS_MAX_PAGE_SIZE, Math.max(1, size ?? WMS_DEFAULT_PAGE_SIZE)),
    ),
  );
}

// ---------------------------------------------------------------------------
// 1.1 inventory snapshot — GET /dashboard/inventory
// ---------------------------------------------------------------------------

export function listInventory(
  params: InventoryQueryParams = {},
): Promise<WmsResult<InventoryPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.locationId) qs.set('locationId', params.locationId);
  if (params.skuId) qs.set('skuId', params.skuId);
  if (params.lotId) qs.set('lotId', params.lotId);
  if (params.lowStockOnly) qs.set('lowStockOnly', 'true');
  if (params.minOnHand !== undefined) {
    qs.set('minOnHand', String(params.minOnHand));
  }
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/inventory?${qs.toString()}` },
    (json) => InventoryPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.1 inventory by-key — GET /dashboard/inventory/by-key
// ---------------------------------------------------------------------------

export function getInventoryByKey(key: {
  locationId: string;
  skuId: string;
  lotId?: string;
}): Promise<WmsResult<InventoryRow>> {
  const qs = new URLSearchParams();
  qs.set('locationId', key.locationId);
  qs.set('skuId', key.skuId);
  if (key.lotId) qs.set('lotId', key.lotId);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/inventory/by-key?${qs.toString()}` },
    (json) => InventoryRowSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.2 throughput — GET /dashboard/throughput
// ---------------------------------------------------------------------------

export function getThroughput(args: {
  warehouseId: string;
  from: string;
  to: string;
}): Promise<WmsResult<Throughput>> {
  const qs = new URLSearchParams({
    warehouseId: args.warehouseId,
    from: args.from,
    to: args.to,
  });
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/throughput?${qs.toString()}` },
    (json) => ThroughputSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.3 orders — GET /dashboard/orders
// ---------------------------------------------------------------------------

export function listOrders(
  params: { warehouseId?: string; status?: string; page?: number; size?: number } = {},
): Promise<WmsResult<OrderPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.status) qs.set('status', params.status);
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/orders?${qs.toString()}` },
    (json) => OrderPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.3 shipments — GET /dashboard/shipments
// ---------------------------------------------------------------------------

export function listShipments(
  params: ShipmentQueryParams = {},
): Promise<WmsResult<ShipmentPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.carrierCode) qs.set('carrierCode', params.carrierCode);
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/shipments?${qs.toString()}` },
    (json) => ShipmentPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.4 asns — GET /dashboard/asns
// ---------------------------------------------------------------------------

export function listAsns(
  params: { warehouseId?: string; status?: string; page?: number; size?: number } = {},
): Promise<WmsResult<AsnPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.status) qs.set('status', params.status);
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/asns?${qs.toString()}` },
    (json) => AsnPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.4 asn inspection — GET /dashboard/asns/{asnId}/inspection
// ---------------------------------------------------------------------------

export function getAsnInspection(
  asnId: string,
): Promise<WmsResult<Inspection>> {
  return callWmsAdmin(
    {
      method: 'GET',
      path: `/dashboard/asns/${encodeURIComponent(asnId)}/inspection`,
    },
    (json) => InspectionSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.5 adjustments audit (append-only — READ only, no edit) — GET /dashboard/adjustments
// ---------------------------------------------------------------------------

export function listAdjustments(
  params: { warehouseId?: string; reasonCode?: string; page?: number; size?: number } = {},
): Promise<WmsResult<AdjustmentPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.reasonCode) qs.set('reasonCode', params.reasonCode);
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/adjustments?${qs.toString()}` },
    (json) => AdjustmentPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.6 alerts — GET /dashboard/alerts
// ---------------------------------------------------------------------------

export function listAlerts(
  params: AlertQueryParams = {},
): Promise<WmsResult<AlertPage>> {
  const qs = new URLSearchParams();
  if (params.alertType) qs.set('alertType', params.alertType);
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.acknowledged !== undefined) {
    qs.set('acknowledged', String(params.acknowledged));
  }
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/alerts?${qs.toString()}` },
    (json) => AlertPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.6 alert acknowledge — POST /dashboard/alerts/{alertId}/acknowledge
//     THE ONLY mutation. Idempotency-Key (caller-supplied, stable per
//     confirmed action) + EMPTY body. NO X-Operator-Reason (wms surface
//     does not define it — confirm-gated in the UI instead).
// ---------------------------------------------------------------------------

export function acknowledgeAlert(
  alertId: string,
  idempotencyKey: string,
): Promise<WmsResult<AckResult>> {
  return callWmsAdmin(
    {
      method: 'POST',
      path: `/dashboard/alerts/${encodeURIComponent(alertId)}/acknowledge`,
      idempotencyKey,
      // EMPTY body per admin-service-api.md § 1.6 (the producer sets
      // acknowledged_at = now(), acknowledged_by = X-Actor-Id).
      body: undefined,
    },
    (json) => AckResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.7 master refs — GET /dashboard/refs/{type}
// ---------------------------------------------------------------------------

export function listRefs(
  type: string,
  params: { page?: number; size?: number } = {},
): Promise<WmsResult<RefPage>> {
  const qs = new URLSearchParams();
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    {
      method: 'GET',
      path: `/dashboard/refs/${encodeURIComponent(type)}?${qs.toString()}`,
    },
    (json) => RefPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 6.2 projection status — GET /operations/projection-status
// ---------------------------------------------------------------------------

export function getProjectionStatus(): Promise<
  WmsResult<ProjectionStatus>
> {
  return callWmsAdmin(
    { method: 'GET', path: `/operations/projection-status` },
    (json) => ProjectionStatusSchema.parse(json),
  );
}
