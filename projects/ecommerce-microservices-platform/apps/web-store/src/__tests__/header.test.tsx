import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Header } from '@/widgets/header';

const mockLogout = vi.fn();
const mockUseAuth = vi.fn();
const mockUseCart = vi.fn();

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock('@/features/auth', () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock('@/features/cart', () => ({
  useCart: () => mockUseCart(),
}));

vi.mock('@/shared/context/ProfileImageContext', () => ({
  useProfileImage: () => ({ imageUrl: '' }),
}));

vi.mock('@/shared/hooks/use-click-outside', () => ({
  useClickOutside: vi.fn(),
}));

describe('Header', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      logout: mockLogout,
    });
    mockUseCart.mockReturnValue({ items: [] });
  });

  it('로고를 표시한다', () => {
    render(<Header />);

    expect(screen.getByText('WebStore')).toBeInTheDocument();
  });

  it('로고가 홈으로 링크된다', () => {
    render(<Header />);

    const logo = screen.getByText('WebStore');
    expect(logo.closest('a')).toHaveAttribute('href', '/');
  });

  it('전체상품 링크를 표시한다', () => {
    render(<Header />);

    const link = screen.getByText('전체상품');
    expect(link).toHaveAttribute('href', '/products');
  });

  it('인증 상태에서 장바구니 링크를 표시한다', () => {
    mockUseAuth.mockReturnValue({
      user: { name: '홍길동' },
      isAuthenticated: true,
      isLoading: false,
      logout: mockLogout,
    });

    render(<Header />);

    expect(screen.getByLabelText('장바구니')).toHaveAttribute('href', '/cart');
  });

  it('비로그인 상태에서 장바구니 링크를 숨긴다', () => {
    render(<Header />);

    expect(screen.queryByLabelText('장바구니')).not.toBeInTheDocument();
  });

  it('인증 로딩 중에는 장바구니 링크를 숨긴다', () => {
    mockUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: true,
      logout: mockLogout,
    });

    render(<Header />);

    expect(screen.queryByLabelText('장바구니')).not.toBeInTheDocument();
  });

  it('비인증 상태에서 로그인 링크를 표시한다', () => {
    render(<Header />);

    expect(screen.getByText('로그인')).toHaveAttribute('href', '/login');
  });

  it('인증 상태에서 프로필 메뉴 버튼을 표시한다', () => {
    mockUseAuth.mockReturnValue({
      user: { name: '홍길동' },
      isAuthenticated: true,
      isLoading: false,
      logout: mockLogout,
    });

    render(<Header />);

    expect(screen.getByLabelText('프로필 메뉴')).toBeInTheDocument();
    expect(screen.queryByText('로그인')).not.toBeInTheDocument();
  });

  it('인증 상태에서 장바구니에 아이템이 있으면 뱃지 숫자를 표시한다', () => {
    mockUseAuth.mockReturnValue({
      user: { name: '홍길동' },
      isAuthenticated: true,
      isLoading: false,
      logout: mockLogout,
    });
    mockUseCart.mockReturnValue({
      items: [
        { productId: 'p1', variantId: 'v1', quantity: 3 },
        { productId: 'p2', variantId: 'v2', quantity: 2 },
      ],
    });

    render(<Header />);

    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('모바일 메뉴 토글 버튼이 있다', async () => {
    const user = userEvent.setup();
    render(<Header />);

    const menuBtn = screen.getByLabelText('메뉴 열기');
    expect(menuBtn).toBeInTheDocument();

    await user.click(menuBtn);
    expect(screen.getByLabelText('메뉴 닫기')).toBeInTheDocument();
  });
});
