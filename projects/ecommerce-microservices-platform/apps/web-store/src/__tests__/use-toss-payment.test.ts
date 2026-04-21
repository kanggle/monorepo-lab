import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';

const mockRequestPayment = vi.fn();
const mockPayment = vi.fn().mockReturnValue({ requestPayment: mockRequestPayment });

const mockLoadTossPayments = vi.hoisted(() => vi.fn());

// Set env before module import so TOSS_CLIENT_KEY captures a non-empty value
vi.hoisted(() => {
  process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY = 'test_client_key';
});

vi.mock('@tosspayments/tosspayments-sdk', () => ({
  loadTossPayments: mockLoadTossPayments,
}));

import { useTossPayment } from '@/features/checkout/model/use-toss-payment';

describe('useTossPayment', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLoadTossPayments.mockResolvedValue({ payment: mockPayment });
  });

  it('초기화 성공 시 isReady가 true가 된다', async () => {
    const { result } = renderHook(() => useTossPayment());

    await waitFor(() => expect(result.current.isReady).toBe(true));

    expect(result.current.error).toBeNull();
    expect(mockLoadTossPayments).toHaveBeenCalledWith('test_client_key');
    expect(mockPayment).toHaveBeenCalledWith({ customerKey: 'ANONYMOUS' });
  });

  it('loadTossPayments 실패 시 에러를 반환한다', async () => {
    mockLoadTossPayments.mockRejectedValueOnce(new Error('SDK load failed'));

    const { result } = renderHook(() => useTossPayment());

    await waitFor(() => expect(result.current.error).not.toBeNull());

    expect(result.current.isReady).toBe(false);
    expect(result.current.error).toContain('결제 모듈을 불러오는데 실패했습니다');
    expect(result.current.error).toContain('SDK load failed');
  });

  it('requestPayment 호출 시 올바른 파라미터로 결제를 요청한다', async () => {
    mockRequestPayment.mockResolvedValueOnce(undefined);

    const { result } = renderHook(() => useTossPayment());

    await waitFor(() => expect(result.current.isReady).toBe(true));

    await act(async () => {
      await result.current.requestPayment({
        orderId: 'order-1',
        amount: 50000,
        orderName: '테스트 주문',
      });
    });

    expect(mockRequestPayment).toHaveBeenCalledWith({
      method: 'CARD',
      amount: { currency: 'KRW', value: 50000 },
      orderId: 'order-1',
      orderName: '테스트 주문',
      successUrl: `${window.location.origin}/checkout/payment/success`,
      failUrl: `${window.location.origin}/checkout/payment/fail`,
    });
  });

  it('결제 모듈이 준비되지 않은 상태에서 requestPayment 호출 시 에러를 던진다', async () => {
    // Make loadTossPayments hang so isReady stays false
    mockLoadTossPayments.mockReturnValue(new Promise(() => {}));

    const { result } = renderHook(() => useTossPayment());

    expect(result.current.isReady).toBe(false);

    await expect(
      result.current.requestPayment({
        orderId: 'order-1',
        amount: 50000,
        orderName: '테스트 주문',
      }),
    ).rejects.toThrow('결제 모듈이 준비되지 않았습니다.');
  });
});
