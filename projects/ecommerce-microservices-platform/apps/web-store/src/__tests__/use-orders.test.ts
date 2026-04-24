import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockGetOrders = vi.fn();

vi.mock('@/entities/order', () => ({
  getOrders: (...args: unknown[]) => mockGetOrders(...args),
}));

import { useOrders } from '@/features/order/model/use-orders';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useOrders', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('주문 목록을 성공적으로 조회한다', async () => {
    const mockData = {
      content: [
        { orderId: 'order-1', status: 'CONFIRMED', totalPrice: 30000, itemCount: 1, firstItemName: '상품A', createdAt: '2026-04-01T00:00:00Z' },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
    };
    mockGetOrders.mockResolvedValueOnce(mockData);

    const { result } = renderHook(() => useOrders(0, 10), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData);
    expect(mockGetOrders).toHaveBeenCalledWith(0, 10);
  });

  it('페이지와 사이즈 파라미터를 전달한다', async () => {
    mockGetOrders.mockResolvedValueOnce({ content: [], page: 2, size: 5, totalElements: 0 });

    const { result } = renderHook(() => useOrders(2, 5), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockGetOrders).toHaveBeenCalledWith(2, 5);
  });

  it('API 호출 실패 시 에러 상태를 반환한다', async () => {
    mockGetOrders.mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => useOrders(0, 10), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeInstanceOf(Error);
  });

  it('로딩 상태를 올바르게 반환한다', () => {
    mockGetOrders.mockReturnValue(new Promise(() => {}));

    const { result } = renderHook(() => useOrders(0, 10), { wrapper: createWrapper() });

    expect(result.current.isLoading).toBe(true);
  });
});
