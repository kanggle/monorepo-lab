import { ErpUnavailableError } from '@/shared/api/errors';
import {
  callFlatEnvelopeGateway,
  type FlatEnvelopeGatewayProfile,
} from '@/shared/api/flat-envelope-gateway';

/**
 * Server-side erp `masterdata-service` operations client (TASK-PC-FE-010 —
 * ADR-MONO-013 Phase 6). As of TASK-PC-FE-243 the hardened call scaffold is the
 * shared {@link callFlatEnvelopeGateway} FLAT-envelope core; this file is now a
 * thin wrapper supplying the {@link ERP_PROFILE} (its `ERP_BASE_URL` /
 * `ERP_TIMEOUT_MS` selectors, `ErpUnavailableError` degrade class, `erp_*`
 * log-event prefix + messages). Behaviour is IDENTICAL to the pre-consolidation
 * per-client copy.
 *
 * Server-only by construction (same posture as `finance-api.ts` / `scm-api.ts`
 * / `wms-api.ts`): imported exclusively from server components and the
 * `runtime = 'nodejs'` route handlers. The token + any data never reach client
 * JS — client components call the same-origin `/api/erp/**` proxy routes, which
 * attach the HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of § 2.4.5 (NOT re-derived) ──
 * The domain-facing IAM OIDC token (`getDomainFacingToken()` — assumed-when-
 * switched, else base; ADR-MONO-020 D4), NEVER `getOperatorToken()` (the #569
 * invariant is GAP-domain-scoped). erp resolves the tenant from the JWT
 * `tenant_id ∈ {erp,*}` claim producer-side — the console sends NO
 * `X-Tenant-Id`.
 *
 * READ + DEPARTMENT/MASTER WRITE (§ 2.4.8): the read functions are pure GET —
 * NO `Idempotency-Key`, NO `X-Operator-Reason`, NO body. Writes carry an
 * `Idempotency-Key` + a JSON body; `reason` rides in the BODY where the
 * producer has a slot (retire / move-parent) — the console NEVER sends
 * `X-Operator-Reason` (erp has no producer slot for it). The idempotency
 * fail-fast guard is OFF (the header is attached only when the caller supplies
 * a key — the honest erp posture, unlike wms's client-side 400).
 *
 * Error envelope (§ 2.4.8 / § 2.5): erp uses the FLAT shape
 * `{ code, message, details?, timestamp }` (DISTINCT from wms's NESTED
 * `{ error: { code } }`); the shared parser reads the flat shape against the
 * erp vocabulary (`MASTERDATA_NOT_FOUND`, `TENANT_FORBIDDEN`,
 * `DATA_SCOPE_FORBIDDEN`, `EXTERNAL_TRAFFIC_REJECTED`). A wms-nested body is NOT
 * mis-parsed (each domain owns its own envelope correctness).
 *
 * NO rate-limit handling (§ 2.4.8, identical to finance § 2.4.7 — honest
 * difference from scm § 2.4.6): `masterdata-api.md` documents no `429`; the
 * profile supplies no rate-limit policy, so a stray 429 surfaces through the
 * default-error path as a plain `ApiError` (no backoff, no Retry-After honour).
 *
 * Resilience (§ 2.5): AbortController hard timeout; `401` → whole-session
 * re-login `ApiError`; `403` → inline `ApiError`; `404 MASTERDATA_NOT_FOUND` /
 * `400` / `422` → inline `ApiError`; `503` / timeout / network →
 * `ErpUnavailableError` (ONLY the erp section degrades).
 *
 * Confidential / audit-heavy (§ 2.4.8): structured logs are server-side only;
 * the token, employee PII, business-partner financial details, cost-center
 * attrs, and record ids are NEVER logged — the log `path` carries the sanitised
 * `logPath` route shape (a literal `{id}` placeholder, NOT the URL).
 */

interface CallOptions {
  /** Path relative to `${ERP_BASE_URL}` (built by the caller, including any
   *  encoded `{id}` AND the URLSearchParams for `asOf` + filters + pagination). */
  path: string;
  /** Sanitised path shape for logging (no record id / no PII —
   *  e.g. `/api/erp/masterdata/departments/{id}`). */
  logPath: string;
  /** HTTP method. Defaults to `GET` (the read surface). The write functions
   *  supply `POST` / `PATCH`. Reads NEVER set this. */
  method?: 'GET' | 'POST' | 'PATCH';
  /** JSON request body for a mutation. Undefined on reads (a test asserts reads
   *  carry no body). */
  body?: unknown;
  /** `Idempotency-Key` header value — set on every master mutation, generated
   *  console-side per attempt. Undefined on reads (asserted absent). */
  idempotencyKey?: string;
}

/**
 * erp profile for the shared {@link callFlatEnvelopeGateway} core: degrades via
 * {@link ErpUnavailableError} and logs `erp_*` events against the erp
 * `masterdata-service` at `${ERP_BASE_URL}` (timeout `ERP_TIMEOUT_MS`). No
 * rate-limit policy (erp documents no 429) and no idempotency fail-fast guard
 * (the header is attached only when present).
 */
const ERP_PROFILE: FlatEnvelopeGatewayProfile = {
  logPrefix: 'erp',
  requestFailedLabel: 'erp request failed',
  resolveDefaults: (env) => ({
    baseUrl: env.ERP_BASE_URL,
    timeoutMs: env.ERP_TIMEOUT_MS,
  }),
  makeUnavailable: (reason, code, message) =>
    new ErpUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof ErpUnavailableError,
  messages: {
    degraded: 'erp unavailable',
    timeout: 'erp call timed out',
    network: 'erp call failed',
  },
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callFlatEnvelopeGateway} core with the {@link ERP_PROFILE}. Returns the
 * parsed body (the caller does not need the raw `Response`).
 */
export async function callErp<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const { raw } = await callFlatEnvelopeGateway(
    {
      path: opts.path,
      logPath: opts.logPath,
      method: opts.method,
      body: opts.body,
      idempotencyKey: opts.idempotencyKey,
    },
    parse,
    ERP_PROFILE,
  );
  return raw;
}
