import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockReplace = vi.fn();
const mockPush = vi.fn();
const mockSearchParams = vi.fn();
const mockConfirmPayment = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: mockPush }),
  useSearchParams: () => ({
    get: (key: string) => mockSearchParams(key),
  }),
}));

vi.mock('@/entities/payment', () => ({
  confirmPayment: (...args: unknown[]) => mockConfirmPayment(...args),
}));

import { usePaymentConfirmation } from '@/features/checkout/model/use-payment-confirmation';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

function setupSearchParams(params: Record<string, string | null>) {
  mockSearchParams.mockImplementation((key: string) => params[key] ?? null);
}

describe('usePaymentConfirmation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('유효한 파라미터가 없으면 invalid 상태를 반환한다', () => {
    setupSearchParams({ paymentKey: null, orderId: null, amount: null });

    const { result } = renderHook(() => usePaymentConfirmation(), {
      wrapper: createWrapper(),
    });

    expect(result.current.status).toBe('invalid');
    expect(result.current.errorMessage).toBeNull();
  });

  it('amount가 0 이하이면 invalid 상태를 반환한다', () => {
    setupSearchParams({ paymentKey: 'pk_123', orderId: 'order-1', amount: '0' });

    const { result } = renderHook(() => usePaymentConfirmation(), {
      wrapper: createWrapper(),
    });

    expect(result.current.status).toBe('invalid');
  });

  it('amount가 숫자가 아니면 invalid 상태를 반환한다', () => {
    setupSearchParams({ paymentKey: 'pk_123', orderId: 'order-1', amount: 'abc' });

    const { result } = renderHook(() => usePaymentConfirmation(), {
      wrapper: createWrapper(),
    });

    expect(result.current.status).toBe('invalid');
  });

  it('유효한 파라미터로 결제 승인을 시도하고 성공 시 리다이렉트한다', async () => {
    setupSearchParams({ paymentKey: 'pk_123', orderId: 'order-1', amount: '50000' });
    mockConfirmPayment.mockResolvedValueOnce({ orderId: 'order-1', status: 'DONE' });

    renderHook(() => usePaymentConfirmation(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(mockConfirmPayment).toHaveBeenCalledWith({
        paymentKey: 'pk_123',
        orderId: 'order-1',
        amount: 50000,
      });
    });

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/checkout/complete?orderId=order-1');
    });
  });

  it('결제 승인 실패 시 error 상태와 에러 메시지를 반환한다', async () => {
    setupSearchParams({ paymentKey: 'pk_123', orderId: 'order-1', amount: '50000' });
    mockConfirmPayment.mockRejectedValueOnce(new Error('승인 실패'));

    const { result } = renderHook(() => usePaymentConfirmation(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.status).toBe('error'));

    expect(result.current.errorMessage).toBe('승인 실패');
  });

  it('goToCart 호출 시 /cart로 이동한다', () => {
    setupSearchParams({ paymentKey: null, orderId: null, amount: null });

    const { result } = renderHook(() => usePaymentConfirmation(), {
      wrapper: createWrapper(),
    });

    result.current.goToCart();

    expect(mockPush).toHaveBeenCalledWith('/cart');
  });

  it('retry 호출 시 orderId가 있으면 결제 페이지로 이동한다', () => {
    setupSearchParams({ paymentKey: 'pk_123', orderId: 'order-1', amount: '50000' });
    // prevent actual mutation from firing by making it reject (it will be called by useEffect)
    mockConfirmPayment.mockRejectedValue(new Error('fail'));

    const { result } = renderHook(() => usePaymentConfirmation(), {
      wrapper: createWrapper(),
    });

    result.current.retry();

    expect(mockPush).toHaveBeenCalledWith(
      '/checkout/payment?orderId=order-1&amount=50000&orderName=재시도',
    );
  });

  it('retry 호출 시 orderId가 없으면 /cart로 이동한다', () => {
    setupSearchParams({ paymentKey: null, orderId: null, amount: null });

    const { result } = renderHook(() => usePaymentConfirmation(), {
      wrapper: createWrapper(),
    });

    result.current.retry();

    expect(mockPush).toHaveBeenCalledWith('/cart');
  });
});
