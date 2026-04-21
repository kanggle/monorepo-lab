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
}

export interface PlaceOrderResponse {
  orderId: string;
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
