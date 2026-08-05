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

/**
 * The hook asks `/api/store-config` before it touches the SDK (TASK-BE-572), so every case has to
 * say which answer it gets. Defaulting to `demoPayment: false` keeps all the pre-existing cases
 * describing exactly what they described before: the real Toss path.
 */
function stubStoreConfig(demoPayment: boolean) {
  global.fetch = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ demoPayment }),
  }) as unknown as typeof fetch;
}

describe('useTossPayment', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLoadTossPayments.mockResolvedValue({ payment: mockPayment });
    stubStoreConfig(false);
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

  // --- demo-pg mode (TASK-BE-572 AC-4) ------------------------------------------------------

  describe('데모 결제 모드', () => {
    it('데모 모드에서는 Toss SDK 를 아예 로드하지 않는다 (더미 키 배너의 원인 제거)', async () => {
      stubStoreConfig(true);

      const { result } = renderHook(() => useTossPayment());

      await waitFor(() => expect(result.current.isReady).toBe(true));

      expect(mockLoadTossPayments).not.toHaveBeenCalled();
      expect(result.current.error).toBeNull();
    });

    it('requestPayment 는 Toss 가 보내는 것과 같은 success URL 로 이동한다', async () => {
      stubStoreConfig(true);
      const assign = vi.fn();
      Object.defineProperty(window, 'location', {
        configurable: true,
        value: { ...window.location, assign, origin: 'http://web.ecommerce.local' },
      });

      const { result } = renderHook(() => useTossPayment());
      await waitFor(() => expect(result.current.isReady).toBe(true));

      await act(async () => {
        await result.current.requestPayment({
          orderId: 'order-9',
          amount: 12900,
          orderName: '데모 주문',
        });
      });

      expect(mockRequestPayment).not.toHaveBeenCalled();
      expect(assign).toHaveBeenCalledWith(
        'http://web.ecommerce.local/checkout/payment/success'
          + '?paymentKey=demo_order-9&orderId=order-9&amount=12900',
      );
    });

    /**
     * Fail-safe direction matters here: an unreachable config endpoint must NOT be read as
     * "demo mode on". Getting this backwards would let a transient fetch failure turn a real
     * storefront into one where every payment succeeds without money.
     */
    it('설정 조회가 실패하면 데모가 아니라 실 SDK 경로로 떨어진다', async () => {
      global.fetch = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;

      const { result } = renderHook(() => useTossPayment());

      await waitFor(() => expect(result.current.isReady).toBe(true));
      expect(mockLoadTossPayments).toHaveBeenCalledWith('test_client_key');
    });
  });
});
