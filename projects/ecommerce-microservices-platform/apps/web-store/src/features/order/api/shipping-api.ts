import { apiClient } from '@/shared/config/api';
import { createShippingApi } from '@repo/api-client';
import type { ShippingResponse } from '@repo/types';

const shippingApi = createShippingApi(apiClient);

export async function getShippingByOrder(orderId: string): Promise<ShippingResponse> {
  return shippingApi.getShippingByOrder(orderId);
}
