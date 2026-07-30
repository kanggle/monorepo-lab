import type { OrderStatus } from '../api/order-types';

/**
 * Shared display helpers for the ecommerce operator **overview snapshot**
 * (TASK-PC-FE-199 — extracted from {@link EcommerceOverview} so the count-card
 * and recent-panel presentational pieces can reuse them without a circular
 * container dependency). Presentation-only; no behavior change.
 *
 * `cellPlaceholder` re-exported from `shared/lib/overview-cell.ts`
 * (TASK-PC-FE-264 — 5-domain duplication).
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

export { overviewCellPlaceholder as cellPlaceholder } from '@/shared/lib/overview-cell';
