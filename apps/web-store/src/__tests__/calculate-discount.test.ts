import { describe, it, expect } from 'vitest';
import type { CouponSummary } from '@repo/types';
import { calculateDiscount, isCouponExpired } from '@/features/coupon/lib/calculate-discount';

function createCoupon(overrides: Partial<CouponSummary> = {}): CouponSummary {
  return {
    couponId: 'coupon-1',
    promotionId: 'promo-1',
    promotionName: '테스트 쿠폰',
    discountType: 'FIXED',
    discountValue: 5000,
    maxDiscountAmount: 0,
    status: 'ISSUED',
    issuedAt: '2026-01-01T00:00:00Z',
    expiresAt: '2027-12-31T23:59:59Z',
    ...overrides,
  };
}

describe('calculateDiscount', () => {
  it('FIXED 쿠폰의 할인 금액을 그대로 적용한다', () => {
    const coupon = createCoupon({ discountType: 'FIXED', discountValue: 5000 });

    const result = calculateDiscount(coupon, 30000);

    expect(result).toEqual({
      couponId: 'coupon-1',
      discountAmount: 5000,
      finalAmount: 25000,
    });
  });

  it('PERCENTAGE 쿠폰의 할인 금액을 계산한다', () => {
    const coupon = createCoupon({
      discountType: 'PERCENTAGE',
      discountValue: 10,
      maxDiscountAmount: 10000,
    });

    const result = calculateDiscount(coupon, 30000);

    // 30000 * 10% = 3000
    expect(result).toEqual({
      couponId: 'coupon-1',
      discountAmount: 3000,
      finalAmount: 27000,
    });
  });

  it('PERCENTAGE 쿠폰의 할인이 maxDiscountAmount를 초과하지 않는다', () => {
    const coupon = createCoupon({
      discountType: 'PERCENTAGE',
      discountValue: 50,
      maxDiscountAmount: 10000,
    });

    const result = calculateDiscount(coupon, 100000);

    // 100000 * 50% = 50000 → max 10000
    expect(result).toEqual({
      couponId: 'coupon-1',
      discountAmount: 10000,
      finalAmount: 90000,
    });
  });

  it('PERCENTAGE 쿠폰에서 maxDiscountAmount가 0이면 제한 없이 할인한다', () => {
    const coupon = createCoupon({
      discountType: 'PERCENTAGE',
      discountValue: 50,
      maxDiscountAmount: 0,
    });

    const result = calculateDiscount(coupon, 100000);

    expect(result).toEqual({
      couponId: 'coupon-1',
      discountAmount: 50000,
      finalAmount: 50000,
    });
  });

  it('할인이 주문 금액을 초과하지 않는다', () => {
    const coupon = createCoupon({ discountType: 'FIXED', discountValue: 50000 });

    const result = calculateDiscount(coupon, 30000);

    expect(result).toEqual({
      couponId: 'coupon-1',
      discountAmount: 30000,
      finalAmount: 0,
    });
  });

  it('소수점 할인 금액을 내림 처리한다', () => {
    const coupon = createCoupon({
      discountType: 'PERCENTAGE',
      discountValue: 33,
      maxDiscountAmount: 0,
    });

    const result = calculateDiscount(coupon, 10000);

    // 10000 * 33% = 3300 (Math.floor)
    expect(result.discountAmount).toBe(3300);
    expect(result.finalAmount).toBe(6700);
  });
});

describe('isCouponExpired', () => {
  it('만료 일시가 과거이면 true를 반환한다', () => {
    const pastDate = new Date();
    pastDate.setDate(pastDate.getDate() - 1);

    const coupon = createCoupon({ expiresAt: pastDate.toISOString() });

    expect(isCouponExpired(coupon)).toBe(true);
  });

  it('만료 일시가 미래이면 false를 반환한다', () => {
    const futureDate = new Date();
    futureDate.setFullYear(futureDate.getFullYear() + 1);

    const coupon = createCoupon({ expiresAt: futureDate.toISOString() });

    expect(isCouponExpired(coupon)).toBe(false);
  });
});
