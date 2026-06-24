import type { AxiosRequestConfig } from 'axios';
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
    // `config` lets the caller attach per-request options — notably the
    // `Idempotency-Key` header that makes order placement idempotent (TASK-BE-430).
    placeOrder: (data: PlaceOrderRequest, config?: AxiosRequestConfig) =>
      client.post<PlaceOrderResponse>('/api/orders', data, config),

    getOrders: (params?: OrderListParams) =>
      client.get<PaginatedResponse<OrderSummary>>('/api/orders', { params }),

    getOrder: (orderId: string) =>
      client.get<OrderDetail>(`/api/orders/${orderId}`),

    cancelOrder: (orderId: string) =>
      client.post<CancelOrderResponse>(`/api/orders/${orderId}/cancel`, {}),
  };
}
