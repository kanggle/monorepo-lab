import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockGetShippingByOrder = vi.fn();

vi.mock('@/features/order/api/shipping-api', () => ({
  getShippingByOrder: (...args: unknown[]) => mockGetShippingByOrder(...args),
}));

import { useShippingTracking } from '@/features/order/model/use-shipping-tracking';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, retryDelay: 0 } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useShippingTracking', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('배송 정보를 성공적으로 조회한다', async () => {
    const mockShipping = { orderId: 'o1', trackingNumber: 'T12345', status: 'IN_TRANSIT' };
    mockGetShippingByOrder.mockResolvedValueOnce(mockShipping);

    const { result } = renderHook(() => useShippingTracking('o1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.shipping).toEqual(mockShipping);
    expect(result.current.isNotFound).toBe(false);
    expect(result.current.error).toBe('');
  });

  it('orderId가 비어있으면 쿼리를 실행하지 않는다', () => {
    renderHook(() => useShippingTracking(''), { wrapper: createWrapper() });

    expect(mockGetShippingByOrder).not.toHaveBeenCalled();
  });

  it('SHIPPING_NOT_FOUND 에러 시 isNotFound가 true이고 error는 빈 문자열이다', async () => {
    mockGetShippingByOrder.mockRejectedValueOnce({
      code: 'SHIPPING_NOT_FOUND',
      message: 'not found',
    });

    const { result } = renderHook(() => useShippingTracking('o1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.isNotFound).toBe(true);
    expect(result.current.error).toBe('');
    expect(result.current.shipping).toBeNull();
  });

  it('ACCESS_DENIED 에러 시 전용 메시지를 반환한다', async () => {
    mockGetShippingByOrder.mockRejectedValueOnce({
      code: 'ACCESS_DENIED',
      message: 'denied',
    });

    const { result } = renderHook(() => useShippingTracking('o1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.error).not.toBe(''));
    expect(result.current.error).toBe('배송 정보에 접근할 수 없습니다.');
    expect(result.current.isNotFound).toBe(false);
  });

  it('일반 에러 시 기본 메시지를 반환한다', async () => {
    mockGetShippingByOrder.mockRejectedValue(new Error('network'));

    const { result } = renderHook(() => useShippingTracking('o1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.error).not.toBe(''), { timeout: 3000 });
    expect(result.current.error).toBe('배송 정보를 불러오는데 실패했습니다.');
  });
});
