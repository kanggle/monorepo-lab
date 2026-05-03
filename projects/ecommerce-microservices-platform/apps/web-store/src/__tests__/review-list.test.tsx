import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReviewItem, ReviewListResponse, ReviewSummary } from '@repo/types';
import { TestQueryProvider } from './test-utils';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock('@/shared/lib/auth-context', () => ({
  useAuth: vi.fn(),
}));

vi.mock('@/features/review/api/review-api', () => ({
  getProductReviews: vi.fn(),
  getProductReviewSummary: vi.fn(),
  createReview: vi.fn(),
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

import { useAuth } from '@/shared/lib/auth-context';
import {
  getProductReviews,
  getProductReviewSummary,
} from '@/features/review/api/review-api';
import { ReviewList } from '@/features/review/ui/ReviewList';

const mockUseAuth = vi.mocked(useAuth);
const mockGetProductReviews = vi.mocked(getProductReviews);
const mockGetProductReviewSummary = vi.mocked(getProductReviewSummary);

const MOCK_REVIEWS: ReviewItem[] = [
  {
    reviewId: 'review-1',
    userId: 'user-1',
    rating: 5,
    title: '아주 좋아요',
    content: '정말 만족스러운 상품입니다.',
    createdAt: '2026-04-01T10:00:00Z',
    updatedAt: '2026-04-01T10:00:00Z',
  },
  {
    reviewId: 'review-2',
    userId: 'user-2',
    rating: 3,
    title: '보통이에요',
    content: '가격 대비 평범합니다.',
    createdAt: '2026-03-30T10:00:00Z',
    updatedAt: '2026-03-30T10:00:00Z',
  },
];

function createReviewListResponse(
  content: ReviewItem[],
  page = 0,
  size = 10,
  totalElements = content.length,
): ReviewListResponse {
  return {
    content,
    page,
    size,
    totalElements,
    averageRating: 4.0,
    totalReviews: totalElements,
  };
}

const MOCK_SUMMARY: ReviewSummary = {
  productId: 'product-1',
  averageRating: 4.0,
  totalReviews: 2,
  ratingDistribution: { '1': 0, '2': 0, '3': 1, '4': 0, '5': 1 },
};

describe('ReviewList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockGetProductReviewSummary.mockResolvedValue(MOCK_SUMMARY);
  });

  it('로딩 중일 때 스켈레톤이 표시된다', () => {
    mockGetProductReviews.mockReturnValue(new Promise(() => {}));

    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    expect(screen.getByText('상품 리뷰')).toBeInTheDocument();
  });

  it('리뷰 목록을 렌더링한다', async () => {
    mockGetProductReviews.mockResolvedValueOnce(createReviewListResponse(MOCK_REVIEWS));

    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });
    expect(screen.getByText('보통이에요')).toBeInTheDocument();
    expect(screen.getByText('정말 만족스러운 상품입니다.')).toBeInTheDocument();
  });

  it('리뷰가 없으면 빈 상태를 표시한다', async () => {
    mockGetProductReviews.mockResolvedValueOnce(createReviewListResponse([]));

    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
    expect(screen.getByText('아직 리뷰가 없습니다.')).toBeInTheDocument();
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockGetProductReviews.mockRejectedValueOnce(new Error('fail'));

    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('리뷰를 불러오는데 실패했습니다.')).toBeInTheDocument();
  });

  it('에러 후 재시도 버튼을 클릭하면 다시 로드한다', async () => {
    mockGetProductReviews.mockRejectedValueOnce(new Error('fail'));

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });

    mockGetProductReviews.mockResolvedValueOnce(createReviewListResponse(MOCK_REVIEWS));
    await user.click(screen.getByText('재시도'));

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });
  });

  it('비로그인 사용자에게 리뷰 작성 버튼을 표시하지 않는다', async () => {
    mockGetProductReviews.mockResolvedValueOnce(createReviewListResponse(MOCK_REVIEWS));

    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });
    expect(screen.queryByText('리뷰 작성')).not.toBeInTheDocument();
  });

  it('로그인한 사용자에게 리뷰 작성 버튼을 표시한다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1' } as never,
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockGetProductReviews.mockResolvedValueOnce(createReviewListResponse(MOCK_REVIEWS));

    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: '리뷰 작성' })).toBeInTheDocument();
  });

  it('리뷰 작성 버튼 클릭 시 폼이 표시된다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1' } as never,
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockGetProductReviews.mockResolvedValueOnce(createReviewListResponse(MOCK_REVIEWS));

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '리뷰 작성' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '리뷰 작성' }));

    expect(screen.getByLabelText('제목')).toBeInTheDocument();
    expect(screen.getByLabelText('내용')).toBeInTheDocument();
  });

  it('본인 리뷰에 수정/삭제 버튼이 표시된다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-1' } as never,
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockGetProductReviews.mockResolvedValueOnce(createReviewListResponse(MOCK_REVIEWS));

    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });

    // user-1's review should have edit/delete buttons
    expect(screen.getByText('수정')).toBeInTheDocument();
    expect(screen.getByText('삭제')).toBeInTheDocument();
  });

  it('다른 사용자 리뷰에 수정/삭제 버튼이 표시되지 않는다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { userId: 'user-3' } as never,
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockGetProductReviews.mockResolvedValueOnce(createReviewListResponse(MOCK_REVIEWS));

    render(
      <TestQueryProvider>
        <ReviewList productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('아주 좋아요')).toBeInTheDocument();
    });

    expect(screen.queryByText('수정')).not.toBeInTheDocument();
    expect(screen.queryByText('삭제')).not.toBeInTheDocument();
  });

  describe('페이지네이션', () => {
    it('페이지네이션 컨트롤을 표시한다', async () => {
      mockGetProductReviews.mockResolvedValueOnce(
        createReviewListResponse(MOCK_REVIEWS, 0, 10, 25),
      );

      render(
        <TestQueryProvider>
          <ReviewList productId="product-1" />
        </TestQueryProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText('1 / 3')).toBeInTheDocument();
      });
    });

    it('첫 페이지에서 이전 버튼이 비활성화된다', async () => {
      mockGetProductReviews.mockResolvedValueOnce(
        createReviewListResponse(MOCK_REVIEWS, 0, 10, 25),
      );

      render(
        <TestQueryProvider>
          <ReviewList productId="product-1" />
        </TestQueryProvider>,
      );

      await waitFor(() => {
        expect(screen.getByLabelText('이전 페이지')).toBeDisabled();
      });
    });

    it('다음 버튼 클릭 시 다음 페이지를 로드한다', async () => {
      mockGetProductReviews.mockResolvedValueOnce(
        createReviewListResponse(MOCK_REVIEWS, 0, 10, 25),
      );

      const user = userEvent.setup();
      render(
        <TestQueryProvider>
          <ReviewList productId="product-1" />
        </TestQueryProvider>,
      );

      await waitFor(() => {
        expect(screen.getByLabelText('다음 페이지')).toBeEnabled();
      });

      mockGetProductReviews.mockResolvedValueOnce(
        createReviewListResponse(MOCK_REVIEWS, 1, 10, 25),
      );
      await user.click(screen.getByLabelText('다음 페이지'));

      await waitFor(() => {
        expect(screen.getByText('2 / 3')).toBeInTheDocument();
      });
    });
  });
});
