import { clampPageSize } from '@/shared/lib/pagination';
import { ScmUnavailableError } from '@/shared/api/errors';
import {
  callScmGateway,
  type ScmGatewayProfile,
} from '@/shared/api/scm-gateway';
import { SCM_DEFAULT_PAGE_SIZE, SCM_MAX_PAGE_SIZE } from './types';

/**
 * Server-side scm gateway operations HTTP core (TASK-PC-FE-008 —
 * ADR-MONO-013 Phase 4 slice 2, the SECOND non-IAM federated domain;
 * completes Phase 4). STRICTLY READ-ONLY.
 *
 * ── MODULE SPLIT (TASK-PC-FE-145) ── this leaf holds the hardened
 * `callScm` call site, the scm FLAT error-envelope parser, the 429
 * `Retry-After` / `X-Cache` header readers, and the page-param helper.
 * The endpoint groups (`scm-procurement-api.ts` /
 * `scm-inventory-visibility-api.ts`) import this leaf; the
 * `scm-api.ts` barrel re-exports ONLY the endpoint functions (the public
 * surface — the `scm-api.test.ts` `Object.keys` guard pins exactly the
 * 6 endpoint exports, so this core stays barrel-internal). Pure
 * structural split — 0 behavior / contract / log-event change.
 *
 * Server-only by construction (same posture as `wms-api.ts` /
 * `accounts-api.ts`): imported exclusively from server components and the
 * `runtime = 'nodejs'` route handlers; `getServerEnv()` throws outside the
 * server runtime. The token + any data never reach client JS — client
 * components call the same-origin `/api/scm/**` proxy routes, which attach
 * the HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of the § 2.4.5 rule (NOT re-derived) ──
 *
 * The normative per-domain credential rule is DEFINED in
 * console-integration-contract § 2.4.5 (each binding declares its own
 * credential against its producer; never blanket-apply one domain's auth
 * to another). scm REUSES it with the SAME outcome as wms: the scm gateway
 * validates a IAM RS256 JWT (ADR-001) against IAM JWKS,
 * `tenant_id ∈ { scm, * }` enforced producer-side from the JWT claim (scm
 * `gateway-public-routes.md` § platform-console operator read consumer —
 * TASK-SCM-BE-015). scm has NO token-exchange.
 *
 * Therefore this client uses `getAccessToken()` (the GAP-session HttpOnly
 * cookie) and NEVER `getOperatorToken()` — exactly like `wms-api.ts`, and
 * the EXACT INVERSE of the IAM `features/{accounts,audit,operators,
 * dashboards}` clients. The #569 trust-boundary invariant is
 * GAP-domain-scoped and does NOT generalise to scm. A test pins this (the
 * `getOperatorToken` path MUST be absent for scm; the cross-domain
 * regression covers GAP=operator-token / wms=GAP-OIDC / scm=GAP-OIDC).
 *
 * Tenant invariant (§ 2.4.6 / reuse of § 2.4.5): scm resolves the tenant
 * from the JWT `tenant_id` claim (`∈ {scm,*}`) — NOT an `X-Tenant-Id`
 * header. The console therefore does NOT send `X-Tenant-Id` to scm; the
 * tenant rides inside the IAM OIDC token. scm rejects cross-tenant
 * producer-side (`403 TENANT_FORBIDDEN`).
 *
 * READ-ONLY (§ 2.4.6, NORMATIVE): every call is a pure GET. There is NO
 * mutation anywhere — NO `Idempotency-Key`, NO `X-Operator-Reason`, NO
 * body, NO PO write (`/submit|/confirm|/cancel`), NO webhook. Carrying the
 * FE-007 alert-ack OR the IAM § 2.4.1 mutation scaffolding here is a
 * defect (tests assert their absence).
 *
 * Error envelope (§ 2.4.6 / § 2.5): scm uses the FLAT shape
 * `{ code, message, details?, timestamp }` — DISTINCT from wms's NESTED
 * `{ error: { code … } }` and not assumed-identical to GAP's.
 * `parseScmError()` reads the scm flat shape (and tolerates an
 * absent/non-JSON body without crashing).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout
 * (no unbounded default); `401` → `ApiError` (forced WHOLE-SESSION GAP
 * re-login — not a per-section degrade); `403` → `ApiError` (inline "not
 * available / not scoped"); `404`/`400`/`422` → `ApiError` (inline
 * actionable, no crash); `429 RATE_LIMIT_EXCEEDED` → `ScmRateLimitedError`
 * (ONE bounded backoff honouring `Retry-After`, then surface — NO retry
 * storm); `503`/timeout/network → `ScmUnavailableError` (ONLY the scm
 * section degrades — shell + GAP/wms sections intact).
 *
 * Freshness honesty (§ 2.4.6): the per-SKU read's `X-Cache` header
 * (HIT|MISS|UNAVAILABLE) is surfaced on the result (`cache`); the
 * `/staleness` per-node status is surfaced as-is by the UI. The S5
 * `meta.warning` is a REQUIRED, surfaced field of every
 * inventory-visibility view-model (never stripped — enforced in
 * `types.ts`).
 *
 * Logging: structured, server-side only; the IAM access token and any scm
 * data are NEVER logged (redacted) — § 2.6 logging invariant extended.
 */

interface CallOptions {
  /** Path relative to `${SCM_GATEWAY_BASE_URL}` (e.g.
   *  `/api/v1/procurement/po`). */
  path: string;
}

/**
 * scm-ops profile for the shared {@link callScmGateway} core: the 개요 read
 * surface (procurement + inventory-visibility) degrades via
 * {@link ScmUnavailableError} and logs `scm_*` events. Every call is a pure GET
 * (read-only) — the caller passes no method/body, so the shared core attaches
 * NO `Content-Type` / mutation headers (pinned by the read-only test).
 */
const SCM_PROFILE: ScmGatewayProfile = {
  logPrefix: 'scm',
  requestFailedLabel: 'scm request failed',
  makeUnavailable: (reason, code, message) =>
    new ScmUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof ScmUnavailableError,
  messages: {
    degraded: 'scm gateway unavailable',
    timeout: 'scm gateway call timed out',
    network: 'scm gateway call failed',
  },
};

export function readCacheHeader(
  res: Response,
): 'HIT' | 'MISS' | 'UNAVAILABLE' | null {
  const raw = res.headers.get('X-Cache');
  if (raw === 'HIT' || raw === 'MISS' || raw === 'UNAVAILABLE') return raw;
  return null;
}

/**
 * Single hardened call site — a thin GET wrapper over the shared
 * {@link callScmGateway} core with the {@link SCM_PROFILE}. Read-only: no
 * method/body is passed, so the core sends a GET with no `Content-Type` /
 * mutation headers. Returns `{ raw, res }` — the caller reads `res` for the
 * per-SKU `X-Cache` header ({@link readCacheHeader}).
 */
export async function callScm<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<{ raw: T; res: Response }> {
  return callScmGateway({ path: opts.path }, parse, SCM_PROFILE);
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
      clampPageSize(size, SCM_DEFAULT_PAGE_SIZE, SCM_MAX_PAGE_SIZE),
    ),
  );
}
