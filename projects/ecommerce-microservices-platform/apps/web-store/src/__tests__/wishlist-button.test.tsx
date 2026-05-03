import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestQueryProvider } from './test-utils';

const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/products',
}));

vi.mock('@/shared/lib/auth-context', () => ({
  useAuth: vi.fn(),
}));

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

vi.mock('@repo/api-client', () => ({
  createWishlistApi: vi.fn(() => ({
    addToWishlist: vi.fn(),
    removeFromWishlist: vi.fn(),
    getMyWishlist: vi.fn(),
    checkWishlist: vi.fn(),
  })),
  createAuthApi: vi.fn(() => ({})),
  getUserFromToken: vi.fn(() => null),
  clearTokens: vi.fn(),
}));

vi.mock('@/features/wishlist/api/wishlist-api', () => ({
  addToWishlist: vi.fn(),
  removeFromWishlist: vi.fn(),
  getMyWishlist: vi.fn(),
  checkWishlist: vi.fn(),
}));

import { useAuth } from '@/shared/lib/auth-context';
import { checkWishlist, addToWishlist, removeFromWishlist } from '@/features/wishlist/api/wishlist-api';
import { WishlistButton } from '@/features/wishlist';

const mockUseAuth = vi.mocked(useAuth);
const mockCheckWishlist = vi.mocked(checkWishlist);
const mockAddToWishlist = vi.mocked(addToWishlist);
const mockRemoveFromWishlist = vi.mocked(removeFromWishlist);

describe('WishlistButton', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('비로그인 사용자가 클릭하면 로그인 페이지로 이동한다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      login: vi.fn(),
      logout: vi.fn(),
    });

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <WishlistButton productId="product-1" />
      </TestQueryProvider>,
    );

    const button = screen.getByRole('button', { name: '위시리스트에 추가' });
    await user.click(button);

    expect(mockPush).toHaveBeenCalledWith('/login');
  });

  it('인증 로딩 중에는 버튼이 비활성화된다', () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
      user: null,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <TestQueryProvider>
        <WishlistButton productId="product-1" />
      </TestQueryProvider>,
    );

    const button = screen.getByRole('button', { name: '위시리스트에 추가' });
    expect(button).toBeDisabled();
  });

  it('로그인 사용자에게 찜 상태를 확인하여 표시한다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1', email: 'test@test.com', name: 'Test' },
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockCheckWishlist.mockResolvedValue({ productId: 'product-1', inWishlist: true, wishlistItemId: 'item-1' });

    render(
      <TestQueryProvider>
        <WishlistButton productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '위시리스트에서 제거' })).toBeInTheDocument();
    });
  });

  it('찜되지 않은 상품을 클릭하면 추가 요청을 보낸다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1', email: 'test@test.com', name: 'Test' },
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockCheckWishlist.mockResolvedValue({ productId: 'product-1', inWishlist: false, wishlistItemId: null });
    mockAddToWishlist.mockResolvedValue({ wishlistItemId: 'item-1', productId: 'product-1' });

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <WishlistButton productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '위시리스트에 추가' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '위시리스트에 추가' }));

    await waitFor(() => {
      expect(mockAddToWishlist).toHaveBeenCalledWith('product-1');
    });
  });

  it('이미 찜된 상품을 클릭하면 삭제 요청을 보낸다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1', email: 'test@test.com', name: 'Test' },
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockCheckWishlist.mockResolvedValue({ productId: 'product-1', inWishlist: true, wishlistItemId: 'item-1' });
    mockRemoveFromWishlist.mockResolvedValue(undefined);

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <WishlistButton productId="product-1" wishlistItemId="item-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '위시리스트에서 제거' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '위시리스트에서 제거' }));

    await waitFor(() => {
      expect(mockRemoveFromWishlist).toHaveBeenCalledWith('item-1');
    });
  });

  it('wishlistItemId prop 없이도 check API의 wishlistItemId로 삭제할 수 있다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1', email: 'test@test.com', name: 'Test' },
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockCheckWishlist.mockResolvedValue({ productId: 'product-1', inWishlist: true, wishlistItemId: 'item-from-check' });
    mockRemoveFromWishlist.mockResolvedValue(undefined);

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <WishlistButton productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '위시리스트에서 제거' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '위시리스트에서 제거' }));

    await waitFor(() => {
      expect(mockRemoveFromWishlist).toHaveBeenCalledWith('item-from-check');
    });
  });

  it('찜 상태 조회 실패 시 기본값으로 찜되지 않은 상태를 표시한다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1', email: 'test@test.com', name: 'Test' },
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockCheckWishlist.mockRejectedValue(new Error('Network error'));

    render(
      <TestQueryProvider>
        <WishlistButton productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      const button = screen.getByRole('button', { name: '위시리스트에 추가' });
      expect(button).toBeInTheDocument();
      expect(button).not.toBeDisabled();
    });
  });

  it('위시리스트 100개 초과 시 안내 메시지를 표시한다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1', email: 'test@test.com', name: 'Test' },
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockCheckWishlist.mockResolvedValue({ productId: 'product-1', inWishlist: false, wishlistItemId: null });
    mockAddToWishlist.mockRejectedValue({ code: 'WISHLIST_LIMIT_EXCEEDED', message: 'Wishlist limit exceeded', timestamp: '2026-04-06T00:00:00Z' });

    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <WishlistButton productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '위시리스트에 추가' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '위시리스트에 추가' }));

    await waitFor(() => {
      expect(alertSpy).toHaveBeenCalledWith('위시리스트는 최대 100개까지 추가할 수 있습니다.');
    });

    alertSpy.mockRestore();
  });

  it('inWishlist=true인데 wishlistItemId가 없으면 사용자 안내 후 refetch한다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1', email: 'test@test.com', name: 'Test' },
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockCheckWishlist.mockResolvedValue({ productId: 'product-1', inWishlist: true, wishlistItemId: null as unknown as string });

    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <WishlistButton productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '위시리스트에서 제거' })).toBeInTheDocument();
    });

    mockCheckWishlist.mockClear();
    await user.click(screen.getByRole('button', { name: '위시리스트에서 제거' }));

    expect(alertSpy).toHaveBeenCalledWith('위시리스트 상태를 다시 불러오는 중입니다. 잠시 후 다시 시도해주세요.');
    expect(mockRemoveFromWishlist).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(mockCheckWishlist).toHaveBeenCalledWith('product-1');
    });

    alertSpy.mockRestore();
  });
});
