import { describe, it, expect } from 'vitest';
import type { CouponSummary } from '@repo/types';
import {
  formatDiscountValue,
  formatMaxDiscount,
} from '@/features/coupon/lib/format-discount';

/**
 * TASK-FE-077 — coupon discount display formatting. A regression here silently
 * shows the wrong discount label (currency vs percent, missing/extra max).
 */

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

describe('formatDiscountValue', () => {
  it('formats a FIXED coupon as grouped 원 할인', () => {
    expect(
      formatDiscountValue(createCoupon({ discountType: 'FIXED', discountValue: 5000 })),
    ).toBe('5,000원 할인');
  });

  it('formats a PERCENTAGE coupon as % 할인 (no grouping)', () => {
    expect(
      formatDiscountValue(createCoupon({ discountType: 'PERCENTAGE', discountValue: 15 })),
    ).toBe('15% 할인');
  });
});

describe('formatMaxDiscount', () => {
  it('shows the cap for a PERCENTAGE coupon with a positive max', () => {
    expect(
      formatMaxDiscount(
        createCoupon({ discountType: 'PERCENTAGE', maxDiscountAmount: 3000 }),
      ),
    ).toBe('최대 3,000원');
  });

  it('returns null for a PERCENTAGE coupon with no cap (maxDiscountAmount=0)', () => {
    expect(
      formatMaxDiscount(
        createCoupon({ discountType: 'PERCENTAGE', maxDiscountAmount: 0 }),
      ),
    ).toBeNull();
  });

  it('returns null for a FIXED coupon regardless of maxDiscountAmount', () => {
    expect(
      formatMaxDiscount(
        createCoupon({ discountType: 'FIXED', maxDiscountAmount: 9999 }),
      ),
    ).toBeNull();
  });
});
