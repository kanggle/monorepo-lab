import {
  CostCenterListResponseSchema,
  type CostCenterListResponse,
  CostCenterDetailResponseSchema,
  type CostCenter,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateCostCenterInput,
  type UpdateCostCenterInput,
} from '../types';
import { callErp } from '../erp-client';
import { listQs, detailQs, compact } from './masters-qs';

/**
 * erp masters — cost-centers api (TASK-PC-FE-108 split). List + detail reads +
 * create/update/retire mutations (TASK-PC-FE-048). Behavior-preserving;
 * re-exported through the `erp-masters-api` barrel.
 */

// ---------------------------------------------------------------------------
// cost-centers — list + detail
//   GET /api/erp/masterdata/cost-centers
//   GET /api/erp/masterdata/cost-centers/{id}
// ---------------------------------------------------------------------------

export async function listCostCenters(
  params: ErpListQueryParams = {},
): Promise<CostCenterListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers?${listQs(params)}`,
      logPath: '/api/erp/masterdata/cost-centers',
    },
    (json) => CostCenterListResponseSchema.parse(json),
  );
}

export async function getCostCenterById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<CostCenter> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/cost-centers/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return CostCenterDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// cost-centers — WRITE (TASK-PC-FE-048).
// ---------------------------------------------------------------------------

function parseCostCenterData(json: unknown): CostCenter {
  const env = (json ?? {}) as { data?: unknown };
  return CostCenterDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

export async function createCostCenter(
  input: CreateCostCenterInput,
  idempotencyKey: string,
): Promise<CostCenter> {
  return callErp(
    {
      path: '/api/erp/masterdata/cost-centers',
      logPath: '/api/erp/masterdata/cost-centers',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseCostCenterData,
  );
}
export async function updateCostCenter(
  id: string,
  input: UpdateCostCenterInput,
  idempotencyKey: string,
): Promise<CostCenter> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/cost-centers/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseCostCenterData,
  );
}
export async function retireCostCenter(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<CostCenter> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/cost-centers/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseCostCenterData,
  );
}
