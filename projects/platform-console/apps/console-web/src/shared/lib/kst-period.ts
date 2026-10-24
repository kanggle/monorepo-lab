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
 *
 * TASK-MONO-806 moved this module from `features/wms-ops/api/` to `shared/lib/`
 * — ADR-MONO-084 D3: the console cuts KST periods in ONE place — and added
 * {@link kstMetricsWindows}, the TS twin of the java `KstPeriodBounds`
 * `currentWindow` / `previousWindow` / `currentDates`. Both sides are checked
 * against the same fixture (`platform/contracts/fixtures/performance-metrics-windows.json`).
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

/**
 * The KST calendar date (`YYYY-MM-DD`) of an instant — same fixed +09:00 shift
 * as {@link kstPeriodBounds} (TASK-MONO-815).
 */
export function kstDateOf(now: Date = new Date()): string {
  return new Date(now.getTime() + KST_OFFSET_MS).toISOString().slice(0, 10);
}

// ---------------------------------------------------------------------------
// Performance-metrics windows (ADR-MONO-084 D2 · D3, TASK-MONO-806)
// ---------------------------------------------------------------------------

/** The `?period=` of a performance-metrics read. */
export type MetricsPeriod = 'week' | 'month';

export const METRICS_PERIODS: readonly MetricsPeriod[] = ['week', 'month'];

/** A window as it appears on the wire: KST offset, whole seconds. */
export interface KstWindowWire {
  from: string;
  to: string;
}

export interface KstMetricsWindows {
  /** Period start → now. */
  current: KstWindowWire;
  /** The previous period's start + the same elapsed time, stopped at the
   *  current period's start when the previous month is shorter. */
  previous: KstWindowWire;
  /** The current window's KST dates (`YYYY-MM-DD`), both ends inclusive — never empty. */
  dates: string[];
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** A KST wall-clock instant (held as UTC-epoch ms of the wall clock) → wire string. */
function wallToWire(wallMs: number): string {
  return `${new Date(wallMs).toISOString().slice(0, 19)}+09:00`;
}

/**
 * The current and previous windows for one period, plus the series dates —
 * the same answer the java `KstPeriodBounds` gives for the same `now`.
 *
 * Works on the KST **wall clock** expressed as epoch ms (UTC accessors over a
 * +09:00-shifted instant), so the host's own zone never enters. `now` is cut to
 * whole seconds first; the period starts are whole seconds, so this equals the
 * java side truncating the formatted bounds.
 */
export function kstMetricsWindows(
  period: MetricsPeriod,
  now: Date = new Date(),
): KstMetricsWindows {
  const nowWall = Math.floor((now.getTime() + KST_OFFSET_MS) / 1000) * 1000;
  const kst = new Date(nowWall);
  const y = kst.getUTCFullYear();
  const mon = kst.getUTCMonth();
  const day = kst.getUTCDate();
  const daysSinceMonday = (kst.getUTCDay() + 6) % 7;

  const startWall =
    period === 'week' ? Date.UTC(y, mon, day - daysSinceMonday) : Date.UTC(y, mon, 1);
  const previousStartWall =
    period === 'week' ? startWall - 7 * DAY_MS : Date.UTC(y, mon - 1, 1);
  const previousToWall = Math.min(previousStartWall + (nowWall - startWall), startWall);

  const lastDayWall = Date.UTC(y, mon, day);
  const dates: string[] = [];
  for (let d = startWall; d <= lastDayWall; d += DAY_MS) {
    dates.push(new Date(d).toISOString().slice(0, 10));
  }

  return {
    current: { from: wallToWire(startWall), to: wallToWire(nowWall) },
    previous: { from: wallToWire(previousStartWall), to: wallToWire(previousToWall) },
    dates,
  };
}
