import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { CheckoutCartItem } from '@/features/checkout/model/types';

let mockSearchParams = new URLSearchParams();
vi.mock('next/navigation', () => ({
  useSearchParams: () => mockSearchParams,
}));

import { useCheckoutItems } from '@/features/checkout/model/use-checkout-items';

const ITEM_A: CheckoutCartItem = {
  productId: 'p1',
  variantId: 'v1',
  productName: '상품A',
  optionName: '옵션1',
  price: 10000,
  quantity: 2,
};

const ITEM_B: CheckoutCartItem = {
  productId: 'p2',
  variantId: 'v2',
  productName: '상품B',
  optionName: '옵션2',
  price: 20000,
  quantity: 1,
};

const ITEM_C: CheckoutCartItem = {
  productId: 'p3',
  variantId: 'v3',
  productName: '상품C',
  optionName: '옵션3',
  price: 5000,
  quantity: 3,
};

describe('useCheckoutItems', () => {
  beforeEach(() => {
    mockSearchParams = new URLSearchParams();
  });

  it('items 쿼리 파라미터가 없으면 모든 아이템을 반환한다', () => {
    const { result } = renderHook(() => useCheckoutItems({ items: [ITEM_A, ITEM_B] }));

    expect(result.current.checkoutItems).toEqual([ITEM_A, ITEM_B]);
    expect(result.current.totalAmount).toBe(10000 * 2 + 20000 * 1);
  });

  it('items 쿼리 파라미터로 선택된 아이템만 필터링한다', () => {
    mockSearchParams = new URLSearchParams({ items: 'p1:v1' });

    const { result } = renderHook(() => useCheckoutItems({ items: [ITEM_A, ITEM_B] }));

    expect(result.current.checkoutItems).toEqual([ITEM_A]);
    expect(result.current.totalAmount).toBe(10000 * 2);
  });

  it('여러 아이템이 쿼리 파라미터에 쉼표로 구분되어 있으면 모두 필터링한다', () => {
    mockSearchParams = new URLSearchParams({ items: 'p1:v1,p3:v3' });

    const { result } = renderHook(() => useCheckoutItems({ items: [ITEM_A, ITEM_B, ITEM_C] }));

    expect(result.current.checkoutItems).toEqual([ITEM_A, ITEM_C]);
    expect(result.current.totalAmount).toBe(10000 * 2 + 5000 * 3);
  });

  it('아이템이 비어있으면 isEmpty가 true이다', () => {
    const { result } = renderHook(() => useCheckoutItems({ items: [] }));

    expect(result.current.isEmpty).toBe(true);
    expect(result.current.checkoutItems).toEqual([]);
    expect(result.current.totalAmount).toBe(0);
  });

  it('completeOrder는 카트를 비우지 않는다 — 비우기는 결제 성공 페이지로 이동 (TASK-BE-430)', () => {
    // completeOrder only snapshots + flags completion; the cart is cleared on the
    // payment-complete page, so a failed/abandoned payment keeps the cart.
    const { result, rerender } = renderHook(
      ({ items }) => useCheckoutItems({ items }),
      { initialProps: { items: [ITEM_A, ITEM_B] } },
    );

    act(() => {
      result.current.completeOrder();
    });

    // The live cart (source items) is untouched by completeOrder.
    rerender({ items: [ITEM_A, ITEM_B] });
    expect(result.current.checkoutItems).toEqual([ITEM_A, ITEM_B]);
  });

  it('completeOrder 후 isEmpty는 false이다 (스냅샷 유지)', () => {
    const { result } = renderHook(() => useCheckoutItems({ items: [ITEM_A] }));

    act(() => {
      result.current.completeOrder();
    });

    expect(result.current.isEmpty).toBe(false);
    expect(result.current.checkoutItems).toEqual([ITEM_A]);
  });

  it('completeOrder 후 items가 비어도 스냅샷된 아이템을 유지한다', () => {
    const { result, rerender } = renderHook(
      ({ items }) => useCheckoutItems({ items }),
      { initialProps: { items: [ITEM_A, ITEM_B] } },
    );

    act(() => {
      result.current.completeOrder();
    });

    rerender({ items: [] });

    expect(result.current.checkoutItems).toEqual([ITEM_A, ITEM_B]);
    expect(result.current.totalAmount).toBe(10000 * 2 + 20000 * 1);
  });
});
