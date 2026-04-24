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
  let mockRemoveItem: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockRemoveItem = vi.fn();
    mockSearchParams = new URLSearchParams();
  });

  it('items 쿼리 파라미터가 없으면 모든 아이템을 반환한다', () => {
    const { result } = renderHook(() =>
      useCheckoutItems({ items: [ITEM_A, ITEM_B], removeItem: mockRemoveItem }),
    );

    expect(result.current.checkoutItems).toEqual([ITEM_A, ITEM_B]);
    expect(result.current.totalAmount).toBe(10000 * 2 + 20000 * 1);
  });

  it('items 쿼리 파라미터로 선택된 아이템만 필터링한다', () => {
    mockSearchParams = new URLSearchParams({ items: 'p1:v1' });

    const { result } = renderHook(() =>
      useCheckoutItems({ items: [ITEM_A, ITEM_B], removeItem: mockRemoveItem }),
    );

    expect(result.current.checkoutItems).toEqual([ITEM_A]);
    expect(result.current.totalAmount).toBe(10000 * 2);
  });

  it('여러 아이템이 쿼리 파라미터에 쉼표로 구분되어 있으면 모두 필터링한다', () => {
    mockSearchParams = new URLSearchParams({ items: 'p1:v1,p3:v3' });

    const { result } = renderHook(() =>
      useCheckoutItems({ items: [ITEM_A, ITEM_B, ITEM_C], removeItem: mockRemoveItem }),
    );

    expect(result.current.checkoutItems).toEqual([ITEM_A, ITEM_C]);
    expect(result.current.totalAmount).toBe(10000 * 2 + 5000 * 3);
  });

  it('아이템이 비어있으면 isEmpty가 true이다', () => {
    const { result } = renderHook(() =>
      useCheckoutItems({ items: [], removeItem: mockRemoveItem }),
    );

    expect(result.current.isEmpty).toBe(true);
    expect(result.current.checkoutItems).toEqual([]);
    expect(result.current.totalAmount).toBe(0);
  });

  it('completeOrder 호출 시 각 아이템에 대해 removeItem을 호출한다', () => {
    const { result } = renderHook(() =>
      useCheckoutItems({ items: [ITEM_A, ITEM_B], removeItem: mockRemoveItem }),
    );

    act(() => {
      result.current.completeOrder();
    });

    expect(mockRemoveItem).toHaveBeenCalledTimes(2);
    expect(mockRemoveItem).toHaveBeenCalledWith('p1', 'v1');
    expect(mockRemoveItem).toHaveBeenCalledWith('p2', 'v2');
  });

  it('completeOrder 후 isEmpty는 false이다 (스냅샷 유지)', () => {
    const { result } = renderHook(() =>
      useCheckoutItems({ items: [ITEM_A], removeItem: mockRemoveItem }),
    );

    act(() => {
      result.current.completeOrder();
    });

    expect(result.current.isEmpty).toBe(false);
    expect(result.current.checkoutItems).toEqual([ITEM_A]);
  });

  it('completeOrder 후 items가 비어도 스냅샷된 아이템을 유지한다', () => {
    const { result, rerender } = renderHook(
      ({ items }) => useCheckoutItems({ items, removeItem: mockRemoveItem }),
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
