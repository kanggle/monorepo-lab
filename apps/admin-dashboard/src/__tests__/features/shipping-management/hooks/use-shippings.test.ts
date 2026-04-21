import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useShippings } from '@/features/shipping-management/hooks/use-shippings';
import type { ReactNode } from 'react';
import React from 'react';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

const mockGetShippings = vi.fn();

vi.mock('@/features/shipping-management/api/shipping-api', () => ({
  getShippings: (...args: unknown[]) => mockGetShippings(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

const mockResponse = {
  content: [
    {
      shippingId: 's1',
      orderId: 'o1',
      status: 'PREPARING',
      trackingNumber: null,
      carrier: null,
      createdAt: '2026-04-01T10:00:00Z',
      updatedAt: '2026-04-01T10:00:00Z',
    },
  ],
  totalElements: 1,
  page: 0,
  size: 20,
};

describe('useShippings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetShippings.mockResolvedValue(mockResponse);
  });

  it('배송 목록을 성공적으로 조회한다', async () => {
    const { result } = renderHook(() => useShippings(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toHaveLength(1);
    expect(result.current.data?.content[0].shippingId).toBe('s1');
  });

  it('페이지네이션 정보를 반환한다', async () => {
    const { result } = renderHook(() => useShippings(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.pagination.page).toBe(0);
    expect(result.current.pagination.totalPages).toBe(1);
  });

  it('필터 정보를 반환한다', async () => {
    const { result } = renderHook(() => useShippings(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.filters.status).toBeUndefined();
    expect(result.current.filters.setFilter).toBeDefined();
  });

  it('API 실패 시 에러 상태를 반환한다', async () => {
    mockGetShippings.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useShippings(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
