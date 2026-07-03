/**
 * KST (`Asia/Seoul`) calendar-period-to-date boundaries, computed console-side
 * for the wms 운영 개요 배송 flow metrics (TASK-PC-FE-174). The TypeScript
 * analogue of the shared java `KstPeriodBounds` (`libs/java-common`) that the
 * ecommerce `.../summary` endpoints use: today = start of the KST day, week =
 * the current ISO week (Monday), month = the 1st — each returned as a UTC
 * instant ISO string. Range is half-open at the period start through
 * {@link KstPeriodBounds.nowInstant}.
 *
 * Korea observes NO daylight saving, so KST is a FIXED +09:00 offset — the
 * boundaries are derived with plain offset arithmetic (no tz database needed),
 * which also keeps the value stable regardless of the SSR container's own zone.
 *
 * Unlike ecommerce (where the producer computes these boundaries inside each
 * `/summary` endpoint), the wms producer is NOT retrofitted with a `/summary`
 * (ADR-MONO-017 D3.B); the console derives the window here and passes it as the
 * existing `GET /dashboard/shipments` `shippedAtFrom`/`shippedAtTo` params.
 */

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

export interface KstPeriodBounds {
  /** Start of the current KST day, as a UTC instant ISO string. */
  todayStartInstant: string;
  /** Start of the current KST ISO week (Monday), as a UTC instant ISO string. */
  weekStartInstant: string;
  /** Start of the current KST month (the 1st), as a UTC instant ISO string. */
  monthStartInstant: string;
  /** `now`, as a UTC instant ISO string (the half-open upper bound). */
  nowInstant: string;
}

/**
 * @param now the reference instant (defaults to `new Date()`; injectable for
 *   deterministic tests).
 */
export function kstPeriodBounds(now: Date = new Date()): KstPeriodBounds {
  // Shift UTC → KST wall clock by the fixed +09:00 offset, then read the
  // calendar fields off the shifted instant with the UTC accessors.
  const kst = new Date(now.getTime() + KST_OFFSET_MS);
  const y = kst.getUTCFullYear();
  const mon = kst.getUTCMonth();
  const day = kst.getUTCDate();
  const dow = kst.getUTCDay(); // 0=Sun … 6=Sat (KST wall clock)
  const daysSinceMonday = (dow + 6) % 7; // ISO week: Monday = 0

  // KST midnight of (yy, mm, dd) as a UTC instant: take the UTC midnight of that
  // calendar date and subtract the +09:00 offset. `Date.UTC` normalizes an
  // out-of-range day argument (e.g. `day - daysSinceMonday` crossing a
  // month/year boundary), which handles the ISO-week rollback cleanly.
  const kstMidnight = (yy: number, mm: number, dd: number): string =>
    new Date(Date.UTC(yy, mm, dd) - KST_OFFSET_MS).toISOString();

  return {
    todayStartInstant: kstMidnight(y, mon, day),
    weekStartInstant: kstMidnight(y, mon, day - daysSinceMonday),
    monthStartInstant: kstMidnight(y, mon, 1),
    nowInstant: now.toISOString(),
  };
}
