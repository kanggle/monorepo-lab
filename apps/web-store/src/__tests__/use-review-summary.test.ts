import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockGetProductReviewSummary = vi.fn();

vi.mock('@/features/review/api/review-api', () => ({
  getProductReviews: vi.fn(),
  getProductReviewSummary: (...args: unknown[]) => mockGetProductReviewSummary(...args),
  createReview: vi.fn(),
}));

import { useReviewSummary } from '@/features/review/model/use-review-summary';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useReviewSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('리뷰 요약 정보를 성공적으로 조회한다', async () => {
    const mockSummary = { productId: 'prod-1', averageRating: 4.5, reviewCount: 120 };
    mockGetProductReviewSummary.mockResolvedValueOnce(mockSummary);

    const { result } = renderHook(() => useReviewSummary('prod-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockSummary);
    expect(mockGetProductReviewSummary).toHaveBeenCalledWith('prod-1');
  });

  it('API 실패 시 에러 상태를 반환한다', async () => {
    mockGetProductReviewSummary.mockRejectedValueOnce(new Error('fail'));

    const { result } = renderHook(() => useReviewSummary('prod-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
