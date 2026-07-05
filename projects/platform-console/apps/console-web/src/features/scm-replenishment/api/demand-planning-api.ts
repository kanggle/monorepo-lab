import { clampPageSize } from '@/shared/lib/pagination';
import { ScmReplenishmentUnavailableError } from '@/shared/api/errors';
import {
  callScmGateway,
  type ScmGatewayProfile,
} from '@/shared/api/scm-gateway';
import {
  SuggestionPageSchema,
  type SuggestionPage,
  SuggestionSchema,
  type Suggestion,
  ApproveResultSchema,
  type ApproveResult,
  DismissResultSchema,
  type DismissResult,
  type SuggestionQueryParams,
  REPL_DEFAULT_PAGE_SIZE,
  REPL_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side scm `demand-planning-service` replenishment operator client
 * (TASK-PC-FE-077 — the FIRST scm operator-MUTATION surface, the human gate
 * of the ADR-MONO-027 wms→scm replenishment loop). Read (suggestions
 * list/detail) + two operator ACTIONS (approve / dismiss).
 *
 * Server-only by construction (same posture as `scm-ops/api/scm-api.ts`):
 * imported exclusively from server components and the `runtime = 'nodejs'`
 * route handlers; `getServerEnv()` throws outside the server runtime. The
 * token + any data never reach client JS — client components call the
 * same-origin `/api/scm/demand-planning/**` proxy routes, which attach the
 * HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of the § 2.4.5 / § 2.4.6 rule (NOT
 *    re-derived) ──────────────────────────────────────────────────────────
 *
 * scm demand-planning REUSES the § 2.4.6 scm credential rule verbatim: the
 * scm gateway validates a IAM RS256 JWT (ADR-001) against IAM JWKS,
 * `tenant_id ∈ { scm, * }` enforced producer-side from the JWT claim. scm has
 * NO token-exchange. This client therefore uses `getDomainFacingToken()` (the
 * assumed tenant-scoped IAM OIDC token when the operator switched, else the
 * base access token — net-zero; ADR-MONO-020 D4) and NEVER `getOperatorToken()`
 * — exactly like `scm-api.ts`, and the EXACT INVERSE of the IAM
 * `features/{accounts,audit,operators,dashboards}` clients. The #569
 * trust-boundary invariant is GAP-domain-scoped and does NOT generalise to scm.
 * A test pins this (the `getOperatorToken` path MUST be absent for scm).
 *
 * Tenant invariant (§ 2.4.6.1 / reuse of § 2.4.5/§ 2.4.6): scm resolves the
 * tenant from the JWT `tenant_id` claim (`∈ {scm,*}`) — NOT an `X-Tenant-Id`
 * header. The console does NOT send `X-Tenant-Id`; scm rejects cross-tenant
 * producer-side (`403 TENANT_FORBIDDEN`).
 *
 * ── MUTATION DISCIPLINE (the net-new part — § 2.4.6.1; follows what
 *    demand-planning-api.md ACTUALLY defines, NOT a cargo-cult of IAM
 *    § 2.4.1 mutation scaffolding) ──────────────────────────────────────────
 *
 * approve / dismiss are `POST` with an OPTIONAL JSON body (`{ note }` /
 * `{ reason }`). The producer is **server-side idempotent by suggestion
 * state** (re-approve returns the existing `poId`; re-dismiss is a no-op) — so
 * a client `Idempotency-Key` header is NOT required and is NOT attached, and
 * the reason rides in the BODY, NOT an `X-Operator-Reason` header (the
 * producer defines neither header — inventing them is a defect; a test asserts
 * BOTH absent). approve materialises a DRAFT PO only (ADR-MONO-027 D5); this
 * client NEVER issues a procurement submit/confirm/cancel.
 *
 * Error envelope (§ 2.4.6.1 / § 2.5): scm uses the FLAT shape
 * `{ code, message, details?, timestamp }` — DISTINCT from wms's NESTED
 * `{ error: { code … } }`. `parseScmError()` reads the scm flat shape (and
 * tolerates an absent/non-JSON body without crashing).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * `401` → `ApiError` (forced WHOLE-SESSION re-login); `403` → `ApiError`
 * (inline "not scoped"); `404`/`400`/`422`/`409` → `ApiError` (inline
 * actionable — the idempotent approve path is handled as success by the caller,
 * a hard `409 SUGGESTION_ALREADY_MATERIALIZED` as a benign notice);
 * `429 RATE_LIMIT_EXCEEDED` → `ScmRateLimitedError` (ONE bounded backoff
 * honouring `Retry-After`, reused from the § 2.4.6 read surface — NO storm);
 * `503`/timeout/network → `ScmReplenishmentUnavailableError` (ONLY this section
 * degrades).
 *
 * Logging: structured, server-side only; the IAM access token and any scm data
 * (incl. the note/reason) are NEVER logged (redacted).
 */

interface CallOptions {
  method: 'GET' | 'POST';
  /** Path relative to `${SCM_GATEWAY_BASE_URL}` (e.g.
   *  `/api/v1/demand-planning/suggestions`). */
  path: string;
  /** Optional mutation body (`{ note }` / `{ reason }`); `undefined` for
   *  reads AND for a no-note/no-reason action (the body is OPTIONAL). */
  body?: unknown;
}

/**
 * scm-replenishment profile for the shared {@link callScmGateway} core: the 보충
 * surface (demand-planning suggestions read + approve/dismiss) degrades via
 * {@link ScmReplenishmentUnavailableError} and logs `scm_replenishment_*`
 * events. Reads AND the two actions share the domain-facing credential; the
 * OPTIONAL note/reason rides in the body (NO `Idempotency-Key`, NO
 * `X-Operator-Reason` — the shared core adds neither).
 */
const REPL_PROFILE: ScmGatewayProfile = {
  logPrefix: 'scm_replenishment',
  requestFailedLabel: 'scm demand-planning request failed',
  makeUnavailable: (reason, code, message) =>
    new ScmReplenishmentUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof ScmReplenishmentUnavailableError,
  messages: {
    degraded: 'scm demand-planning unavailable',
    timeout: 'scm demand-planning call timed out',
    network: 'scm demand-planning call failed',
  },
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callScmGateway} core with the {@link REPL_PROFILE}. Passes the method +
 * optional body through (a body ⇒ `Content-Type: application/json`); returns the
 * parsed body (`res` is not needed here).
 */
async function callDemandPlanning<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const { raw } = await callScmGateway(
    { path: opts.path, method: opts.method, body: opts.body },
    parse,
    REPL_PROFILE,
  );
  return raw;
}

// ---------------------------------------------------------------------------
// pagination helper
// ---------------------------------------------------------------------------

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      clampPageSize(size, REPL_DEFAULT_PAGE_SIZE, REPL_MAX_PAGE_SIZE),
    ),
  );
}

// ===========================================================================
// READS (operator read — no mutation artifacts)
// ===========================================================================

/** GET /api/v1/demand-planning/suggestions — list, filter by status/skuCode,
 *  paginated. Envelope = { data: [...], meta: { page, size, totalElements,
 *  totalPages } } → normalised to a `{ content, page, size, totalElements,
 *  totalPages }` view-model. */
export async function listSuggestions(
  params: SuggestionQueryParams = {},
): Promise<SuggestionPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.skuCode) qs.set('skuCode', params.skuCode);
  pageParams(qs, params.page, params.size);
  return callDemandPlanning(
    {
      method: 'GET',
      path: `/api/v1/demand-planning/suggestions?${qs.toString()}`,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown; meta?: unknown };
      const content = Array.isArray(env.data) ? env.data : [];
      const meta = (env.meta ?? {}) as {
        page?: number;
        size?: number;
        totalElements?: number;
        totalPages?: number;
      };
      return SuggestionPageSchema.parse({
        content,
        page: typeof meta.page === 'number' ? meta.page : 0,
        size:
          typeof meta.size === 'number' && meta.size > 0
            ? meta.size
            : REPL_DEFAULT_PAGE_SIZE,
        totalElements:
          typeof meta.totalElements === 'number'
            ? meta.totalElements
            : content.length,
        totalPages:
          typeof meta.totalPages === 'number' ? meta.totalPages : undefined,
      });
    },
  );
}

/** GET /api/v1/demand-planning/suggestions/{id} — single suggestion. */
export async function getSuggestion(id: string): Promise<Suggestion> {
  return callDemandPlanning(
    {
      method: 'GET',
      path: `/api/v1/demand-planning/suggestions/${encodeURIComponent(id)}`,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return SuggestionSchema.parse(env.data ?? json);
    },
  );
}

// ===========================================================================
// OPERATOR ACTIONS (POST, optional body, server-side idempotent by state)
//   NO Idempotency-Key, NO X-Operator-Reason — the reason rides in the body.
// ===========================================================================

/** POST /api/v1/demand-planning/suggestions/{id}/approve — resolves
 *  sku_supplier_map → procurement creates a DRAFT PO → suggestion
 *  `MATERIALIZED` with `materializedPoId`. Idempotent: re-approving (or an
 *  already-`MATERIALIZED` suggestion) returns the existing `poId` — no
 *  duplicate PO. `note` is OPTIONAL and rides in the BODY (no header).
 *  The materialised PO is DRAFT ONLY — this client NEVER submits it. */
export async function approveSuggestion(
  id: string,
  note?: string,
): Promise<ApproveResult> {
  const trimmed = note?.trim();
  return callDemandPlanning(
    {
      method: 'POST',
      path: `/api/v1/demand-planning/suggestions/${encodeURIComponent(id)}/approve`,
      // OPTIONAL body — omitted entirely when there is no note (the producer
      // accepts an empty/absent body). NO Idempotency-Key, NO X-Operator-Reason.
      body: trimmed ? { note: trimmed } : undefined,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return ApproveResultSchema.parse(env.data ?? json);
    },
  );
}

/** POST /api/v1/demand-planning/suggestions/{id}/dismiss — `* → DISMISSED`,
 *  releases the open-suggestion guard. Idempotent (re-dismiss = no-op).
 *  `reason` is OPTIONAL and rides in the BODY (no header). */
export async function dismissSuggestion(
  id: string,
  reason?: string,
): Promise<DismissResult> {
  const trimmed = reason?.trim();
  return callDemandPlanning(
    {
      method: 'POST',
      path: `/api/v1/demand-planning/suggestions/${encodeURIComponent(id)}/dismiss`,
      body: trimmed ? { reason: trimmed } : undefined,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return DismissResultSchema.parse(env.data ?? json);
    },
  );
}
