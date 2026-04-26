import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockGetOrder = vi.fn();
const mockCancelOrder = vi.fn();
const mockGetPayment = vi.fn();

vi.mock('@/entities/order', () => ({
  getOrder: (...args: unknown[]) => mockGetOrder(...args),
  cancelOrder: (...args: unknown[]) => mockCancelOrder(...args),
}));

vi.mock('@/entities/payment', () => ({
  getPayment: (...args: unknown[]) => mockGetPayment(...args),
}));

vi.mock('@repo/types/guards', () => ({
  isApiError: (err: unknown) =>
    typeof err === 'object' && err !== null && 'code' in err && 'message' in err,
}));

import { useOrderDetail } from '@/features/order/model/use-order-detail';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const Wrapper = ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  return Wrapper;
}

const MOCK_ORDER = {
  orderId: 'order-1',
  status: 'CONFIRMED',
  totalPrice: 30000,
  items: [{ productId: 'p1', variantId: 'v1', productName: '상품A', optionName: '옵션1', quantity: 1, unitPrice: 30000 }],
  shippingAddress: { recipient: '홍길동', phone: '010-1234-5678', zipCode: '12345', address1: '서울시', address2: '' },
  createdAt: '2026-04-01T00:00:00Z',
  updatedAt: '2026-04-01T00:00:00Z',
};

const MOCK_PAYMENT = {
  paymentId: 'pay-1',
  orderId: 'order-1',
  amount: 30000,
  status: 'COMPLETED',
  method: 'CARD',
};

describe('useOrderDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('주문 상세를 성공적으로 조회한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    mockGetPayment.mockResolvedValueOnce(MOCK_PAYMENT);

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.order).toEqual(MOCK_ORDER));

    expect(result.current.error).toBe('');
    expect(result.current.isLoading).toBe(false);
  });

  it('결제 정보도 함께 조회한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    mockGetPayment.mockResolvedValueOnce(MOCK_PAYMENT);

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.payment).toEqual(MOCK_PAYMENT));
  });

  it('orderId가 빈 문자열이면 쿼리를 실행하지 않는다', () => {
    const { result } = renderHook(() => useOrderDetail(''), { wrapper: createWrapper() });

    expect(mockGetOrder).not.toHaveBeenCalled();
    expect(result.current.order).toBeNull();
  });

  it('주문 조회 실패 시 일반 에러 메시지를 반환한다', async () => {
    mockGetOrder.mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.error).toBe('주문 정보를 불러오는데 실패했습니다.'));
  });

  it('ORDER_NOT_FOUND 에러 시 전용 메시지를 반환한다', async () => {
    mockGetOrder.mockRejectedValueOnce({ code: 'ORDER_NOT_FOUND', message: 'Not found' });

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.error).toBe('주문을 찾을 수 없습니다.'));
  });

  it('결제 조회 실패 시 결제 에러 메시지를 반환한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    mockGetPayment.mockRejectedValueOnce(new Error('Payment error'));

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.paymentError).toBe('결제 정보를 불러오는데 실패했습니다.'));
  });

  it('PAYMENT_NOT_FOUND 에러 시 결제 에러 메시지를 빈 문자열로 반환한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    mockGetPayment.mockRejectedValueOnce({ code: 'PAYMENT_NOT_FOUND', message: 'Not found' });

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.order).toEqual(MOCK_ORDER));
    await waitFor(() => expect(result.current.paymentError).toBe(''));
  });

  it('handleCancel 호출 시 주문을 취소하고 상태를 CANCELLED로 업데이트한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    mockGetPayment.mockResolvedValueOnce(MOCK_PAYMENT);
    mockCancelOrder.mockResolvedValueOnce({ orderId: 'order-1', status: 'CANCELLED' });

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.order).toEqual(MOCK_ORDER));

    await act(async () => {
      await result.current.handleCancel();
    });

    expect(mockCancelOrder).toHaveBeenCalledWith('order-1');
    expect(result.current.order?.status).toBe('CANCELLED');
  });

  it('handleCancel 실패 시 에러 메시지를 설정한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    mockGetPayment.mockResolvedValueOnce(MOCK_PAYMENT);
    mockCancelOrder.mockRejectedValueOnce({ code: 'CANCEL_FAILED', message: '취소 불가' });

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.order).toEqual(MOCK_ORDER));

    await act(async () => {
      await result.current.handleCancel();
    });

    expect(result.current.error).toBe('취소 불가');
  });

  it('handleCancel 중 isCancelling 상태가 true가 된다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    mockGetPayment.mockResolvedValueOnce(MOCK_PAYMENT);

    let resolveCancel: (value: unknown) => void;
    mockCancelOrder.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveCancel = resolve;
      }),
    );

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.order).toEqual(MOCK_ORDER));

    act(() => {
      result.current.handleCancel();
    });

    expect(result.current.isCancelling).toBe(true);

    await act(async () => {
      resolveCancel!({ orderId: 'order-1', status: 'CANCELLED' });
    });

    expect(result.current.isCancelling).toBe(false);
  });

  it('order가 없으면 handleCancel이 아무 동작도 하지 않는다', async () => {
    mockGetOrder.mockReturnValue(new Promise(() => {}));

    const { result } = renderHook(() => useOrderDetail('order-1'), { wrapper: createWrapper() });

    await act(async () => {
      await result.current.handleCancel();
    });

    expect(mockCancelOrder).not.toHaveBeenCalled();
  });
});
