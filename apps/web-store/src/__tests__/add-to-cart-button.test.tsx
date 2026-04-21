import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';

const mockAddItem = vi.fn();
const mockPush = vi.fn();
const mockAuthState = { isAuthenticated: true };
const mockPathname = { value: '/products/p1' };

vi.mock('@/features/cart/model/cart-context', () => ({
  useCart: () => ({ addItem: mockAddItem }),
}));

vi.mock('@/shared/lib/auth-context', () => ({
  useAuth: () => mockAuthState,
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => mockPathname.value,
}));

import { AddToCartButton } from '@/features/cart/ui/AddToCartButton';

describe('AddToCartButton', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockAddItem.mockClear();
    mockPush.mockClear();
    mockAuthState.isAuthenticated = true;
    mockPathname.value = '/products/p1';
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  const defaultProps = {
    productId: 'p1',
    variantId: 'v1',
    productName: '상품A',
    optionName: '옵션1',
    price: 10000,
  };

  it('클릭 시 addItem을 호출하고 토스트를 표시한다', () => {
    render(<AddToCartButton {...defaultProps} />);

    fireEvent.click(screen.getByRole('button'));

    expect(mockAddItem).toHaveBeenCalledWith(
      {
        productId: 'p1',
        variantId: 'v1',
        productName: '상품A',
        optionName: '옵션1',
        price: 10000,
      },
      1,
    );
    expect(screen.getByText('장바구니에 추가되었습니다.')).toBeInTheDocument();
  });

  it('3000ms 후 토스트가 사라진다', () => {
    render(<AddToCartButton {...defaultProps} />);

    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('장바구니에 추가되었습니다.')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(screen.queryByText('장바구니에 추가되었습니다.')).not.toBeInTheDocument();
  });

  it('클릭 후 버튼 텍스트는 장바구니 담기를 유지한다', () => {
    render(<AddToCartButton {...defaultProps} />);

    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByRole('button')).toHaveTextContent('장바구니 담기');
  });

  it('disabled 상태에서는 품절 텍스트를 표시한다', () => {
    render(<AddToCartButton {...defaultProps} disabled />);
    expect(screen.getByRole('button')).toHaveTextContent('품절');
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('비로그인 상태에서 클릭 시 addItem을 호출하지 않고 로그인 페이지로 이동한다', () => {
    mockAuthState.isAuthenticated = false;
    mockPathname.value = '/products/p1';

    render(<AddToCartButton {...defaultProps} />);
    fireEvent.click(screen.getByRole('button'));

    expect(mockAddItem).not.toHaveBeenCalled();
    expect(mockPush).toHaveBeenCalledWith('/login?redirect=%2Fproducts%2Fp1');
    expect(screen.queryByText('장바구니에 추가되었습니다.')).not.toBeInTheDocument();
  });
});
