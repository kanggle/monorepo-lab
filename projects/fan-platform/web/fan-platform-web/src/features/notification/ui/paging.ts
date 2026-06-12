import type { NotificationStatus } from '@/entities/notification';

/**
 * Total page count for {@code totalElements} at {@code size} per page.
 * Always >= 1 (an empty inbox is one empty page); a non-positive size yields 1
 * (never divide-by-zero / Infinity). Pure — unit-tested.
 */
export function computeTotalPages(totalElements: number, size: number): number {
  if (size <= 0) return 1;
  return Math.max(1, Math.ceil(totalElements / size));
}

/**
 * The inbox href for a target page, preserving the status filter. Omits `page=0`
 * and an absent status so the all-status / first-page link stays `/notifications`.
 * Pure — unit-tested.
 */
export function buildNotificationsHref(
  status: NotificationStatus | undefined,
  page: number,
): string {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (page > 0) params.set('page', String(page));
  const qs = params.toString();
  return qs ? `/notifications?${qs}` : '/notifications';
}
