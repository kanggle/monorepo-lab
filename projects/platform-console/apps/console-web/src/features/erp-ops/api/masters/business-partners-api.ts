import {
  BusinessPartnerListResponseSchema,
  type BusinessPartnerListResponse,
  BusinessPartnerDetailResponseSchema,
  type BusinessPartner,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateBusinessPartnerInput,
  type UpdateBusinessPartnerInput,
} from '../types';
import { callErp } from '../erp-client';
import { listQs, detailQs, compact } from './masters-qs';

/**
 * erp masters — business-partners api (TASK-PC-FE-108 split; confidential
 * paymentTerms — producer enforces F7-equivalent masking, console never logs).
 * List + detail reads + create/update/retire mutations (TASK-PC-FE-048).
 * Behavior-preserving; re-exported through the `erp-masters-api` barrel.
 */

// ---------------------------------------------------------------------------
// business-partners — list + detail (confidential paymentTerms;
//   producer enforces F7-equivalent masking — console never logs)
//   GET /api/erp/masterdata/business-partners
//   GET /api/erp/masterdata/business-partners/{id}
// ---------------------------------------------------------------------------

export async function listBusinessPartners(
  params: ErpListQueryParams = {},
): Promise<BusinessPartnerListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners?${listQs(params)}`,
      logPath: '/api/erp/masterdata/business-partners',
    },
    (json) => BusinessPartnerListResponseSchema.parse(json),
  );
}

export async function getBusinessPartnerById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<BusinessPartner> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/business-partners/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return BusinessPartnerDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// business-partners — WRITE (TASK-PC-FE-048).
// ---------------------------------------------------------------------------

function parseBusinessPartnerData(json: unknown): BusinessPartner {
  const env = (json ?? {}) as { data?: unknown };
  return BusinessPartnerDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

export async function createBusinessPartner(
  input: CreateBusinessPartnerInput,
  idempotencyKey: string,
): Promise<BusinessPartner> {
  return callErp(
    {
      path: '/api/erp/masterdata/business-partners',
      logPath: '/api/erp/masterdata/business-partners',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseBusinessPartnerData,
  );
}
export async function updateBusinessPartner(
  id: string,
  input: UpdateBusinessPartnerInput,
  idempotencyKey: string,
): Promise<BusinessPartner> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/business-partners/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseBusinessPartnerData,
  );
}
export async function retireBusinessPartner(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<BusinessPartner> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/business-partners/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseBusinessPartnerData,
  );
}
