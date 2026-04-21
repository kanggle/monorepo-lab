import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockGetProductReviews = vi.fn();

vi.mock('@/features/review/api/review-api', () => ({
  getProductReviews: (...args: unknown[]) => mockGetProductReviews(...args),
  getProductReviewSummary: vi.fn(),
  createReview: vi.fn(),
}));

import { useProductReviews } from '@/features/review/model/use-product-reviews';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useProductReviews', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('상품 리뷰 목록을 성공적으로 조회한다', async () => {
    const mockData = {
      content: [{ reviewId: 'r1', rating: 5, content: '좋아요' }],
      page: 0,
      size: 10,
      totalElements: 1,
    };
    mockGetProductReviews.mockResolvedValueOnce(mockData);

    const { result } = renderHook(() => useProductReviews('prod-1', 0, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData);
    expect(mockGetProductReviews).toHaveBeenCalledWith('prod-1', { page: 0, size: 10 });
  });

  it('API 실패 시 에러 상태를 반환한다', async () => {
    mockGetProductReviews.mockRejectedValueOnce(new Error('fail'));

    const { result } = renderHook(() => useProductReviews('prod-1', 0, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
