// Shipping domain types based on specs/contracts/http/shipping-api.md

export type ShippingStatus = 'PREPARING' | 'SHIPPED' | 'IN_TRANSIT' | 'DELIVERED';

export interface ShippingStatusHistoryEntry {
  status: ShippingStatus;
  changedAt: string;
}

export interface ShippingResponse {
  shippingId: string;
  orderId: string;
  status: ShippingStatus;
  trackingNumber: string | null;
  carrier: string | null;
  statusHistory: ShippingStatusHistoryEntry[];
  createdAt: string;
  updatedAt: string;
}

// Admin shipping types

export interface ShippingSummary {
  shippingId: string;
  orderId: string;
  status: ShippingStatus;
  trackingNumber: string | null;
  carrier: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ShippingListParams {
  page?: number;
  size?: number;
  status?: ShippingStatus;
}

export interface UpdateShippingStatusRequest {
  status: ShippingStatus;
  trackingNumber?: string;
  carrier?: string;
}

export interface UpdateShippingStatusResponse {
  shippingId: string;
  status: ShippingStatus;
  updatedAt: string;
}
