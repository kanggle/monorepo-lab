// Order domain types based on specs/contracts/http/order-api.md

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface ShippingAddress {
  recipient: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2: string | null;
}

export interface OrderItem {
  productId: string;
  variantId: string;
  productName: string;
  optionName?: string;
  quantity: number;
  unitPrice: number;
}

export interface OrderItemDetail {
  productId: string;
  variantId: string;
  productName: string;
  optionName: string;
  quantity: number;
  unitPrice: number;
}

export interface PlaceOrderRequest {
  items: OrderItem[];
  shippingAddress: ShippingAddress;
  /** 적용할 쿠폰 (TASK-INT-026). 할인액은 order-service 가 promotion-service 에 물어 정한다. */
  couponId?: string;
}

export interface PlaceOrderResponse {
  orderId: string;
  /** 결제할 금액 — 할인 반영 후. PG 에 이 금액을 그대로 요청해야 승인이 통과한다. */
  totalPrice: number;
  /** 서버가 확정한 쿠폰 할인액. 쿠폰이 없으면 0. */
  discountAmount: number;
}

export interface OrderListParams {
  page?: number;
  size?: number;
  status?: OrderStatus;
}

export interface OrderSummary {
  orderId: string;
  status: OrderStatus;
  totalPrice: number;
  itemCount: number;
  firstItemName: string | null;
  createdAt: string;
}

export interface OrderDetail {
  orderId: string;
  status: OrderStatus;
  totalPrice: number;
  items: OrderItemDetail[];
  shippingAddress: ShippingAddress;
  createdAt: string;
  updatedAt: string;
}

export interface CancelOrderResponse {
  orderId: string;
  status: 'CANCELLED';
}

// Admin order types

export interface AdminOrderSummary {
  orderId: string;
  userId: string;
  status: OrderStatus;
  totalPrice: number;
  itemCount: number;
  firstItemName: string | null;
  createdAt: string;
}

export interface AdminOrderDetail {
  orderId: string;
  userId: string;
  status: OrderStatus;
  totalPrice: number;
  items: OrderItemDetail[];
  shippingAddress: ShippingAddress;
  createdAt: string;
  updatedAt: string;
}

export interface AdminOrderStatusChangeRequest {
  status: OrderStatus;
}

export interface AdminOrderStatusChangeResponse {
  orderId: string;
  status: OrderStatus;
}
