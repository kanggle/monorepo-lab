import type { MembershipListItem } from '@/entities/membership';

/** A membership's lifecycle state for the history view. */
export type HistoryStatus = 'active' | 'scheduled' | 'expired' | 'canceled';

/**
 * Maps a membership list item to its history badge state. Pure — `now` is
 * injectable for deterministic tests.
 *
 * - `CANCELED` → `canceled` (an explicit opt-out, regardless of window).
 * - read-time `active` (status ACTIVE && now ∈ window) → `active`.
 * - status ACTIVE, window not started yet (`now < validFrom`) → `scheduled`
 *   (e.g. the future half of a seamlessly-renewed pair).
 * - status ACTIVE, window passed (`now > validTo`) → `expired`.
 */
export function historyStatus(m: MembershipListItem, now: number = Date.now()): HistoryStatus {
  if (m.status === 'CANCELED') return 'canceled';
  if (m.active) return 'active';
  if (Date.parse(m.validFrom) > now) return 'scheduled';
  return 'expired';
}

export const HISTORY_LABEL: Record<HistoryStatus, string> = {
  active: '이용 중',
  scheduled: '예정',
  expired: '만료됨',
  canceled: '해지됨',
};

export const HISTORY_BADGE: Record<HistoryStatus, string> = {
  active: 'bg-emerald-100 text-emerald-700',
  scheduled: 'bg-brand-100 text-brand-700',
  expired: 'bg-amber-100 text-amber-700',
  canceled: 'bg-ink-100 text-ink-600',
};
