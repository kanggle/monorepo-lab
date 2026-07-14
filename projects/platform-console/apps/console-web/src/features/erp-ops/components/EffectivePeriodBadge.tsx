import { statusToneClass } from '@/shared/ui/StatusBadge';
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
  // Retired is the one state that asks for attention (§ 2.4.8 surfaces it
  // honestly rather than hiding the row) → `warning`. Active and
  // future-scheduled are both ordinary states → `neutral`. Palette comes from
  // the shared tone map (TASK-PC-FE-242); this badge composes its own <span>
  // because it carries `data-retired` + `title`, which `<StatusBadge>` does not
  // forward — the sanctioned escape hatch.
  const tone = retired ? 'warning' : 'neutral';
  return (
    <span
      className={statusToneClass(tone)}
      data-testid={retired ? 'erp-effective-retired' : 'erp-effective-active'}
      data-retired={retired ? 'true' : 'false'}
      title={`${period.effectiveFrom} ~ ${period.effectiveTo ?? 'open-ended'}`}
    >
      {label}
    </span>
  );
}
