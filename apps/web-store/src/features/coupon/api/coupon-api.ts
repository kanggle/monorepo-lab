import { apiClient } from '@/shared/config/api';
import { createCouponApi } from '@repo/api-client';
import type {
  PaginatedResponse,
  CouponSummary,
  CouponListParams,
  ApplyCouponRequest,
  ApplyCouponResponse,
} from '@repo/types';

const couponApi = createCouponApi(apiClient);

export async function getMyCoupons(
  params?: CouponListParams,
): Promise<PaginatedResponse<CouponSummary>> {
  return couponApi.getMyCoupons(params);
}

export async function applyCoupon(
  couponId: string,
  data: ApplyCouponRequest,
): Promise<ApplyCouponResponse> {
  return couponApi.applyCoupon(couponId, data);
}
