import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';

// ===========================================================================
// PROMOTIONS — read shapes, write bodies, list params
// (TASK-PC-FE-086, ADR-031 Phase 3b)
// Producer: promotion-service `/api/promotions` (operator plane — TASK-BE-368)
// Error envelope: flat { code, message, timestamp } (same as products)
// ===========================================================================

/** PromotionStatus producer enum values. Kept tolerant as a plain string so
 *  a future status renders rather than throwing. */
export const PROMOTION_STATUS_VALUES = ['ACTIVE', 'SCHEDULED', 'ENDED'] as const;
export type PromotionStatus = (typeof PROMOTION_STATUS_VALUES)[number];

/**
 * Promotion status → shared semantic {@link StatusTone} (rendered via the shared
 * `<StatusBadge>` — TASK-PC-FE-158/159). ACTIVE is live (success); SCHEDULED is
 * upcoming (progress); ENDED is finished (neutral). An unknown/future status →
 * `neutral` (TOLERANCE).
 */
const PROMOTION_STATUS_TONE: Record<PromotionStatus, StatusTone> = {
  ACTIVE: 'success',
  SCHEDULED: 'progress',
  ENDED: 'neutral',
};

export function promotionStatusTone(status: string): StatusTone {
  return PROMOTION_STATUS_TONE[status as PromotionStatus] ?? 'neutral';
}

/** discountType producer enum values. */
export const DISCOUNT_TYPE_VALUES = ['FIXED', 'PERCENTAGE'] as const;
export type DiscountType = (typeof DISCOUNT_TYPE_VALUES)[number];

/** 1. list — PromotionSummary row. */
export const PromotionSummarySchema = z
  .object({
    promotionId: z.string(),
    name: z.string(),
    discountType: z.string(),
    discountValue: z.number().int(),
    issuedCount: z.number().int().nonnegative(),
    maxIssuanceCount: z.number().int().positive(),
    startDate: z.string(),
    endDate: z.string(),
    status: z.string(),
  })
  .passthrough();
export type PromotionSummary = z.infer<typeof PromotionSummarySchema>;

/** 1. list — PromotionListResponse envelope ({content, page, size, totalElements}). */
export const PromotionListSchema = z
  .object({
    content: z.array(PromotionSummarySchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
  })
  .passthrough();
export type PromotionList = z.infer<typeof PromotionListSchema>;

/** 2. detail — PromotionDetailResponse (adds description, maxDiscountAmount, createdAt, updatedAt). */
export const PromotionDetailSchema = z
  .object({
    promotionId: z.string(),
    name: z.string(),
    description: z.string().nullable().optional(),
    discountType: z.string(),
    discountValue: z.number().int(),
    maxDiscountAmount: z.number().int().nonnegative().optional().nullable(),
    issuedCount: z.number().int().nonnegative(),
    maxIssuanceCount: z.number().int().positive(),
    startDate: z.string(),
    endDate: z.string(),
    status: z.string(),
    createdAt: z.string().optional().nullable(),
    updatedAt: z.string().optional().nullable(),
  })
  .passthrough();
export type PromotionDetail = z.infer<typeof PromotionDetailSchema>;

/** create / update response — { promotionId }. */
export const PromotionMutationResponseSchema = z
  .object({ promotionId: z.string() })
  .passthrough();
export type PromotionMutationResponse = z.infer<typeof PromotionMutationResponseSchema>;

/** POST /api/promotions/{id}/coupons/issue response — { issuedCount }. */
export const IssueCouponResponseSchema = z
  .object({ issuedCount: z.number().int().nonnegative() })
  .passthrough();
export type IssueCouponResponse = z.infer<typeof IssueCouponResponseSchema>;

// ===========================================================================
// PROMOTION WRITE request bodies — matched to the producer request DTOs verbatim
// ===========================================================================

/** CreatePromotionRequest / UpdatePromotionRequest (PUT full replace — same body). */
export const CreatePromotionBodySchema = z.object({
  name: z.string().min(1),
  description: z.string().optional(),
  discountType: z.enum(DISCOUNT_TYPE_VALUES),
  discountValue: z.number().int().positive(),
  maxDiscountAmount: z.number().int().nonnegative(),
  maxIssuanceCount: z.number().int().positive(),
  startDate: z.string(),
  endDate: z.string(),
});
export type CreatePromotionBody = z.infer<typeof CreatePromotionBodySchema>;

/** UpdatePromotionRequest — PUT full replace (same shape as create). */
export const UpdatePromotionBodySchema = CreatePromotionBodySchema;
export type UpdatePromotionBody = z.infer<typeof UpdatePromotionBodySchema>;

/** POST /api/promotions/{id}/coupons/issue request body. */
export const IssueCouponBodySchema = z.object({
  userIds: z.array(z.string().min(1)).min(1),
});
export type IssueCouponBody = z.infer<typeof IssueCouponBodySchema>;

/** GET /api/promotions/summary — period-based count (TASK-PC-FE-164).
 *  Response: { today, week, month, total } all non-negative integers. */
export const PromotionAreaSummarySchema = z
  .object({
    today: z.number().int().nonnegative(),
    week: z.number().int().nonnegative(),
    month: z.number().int().nonnegative(),
    total: z.number().int().nonnegative(),
  })
  .passthrough();
export type PromotionAreaSummary = z.infer<typeof PromotionAreaSummarySchema>;

export const PROMOTION_DEFAULT_PAGE_SIZE = 20;
export const PROMOTION_MAX_PAGE_SIZE = 100;

export interface PromotionListParams {
  status?: string;
  page?: number;
  size?: number;
}
