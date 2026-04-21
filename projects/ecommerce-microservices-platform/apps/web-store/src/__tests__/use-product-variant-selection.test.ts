import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { ProductDetail } from '@repo/types';

const mockPush = vi.fn();
const mockAddItem = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: vi.fn() }),
}));

vi.mock('@/features/cart', () => ({
  useCart: () => ({ addItem: mockAddItem }),
}));

import { useProductVariantSelection } from '@/widgets/product-detail-with-cart/use-product-variant-selection';

const PRODUCT: ProductDetail = {
  id: 'prod-1',
  name: '테스트 상품',
  description: '설명',
  status: 'ON_SALE',
  price: 10000,
  categoryId: 'cat-1',
  variants: [
    { id: 'v1', optionName: '옵션1', stock: 10, additionalPrice: 0 },
    { id: 'v2', optionName: '옵션2', stock: 5, additionalPrice: 2000 },
    { id: 'v3', optionName: '품절옵션', stock: 0, additionalPrice: 0 },
  ],
};

describe('useProductVariantSelection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('초기 상태는 선택 없음, 드롭다운 닫힘, 토스트 미표시이다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    expect(result.current.selectedItems).toEqual([]);
    expect(result.current.dropdownOpen).toBe(false);
    expect(result.current.showToast).toBe(false);
    expect(result.current.canAdd).toBe(false);
    expect(result.current.totalQuantity).toBe(0);
    expect(result.current.totalPrice).toBe(0);
  });

  it('옵션을 선택하면 수량 1로 추가되고 드롭다운이 닫힌다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleDropdownToggle();
    });
    act(() => {
      result.current.handleSelect('v1');
    });

    expect(result.current.selectedItems).toEqual([{ variantId: 'v1', quantity: 1 }]);
    expect(result.current.dropdownOpen).toBe(false);
    expect(result.current.canAdd).toBe(true);
  });

  it('동일 옵션을 중복 선택해도 한 번만 추가된다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleSelect('v1');
    });
    act(() => {
      result.current.handleSelect('v1');
    });

    expect(result.current.selectedItems).toHaveLength(1);
  });

  it('재고 0인 옵션은 선택되지 않는다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleSelect('v3');
    });

    expect(result.current.selectedItems).toEqual([]);
  });

  it('존재하지 않는 variantId는 무시한다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleSelect('unknown');
    });
    act(() => {
      result.current.handleSelect('');
    });

    expect(result.current.selectedItems).toEqual([]);
  });

  it('totalQuantity와 totalPrice는 선택된 아이템의 합계이다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleSelect('v1');
    });
    act(() => {
      result.current.handleSelect('v2');
    });
    act(() => {
      result.current.handleQuantity('v1', 3);
    });
    act(() => {
      result.current.handleQuantity('v2', 2);
    });

    expect(result.current.totalQuantity).toBe(5);
    expect(result.current.totalPrice).toBe(10000 * 3 + 12000 * 2);
  });

  it('수량은 1 이상 재고 이하로 제한된다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleSelect('v2');
    });
    act(() => {
      result.current.handleQuantity('v2', 99);
    });
    expect(result.current.selectedItems[0].quantity).toBe(5);

    act(() => {
      result.current.handleQuantity('v2', 0);
    });
    expect(result.current.selectedItems[0].quantity).toBe(1);
  });

  it('handleRemove로 선택 항목을 제거할 수 있다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleSelect('v1');
    });
    act(() => {
      result.current.handleSelect('v2');
    });
    act(() => {
      result.current.handleRemove('v1');
    });

    expect(result.current.selectedItems).toEqual([{ variantId: 'v2', quantity: 1 }]);
  });

  it('handleAddToCart는 각 항목을 addItem에 전달하고 선택을 초기화한다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleSelect('v1');
    });
    act(() => {
      result.current.handleSelect('v2');
    });
    act(() => {
      result.current.handleQuantity('v2', 3);
    });
    act(() => {
      result.current.handleAddToCart();
    });

    expect(mockAddItem).toHaveBeenCalledTimes(2);
    expect(mockAddItem).toHaveBeenCalledWith(
      {
        productId: 'prod-1',
        variantId: 'v1',
        productName: '테스트 상품',
        optionName: '옵션1',
        price: 10000,
      },
      1,
    );
    expect(mockAddItem).toHaveBeenCalledWith(
      {
        productId: 'prod-1',
        variantId: 'v2',
        productName: '테스트 상품',
        optionName: '옵션2',
        price: 12000,
      },
      3,
    );
    expect(result.current.selectedItems).toEqual([]);
    expect(result.current.showToast).toBe(true);
  });

  it('handleBuyNow는 addItem 호출 후 체크아웃 경로로 이동한다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleSelect('v1');
    });
    act(() => {
      result.current.handleSelect('v2');
    });
    act(() => {
      result.current.handleBuyNow();
    });

    expect(mockAddItem).toHaveBeenCalledTimes(2);
    expect(mockPush).toHaveBeenCalledWith(
      `/checkout?items=${encodeURIComponent('prod-1:v1,prod-1:v2')}`,
    );
  });

  it('clearToast로 토스트를 닫을 수 있다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleSelect('v1');
    });
    act(() => {
      result.current.handleAddToCart();
    });
    expect(result.current.showToast).toBe(true);

    act(() => {
      result.current.clearToast();
    });
    expect(result.current.showToast).toBe(false);
  });

  it('handleDropdownToggle와 handleDropdownClose로 드롭다운 상태를 제어한다', () => {
    const { result } = renderHook(() => useProductVariantSelection(PRODUCT));

    act(() => {
      result.current.handleDropdownToggle();
    });
    expect(result.current.dropdownOpen).toBe(true);

    act(() => {
      result.current.handleDropdownClose();
    });
    expect(result.current.dropdownOpen).toBe(false);
  });
});
