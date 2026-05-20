import { NextResponse } from 'next/server';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the erp-ops same-origin proxy
 * routes (console-integration-contract § 2.4.8 / § 2.5). The
 * HttpOnly **GAP OIDC access token** is attached server-side in
 * `erp-api.ts` — NOT the GAP exchanged operator token (erp
 * requires the GAP OIDC token; the #569 invariant is
 * GAP-domain-scoped — the § 2.4.5 rule reused, NOT re-derived).
 * Mirrors the FE-009 finance `_proxy` shape for the flat envelope.
 *
 * STRICTLY READ-ONLY: these proxies expose ONLY GET routes — there
 * is NO mutation route (no erp write — `POST .../departments`,
 * `PATCH .../{id}`, `POST .../retire`, `POST .../move-parent`; no
 * Idempotency-Key, no X-Operator-Reason, no request body, no v2
 * approval-service / read-model-service / future admin-service
 * surface).
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION
 *     re-login; no partial authed state — NOT a per-section
 *     degrade).
 *   - 403 → 403 (token not erp-scoped / outside org subtree /
 *     external traffic at internal-only boundary → inline "not
 *     available / not scoped").
 *   - 404 → passthrough (inline actionable — typically
 *     `MASTERDATA_NOT_FOUND`, no crash).
 *   - 400 / 422 → passthrough (inline actionable, no crash).
 *   - **NO 429 handling** (§ 2.4.8 — identical to finance § 2.4.7):
 *     erp has no documented rate-limit response; a stray 429
 *     lands here as an `ApiError` → passthrough to the client
 *     (NOT a Retry-After branch, NOT a bounded backoff — a
 *     fabricated backoff would be cargo-culted from scm § 2.4.6,
 *     asserted absent by test).
 *   - 503 / timeout / network → 503 (ONLY the erp section
 *     degrades; the console shell + GAP / wms / scm / finance
 *     sections stay intact).
 *
 * No token / erp data is ever logged (confidential + audit-heavy).
 */
export function mapErpError(err: unknown, requestId: string): NextResponse {
  if (err instanceof ApiError && err.status === 401) {
    return NextResponse.json(
      { code: err.code || 'UNAUTHORIZED', message: 'session expired' },
      { status: 401 },
    );
  }
  if (err instanceof ApiError && err.status === 403) {
    return NextResponse.json(
      { code: err.code || 'TENANT_FORBIDDEN', message: 'not permitted' },
      { status: 403 },
    );
  }
  if (err instanceof ApiError) {
    // 400/422 VALIDATION_ERROR / 404 MASTERDATA_NOT_FOUND / any
    // unexpected status (incl. a stray 429 — erp has no documented
    // 429, no Retry-After branch; the 429 falls through here as a
    // passthrough, NOT into a fabricated backoff) → inline
    // actionable.
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof ErpUnavailableError) {
    logger.warn('erp_proxy_degraded', {
      requestId,
      reason: err.reason,
    });
    return NextResponse.json(
      { code: err.code, message: 'erp unavailable' },
      { status: 503 },
    );
  }
  logger.error('erp_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'SERVICE_UNAVAILABLE', message: 'erp unavailable' },
    { status: 503 },
  );
}

/** Builds an `ErpListQueryParams` from the incoming `Request`'s
 *  URL search-params. CORE E3: `asOf` is threaded through
 *  verbatim. Producer-defined list filters (`active`, `parentId`,
 *  `departmentId`, `costCenterId`, `partnerType`) are forwarded
 *  as a `filters` record. */
export function buildListParams(req: Request): {
  asOf?: string;
  active?: boolean;
  page?: number;
  size?: number;
  filters?: Record<string, string>;
} {
  const sp = new URL(req.url).searchParams;
  const out: ReturnType<typeof buildListParams> = {};
  // E3 — `asOf` thread-through.
  const asOf = sp.get('asOf');
  if (asOf) out.asOf = asOf;
  const active = sp.get('active');
  if (active !== null) out.active = active === 'true';
  if (sp.has('page')) out.page = Number(sp.get('page'));
  if (sp.has('size')) out.size = Number(sp.get('size'));
  // Producer per-master filters — forwarded verbatim, no special
  // mapping (the producer is the authority for the supported
  // filter set).
  const filterKeys = [
    'parentId',
    'departmentId',
    'costCenterId',
    'partnerType',
  ];
  const filters: Record<string, string> = {};
  for (const k of filterKeys) {
    const v = sp.get(k);
    if (v) filters[k] = v;
  }
  if (Object.keys(filters).length) out.filters = filters;
  return out;
}

/** Builds an `ErpDetailQueryParams` from the incoming `Request`'s
 *  URL search-params. CORE E3: `asOf` threaded through verbatim. */
export function buildDetailParams(req: Request): { asOf?: string } {
  const sp = new URL(req.url).searchParams;
  const asOf = sp.get('asOf');
  return asOf ? { asOf } : {};
}

export { newRequestId };
