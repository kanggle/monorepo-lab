import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockGetMyCoupons = vi.fn();

vi.mock('@/features/coupon/api/coupon-api', () => ({
  getMyCoupons: (...args: unknown[]) => mockGetMyCoupons(...args),
}));

import { useCoupons } from '@/features/coupon/model/use-coupons';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const Wrapper = ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  return Wrapper;
}

describe('useCoupons', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('내 쿠폰 목록을 성공적으로 조회한다', async () => {
    const mockData = {
      content: [
        { couponId: 'c1', status: 'ISSUED', discountType: 'FIXED', discountValue: 1000 },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
    };
    mockGetMyCoupons.mockResolvedValueOnce(mockData);

    const { result } = renderHook(() => useCoupons(0, 10, 'ISSUED'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData);
    expect(mockGetMyCoupons).toHaveBeenCalledWith({ page: 0, size: 10, status: 'ISSUED' });
  });

  it('status 없이 호출할 수 있다', async () => {
    mockGetMyCoupons.mockResolvedValueOnce({ content: [], page: 0, size: 10, totalElements: 0 });

    const { result } = renderHook(() => useCoupons(1, 5), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockGetMyCoupons).toHaveBeenCalledWith({ page: 1, size: 5, status: undefined });
  });

  it('API 호출 실패 시 에러 상태를 반환한다', async () => {
    mockGetMyCoupons.mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => useCoupons(0, 10), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
