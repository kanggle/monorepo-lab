import type { ApiClient } from '../client';
import type {
  PaginatedResponse,
  OrderListParams,
  OrderSummary,
  OrderDetail,
  PlaceOrderRequest,
  PlaceOrderResponse,
  CancelOrderResponse,
} from '@repo/types';

export function createOrderApi(client: ApiClient) {
  return {
    placeOrder: (data: PlaceOrderRequest) =>
      client.post<PlaceOrderResponse>('/api/orders', data),

    getOrders: (params?: OrderListParams) =>
      client.get<PaginatedResponse<OrderSummary>>('/api/orders', { params }),

    getOrder: (orderId: string) =>
      client.get<OrderDetail>(`/api/orders/${orderId}`),

    cancelOrder: (orderId: string) =>
      client.post<CancelOrderResponse>(`/api/orders/${orderId}/cancel`, {}),
  };
}
