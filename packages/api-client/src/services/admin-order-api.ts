import type { ApiClient } from '../client';
import type {
  PaginatedResponse,
  OrderListParams,
  OrderStatus,
  AdminOrderSummary,
  AdminOrderDetail,
  AdminOrderStatusChangeResponse,
} from '@repo/types';

export function createAdminOrderApi(client: ApiClient) {
  return {
    getOrders: (params?: OrderListParams) =>
      client.get<PaginatedResponse<AdminOrderSummary>>('/api/admin/orders', { params }),

    getOrder: (orderId: string) =>
      client.get<AdminOrderDetail>(`/api/admin/orders/${orderId}`),

    changeStatus: (orderId: string, status: OrderStatus) =>
      client.post<AdminOrderStatusChangeResponse>(`/api/admin/orders/${orderId}/status`, { status }),
  };
}
