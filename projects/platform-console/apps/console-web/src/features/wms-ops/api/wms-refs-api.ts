import {
  RefPageSchema,
  type RefPage,
  ProjectionStatusSchema,
  type ProjectionStatus,
} from './types';
import { callWmsAdmin, pageParams, type WmsResult } from './wms-client';

// ---------------------------------------------------------------------------
// 1.7 master refs — GET /dashboard/refs/{type}
// ---------------------------------------------------------------------------

export function listRefs(
  type: string,
  params: { page?: number; size?: number } = {},
): Promise<WmsResult<RefPage>> {
  const qs = new URLSearchParams();
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
