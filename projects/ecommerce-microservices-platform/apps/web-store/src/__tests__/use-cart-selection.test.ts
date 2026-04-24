import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCartSelection } from '@/features/cart/model/use-cart-selection';
import type { CartItem } from '@/features/cart/model/types';

const ITEM_A: CartItem = {
  productId: 'p1',
  variantId: 'v1',
  productName: '상품A',
  optionName: '옵션1',
  price: 10000,
  quantity: 2,
};

const ITEM_B: CartItem = {
  productId: 'p2',
  variantId: 'v2',
  productName: '상품B',
  optionName: '옵션2',
  price: 20000,
  quantity: 1,
};

const ITEM_C: CartItem = {
  productId: 'p3',
  variantId: 'v3',
  productName: '상품C',
  optionName: '옵션3',
  price: 5000,
  quantity: 3,
};

describe('useCartSelection', () => {
  it('초기 상태에서 모든 아이템이 선택되어 있다', () => {
    const { result } = renderHook(() => useCartSelection([ITEM_A, ITEM_B]));

    expect(result.current.allChecked).toBe(true);
    expect(result.current.checkedItems).toEqual([ITEM_A, ITEM_B]);
  });

  it('초기 totalAmount는 모든 아이템의 price * quantity 합이다', () => {
    const { result } = renderHook(() => useCartSelection([ITEM_A, ITEM_B]));

    expect(result.current.totalAmount).toBe(10000 * 2 + 20000 * 1);
  });

  it('빈 배열일 때 allChecked는 false이다', () => {
    const { result } = renderHook(() => useCartSelection([]));

    expect(result.current.allChecked).toBe(false);
    expect(result.current.checkedItems).toEqual([]);
    expect(result.current.totalAmount).toBe(0);
  });

  it('toggleAll로 전체 선택을 해제할 수 있다', () => {
    const { result } = renderHook(() => useCartSelection([ITEM_A, ITEM_B]));

    act(() => {
      result.current.toggleAll();
    });

    expect(result.current.allChecked).toBe(false);
    expect(result.current.checkedItems).toEqual([]);
    expect(result.current.totalAmount).toBe(0);
  });

  it('toggleAll로 전체 해제 후 다시 전체 선택할 수 있다', () => {
    const { result } = renderHook(() => useCartSelection([ITEM_A, ITEM_B]));

    act(() => {
      result.current.toggleAll();
    });
    act(() => {
      result.current.toggleAll();
    });

    expect(result.current.allChecked).toBe(true);
    expect(result.current.checkedItems).toEqual([ITEM_A, ITEM_B]);
  });

  it('toggleItem으로 개별 아이템을 해제할 수 있다', () => {
    const { result } = renderHook(() => useCartSelection([ITEM_A, ITEM_B]));
    const keyA = result.current.itemKey(ITEM_A);

    act(() => {
      result.current.toggleItem(keyA);
    });

    expect(result.current.isChecked(ITEM_A)).toBe(false);
    expect(result.current.isChecked(ITEM_B)).toBe(true);
    expect(result.current.allChecked).toBe(false);
    expect(result.current.checkedItems).toEqual([ITEM_B]);
  });

  it('toggleItem으로 해제한 아이템을 다시 선택할 수 있다', () => {
    const { result } = renderHook(() => useCartSelection([ITEM_A, ITEM_B]));
    const keyA = result.current.itemKey(ITEM_A);

    act(() => {
      result.current.toggleItem(keyA);
    });
    act(() => {
      result.current.toggleItem(keyA);
    });

    expect(result.current.isChecked(ITEM_A)).toBe(true);
    expect(result.current.allChecked).toBe(true);
  });

  it('clearSelection으로 모든 선택을 해제할 수 있다', () => {
    const { result } = renderHook(() => useCartSelection([ITEM_A, ITEM_B, ITEM_C]));

    act(() => {
      result.current.clearSelection();
    });

    expect(result.current.allChecked).toBe(false);
    expect(result.current.checkedItems).toEqual([]);
    expect(result.current.totalAmount).toBe(0);
  });

  it('itemKey는 productId-variantId 형식의 키를 반환한다', () => {
    const { result } = renderHook(() => useCartSelection([ITEM_A]));

    expect(result.current.itemKey(ITEM_A)).toBe('p1-v1');
    expect(result.current.itemKey(ITEM_B)).toBe('p2-v2');
  });

  it('일부 아이템만 선택된 경우 totalAmount는 선택된 아이템 합산이다', () => {
    const { result } = renderHook(() => useCartSelection([ITEM_A, ITEM_B, ITEM_C]));
    const keyB = result.current.itemKey(ITEM_B);

    act(() => {
      result.current.toggleItem(keyB);
    });

    expect(result.current.totalAmount).toBe(10000 * 2 + 5000 * 3);
  });
});
