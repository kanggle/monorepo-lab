import { clampPageSize } from '@/shared/lib/pagination';
import { WmsUnavailableError } from '@/shared/api/errors';
import {
  callWmsGateway,
  type WmsGatewayProfile,
} from '@/shared/api/wms-gateway';
import { WMS_DEFAULT_PAGE_SIZE, WMS_MAX_PAGE_SIZE } from './types';

/**
 * Server-side wms `admin-service` HTTP core (TASK-PC-FE-007 —
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

export interface CallOptions {
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
 * wms-ops profile for the shared {@link callWmsGateway} core: the WMS admin
 * read-model surface (`WMS_ADMIN_BASE_URL`, `WMS_TIMEOUT_MS`) degrades via
 * {@link WmsUnavailableError} and logs `wms_*` events. Surfaces the
 * `X-Read-Model-Lag-Seconds` hint via the shared result's `lagSeconds`.
 */
const WMS_ADMIN_PROFILE: WmsGatewayProfile = {
  logPrefix: 'wms',
  requestFailedLabel: 'wms request failed',
  resolveDefaults: (env) => ({
    baseUrl: env.WMS_ADMIN_BASE_URL,
    timeoutMs: env.WMS_TIMEOUT_MS,
  }),
  makeUnavailable: (reason, code, message) =>
    new WmsUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof WmsUnavailableError,
  messages: {
    degraded: 'wms admin-service unavailable',
    timeout: 'wms admin-service call timed out',
    network: 'wms admin-service call failed',
  },
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callWmsGateway} core with the {@link WMS_ADMIN_PROFILE}. Returns the
 * `{ data, lagSeconds }` result directly ({@link WmsResult}) — the admin
 * read-model surfaces the `X-Read-Model-Lag-Seconds` hint.
 */
export async function callWmsAdmin<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<WmsResult<T>> {
  return callWmsGateway(
    {
      method: opts.method,
      path: opts.path,
      idempotencyKey: opts.idempotencyKey,
      body: opts.body,
    },
    parse,
    WMS_ADMIN_PROFILE,
  );
}

// ---------------------------------------------------------------------------
// pagination helper
// ---------------------------------------------------------------------------

export function pageParams(
  qs: URLSearchParams,
  page?: number,
  size?: number,
): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      clampPageSize(size, WMS_DEFAULT_PAGE_SIZE, WMS_MAX_PAGE_SIZE),
    ),
  );
}
