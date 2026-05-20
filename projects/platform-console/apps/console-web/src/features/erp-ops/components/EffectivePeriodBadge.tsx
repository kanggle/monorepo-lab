import { isRetired, type EffectivePeriod } from '../api/types';

/**
 * `<EffectivePeriodBadge>` — surfaces the `effectivePeriod` of a
 * master record HONESTLY (TASK-PC-FE-010 / § 2.4.8 / E2 obligation).
 *
 *   - `effectiveTo: null` (open-ended / currently active) → "active"
 *     normal badge.
 *   - `effectiveTo: <past>` (retired in current view) → "retired"
 *     warn badge.
 *   - `effectiveTo: <future>` (future-effective) → "scheduled" badge.
 *
 * The badge is rendered EVERYWHERE the consumer surfaces a master —
 * list rows AND detail panels — so retired rows are visually
 * distinct but NEVER hidden / filtered (the E2 honesty invariant
 * the task pins).
 */
export interface EffectivePeriodBadgeProps {
  period: EffectivePeriod;
  /** Optional reference instant — defaults to `new Date()`. Allows
   *  tests to pin "now" deterministically. */
  now?: Date;
}

export function EffectivePeriodBadge({
  period,
  now,
}: EffectivePeriodBadgeProps) {
  const retired = isRetired(period, now ?? new Date());
  const futureStart =
    period.effectiveFrom &&
    (() => {
      try {
        return new Date(period.effectiveFrom).getTime() > (now ?? new Date()).getTime();
      } catch {
        return false;
      }
    })();

  const label = retired
    ? `retired (~${period.effectiveTo})`
    : futureStart
      ? `scheduled (${period.effectiveFrom}~)`
      : `active (${period.effectiveFrom}~${period.effectiveTo ?? '∞'})`;
  const variant: 'normal' | 'warn' | 'danger' = retired
    ? 'warn'
    : futureStart
      ? 'normal'
      : 'normal';
  const cls: Record<'normal' | 'warn' | 'danger', string> = {
    normal: 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground',
    warn: 'rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-900 dark:bg-amber-950/60 dark:text-amber-100',
    danger:
      'rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive',
  };
  return (
    <span
      className={cls[variant]}
      data-testid={retired ? 'erp-effective-retired' : 'erp-effective-active'}
      data-retired={retired ? 'true' : 'false'}
      title={`${period.effectiveFrom} ~ ${period.effectiveTo ?? 'open-ended'}`}
    >
      {label}
    </span>
  );
}
