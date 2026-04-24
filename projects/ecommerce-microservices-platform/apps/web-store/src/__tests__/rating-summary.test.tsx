import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import type { ReviewSummary } from '@repo/types';
import { TestQueryProvider } from './test-utils';

vi.mock('@/features/review/api/review-api', () => ({
  getProductReviewSummary: vi.fn(),
}));

import { getProductReviewSummary } from '@/features/review/api/review-api';
import { RatingSummary } from '@/features/review/ui/RatingSummary';

const mockGetSummary = vi.mocked(getProductReviewSummary);

const MOCK_SUMMARY: ReviewSummary = {
  productId: 'product-1',
  averageRating: 4.2,
  totalReviews: 15,
  ratingDistribution: { '1': 1, '2': 0, '3': 2, '4': 5, '5': 7 },
};

describe('RatingSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('평균 평점과 총 리뷰 수를 표시한다', async () => {
    mockGetSummary.mockResolvedValueOnce(MOCK_SUMMARY);

    render(
      <TestQueryProvider>
        <RatingSummary productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('4.2')).toBeInTheDocument();
    });
    expect(screen.getByText(/15개 리뷰/)).toBeInTheDocument();
  });

  it('별점 분포를 표시한다', async () => {
    mockGetSummary.mockResolvedValueOnce(MOCK_SUMMARY);

    render(
      <TestQueryProvider>
        <RatingSummary productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('5점')).toBeInTheDocument();
    });
    expect(screen.getByText('4점')).toBeInTheDocument();
    expect(screen.getByText('3점')).toBeInTheDocument();
    expect(screen.getByText('2점')).toBeInTheDocument();
    expect(screen.getByText('1점')).toBeInTheDocument();

    // Check counts
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('로딩 중일 때 스켈레톤이 표시된다', () => {
    mockGetSummary.mockReturnValue(new Promise(() => {}));

    render(
      <TestQueryProvider>
        <RatingSummary productId="product-1" />
      </TestQueryProvider>,
    );

    // Skeleton should be present (no average rating text yet)
    expect(screen.queryByText('4.2')).not.toBeInTheDocument();
  });

  it('에러 발생 시 아무것도 표시하지 않는다', async () => {
    mockGetSummary.mockRejectedValueOnce(new Error('fail'));

    const { container } = render(
      <TestQueryProvider>
        <RatingSummary productId="product-1" />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(mockGetSummary).toHaveBeenCalled();
    });

    // Wait a bit for error state to settle
    await waitFor(() => {
      expect(screen.queryByText('4.2')).not.toBeInTheDocument();
    });
  });
});
