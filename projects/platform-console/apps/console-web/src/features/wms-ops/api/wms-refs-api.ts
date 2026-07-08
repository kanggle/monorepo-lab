import {
  RefPageSchema,
  type RefPage,
  type RefQueryParams,
  ProjectionStatusSchema,
  type ProjectionStatus,
} from './types';
import { callWmsAdmin, pageParams, type WmsResult } from './wms-client';

// ---------------------------------------------------------------------------
// 1.7 master refs — GET /dashboard/refs/{type}
// ---------------------------------------------------------------------------

/**
 * TASK-PC-FE-223 — surfaces the previously-zero-consumer `listRefs` on the
 * `/wms/master` screen. `q`/`status` are OPTIONAL filters (see
 * `RefQueryParams` doc — § 1.7 does not enumerate per-type param names; the
 * console adopts the same `q`/`status` convention as `GET /users` § 2.2).
 */
export function listRefs(
  type: string,
  params: RefQueryParams = {},
): Promise<WmsResult<RefPage>> {
  const qs = new URLSearchParams();
  if (params.q) qs.set('q', params.q);
  if (params.status) qs.set('status', params.status);
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
