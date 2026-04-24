import { apiClient } from '@/shared/config/api';
import { createAdminPromotionApi } from '@repo/api-client';
import {
  isMock,
  mockGetPromotions,
  mockGetPromotion,
} from '@/shared/lib/mock-data';
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

const adminPromotionApi = createAdminPromotionApi(apiClient);

export async function getPromotions(
  params?: PromotionListParams,
): Promise<PaginatedResponse<PromotionSummary>> {
  if (isMock()) return mockGetPromotions(params);
  return adminPromotionApi.getPromotions(params);
}

export async function getPromotion(promotionId: string): Promise<PromotionDetail> {
  if (isMock()) return mockGetPromotion(promotionId);
  return adminPromotionApi.getPromotion(promotionId);
}

export async function createPromotion(
  data: CreatePromotionRequest,
): Promise<CreatePromotionResponse> {
  return adminPromotionApi.createPromotion(data);
}

export async function updatePromotion(
  promotionId: string,
  data: UpdatePromotionRequest,
): Promise<UpdatePromotionResponse> {
  return adminPromotionApi.updatePromotion(promotionId, data);
}

export async function deletePromotion(promotionId: string): Promise<void> {
  return adminPromotionApi.deletePromotion(promotionId);
}

export async function issueCoupons(
  promotionId: string,
  data: IssueCouponsRequest,
): Promise<IssueCouponsResponse> {
  return adminPromotionApi.issueCoupons(promotionId, data);
}
