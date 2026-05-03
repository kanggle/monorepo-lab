import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { MyReviewItem, MyReviewListResponse } from '@repo/types';
import { TestQueryProvider } from './test-utils';

const mockReplace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn() }),
  usePathname: () => '/my/reviews',
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

vi.mock('@/features/review/api/review-api', () => ({
  getMyReviews: vi.fn(),
  updateReview: vi.fn(),
  deleteReview: vi.fn(),
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
import { getMyReviews } from '@/features/review/api/review-api';
import MyReviewsPage from '@/app/(store)/my/reviews/page';

const mockUseAuth = vi.mocked(useAuth);
const mockGetMyReviews = vi.mocked(getMyReviews);

const MOCK_MY_REVIEWS: MyReviewItem[] = [
  {
    reviewId: 'review-1',
    productId: 'product-1',
    productName: '클래식 화이트 티셔츠',
    rating: 5,
    title: '아주 좋아요',
    content: '정말 만족스러운 상품입니다.',
    createdAt: '2026-04-01T10:00:00Z',
  },
  {
    reviewId: 'review-2',
    productId: 'product-2',
    productName: '캐주얼 후드 집업',
    rating: 4,
    title: '괜찮아요',
    content: '가격 대비 괜찮습니다.',
    createdAt: '2026-03-30T10:00:00Z',
  },
];

function createMyReviewListResponse(
  content: MyReviewItem[],
  page = 0,
  size = 20,
  totalElements = content.length,
): MyReviewListResponse {
  return { content, page, size, totalElements };
}

describe('MyReviewsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1' } as never,
      login: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(useRequireAuth).mockReturnValue({ isReady: true });
  });

  it('내 리뷰 목록을 렌더링한다', async () => {
    mockGetMyReviews.mockResolvedValueOnce(createMyReviewListResponse(MOCK_MY_REVIEWS));

    render(
      <TestQueryProvider>
        <MyReviewsPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });
    expect(screen.getByText('괜찮아요')).toBeInTheDocument();
    expect(screen.getByText('클래식 화이트 티셔츠')).toBeInTheDocument();
    expect(screen.getByText('캐주얼 후드 집업')).toBeInTheDocument();
  });

  it('리뷰가 없으면 빈 상태를 표시한다', async () => {
    mockGetMyReviews.mockResolvedValueOnce(createMyReviewListResponse([]));

    render(
      <TestQueryProvider>
        <MyReviewsPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
    expect(screen.getByText('작성한 리뷰가 없습니다.')).toBeInTheDocument();
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockGetMyReviews.mockRejectedValueOnce(new Error('fail'));

    render(
      <TestQueryProvider>
        <MyReviewsPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('리뷰를 불러오는데 실패했습니다.')).toBeInTheDocument();
  });

  it('에러 후 재시도 버튼을 클릭하면 다시 로드한다', async () => {
    mockGetMyReviews.mockRejectedValueOnce(new Error('fail'));

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <MyReviewsPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });

    mockGetMyReviews.mockResolvedValueOnce(createMyReviewListResponse(MOCK_MY_REVIEWS));
    await user.click(screen.getByText('재시도'));

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });
  });

  it('각 리뷰에 수정/삭제 버튼이 표시된다', async () => {
    mockGetMyReviews.mockResolvedValueOnce(createMyReviewListResponse(MOCK_MY_REVIEWS));

    render(
      <TestQueryProvider>
        <MyReviewsPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });

    const editButtons = screen.getAllByText('수정');
    const deleteButtons = screen.getAllByText('삭제');
    expect(editButtons).toHaveLength(2);
    expect(deleteButtons).toHaveLength(2);
  });

  it('수정 버튼 클릭 시 편집 폼이 표시된다', async () => {
    mockGetMyReviews.mockResolvedValueOnce(createMyReviewListResponse(MOCK_MY_REVIEWS));

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <MyReviewsPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });

    await user.click(screen.getAllByText('수정')[0]);

    expect(screen.getByLabelText('제목')).toHaveValue('아주 좋아요');
    expect(screen.getByLabelText('내용')).toHaveValue('정말 만족스러운 상품입니다.');
  });

  it('상품 이름 클릭 시 상품 페이지로 링크된다', async () => {
    mockGetMyReviews.mockResolvedValueOnce(createMyReviewListResponse(MOCK_MY_REVIEWS));

    render(
      <TestQueryProvider>
        <MyReviewsPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('클래식 화이트 티셔츠')).toBeInTheDocument();
    });

    const link = screen.getByText('클래식 화이트 티셔츠');
    expect(link.closest('a')).toHaveAttribute('href', '/products/product-1');
  });
});
