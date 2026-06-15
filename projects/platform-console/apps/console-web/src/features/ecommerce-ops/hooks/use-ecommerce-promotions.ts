'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  PromotionListSchema,
  type PromotionList,
  PromotionDetailSchema,
  type PromotionDetail,
  type PromotionListParams,
  type CreatePromotionBody,
  type UpdatePromotionBody,
  type IssueCouponBody,
  PROMOTION_DEFAULT_PAGE_SIZE,
  PROMOTION_MAX_PAGE_SIZE,
} from '../api/types';

/**
 * Client-side ecommerce-ops promotion hooks (TASK-PC-FE-086 — ADR-031 Phase 3b).
 * Every call goes to the same-origin `/api/ecommerce/promotions/**` proxy (the
 * typed API client's single backend entry point); the proxy attaches the HttpOnly
 * **domain-facing IAM OIDC token** server-side — the browser never reads a token
 * or calls the ecommerce gateway directly (contract § 2.3 / § 2.4.10).
 *
 * Mutation discipline: NO `Idempotency-Key` (the producer defines none) —
 * confirm-gate + producer state guards (422) are the double-submit defence.
 * Update uses PUT (full replace), NOT PATCH.
 */

const PROMOTIONS_KEY = 'ecommerce-promotions';

const clampSize = (size?: number): number =>
  clampPageSize(size, PROMOTION_DEFAULT_PAGE_SIZE, PROMOTION_MAX_PAGE_SIZE);

// --- list -----------------------------------------------------------------

export function promotionsKey(params: PromotionListParams) {
  return [
    PROMOTIONS_KEY,
    'list',
    params.status ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildPromotionsQs(params: PromotionListParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchPromotions(
  params: PromotionListParams,
): Promise<PromotionList> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/promotions?${buildPromotionsQs(params)}`,
  );
  return PromotionListSchema.parse(raw);
}

export function usePromotions(
  params: PromotionListParams,
  initial?: PromotionList,
) {
  const seeded =
    initial !== undefined && (params.page ?? 0) === 0 && !params.status;
  return useQuery({
    queryKey: promotionsKey(params),
    queryFn: () => fetchPromotions(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
}

// --- detail ---------------------------------------------------------------

async function fetchPromotion(id: string): Promise<PromotionDetail> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/promotions/${encodeURIComponent(id)}`,
  );
  return PromotionDetailSchema.parse(raw);
}

export function usePromotion(id: string | null, initial?: PromotionDetail) {
  return useQuery({
    queryKey: [PROMOTIONS_KEY, 'detail', id] as const,
    queryFn: () => fetchPromotion(id as string),
    enabled: id !== null,
    initialData: initial,
    staleTime: 0,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
}

// --- mutations ------------------------------------------------------------

/** Invalidate the list + (optionally) one promotion's detail after a mutation. */
function invalidate(
  qc: ReturnType<typeof useQueryClient>,
  promotionId?: string,
) {
  qc.invalidateQueries({ queryKey: [PROMOTIONS_KEY, 'list'] });
  if (promotionId) {
    qc.invalidateQueries({
      queryKey: [PROMOTIONS_KEY, 'detail', promotionId],
    });
  }
}

export function useCreatePromotion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePromotionBody) =>
      apiClient.post<{ promotionId: string }>(
        '/api/ecommerce/promotions',
        body,
      ),
    onSuccess: () => invalidate(qc),
  });
}

export function useUpdatePromotion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      body,
    }: {
      id: string;
      body: UpdatePromotionBody;
    }) =>
      // PUT (full replace) — NOT PATCH
      apiClient.put<{ promotionId: string }>(
        `/api/ecommerce/promotions/${encodeURIComponent(id)}`,
        body,
      ),
    onSuccess: (_d, { id }) => invalidate(qc, id),
  });
}

export function useDeletePromotion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.delete<void>(
        `/api/ecommerce/promotions/${encodeURIComponent(id)}`,
      ),
    onSuccess: (_d, id) => invalidate(qc, id),
  });
}

export function useIssueCoupons() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: IssueCouponBody }) =>
      apiClient.post<{ issuedCount: number }>(
        `/api/ecommerce/promotions/${encodeURIComponent(id)}/coupons/issue`,
        body,
      ),
    onSuccess: (_d, { id }) => invalidate(qc, id),
  });
}
