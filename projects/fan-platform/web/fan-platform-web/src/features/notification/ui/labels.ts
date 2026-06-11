import type { NotificationType } from '@/entities/notification';

/** Korean label per notification kind. */
export const TYPE_LABEL: Record<NotificationType, string> = {
  WELCOME: '멤버십 시작',
  CANCELLATION: '멤버십 해지',
  EXPIRY_REMINDER: '멤버십 만료',
};

/** Accent classes per kind (small dot / badge tint). */
export const TYPE_ACCENT: Record<NotificationType, string> = {
  WELCOME: 'bg-emerald-100 text-emerald-700',
  CANCELLATION: 'bg-ink-100 text-ink-600',
  EXPIRY_REMINDER: 'bg-amber-100 text-amber-700',
};

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/**
 * Compact relative time ("방금", "5분 전", "3시간 전", "2일 전") falling back to a
 * locale date past a week. Pure — `now` is injectable for deterministic tests.
 */
export function formatRelative(iso: string, now: number = Date.now()): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';
  const diff = now - then;
  if (diff < MINUTE) return '방금';
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}분 전`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)}시간 전`;
  if (diff < 7 * DAY) return `${Math.floor(diff / DAY)}일 전`;
  return new Date(then).toLocaleDateString('ko-KR');
}
