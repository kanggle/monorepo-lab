import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

const mockReplace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn() }),
}));

vi.mock('@/features/auth', () => ({
  useAuth: vi.fn(),
  useRequireAuth: vi.fn(),
}));

vi.mock('@/features/cart', () => ({
  useCart: vi.fn(),
}));

vi.mock('@/features/checkout', () => ({
  CheckoutForm: (props: Record<string, unknown>) => (
    <div data-testid="checkout-form" data-items={JSON.stringify(props.items)} />
  ),
  useCheckoutItems: vi.fn(),
}));

vi.mock('@/features/coupon', () => ({
  CouponSelector: (props: Record<string, unknown>) => (
    <div data-testid="coupon-selector" data-order-amount={props.orderAmount} />
  ),
}));

import { useAuth, useRequireAuth } from '@/features/auth';
import { useCart } from '@/features/cart';
import { useCheckoutItems } from '@/features/checkout';
import CheckoutPage from '@/app/(store)/checkout/page';

const mockUseAuth = vi.mocked(useAuth);
const mockUseRequireAuth = vi.mocked(useRequireAuth);
const mockUseCart = vi.mocked(useCart);
const mockUseCheckoutItems = vi.mocked(useCheckoutItems);

const CART_ITEMS = [
  { productId: 'p1', variantId: 'v1', productName: '노트북', optionName: '실버', price: 1500000, quantity: 1 },
];

describe('CheckoutPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseCheckoutItems.mockReturnValue({
      checkoutItems: [],
      totalAmount: 0,
      completeOrder: vi.fn(),
      isEmpty: true,
    });
    mockUseRequireAuth.mockReturnValue({ isReady: true });
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: null,
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('미인증 상태에서 로그인 페이지로 리다이렉트한다', () => {
    mockUseRequireAuth.mockImplementation(() => {
      mockReplace('/login');
      return { isReady: false };
    });
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });
    mockUseCart.mockReturnValue({
      items: CART_ITEMS,
      totalAmount: 1500000,
      itemCount: 1,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    render(<CheckoutPage />);

    expect(mockReplace).toHaveBeenCalledWith('/login');
  });

  it('장바구니가 비어있으면 장바구니 페이지로 리다이렉트한다', () => {
    mockUseCart.mockReturnValue({
      items: [],
      totalAmount: 0,
      itemCount: 0,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    render(<CheckoutPage />);

    expect(mockReplace).toHaveBeenCalledWith('/cart');
  });

  it('인증 완료 + 장바구니에 상품이 있으면 CheckoutForm을 렌더링한다', () => {
    mockUseCart.mockReturnValue({
      items: CART_ITEMS,
      totalAmount: 1500000,
      itemCount: 1,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });
    mockUseCheckoutItems.mockReturnValue({
      checkoutItems: CART_ITEMS,
      totalAmount: 1500000,
      completeOrder: vi.fn(),
      isEmpty: false,
    });

    const { getByTestId } = render(<CheckoutPage />);

    expect(getByTestId('checkout-form')).toBeInTheDocument();
    expect(getByTestId('coupon-selector')).toBeInTheDocument();
  });

  it('인증 로딩 중이면 아무것도 렌더링하지 않는다', () => {
    mockUseRequireAuth.mockReturnValue({ isReady: false });
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
      user: null,
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });
    mockUseCart.mockReturnValue({
      items: CART_ITEMS,
      totalAmount: 1500000,
      itemCount: 1,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    const { container } = render(<CheckoutPage />);

    expect(container.querySelector('main')).not.toBeInTheDocument();
  });
});
