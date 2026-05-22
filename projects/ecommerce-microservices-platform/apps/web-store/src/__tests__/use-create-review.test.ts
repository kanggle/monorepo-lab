import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockCreateReview = vi.fn();

vi.mock('@/features/review/api/review-api', () => ({
  getProductReviews: vi.fn(),
  getProductReviewSummary: vi.fn(),
  createReview: (...args: unknown[]) => mockCreateReview(...args),
}));

import { useCreateReview } from '@/features/review/model/use-create-review';
import { reviewKeys } from '@/features/review/model/query-keys';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  return { queryClient, wrapper };
}

describe('useCreateReview', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('리뷰 생성 성공 시 review 쿼리를 무효화한다', async () => {
    const { queryClient, wrapper } = createWrapper();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    mockCreateReview.mockResolvedValueOnce({ reviewId: 'r-new' });

    const { result } = renderHook(() => useCreateReview(), { wrapper });

    const payload = { productId: 'prod-1', rating: 5, title: '좋아요', content: '좋아요' };
    result.current.mutate(payload);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockCreateReview.mock.calls[0][0]).toEqual(payload);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: reviewKeys.all });
  });

  it('리뷰 생성 실패 시 에러 상태를 반환한다', async () => {
    const { wrapper } = createWrapper();
    mockCreateReview.mockRejectedValueOnce(new Error('fail'));

    const { result } = renderHook(() => useCreateReview(), { wrapper });

    result.current.mutate({ productId: 'prod-1', rating: 5, title: 't', content: 'x' });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
