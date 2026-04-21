import type { ApiClient } from '../client';
import type {
  PaginatedResponse,
  ShippingListParams,
  ShippingSummary,
  UpdateShippingStatusRequest,
  UpdateShippingStatusResponse,
} from '@repo/types';

export function createAdminShippingApi(client: ApiClient) {
  return {
    getShippings: (params?: ShippingListParams) =>
      client.get<PaginatedResponse<ShippingSummary>>('/api/shippings', { params }),

    updateStatus: (shippingId: string, data: UpdateShippingStatusRequest) =>
      client.put<UpdateShippingStatusResponse>(`/api/shippings/${shippingId}/status`, data),
  };
}
