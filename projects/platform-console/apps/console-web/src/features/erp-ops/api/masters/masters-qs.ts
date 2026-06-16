import {
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from '../types';

/**
 * Shared server-side query-string + body helpers for the erp masters api
 * modules (TASK-PC-FE-108 split of the former `erp-masters-api.ts` god-file).
 * Feature-internal — used by the per-entity masters api modules under
 * `api/masters/`, never widened to the public surface.
 *
 * NOTE: distinct from the CLIENT-side `buildListQs` / `buildDetailQs` in
 * `hooks/use-erp-shared.ts` (a different layer — those feed the typed
 * api-client; these feed the server-side `callErp`). The duplication is
 * intentional layer-separation (consolidating them is out of scope — it would
 * be a behavior-risk, not a structural split).
 */

// ---------------------------------------------------------------------------
// query-string helpers — `asOf` is the E3 first-class thread-through;
// `active` / `page` / `size` / per-master filters are passthroughs.
// ---------------------------------------------------------------------------

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(
        ERP_MAX_PAGE_SIZE,
        Math.max(1, size ?? ERP_DEFAULT_PAGE_SIZE),
      ),
    ),
  );
}

/**
 * Builds the query string for any erp list read. CORE E3 invariant:
 * when `asOf` is supplied it threads through to the producer
 * verbatim (the producer returns the state-at-that-instant; the
 * console NEVER substitutes current state). When `asOf` is omitted
 * the producer resolves to "today" (UTC) per `masterdata-api.md`.
 */
export function listQs(params: ErpListQueryParams): string {
  const qs = new URLSearchParams();
  // E3 thread-through — verbatim, no transformation. This is the
  // single point that pins the asOf-pass-through invariant for
  // every list call.
  if (params.asOf) qs.set('asOf', params.asOf);
  if (params.active !== undefined) qs.set('active', String(params.active));
  if (params.filters) {
    for (const [k, v] of Object.entries(params.filters)) {
      if (v !== undefined && v !== null && v !== '') qs.set(k, v);
    }
  }
  pageParams(qs, params.page, params.size);
  return qs.toString();
}

/**
 * Builds the query string for any erp detail read. Only `asOf` is
 * producer-defined; this is the same E3 thread-through invariant
 * as `listQs`.
 */
export function detailQs(params: ErpDetailQueryParams): string {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  const s = qs.toString();
  return s ? `?${s}` : '';
}

/** Drops undefined keys so optional fields are omitted from the wire body
 *  (a `PATCH` with `{ name: undefined }` would otherwise serialize nothing
 *  meaningful; the producer wants only the changed fields). */
export function compact<T extends Record<string, unknown>>(obj: T): Partial<T> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v !== undefined) out[k] = v;
  }
  return out as Partial<T>;
}
