import type { OrderStatus } from '../api/order-types';
import type { CellStatus } from '../api/overview-state';

/**
 * Shared display helpers for the ecommerce operator **overview snapshot**
 * (TASK-PC-FE-199 — extracted from {@link EcommerceOverview} so the count-card
 * and recent-panel presentational pieces can reuse them without a circular
 * container dependency). Presentation-only; no behavior change.
 */

/** Korean labels for the order-status distribution buckets (tolerant; an
 * unmapped/future status falls back to the raw value at the call site). */
export const ORDER_STATUS_LABELS: Partial<Record<OrderStatus, string>> = {
  PENDING: '대기',
  CONFIRMED: '확정',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  CANCELLED: '취소',
  STUCK_RECOVERY_FAILED: '복구실패',
};

export function cellPlaceholder(status: CellStatus): string {
  return status === 'forbidden' ? '권한 없음' : '점검 필요';
}
