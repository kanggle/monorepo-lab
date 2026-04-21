import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUpdateShippingStatus } from '@/features/shipping-management/hooks/use-update-shipping-status';
import type { ReactNode } from 'react';
import React from 'react';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

const mockUpdateShippingStatus = vi.fn();

vi.mock('@/features/shipping-management/api/shipping-api', () => ({
  updateShippingStatus: (...args: unknown[]) => mockUpdateShippingStatus(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useUpdateShippingStatus', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    mockUpdateShippingStatus.mockResolvedValue({
      shippingId: 's1',
      status: 'SHIPPED',
      updatedAt: '2026-04-01T12:00:00Z',
    });
  });

  it('배송 상태를 성공적으로 변경한다', async () => {
    const { result } = renderHook(() => useUpdateShippingStatus(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({
        shippingId: 's1',
        data: { status: 'SHIPPED', trackingNumber: '123', carrier: 'CJ' },
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockUpdateShippingStatus).toHaveBeenCalledWith('s1', {
      status: 'SHIPPED',
      trackingNumber: '123',
      carrier: 'CJ',
    });
  });

  it('API 실패 시 에러 알림을 표시한다', async () => {
    mockUpdateShippingStatus.mockRejectedValue(new Error('Server error'));

    const { result } = renderHook(() => useUpdateShippingStatus(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({
        shippingId: 's1',
        data: { status: 'SHIPPED', trackingNumber: '123', carrier: 'CJ' },
      });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(window.alert).toHaveBeenCalled();
  });
});
