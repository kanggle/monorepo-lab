// Promotion domain types based on specs/contracts/http/promotion-api.md

export type PromotionStatus = 'SCHEDULED' | 'ACTIVE' | 'ENDED';

export type DiscountType = 'FIXED' | 'PERCENTAGE';

export interface PromotionSummary {
  promotionId: string;
  name: string;
  discountType: DiscountType;
  discountValue: number;
  maxIssuanceCount: number;
  issuedCount: number;
  startDate: string;
  endDate: string;
  status: PromotionStatus;
}

export interface PromotionDetail {
  promotionId: string;
  name: string;
  description: string;
  discountType: DiscountType;
  discountValue: number;
  maxDiscountAmount: number;
  maxIssuanceCount: number;
  issuedCount: number;
  startDate: string;
  endDate: string;
  status: PromotionStatus;
  createdAt: string;
  updatedAt: string;
}

export interface PromotionListParams {
  page?: number;
  size?: number;
  status?: PromotionStatus;
}

export interface CreatePromotionRequest {
  name: string;
  description: string;
  discountType: DiscountType;
  discountValue: number;
  maxDiscountAmount: number;
  maxIssuanceCount: number;
  startDate: string;
  endDate: string;
}

export interface CreatePromotionResponse {
  promotionId: string;
}

export interface UpdatePromotionRequest {
  name: string;
  description: string;
  discountType: DiscountType;
  discountValue: number;
  maxDiscountAmount: number;
  maxIssuanceCount: number;
  startDate: string;
  endDate: string;
}

export interface UpdatePromotionResponse {
  promotionId: string;
}

export interface IssueCouponsRequest {
  userIds: string[];
}

export interface IssueCouponsResponse {
  issuedCount: number;
}

// Coupon types for user-facing coupon APIs

export type CouponStatus = 'ISSUED' | 'USED' | 'EXPIRED';

export interface CouponSummary {
  couponId: string;
  promotionId: string;
  promotionName: string;
  discountType: DiscountType;
  discountValue: number;
  maxDiscountAmount: number;
  status: CouponStatus;
  issuedAt: string;
  expiresAt: string;
}

export interface CouponListParams {
  page?: number;
  size?: number;
  status?: CouponStatus;
}

export interface ApplyCouponRequest {
  orderId: string;
  orderAmount: number;
}

export interface ApplyCouponResponse {
  couponId: string;
  discountAmount: number;
  finalAmount: number;
}
