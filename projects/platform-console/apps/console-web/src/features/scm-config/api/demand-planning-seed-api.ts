import { ApiError, ScmReplenishmentUnavailableError } from '@/shared/api/errors';
import {
  callScmGateway,
  type ScmGatewayProfile,
} from '@/shared/api/scm-gateway';
import {
  ReorderPolicySchema,
  type ReorderPolicy,
  type ReorderPolicyInput,
  SupplierMapSchema,
  type SupplierMap,
  type SupplierMapInput,
  type SeedLookup,
} from './types';

/**
 * Server-side scm `demand-planning-service` **seed/config** client
 * (TASK-PC-FE-080 — the per-SKU reorder-policy + sku-supplier-map inspect/upsert
 * surface; the operator config arm of the ADR-MONO-027 wms→scm replenishment
 * loop). It is the operational fix-path for FE-077's `SKU_SUPPLIER_UNMAPPED`
 * gap: the operator inspects + upserts the seed rows that drive FUTURE reorder
 * evaluation, then returns to 보충 and approves.
 *
 * Server-only by construction (same posture as
 * `scm-replenishment/api/demand-planning-api.ts`): imported exclusively from
 * server components and the `runtime = 'nodejs'` route handlers. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/scm/demand-planning/{policies,sku-supplier-map}/[skuCode]` proxy routes,
 * which attach the HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of the § 2.4.6 / § 2.4.6.1 rule (NOT
 *    re-derived; the EXACT primitive FE-077 uses) ──────────────────────────
 *
 * scm demand-planning REUSES the scm credential rule verbatim: the scm gateway
 * validates a IAM RS256 JWT against IAM JWKS, `tenant_id ∈ { scm, * }` enforced
 * producer-side from the JWT claim. scm has NO token-exchange. This client uses
 * `getDomainFacingToken()` and NEVER `getOperatorToken()` — exactly like
 * `demand-planning-api.ts`. A test pins this (the `getOperatorToken` path MUST
 * be ABSENT for scm, on GET AND PUT). The console sends NO `X-Tenant-Id` (scm
 * resolves the tenant from the JWT claim; cross-tenant → `403 TENANT_FORBIDDEN`
 * producer-side).
 *
 * ── MUTATION DISCIPLINE (the net-new part — § 2.4.6.2; follows what
 *    demand-planning-api.md ACTUALLY defines) ────────────────────────────────
 *
 * PUT is an idempotent **upsert** — the request body IS the FULL row. There is
 * NO `Idempotency-Key` header and NO `X-Operator-Reason` header (the producer
 * defines NEITHER; inventing them is a defect — a test asserts BOTH absent on
 * PUT). The config edit affects FUTURE evaluation only — this client issues NO
 * suggestion / PO / dispatch call (only policies + sku-supplier-map GET/PUT).
 *
 * ── 404-AS-EMPTY-STATE (the net-new resilience nuance) ────────────────────
 *
 * A GET 404 (`POLICY_NOT_FOUND` / `MAPPING_NOT_FOUND`) is NOT an error — it is
 * "not configured yet, create via PUT". `getPolicy` / `getSupplierMap` surface
 * it as a typed `{ found: false }` result, NEVER a thrown error (a test pins
 * that the 404 never propagates as an ApiError). A PUT 404 is not part of the
 * contract (PUT upserts) and is left to the generic inline-error path.
 *
 * Error envelope (§ 2.4.6.2 / § 2.5): the scm FLAT shape
 * `{ code, message, details?, timestamp }` — DISTINCT from wms's NESTED
 * `{ error: { code … } }`. `parseScmError()` reads the scm flat shape (and
 * tolerates an absent/non-JSON body without crashing).
 *
 * Resilience (§ 2.5): AbortController hard timeout; `401` → `ApiError` (forced
 * WHOLE-SESSION re-login); `403` → `ApiError` (inline "not scoped");
 * `422 VALIDATION_ERROR` → `ApiError` (inline field errors);
 * `429 RATE_LIMIT_EXCEEDED` → `ScmRateLimitedError` (ONE bounded backoff
 * honouring `Retry-After`, reused from the § 2.4.6 read surface — NO storm);
 * `503`/timeout/network → `ScmReplenishmentUnavailableError` (ONLY this section
 * degrades). Tokens / PII never logged.
 */

interface CallOptions {
  method: 'GET' | 'PUT';
  /** Path relative to `${SCM_GATEWAY_BASE_URL}`. */
  path: string;
  /** PUT upsert body (the FULL row); `undefined` for a GET. */
  body?: unknown;
  /** When true, a `404` resolves to a typed not-found rather than throwing
   *  (the 404-as-empty-state seed-lookup discipline). */
  notFoundIsEmpty?: boolean;
}

/** Sentinel parse return for the 404-as-empty-state path (distinct from a real
 *  parsed row). */
const NOT_FOUND = Symbol('seed-not-found');

/**
 * scm-config profile for the shared {@link callScmGateway} core: the 설정 surface
 * (per-SKU reorder-policy + sku-supplier-map GET/PUT) degrades via
 * {@link ScmReplenishmentUnavailableError} (same demand-planning-service section
 * as 보충) and logs `scm_config_*` events. `notFoundSentinel: NOT_FOUND` drives
 * the 404-as-empty-state: a seed-lookup GET with `notFoundIsEmpty` resolves a
 * `404` to {@link NOT_FOUND} instead of throwing.
 */
const CONFIG_PROFILE: ScmGatewayProfile = {
  logPrefix: 'scm_config',
  requestFailedLabel: 'scm demand-planning seed request failed',
  makeUnavailable: (reason, code, message) =>
    new ScmReplenishmentUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof ScmReplenishmentUnavailableError,
  messages: {
    degraded: 'scm demand-planning unavailable',
    timeout: 'scm demand-planning seed call timed out',
    network: 'scm demand-planning seed call failed',
  },
  notFoundSentinel: NOT_FOUND,
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callScmGateway} core with the {@link CONFIG_PROFILE}. Passes the method
 * + optional body through (a body ⇒ `Content-Type: application/json`) and, when
 * `notFoundIsEmpty`, the core short-circuits a `404` to the profile's
 * {@link NOT_FOUND} sentinel (a "not configured yet" state, NOT an error).
 */
async function callSeed<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T | typeof NOT_FOUND> {
  const { raw } = await callScmGateway<T | typeof NOT_FOUND>(
    {
      path: opts.path,
      method: opts.method,
      body: opts.body,
      notFoundIsEmpty: opts.notFoundIsEmpty,
    },
    parse,
    CONFIG_PROFILE,
  );
  return raw;
}

function unwrap<T>(value: T | typeof NOT_FOUND, kind: string): T {
  if (value === NOT_FOUND) {
    // A PUT (upsert) must never see a not-found sentinel (notFoundIsEmpty is
    // GET-only); this is a defensive guard, never reached on the happy path.
    throw new ApiError(404, kind, 'unexpected not-found on upsert');
  }
  return value;
}

// ===========================================================================
// reorder policy — GET (inspect; 404 = not configured yet) + PUT (upsert)
// ===========================================================================

/** GET /api/v1/demand-planning/policies/{skuCode}. 404 POLICY_NOT_FOUND is a
 *  typed `{ found: false }` (not configured yet), NOT a thrown error. */
export async function getPolicy(
  skuCode: string,
): Promise<SeedLookup<ReorderPolicy>> {
  const result = await callSeed(
    {
      method: 'GET',
      path: `/api/v1/demand-planning/policies/${encodeURIComponent(skuCode)}`,
      notFoundIsEmpty: true,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return ReorderPolicySchema.parse(env.data ?? json);
    },
  );
  if (result === NOT_FOUND) return { found: false };
  return { found: true, value: result };
}

/** PUT /api/v1/demand-planning/policies/{skuCode} — idempotent upsert. The
 *  body IS the FULL row. NO Idempotency-Key, NO X-Operator-Reason. */
export async function putPolicy(
  skuCode: string,
  body: ReorderPolicyInput,
): Promise<ReorderPolicy> {
  const result = await callSeed(
    {
      method: 'PUT',
      path: `/api/v1/demand-planning/policies/${encodeURIComponent(skuCode)}`,
      body,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return ReorderPolicySchema.parse(env.data ?? json);
    },
  );
  return unwrap(result, 'POLICY_NOT_FOUND');
}

// ===========================================================================
// sku→supplier mapping — GET (inspect; 404 = not configured yet) + PUT (upsert)
// ===========================================================================

/** GET /api/v1/demand-planning/sku-supplier-map/{skuCode}. 404 MAPPING_NOT_FOUND
 *  is a typed `{ found: false }` (not configured yet), NOT a thrown error. */
export async function getSupplierMap(
  skuCode: string,
): Promise<SeedLookup<SupplierMap>> {
  const result = await callSeed(
    {
      method: 'GET',
      path: `/api/v1/demand-planning/sku-supplier-map/${encodeURIComponent(skuCode)}`,
      notFoundIsEmpty: true,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return SupplierMapSchema.parse(env.data ?? json);
    },
  );
  if (result === NOT_FOUND) return { found: false };
  return { found: true, value: result };
}

/** PUT /api/v1/demand-planning/sku-supplier-map/{skuCode} — idempotent upsert.
 *  The body IS the FULL row. `supplierId` is free-text/uuid (no supplier master
 *  in v1). NO Idempotency-Key, NO X-Operator-Reason. */
export async function putSupplierMap(
  skuCode: string,
  body: SupplierMapInput,
): Promise<SupplierMap> {
  const result = await callSeed(
    {
      method: 'PUT',
      path: `/api/v1/demand-planning/sku-supplier-map/${encodeURIComponent(skuCode)}`,
      body,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return SupplierMapSchema.parse(env.data ?? json);
    },
  );
  return unwrap(result, 'MAPPING_NOT_FOUND');
}
