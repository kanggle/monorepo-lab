import { describe, it, expect, vi, beforeEach } from 'vitest';

const { mockGetPayment, mockConfirmPayment } = vi.hoisted(() => ({
  mockGetPayment: vi.fn(),
  mockConfirmPayment: vi.fn(),
}));

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

vi.mock('@repo/api-client', () => ({
  createPaymentApi: vi.fn(() => ({
    getPayment: mockGetPayment,
    confirmPayment: mockConfirmPayment,
  })),
}));

import { getPayment, confirmPayment } from '@/entities/payment/api/payment-api';

describe('payment-api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getPayment', () => {
    it('주문 ID로 결제 정보를 조회한다', async () => {
      const response = {
        paymentId: 'pay-1',
        orderId: 'order-1',
        userId: 'user-1',
        amount: 30000,
        status: 'COMPLETED',
        createdAt: '2026-03-23T10:00:00Z',
        paidAt: '2026-03-23T10:01:00Z',
        refundedAt: null,
      };
      mockGetPayment.mockResolvedValueOnce(response);

      const result = await getPayment('order-1');

      expect(mockGetPayment).toHaveBeenCalledWith('order-1');
      expect(result).toEqual(response);
    });

    it('결제 정보가 없으면 에러를 전파한다', async () => {
      const error = { code: 'PAYMENT_NOT_FOUND', message: 'Payment not found' };
      mockGetPayment.mockRejectedValueOnce(error);

      await expect(getPayment('order-1')).rejects.toEqual(error);
    });

    it('권한 없는 접근 시 에러를 전파한다', async () => {
      const error = { code: 'UNAUTHORIZED', message: 'Unauthorized' };
      mockGetPayment.mockRejectedValueOnce(error);

      await expect(getPayment('order-1')).rejects.toEqual(error);
    });
  });

  describe('confirmPayment', () => {
    const confirmRequest = {
      paymentKey: 'pk_test_123',
      orderId: 'order-1',
      amount: 30000,
    };

    it('결제 승인 요청에 성공한다', async () => {
      const response = {
        paymentId: 'pay-1',
        orderId: 'order-1',
        status: 'COMPLETED',
      };
      mockConfirmPayment.mockResolvedValueOnce(response);

      const result = await confirmPayment(confirmRequest);

      expect(mockConfirmPayment).toHaveBeenCalledWith(confirmRequest);
      expect(result).toEqual(response);
    });

    it('결제 승인 실패 시 에러를 전파한다', async () => {
      const error = { code: 'PAYMENT_FAILED', message: 'Payment failed' };
      mockConfirmPayment.mockRejectedValueOnce(error);

      await expect(confirmPayment(confirmRequest)).rejects.toEqual(error);
    });

    it('중복 결제 시 409 에러를 전파한다', async () => {
      const error = { code: 'PAYMENT_ALREADY_COMPLETED', message: 'Payment already processed', status: 409 };
      mockConfirmPayment.mockRejectedValueOnce(error);

      await expect(confirmPayment(confirmRequest)).rejects.toEqual(error);
    });
  });
});
