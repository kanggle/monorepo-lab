import { ScmRateLimitedError } from '@/shared/api/errors';
import {
  callFlatEnvelopeGateway,
  parseFlatEnvelopeError,
  parseRetryAfterSeconds,
  type FlatEnvelopeGatewayProfile,
} from '@/shared/api/flat-envelope-gateway';

/**
 * Shared server-side **scm gateway HTTP core** (TASK-PC-FE-189 — dedup of the
 * per-feature scm client scaffold). As of TASK-PC-FE-243 this is a thin shim
 * over the shared {@link callFlatEnvelopeGateway} FLAT-envelope core (the SAME
 * scaffold the erp / finance / ledger clients now also share); scm's per-domain
 * variation — its `SCM_GATEWAY_BASE_URL` / `SCM_TIMEOUT_MS` selectors and its
 * bounded-backoff 429 policy ({@link ScmRateLimitedError}) — is supplied here so
 * the three scm feature clients' import paths and profile shape do NOT change.
 *
 * The three scm console feature clients — `scm-ops/api/scm-client.ts` (개요:
 * procurement + inventory-visibility, GET read-only),
 * `scm-replenishment/api/demand-planning-api.ts` (보충: suggestions read +
 * approve/dismiss), `scm-config/api/demand-planning-seed-api.ts` (설정: per-SKU
 * reorder-policy + sku-supplier-map GET/PUT) — each supply a
 * {@link ScmGatewayProfile} (its degrade error class + log-event prefix +
 * messages, plus the config client's 404-as-empty `notFoundSentinel`) and the
 * endpoint path/method/body.
 *
 * Server-only by construction; the token + any scm data never reach client JS —
 * client components call the same-origin `/api/scm/**` proxy routes.
 *
 * ── INVARIANTS PRESERVED VERBATIM (the three clients' contract; pinned by
 *    `tests/unit/{scm-api,demand-planning-api,demand-planning-seed-api}.test.ts`)
 *
 * - **Per-domain credential**: the DOMAIN-FACING IAM OIDC token
 *   (`getDomainFacingToken()`). NEVER `getOperatorToken()`.
 * - **Tenant model**: NO `X-Tenant-Id` — scm resolves the tenant from the JWT
 *   `tenant_id` claim (`∈ {scm,*}`); cross-tenant → `403 TENANT_FORBIDDEN`.
 * - **scm FLAT error envelope** `{ code, message, details?, timestamp }`
 *   (DISTINCT from wms NESTED `{ error: { code } }`); a missing/non-JSON body
 *   degrades to a synthetic code, never throws ({@link parseScmError}).
 * - **Resilience**: AbortController hard timeout (`SCM_TIMEOUT_MS`);
 *   `401`/`403`/`404`(non-empty)/`400`/`422`/`409` → {@link ApiError};
 *   `429` → ONE bounded backoff honouring `Retry-After` (cap 5s) then
 *   {@link ScmRateLimitedError} (NO retry storm); `503`/timeout/network → the
 *   profile's section-degrade error.
 * - **Read-only vs mutation**: `Content-Type` is attached ONLY when a `body` is
 *   present. A read-only caller (scm-ops) passes no method/body → GET, no
 *   `Content-Type`, no mutation headers (pinned).
 *
 * Logging: structured, server-side only; the IAM token + any scm data are NEVER
 * logged. Event names carry the profile prefix (`scm_*` / `scm_replenishment_*`
 * / `scm_config_*`).
 */

/** The bounded 429 backoff: at most ONE retry honouring `Retry-After` (capped)
 *  — NEVER an unbounded retry storm into the rate-limited gateway. */
export const MAX_SCM_RETRY_AFTER_SECONDS = 5;

/** Parses the scm `Retry-After` header (seconds), capped at
 *  {@link MAX_SCM_RETRY_AFTER_SECONDS}; defaults to the contract default (1s). */
export function parseScmRetryAfter(res: Response): number {
  return parseRetryAfterSeconds(res, MAX_SCM_RETRY_AFTER_SECONDS);
}

/**
 * Parses the scm FLAT error envelope (`{ code, message, details?, timestamp }`)
 * — a re-export shim over the shared {@link parseFlatEnvelopeError}. Defensive:
 * a missing / nested (wms-shaped) / non-JSON body degrades to a synthetic code
 * rather than throwing. `failLabel` is the synthetic default used only when the
 * body carries no `message` — the status is appended (`<label> (<status>)`).
 */
export const parseScmError = parseFlatEnvelopeError;

/** A scm gateway request (relative to `${SCM_GATEWAY_BASE_URL}`). */
export interface ScmGatewayRequest {
  /** Path relative to `${SCM_GATEWAY_BASE_URL}` (e.g. `/api/v1/procurement/po`). */
  path: string;
  /** HTTP method; defaults to `GET` (a read-only caller omits it). */
  method?: string;
  /** Optional mutation body; when present, `Content-Type: application/json` is
   *  attached and the body is JSON-serialised. A read-only caller omits it (no
   *  `Content-Type` — pinned by the scm-ops read-only test). */
  body?: unknown;
  /** `Idempotency-Key` header value — attached ONLY when present (forwarded
   *  verbatim to the shared flat-envelope core). The demand-planning
   *  approve/dismiss actions omit it (server-side idempotent by state); the
   *  logistics `dispatches/{id}:retry` action (TASK-PC-FE-258) supplies a stable
   *  key per confirmed attempt. Reads omit it. */
  idempotencyKey?: string;
  /** Seed-lookup GETs only: a `404` short-circuits to the profile's
   *  `notFoundSentinel` instead of throwing (config's 404-as-empty-state). */
  notFoundIsEmpty?: boolean;
}

/**
 * Per-feature behaviour the scm core is parameterised by — the ONLY thing that
 * differs between the three scm clients. A subset of
 * {@link FlatEnvelopeGatewayProfile}: scm's `resolveDefaults` (SCM base URL +
 * timeout) and its 429 `rateLimit` policy are supplied by {@link callScmGateway}
 * itself, so the feature clients keep their existing, smaller profile shape.
 */
export interface ScmGatewayProfile {
  /** Log-event prefix: `scm` | `scm_replenishment` | `scm_config`. */
  logPrefix: string;
  /** Synthetic default label for {@link parseScmError} (body-less errors); the
   *  status is appended, e.g. `'scm request failed'` → `scm request failed (503)`. */
  requestFailedLabel: string;
  /** Build the section-degrade error (`503`/timeout/network) — the feature's
   *  `Scm*UnavailableError` variant so ONLY that console section degrades. */
  makeUnavailable: (
    reason: 'timeout' | 'circuit_open' | 'downstream',
    code: string,
    message: string,
  ) => Error;
  /** `instanceof` guard for the profile's degrade error, so the `catch` re-throws
   *  it unchanged instead of re-wrapping (matches each client's original guard). */
  isUnavailable: (err: unknown) => boolean;
  /** Degrade / timeout / network `message` strings (preserved per feature). */
  messages: { degraded: string; timeout: string; network: string };
  /** Value returned on a `404` when `req.notFoundIsEmpty` (config seed lookups).
   *  Absent for the read/action clients (their 404 is a normal `ApiError`). */
  notFoundSentinel?: unknown;
}

/**
 * The single hardened scm gateway call site — a thin adapter over the shared
 * {@link callFlatEnvelopeGateway} core that injects scm's per-domain
 * `resolveDefaults` (`SCM_GATEWAY_BASE_URL` / `SCM_TIMEOUT_MS`) and its bounded
 * 429 `Retry-After` policy ({@link ScmRateLimitedError}, cap
 * {@link MAX_SCM_RETRY_AFTER_SECONDS}). Returns `{ raw, res }` — `raw` is the
 * parsed body (or the profile's `notFoundSentinel` on a 404-as-empty), `res`
 * exposes response headers to the caller (scm-ops reads `X-Cache`).
 */
export async function callScmGateway<T>(
  req: ScmGatewayRequest,
  parse: (json: unknown) => T,
  profile: ScmGatewayProfile,
): Promise<{ raw: T; res: Response }> {
  const flatProfile: FlatEnvelopeGatewayProfile = {
    logPrefix: profile.logPrefix,
    requestFailedLabel: profile.requestFailedLabel,
    resolveDefaults: (env) => ({
      baseUrl: env.SCM_GATEWAY_BASE_URL,
      timeoutMs: env.SCM_TIMEOUT_MS,
    }),
    makeUnavailable: profile.makeUnavailable,
    isUnavailable: profile.isUnavailable,
    messages: profile.messages,
    rateLimit: {
      maxRetryAfterSeconds: MAX_SCM_RETRY_AFTER_SECONDS,
      makeError: (retryAfterSeconds) =>
        new ScmRateLimitedError(retryAfterSeconds, 'scm gateway rate-limited'),
      isRateLimited: (err) => err instanceof ScmRateLimitedError,
    },
    notFoundSentinel: profile.notFoundSentinel,
  };
  return callFlatEnvelopeGateway(req, parse, flatProfile);
}
