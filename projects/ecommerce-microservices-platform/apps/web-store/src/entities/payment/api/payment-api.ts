import { apiClient } from '@/shared/config/api';
import { createPaymentApi } from '@repo/api-client';
import type { PaymentResponse } from '@repo/types';
import type { ConfirmPaymentRequest, ConfirmPaymentResponse } from '@repo/api-client';

const paymentApi = createPaymentApi(apiClient);

export async function getPayment(orderId: string): Promise<PaymentResponse> {
  return paymentApi.getPayment(orderId);
}

export async function confirmPayment(data: ConfirmPaymentRequest): Promise<ConfirmPaymentResponse> {
  return paymentApi.confirmPayment(data);
}
