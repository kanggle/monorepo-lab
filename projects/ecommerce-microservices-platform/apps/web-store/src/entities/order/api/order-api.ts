import { apiClient } from '@/shared/config/api';
import { createOrderApi } from '@repo/api-client';
import type {
  PlaceOrderRequest,
  PlaceOrderResponse,
  OrderSummary,
  OrderDetail,
  CancelOrderResponse,
} from '@repo/types';
import type { PaginatedResponse } from '@repo/types';

const orderApi = createOrderApi(apiClient);

/**
 * Place a new order.
 *
 * On failure the error is propagated to the caller. Do NOT reintroduce a
 * silent mock fallback here — mock ids are not valid UUIDs and would crash
 * downstream paths. See TASK-FE-061 (same pattern as product-api).
 */
export async function placeOrder(
  data: PlaceOrderRequest,
  idempotencyKey?: string,
): Promise<PlaceOrderResponse> {
  // Attach the checkout idempotency key (TASK-BE-430) so a re-submit/retry of the
  // same checkout returns the original order instead of creating a duplicate. The
  // BFF proxy forwards the `Idempotency-Key` header to order-service unchanged.
  const config = idempotencyKey
    ? { headers: { 'Idempotency-Key': idempotencyKey } }
    : undefined;
  return orderApi.placeOrder(data, config);
}

export async function getOrders(page = 0, size = 20): Promise<PaginatedResponse<OrderSummary>> {
  return orderApi.getOrders({ page, size });
}

export async function getOrder(orderId: string): Promise<OrderDetail> {
  return orderApi.getOrder(orderId);
}

export async function cancelOrder(orderId: string): Promise<CancelOrderResponse> {
  return orderApi.cancelOrder(orderId);
}
