import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { WishlistItem, PaginatedResponse } from '@repo/types';
import { TestQueryProvider } from './test-utils';

const mockReplace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/my/wishlist',
}));

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

vi.mock('@/features/auth', () => ({
  useAuth: vi.fn(),
  useRequireAuth: vi.fn(),
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
}));

vi.mock('@/features/wishlist/api/wishlist-api', () => ({
  addToWishlist: vi.fn(),
  removeFromWishlist: vi.fn(),
  getMyWishlist: vi.fn(),
  checkWishlist: vi.fn(),
}));

vi.mock('@repo/ui', () => ({
  LoadingSpinner: () => <div data-testid="loading-spinner">로딩 중...</div>,
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      {message}
      <button onClick={onRetry}>재시도</button>
    </div>
  ),
  EmptyState: ({ message }: { message: string }) => (
    <div data-testid="empty-state">{message}</div>
  ),
}));

import { useAuth, useRequireAuth } from '@/features/auth';
import { getMyWishlist, removeFromWishlist } from '@/features/wishlist/api/wishlist-api';
import WishlistPage from '@/app/(store)/my/wishlist/page';

const mockUseAuth = vi.mocked(useAuth);
const mockUseRequireAuth = vi.mocked(useRequireAuth);
const mockGetMyWishlist = vi.mocked(getMyWishlist);
const mockRemoveFromWishlist = vi.mocked(removeFromWishlist);

const MOCK_ITEMS: WishlistItem[] = [
  {
    wishlistItemId: 'item-1',
    productId: 'product-1',
    productName: '클래식 화이트 티셔츠',
    productPrice: 29000,
    productStatus: 'ON_SALE',
    addedAt: '2026-03-25T10:00:00Z',
  },
  {
    wishlistItemId: 'item-2',
    productId: 'product-2',
    productName: '캐주얼 후드 집업',
    productPrice: 79000,
    productStatus: 'ON_SALE',
    addedAt: '2026-03-24T10:00:00Z',
  },
];

const DELETED_ITEM: WishlistItem = {
  wishlistItemId: 'item-3',
  productId: 'product-3',
  productName: null,
  productPrice: 0,
  productStatus: 'DELETED',
  addedAt: '2026-03-20T10:00:00Z',
};

function createPaginatedResponse(
  content: WishlistItem[],
  page = 0,
  size = 20,
  totalElements = content.length,
): PaginatedResponse<WishlistItem> {
  return { content, page, size, totalElements };
}

describe('WishlistPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: null,
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });
    mockUseRequireAuth.mockReturnValue({ isReady: true });
  });

  it('위시리스트 목록을 렌더링한다', async () => {
    mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse(MOCK_ITEMS));

    render(
      <TestQueryProvider>
        <WishlistPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('클래식 화이트 티셔츠')).toBeInTheDocument();
    });
    expect(screen.getByText('캐주얼 후드 집업')).toBeInTheDocument();
    expect(screen.getByText('29,000원')).toBeInTheDocument();
    expect(screen.getByText('79,000원')).toBeInTheDocument();
  });

  it('위시리스트가 비어 있으면 빈 상태를 표시한다', async () => {
    mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse([]));

    render(
      <TestQueryProvider>
        <WishlistPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
    expect(screen.getByText('위시리스트가 비어 있습니다.')).toBeInTheDocument();
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockGetMyWishlist.mockRejectedValueOnce(new Error('fail'));

    render(
      <TestQueryProvider>
        <WishlistPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('위시리스트를 불러오는데 실패했습니다.')).toBeInTheDocument();
  });

  it('에러 후 재시도 버튼을 클릭하면 다시 로드한다', async () => {
    mockGetMyWishlist.mockRejectedValueOnce(new Error('fail'));

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <WishlistPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });

    mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse(MOCK_ITEMS));
    await user.click(screen.getByText('재시도'));

    await waitFor(() => {
      expect(screen.getByText('클래식 화이트 티셔츠')).toBeInTheDocument();
    });
  });

  it('삭제된 상품은 "판매 종료"로 표시한다', async () => {
    mockGetMyWishlist.mockResolvedValueOnce(
      createPaginatedResponse([...MOCK_ITEMS, DELETED_ITEM]),
    );

    render(
      <TestQueryProvider>
        <WishlistPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('판매 종료')).toBeInTheDocument();
    });
    expect(screen.getByText('삭제됨')).toBeInTheDocument();
  });

  it('위시리스트에서 제거 버튼을 클릭하면 삭제 요청을 보낸다', async () => {
    mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse(MOCK_ITEMS));
    mockRemoveFromWishlist.mockResolvedValueOnce(undefined);

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <WishlistPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('클래식 화이트 티셔츠')).toBeInTheDocument();
    });

    const removeButtons = screen.getAllByRole('button', { name: '위시리스트에서 제거' });
    await user.click(removeButtons[0]);

    await waitFor(() => {
      expect(mockRemoveFromWishlist).toHaveBeenCalledWith('item-1');
    });
  });

  describe('페이지네이션', () => {
    it('페이지네이션 컨트롤을 표시한다', async () => {
      mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse(MOCK_ITEMS, 0, 20, 50));

      render(
        <TestQueryProvider>
          <WishlistPage />
        </TestQueryProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText('1 / 3')).toBeInTheDocument();
      });
      expect(screen.getByText('이전')).toBeInTheDocument();
      expect(screen.getByText('다음')).toBeInTheDocument();
    });

    it('첫 페이지에서 이전 버튼이 비활성화된다', async () => {
      mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse(MOCK_ITEMS, 0, 20, 50));

      render(
        <TestQueryProvider>
          <WishlistPage />
        </TestQueryProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText('이전')).toBeDisabled();
      });
    });

    it('다음 버튼 클릭 시 다음 페이지를 로드한다', async () => {
      mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse(MOCK_ITEMS, 0, 20, 50));

      const user = userEvent.setup();
      render(
        <TestQueryProvider>
          <WishlistPage />
        </TestQueryProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText('다음')).toBeEnabled();
      });

      mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse(MOCK_ITEMS, 1, 20, 50));
      await user.click(screen.getByText('다음'));

      await waitFor(() => {
        expect(screen.getByText('2 / 3')).toBeInTheDocument();
      });
    });

    it('페이지 크기를 변경할 수 있다', async () => {
      mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse(MOCK_ITEMS, 0, 20, 50));

      const user = userEvent.setup();
      render(
        <TestQueryProvider>
          <WishlistPage />
        </TestQueryProvider>,
      );

      await waitFor(() => {
        expect(screen.getByLabelText('페이지 크기:')).toBeInTheDocument();
      });

      mockGetMyWishlist.mockResolvedValueOnce(createPaginatedResponse(MOCK_ITEMS, 0, 10, 50));
      await user.selectOptions(screen.getByLabelText('페이지 크기:'), '10');

      await waitFor(() => {
        expect(mockGetMyWishlist).toHaveBeenCalledWith(0, 10);
      });
    });
  });
});
