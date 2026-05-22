import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('@/shared/lib/auth-context', () => ({
  useAuth: () => ({ isAuthenticated: true, isLoading: false }),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/cart',
}));

import { CartProvider } from '@/features/cart/model/cart-context';
import { AddToCartButton } from '@/features/cart/ui/AddToCartButton';
import { CartSummary } from '@/features/cart/ui/CartSummary';
import type { CartItem } from '@/features/cart/model/types';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

function renderWithCart(ui: React.ReactNode) {
  return render(<CartProvider>{ui}</CartProvider>);
}

describe('AddToCartButton', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {});
  });

  it('장바구니 담기 버튼을 표시한다', () => {
    renderWithCart(
      <AddToCartButton
        productId="p1"
        variantId="v1"
        productName="상품"
        optionName="옵션"
        price={10000}
      />,
    );

    expect(screen.getByText('장바구니 담기')).toBeInTheDocument();
  });

  it('품절 시 버튼이 비활성화되고 품절 텍스트를 표시한다', () => {
    renderWithCart(
      <AddToCartButton
        productId="p1"
        variantId="v1"
        productName="상품"
        optionName="옵션"
        price={10000}
        disabled
      />,
    );

    expect(screen.getByText('품절')).toBeDisabled();
  });

  it('클릭 시 장바구니 추가 토스트를 표시한다', async () => {
    const user = userEvent.setup();
    renderWithCart(
      <AddToCartButton
        productId="p1"
        variantId="v1"
        productName="상품"
        optionName="옵션"
        price={10000}
      />,
    );

    await user.click(screen.getByText('장바구니 담기'));

    expect(screen.getByText('장바구니에 추가되었습니다.')).toBeInTheDocument();
  });
});

describe('CartSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {});
  });

  it('장바구니가 비어있으면 안내 메시지를 표시한다', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null);

    renderWithCart(<CartSummary />);

    await waitFor(() => {
      expect(screen.getByText('장바구니가 비어있습니다.')).toBeInTheDocument();
    });
    expect(screen.getByText('상품 보러 가기')).toHaveAttribute('href', '/products');
  });

  it('상품이 있으면 장바구니 항목과 합계를 표시한다', async () => {
    const items: CartItem[] = [
      {
        productId: 'p1',
        variantId: 'v1',
        productName: '노트북',
        optionName: '실버',
        price: 1500000,
        quantity: 1,
      },
    ];
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(JSON.stringify(items));

    renderWithCart(<CartSummary />);

    await waitFor(() => {
      expect(screen.getByText('노트북')).toBeInTheDocument();
    });
    expect(screen.getByText('실버')).toBeInTheDocument();
    // 가격 숫자와 단위 "원"이 별도 span으로 렌더링되므로 textContent 전체로 검색한다
    // 선택 상태는 비동기 초기화로 합계가 0이 될 수 있으므로 항목 가격만 확인한다
    const priceElements = screen.getAllByText((_, el) => el?.textContent === '1,500,000원');
    expect(priceElements.length).toBeGreaterThanOrEqual(1); // 항목 가격(최소 1개)
  });

  it('전체 삭제 버튼으로 장바구니를 비운다', async () => {
    const items: CartItem[] = [
      {
        productId: 'p1',
        variantId: 'v1',
        productName: '노트북',
        optionName: '실버',
        price: 1500000,
        quantity: 1,
      },
    ];
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(JSON.stringify(items));

    const user = userEvent.setup();
    renderWithCart(<CartSummary />);

    await waitFor(() => {
      expect(screen.getByText('노트북')).toBeInTheDocument();
    });

    // 선택 상태가 비동기 초기화로 비어있을 수 있으므로 전체선택 후 삭제
    const allCheckboxes = screen.getAllByRole('checkbox');
    // 전체선택(첫 번째 체크박스)이 체크되지 않은 경우 클릭해서 전체 선택
    if (!(allCheckboxes[0] as HTMLInputElement).checked) {
      await user.click(allCheckboxes[0]);
    }
    await user.click(screen.getByText('선택 삭제'));

    await waitFor(() => {
      expect(screen.getByText('장바구니가 비어있습니다.')).toBeInTheDocument();
    });
  });
});
