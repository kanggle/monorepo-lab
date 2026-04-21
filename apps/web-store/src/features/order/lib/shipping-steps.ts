import type { ShippingStatus, ShippingResponse } from '@repo/types';

export const SHIPPING_STEPS: { status: ShippingStatus; label: string }[] = [
  { status: 'PREPARING', label: '상품 준비중' },
  { status: 'SHIPPED', label: '배송 시작' },
  { status: 'IN_TRANSIT', label: '배송중' },
  { status: 'DELIVERED', label: '배송 완료' },
];

export function getStepIndex(status: ShippingStatus): number {
  return SHIPPING_STEPS.findIndex((step) => step.status === status);
}

export function getDeliveredDate(shipping: ShippingResponse): string | null {
  if (shipping.status !== 'DELIVERED') return null;
  const entry = shipping.statusHistory.find((h) => h.status === 'DELIVERED');
  return entry ? entry.changedAt : null;
}
