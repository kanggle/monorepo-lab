import { SHIPPING_STATUS_VALUES } from '../api/shipping-types';

/**
 * Display labels + filter options for the shipping section
 * (TASK-PC-FE-140 — extracted from `ShippingsScreen`, pure display constants
 * shared by the container filter, the table and the confirm dialog).
 */

export const STATUS_FILTER_OPTIONS = ['', ...SHIPPING_STATUS_VALUES] as const;

const STATUS_LABELS: Record<string, string> = {
  PREPARING: '준비중',
  SHIPPED: '발송',
  IN_TRANSIT: '배송중',
  DELIVERED: '배송완료',
};

export function statusLabel(s: string): string {
  return STATUS_LABELS[s] ?? s;
}

const NEXT_STATUS_LABELS: Record<string, string> = {
  SHIPPED: '배송 시작',
  IN_TRANSIT: '배송중',
  DELIVERED: '배송완료',
};

export function nextStatusLabel(s: string): string {
  return NEXT_STATUS_LABELS[s] ?? s;
}
