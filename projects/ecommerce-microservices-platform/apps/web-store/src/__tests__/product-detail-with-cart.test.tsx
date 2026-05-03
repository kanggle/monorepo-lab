import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductDetailWithCart } from '@/widgets/product-detail-with-cart';
import type { ProductDetail } from '@repo/types';

const mockPush = vi.fn();
const mockAddItem = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('next/image', () => ({
  default: ({ src, alt, ...props }: { src: string; alt: string; [key: string]: unknown }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={alt} {...props} />
  ),
}));

vi.mock('@/entities/product', () => ({
  ProductImage: ({ alt }: { alt: string }) => <div data-testid="product-image" data-alt={alt} />,
}));

vi.mock('@/features/cart', () => ({
  useCart: () => ({ addItem: mockAddItem, items: [] }),
}));

vi.mock('@/shared/ui', () => ({
  Toast: ({ message }: { message: string }) => <div data-testid="toast">{message}</div>,
}));

vi.mock('@/shared/hooks/use-click-outside', () => ({
  useClickOutside: vi.fn(),
}));

vi.mock('@/features/auth', () => ({
  useAuth: () => ({
    isAuthenticated: false,
    isLoading: false,
    user: null,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/features/wishlist', () => ({
  WishlistButton: () => <button aria-label="wishlist-stub" />,
}));

const product: ProductDetail = {
  id: 'prod-1',
  name: '테스트 상품',
  description: '상품 설명입니다.',
  status: 'ON_SALE',
  price: 10000,
  categoryId: 'cat-1',
  images: ['/img/1.jpg'],
  variants: [
    { id: 'v1', optionName: '빨강', stock: 10, additionalPrice: 1000 },
    { id: 'v2', optionName: '파랑', stock: 5, additionalPrice: 0 },
    { id: 'v3', optionName: '녹색', stock: 0, additionalPrice: 500 },
  ],
};

/** 드롭다운 트리거 버튼 (placeholder 텍스트를 가진 첫 번째 버튼) */
function getDropdownTrigger() {
  const buttons = screen.getAllByRole('button', { name: /옵션을 선택하세요/ });
  // dropdown trigger has the arrow ▾ inside, cart button does not
  return buttons.find(btn => btn.querySelector('span'))!;
}

describe('ProductDetailWithCart', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('상품 이름과 가격을 표시한다', () => {
    render(<ProductDetailWithCart product={product} />);

    expect(screen.getByRole('heading', { name: '테스트 상품' })).toBeInTheDocument();
    expect(screen.getByText('10,000')).toBeInTheDocument();
  });

  it('상품 설명을 표시한다', () => {
    render(<ProductDetailWithCart product={product} />);

    expect(screen.getByText('상품 설명입니다.')).toBeInTheDocument();
  });

  it('브레드크럼을 표시한다', () => {
    render(<ProductDetailWithCart product={product} />);

    expect(screen.getByText('홈')).toBeInTheDocument();
    expect(screen.getByText('상품')).toBeInTheDocument();
  });

  it('옵션 선택 드롭다운과 장바구니 버튼이 있다', () => {
    render(<ProductDetailWithCart product={product} />);

    const buttons = screen.getAllByRole('button', { name: /옵션을 선택하세요/ });
    expect(buttons).toHaveLength(2); // dropdown trigger + cart button
  });

  it('옵션 미선택 시 즉시 주문 버튼이 비활성화된다', () => {
    render(<ProductDetailWithCart product={product} />);

    expect(screen.getByRole('button', { name: '즉시 주문' })).toBeDisabled();
  });

  it('드롭다운을 열어 옵션 목록을 표시한다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());

    expect(screen.getByText('빨강')).toBeInTheDocument();
    expect(screen.getByText('파랑')).toBeInTheDocument();
    expect(screen.getByText('녹색')).toBeInTheDocument();
  });

  it('품절 옵션은 비활성화된다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());

    const greenBtn = screen.getByText('녹색').closest('button');
    expect(greenBtn).toBeDisabled();
  });

  it('옵션 선택 후 장바구니 담기 버튼이 활성화된다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());
    await user.click(screen.getByText('빨강'));

    expect(screen.getByText('장바구니 담기')).toBeEnabled();
  });

  it('장바구니 담기 클릭 시 addItem을 호출하고 토스트를 표시한다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());
    await user.click(screen.getByText('빨강'));
    await user.click(screen.getByText('장바구니 담기'));

    expect(mockAddItem).toHaveBeenCalledWith(
      expect.objectContaining({
        productId: 'prod-1',
        variantId: 'v1',
        productName: '테스트 상품',
        optionName: '빨강',
        price: 11000,
      }),
      1,
    );
    expect(screen.getByTestId('toast')).toHaveTextContent('장바구니에 추가되었습니다.');
  });

  it('즉시 주문 클릭 시 checkout 페이지로 이동한다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());
    await user.click(screen.getByText('빨강'));
    await user.click(screen.getByText('즉시 주문'));

    expect(mockPush).toHaveBeenCalledWith(
      expect.stringContaining('/checkout?items='),
    );
  });

  it('선택된 옵션을 삭제할 수 있다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());
    await user.click(screen.getByText('빨강'));

    expect(screen.getByText('장바구니 담기')).toBeEnabled();

    await user.click(screen.getByLabelText('빨강 삭제'));

    // 삭제 후 즉시 주문 버튼이 다시 비활성화
    expect(screen.getByRole('button', { name: '즉시 주문' })).toBeDisabled();
  });
});
