import { describe, it, expect, beforeEach } from 'vitest';
import {
  getOrCreateIdempotencyKey,
  clearCheckoutIdempotencyKey,
} from '@/features/checkout/model/checkout-idempotency';
import type { CheckoutCartItem } from '@/features/checkout/model/types';

const cart = (quantity = 1): CheckoutCartItem[] => [
  { productId: 'p1', variantId: 'v1', productName: 'A', optionName: '', price: 1000, quantity },
];

describe('checkout-idempotency (TASK-BE-430)', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('같은 카트는 같은 키를 재사용한다 (재시도 시 동일 주문)', () => {
    const k1 = getOrCreateIdempotencyKey(cart());
    const k2 = getOrCreateIdempotencyKey(cart());
    expect(k2).toBe(k1);
  });

  it('카트 내용이 바뀌면 새 키를 발급한다 (새 주문)', () => {
    const k1 = getOrCreateIdempotencyKey(cart(1));
    const k2 = getOrCreateIdempotencyKey(cart(2));
    expect(k2).not.toBe(k1);
  });

  it('🔴 TASK-INT-026 — 쿠폰이 바뀌면 새 키를 발급한다 (옛 할인으로 매겨진 주문을 재사용하지 않는다)', () => {
    const noCoupon = getOrCreateIdempotencyKey(cart());
    const withCoupon = getOrCreateIdempotencyKey(cart(), 'coupon-1');
    const otherCoupon = getOrCreateIdempotencyKey(cart(), 'coupon-2');
    expect(withCoupon).not.toBe(noCoupon);
    expect(otherCoupon).not.toBe(withCoupon);
  });

  it('같은 카트 + 같은 쿠폰이면 같은 키를 재사용한다', () => {
    const k1 = getOrCreateIdempotencyKey(cart(), 'coupon-1');
    const k2 = getOrCreateIdempotencyKey(cart(), 'coupon-1');
    expect(k2).toBe(k1);
  });

  it('clearCheckoutIdempotencyKey 후에는 새 키가 발급된다 (결제 완주 후 리셋)', () => {
    const k1 = getOrCreateIdempotencyKey(cart());
    clearCheckoutIdempotencyKey();
    const k2 = getOrCreateIdempotencyKey(cart());
    expect(k2).not.toBe(k1);
  });

  it('비어있지 않은 문자열 키를 발급한다', () => {
    const key = getOrCreateIdempotencyKey(cart());
    expect(typeof key).toBe('string');
    expect(key.length).toBeGreaterThan(0);
  });
});
