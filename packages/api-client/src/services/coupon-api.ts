import type { ApiClient } from '../client';
import type {
  PaginatedResponse,
  CouponSummary,
  CouponListParams,
  ApplyCouponRequest,
  ApplyCouponResponse,
} from '@repo/types';

export function createCouponApi(client: ApiClient) {
  return {
    getMyCoupons: (params?: CouponListParams) =>
      client.get<PaginatedResponse<CouponSummary>>('/api/coupons/me', { params }),

    applyCoupon: (couponId: string, data: ApplyCouponRequest) =>
      client.post<ApplyCouponResponse>(`/api/coupons/${couponId}/apply`, data),
  };
}
