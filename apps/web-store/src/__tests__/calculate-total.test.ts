import { describe, it, expect } from 'vitest';
import { calculateTotal, calculateItemCount } from '@/features/cart/lib/calculate-total';
import type { CartItem } from '@/features/cart/model/types';

describe('calculateTotal', () => {
  it('빈 배열이면 0을 반환한다', () => {
    expect(calculateTotal([])).toBe(0);
  });

  it('단일 상품의 합계를 계산한다', () => {
    const items: CartItem[] = [
      { productId: 'p1', variantId: 'v1', productName: 'A', optionName: 'O', price: 10000, quantity: 2 },
    ];
    expect(calculateTotal(items)).toBe(20000);
  });

  it('여러 상품의 합계를 계산한다', () => {
    const items: CartItem[] = [
      { productId: 'p1', variantId: 'v1', productName: 'A', optionName: 'O', price: 10000, quantity: 1 },
      { productId: 'p2', variantId: 'v2', productName: 'B', optionName: 'O', price: 20000, quantity: 3 },
    ];
    expect(calculateTotal(items)).toBe(70000);
  });
});

describe('calculateItemCount', () => {
  it('빈 배열이면 0을 반환한다', () => {
    expect(calculateItemCount([])).toBe(0);
  });

  it('전체 수량을 합산한다', () => {
    const items: CartItem[] = [
      { productId: 'p1', variantId: 'v1', productName: 'A', optionName: 'O', price: 10000, quantity: 2 },
      { productId: 'p2', variantId: 'v2', productName: 'B', optionName: 'O', price: 20000, quantity: 3 },
    ];
    expect(calculateItemCount(items)).toBe(5);
  });
});
