import type { ApiClient } from '../client';
import type { ShippingResponse } from '@repo/types';

export function createShippingApi(client: ApiClient) {
  return {
    getShippingByOrder: (orderId: string) =>
      client.get<ShippingResponse>(`/api/shippings/orders/${orderId}`),
  };
}
