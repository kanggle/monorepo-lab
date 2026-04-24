import type { CouponSummary } from '@repo/types';

export function formatDiscountValue(coupon: Pick<CouponSummary, 'discountType' | 'discountValue'>): string {
  if (coupon.discountType === 'FIXED') {
    return `${coupon.discountValue.toLocaleString()}원 할인`;
  }
  return `${coupon.discountValue}% 할인`;
}

export function formatMaxDiscount(coupon: Pick<CouponSummary, 'discountType' | 'maxDiscountAmount'>): string | null {
  if (coupon.discountType === 'PERCENTAGE' && coupon.maxDiscountAmount > 0) {
    return `최대 ${coupon.maxDiscountAmount.toLocaleString()}원`;
  }
  return null;
}
