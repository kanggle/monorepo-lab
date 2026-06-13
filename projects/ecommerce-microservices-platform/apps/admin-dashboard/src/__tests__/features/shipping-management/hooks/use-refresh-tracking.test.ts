import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useRefreshTracking } from '@/features/shipping-management/hooks/use-refresh-tracking';
import type { ReactNode } from 'react';
import React from 'react';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

const mockRefreshTracking = vi.fn();

vi.mock('@/features/shipping-management/api/shipping-api', () => ({
  refreshTracking: (...args: unknown[]) => mockRefreshTracking(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useRefreshTracking', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    mockRefreshTracking.mockResolvedValue({
      shippingId: 's1',
      status: 'DELIVERED',
      updatedAt: '2026-06-05T14:47:30Z',
    });
  });

  it('택배사 동기화를 성공적으로 호출한다 (shippingId 만 전달)', async () => {
    const { result } = renderHook(() => useRefreshTracking(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ shippingId: 's1' });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockRefreshTracking).toHaveBeenCalledWith('s1');
  });

  it('API 실패 시 에러 알림을 표시한다', async () => {
    mockRefreshTracking.mockRejectedValue(new Error('Server error'));

    const { result } = renderHook(() => useRefreshTracking(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ shippingId: 's1' });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(window.alert).toHaveBeenCalled();
  });
});
