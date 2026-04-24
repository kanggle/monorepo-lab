import { describe, it, expect, vi, beforeEach } from 'vitest';

const { mockPlaceOrder, mockGetOrders, mockGetOrder, mockCancelOrder } = vi.hoisted(() => ({
  mockPlaceOrder: vi.fn(),
  mockGetOrders: vi.fn(),
  mockGetOrder: vi.fn(),
  mockCancelOrder: vi.fn(),
}));

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

vi.mock('@repo/api-client', () => ({
  createOrderApi: vi.fn(() => ({
    placeOrder: mockPlaceOrder,
    getOrders: mockGetOrders,
    getOrder: mockGetOrder,
    cancelOrder: mockCancelOrder,
  })),
}));

import { placeOrder, getOrders, getOrder, cancelOrder } from '@/entities/order/api/order-api';

describe('order-api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('placeOrder', () => {
    it('주문 데이터를 전달하여 주문을 생성한다', async () => {
      const request = {
        items: [{ productId: 'p1', variantId: 'v1', quantity: 2 }],
        shippingAddress: {
          recipient: '홍길동',
          phone: '010-1234-5678',
          zipCode: '12345',
          address1: '서울시',
          address2: '',
        },
      };
      const response = { orderId: 'order-1' };
      mockPlaceOrder.mockResolvedValueOnce(response);

      const result = await placeOrder(request);

      expect(mockPlaceOrder).toHaveBeenCalledWith(request);
      expect(result).toEqual(response);
    });

    it('API 에러를 그대로 전파한다', async () => {
      const error = { code: 'INVALID_ORDER_REQUEST', message: 'Invalid' };
      mockPlaceOrder.mockRejectedValueOnce(error);

      await expect(placeOrder({
        items: [],
        shippingAddress: { recipient: '', phone: '', zipCode: '', address1: '', address2: '' },
      })).rejects.toEqual(error);
    });
  });

  describe('getOrders', () => {
    it('기본 page=0, size=20으로 주문 목록을 조회한다', async () => {
      const response = { content: [], page: 0, size: 20, totalElements: 0 };
      mockGetOrders.mockResolvedValueOnce(response);

      const result = await getOrders();

      expect(mockGetOrders).toHaveBeenCalledWith({ page: 0, size: 20 });
      expect(result).toEqual(response);
    });

    it('지정된 page와 size로 주문 목록을 조회한다', async () => {
      const response = { content: [], page: 2, size: 10, totalElements: 30 };
      mockGetOrders.mockResolvedValueOnce(response);

      const result = await getOrders(2, 10);

      expect(mockGetOrders).toHaveBeenCalledWith({ page: 2, size: 10 });
      expect(result).toEqual(response);
    });
  });

  describe('getOrder', () => {
    it('주문 ID로 주문 상세를 조회한다', async () => {
      const response = {
        orderId: 'order-1',
        status: 'PENDING',
        totalPrice: 30000,
        items: [],
        shippingAddress: { recipient: '', phone: '', zipCode: '', address1: '', address2: '' },
        createdAt: '2026-03-23T10:00:00Z',
        updatedAt: '2026-03-23T10:00:00Z',
      };
      mockGetOrder.mockResolvedValueOnce(response);

      const result = await getOrder('order-1');

      expect(mockGetOrder).toHaveBeenCalledWith('order-1');
      expect(result).toEqual(response);
    });

    it('존재하지 않는 주문 시 에러를 전파한다', async () => {
      const error = { code: 'ORDER_NOT_FOUND', message: 'Order not found' };
      mockGetOrder.mockRejectedValueOnce(error);

      await expect(getOrder('nonexistent')).rejects.toEqual(error);
    });
  });

  describe('cancelOrder', () => {
    it('주문을 취소한다', async () => {
      const response = { orderId: 'order-1', status: 'CANCELLED' };
      mockCancelOrder.mockResolvedValueOnce(response);

      const result = await cancelOrder('order-1');

      expect(mockCancelOrder).toHaveBeenCalledWith('order-1');
      expect(result).toEqual(response);
    });

    it('취소 불가 상태 시 에러를 전파한다', async () => {
      const error = { code: 'ORDER_CANNOT_BE_CANCELLED', message: 'Cannot cancel' };
      mockCancelOrder.mockRejectedValueOnce(error);

      await expect(cancelOrder('order-1')).rejects.toEqual(error);
    });
  });
});
