import type { CouponSummary, ApplyCouponResponse } from '@repo/types';

/**
 * 쿠폰의 할인 금액을 클라이언트에서 계산한다.
 * 실제 쿠폰 적용(apply API)은 주문 생성 시 서버에서 수행된다.
 *
 * - FIXED: discountValue 그대로 적용
 * - PERCENTAGE: orderAmount * discountValue / 100, maxDiscountAmount 이하로 제한
 */
export function calculateDiscount(
  coupon: CouponSummary,
  orderAmount: number,
): ApplyCouponResponse {
  let discountAmount: number;

  if (coupon.discountType === 'FIXED') {
    discountAmount = coupon.discountValue;
  } else {
    const rawDiscount = Math.floor(orderAmount * coupon.discountValue / 100);
    discountAmount =
      coupon.maxDiscountAmount > 0
        ? Math.min(rawDiscount, coupon.maxDiscountAmount)
        : rawDiscount;
  }

  // 할인이 주문 금액을 초과할 수 없다
  discountAmount = Math.min(discountAmount, orderAmount);

  return {
    couponId: coupon.couponId,
    discountAmount,
    finalAmount: orderAmount - discountAmount,
  };
}

/**
 * 쿠폰이 만료되었는지 확인한다.
 */
export function isCouponExpired(coupon: CouponSummary): boolean {
  return new Date(coupon.expiresAt).getTime() <= Date.now();
}
