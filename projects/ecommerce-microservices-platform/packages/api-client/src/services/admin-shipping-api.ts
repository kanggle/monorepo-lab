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

    // TASK-FE-073 — admin on-demand carrier sync. Triggers the published
    // admin endpoint `POST /api/shippings/{id}/refresh-tracking` (TASK-BE-293),
    // which best-effort pulls the logistics aggregator's unified last-event
    // status (Delivery Tracker GraphQL `track`, TASK-BE-364) and forward-advances
    // the shipment. No request body; returns the same UpdateShippingStatusResponse
    // envelope as a status change (the possibly-advanced shipment).
    refreshTracking: (shippingId: string) =>
      client.post<UpdateShippingStatusResponse>(`/api/shippings/${shippingId}/refresh-tracking`, {}),
  };
}
