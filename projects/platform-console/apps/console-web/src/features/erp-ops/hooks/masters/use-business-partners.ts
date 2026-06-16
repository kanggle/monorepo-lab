'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  BusinessPartnerListResponseSchema,
  type BusinessPartnerListResponse,
  BusinessPartnerSchema,
  type BusinessPartner,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateBusinessPartnerInput,
  type UpdateBusinessPartnerInput,
} from '../../api/types';
import {
  businessPartnerDetailKey,
  businessPartnersListKey,
} from '../../api/erp-keys';
import {
  clampSize,
  buildListQs,
  buildDetailQs,
  useThreadedAsOf,
  invalidateMaster,
} from '../use-erp-shared';

/**
 * erp-ops masters — business-partners hooks (TASK-PC-FE-107 split; confidential
 * paymentTerms — never logged). List + detail reads + create/update/retire
 * mutations (TASK-PC-FE-048). Behavior-preserving; re-exported via the barrel.
 */

// ---------------------------------------------------------------------------
// business-partners — list + detail (confidential paymentTerms; never logged)
// ---------------------------------------------------------------------------

async function fetchBusinessPartnersList(
  params: ErpListQueryParams,
): Promise<BusinessPartnerListResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/business-partners?${buildListQs(params)}`,
  );
  return BusinessPartnerListResponseSchema.parse(raw);
}

export function useBusinessPartners(
  paramsIn: ErpListQueryParams = {},
  initial?: BusinessPartnerListResponse,
) {
  const asOf = useThreadedAsOf(paramsIn.asOf);
  const params: ErpListQueryParams = { ...paramsIn, asOf };
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.filters &&
    params.active === undefined;
  return useQuery({
    queryKey: businessPartnersListKey(
      asOf,
      params.page ?? 0,
      clampSize(params.size),
      params.filters,
    ),
    queryFn: () => fetchBusinessPartnersList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

async function fetchBusinessPartnerDetail(
  id: string,
  params: ErpDetailQueryParams,
): Promise<BusinessPartner> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}${buildDetailQs(params)}`,
  );
  return BusinessPartnerSchema.parse(raw);
}

export function useBusinessPartner(
  id: string | null,
  asOfExplicit?: string | null,
) {
  const asOf = useThreadedAsOf(asOfExplicit);
  return useQuery({
    queryKey: businessPartnerDetailKey(id ?? '', asOf),
    queryFn: () => fetchBusinessPartnerDetail(id as string, { asOf }),
    enabled: Boolean(id && id.trim()),
    staleTime: 15_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// ---------------------------------------------------------------------------
// business-partners — WRITE mutations (TASK-PC-FE-048).
// ---------------------------------------------------------------------------

export function useCreateBusinessPartner() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      input: CreateBusinessPartnerInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        '/api/erp/masterdata/business-partners',
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return BusinessPartnerSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'business-partners'),
  });
}
export function useUpdateBusinessPartner() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      input: UpdateBusinessPartnerInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/business-partners/${encodeURIComponent(args.id)}`,
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return BusinessPartnerSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'business-partners'),
  });
}
export function useRetireBusinessPartner() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      reason: string;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/business-partners/${encodeURIComponent(args.id)}/retire`,
        { reason: args.reason, idempotencyKey: args.idempotencyKey },
      );
      return BusinessPartnerSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'business-partners'),
  });
}
