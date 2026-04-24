import type { ApiClient } from '../client';
import type {
  PaginatedResponse,
  PromotionSummary,
  PromotionDetail,
  PromotionListParams,
  CreatePromotionRequest,
  CreatePromotionResponse,
  UpdatePromotionRequest,
  UpdatePromotionResponse,
  IssueCouponsRequest,
  IssueCouponsResponse,
} from '@repo/types';

export function createAdminPromotionApi(client: ApiClient) {
  return {
    getPromotions: (params?: PromotionListParams) =>
      client.get<PaginatedResponse<PromotionSummary>>('/api/promotions', { params }),

    getPromotion: (promotionId: string) =>
      client.get<PromotionDetail>(`/api/promotions/${promotionId}`),

    createPromotion: (data: CreatePromotionRequest) =>
      client.post<CreatePromotionResponse>('/api/promotions', data),

    updatePromotion: (promotionId: string, data: UpdatePromotionRequest) =>
      client.put<UpdatePromotionResponse>(`/api/promotions/${promotionId}`, data),

    deletePromotion: (promotionId: string) =>
      client.delete<void>(`/api/promotions/${promotionId}`),

    issueCoupons: (promotionId: string, data: IssueCouponsRequest) =>
      client.post<IssueCouponsResponse>(`/api/promotions/${promotionId}/coupons/issue`, data),
  };
}
