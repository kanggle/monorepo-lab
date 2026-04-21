// Payment domain types based on specs/contracts/http/payment-api.md

export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';

export interface PaymentResponse {
  paymentId: string;
  orderId: string;
  userId: string;
  amount: number;
  status: PaymentStatus;
  paymentMethod: string | null;
  paymentKey: string | null;
  receiptUrl: string | null;
  createdAt: string;
  paidAt: string | null;
  refundedAt: string | null;
}

export interface PaymentConfirmRequest {
  paymentKey: string;
  orderId: string;
  amount: number;
}

export interface PaymentConfirmResponse {
  paymentId: string;
  orderId: string;
  status: 'COMPLETED';
  paymentMethod: string;
  receiptUrl: string | null;
  paidAt: string;
}
