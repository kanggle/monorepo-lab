import type { ApiClient } from '../client';
import type { PaymentResponse } from '@repo/types';

export interface ConfirmPaymentRequest {
  paymentKey: string;
  orderId: string;
  amount: number;
}

export interface ConfirmPaymentResponse {
  paymentId: string;
  orderId: string;
  status: string;
}

export function createPaymentApi(client: ApiClient) {
  return {
    getPayment: (orderId: string) =>
      client.get<PaymentResponse>(`/api/payments/orders/${orderId}`),
    confirmPayment: (data: ConfirmPaymentRequest) =>
      client.post<ConfirmPaymentResponse>('/api/payments/confirm', data),
  };
}
