import {
  SnapshotResponseSchema,
  type SnapshotResponse,
  SkuBreakdownSchema,
  type SkuBreakdown,
  StalenessResponseSchema,
  type StalenessResponse,
  NodesResponseSchema,
  type NodesResponse,
  type SnapshotQueryParams,
  type ScmResult,
} from './types';
import { callScm, pageParams, readCacheHeader } from './scm-client';

/**
 * scm inventory-visibility read endpoints (TASK-PC-FE-145 split of the
 * former single `scm-api.ts`). STRICTLY READ-ONLY. Every view carries the
 * REQUIRED S5 `meta.warning` (surfaced, never stripped — enforced in
 * `types.ts`); the per-SKU read surfaces the `X-Cache` freshness header
 * (§ 2.4.6 freshness honesty). The credential / resilience / error-envelope
 * narrative lives in the shared `scm-client.ts` core (`callScm`).
 * 0 behavior / contract / log-event change.
 */

// ---------------------------------------------------------------------------
// inventory-visibility — snapshot — GET /api/v1/inventory-visibility/snapshot
//   Carries the REQUIRED S5 meta.warning (surfaced, never stripped).
// ---------------------------------------------------------------------------

export async function getSnapshot(
  params: SnapshotQueryParams = {},
): Promise<ScmResult<SnapshotResponse>> {
  const qs = new URLSearchParams();
  if (params.nodeId) qs.set('nodeId', params.nodeId);
  if (!params.nodeId) pageParams(qs, params.page, params.size);
  const { raw } = await callScm(
    { path: `/api/v1/inventory-visibility/snapshot?${qs.toString()}` },
    (json) => SnapshotResponseSchema.parse(json),
  );
  return { data: raw, cache: null };
}

// ---------------------------------------------------------------------------
// inventory-visibility — per-SKU breakdown
//   GET /api/v1/inventory-visibility/sku/{sku}  (Redis-cached, X-Cache)
// ---------------------------------------------------------------------------

export async function getSkuBreakdown(
  sku: string,
): Promise<ScmResult<SkuBreakdown>> {
  const { raw, res } = await callScm(
    {
      path: `/api/v1/inventory-visibility/sku/${encodeURIComponent(sku)}`,
    },
    (json) => SkuBreakdownSchema.parse(json),
  );
  // Surface X-Cache honestly (§ 2.4.6 freshness honesty).
  return { data: raw, cache: readCacheHeader(res) };
}

// ---------------------------------------------------------------------------
// inventory-visibility — node staleness
//   GET /api/v1/inventory-visibility/staleness
// ---------------------------------------------------------------------------

export async function getStaleness(): Promise<
  ScmResult<StalenessResponse>
> {
  const { raw } = await callScm(
    { path: `/api/v1/inventory-visibility/staleness` },
    (json) => StalenessResponseSchema.parse(json),
  );
  return { data: raw, cache: null };
}

// ---------------------------------------------------------------------------
// inventory-visibility — node list
//   GET /api/v1/inventory-visibility/nodes
// ---------------------------------------------------------------------------

export async function getNodes(): Promise<ScmResult<NodesResponse>> {
  const { raw } = await callScm(
    { path: `/api/v1/inventory-visibility/nodes` },
    (json) => NodesResponseSchema.parse(json),
  );
  return { data: raw, cache: null };
}
