import { chartMagnitude } from '@/shared/lib/chart-scale';
import type { FxRateHistoryQuote } from '../api/types';

/**
 * FX 환율 이력 선 그래프의 기하 (TASK-PC-FE-359). Pure — no React.
 *
 * F5: the rate is a decimal STRING. Its magnitude for the line's y comes ONLY
 * from {@link chartMagnitude} (the spec's single chart-geometry exception) and
 * is used ONLY for coordinates. Which quote is labelled 최저 / 최고 is decided
 * by {@link compareDecimalStrings} — an exact string comparison, never the
 * float — and the label prints the producer's original string.
 *
 * Order: the producer returns quotes newest-first (`fetched_at DESC`); the line
 * runs oldest → newest left to right, so the input is reversed (not re-sorted —
 * the producer's order is authoritative). Points are evenly spaced by poll,
 * not by elapsed time.
 */

export const FX_LINE_WIDTH = 600;
export const FX_LINE_HEIGHT = 120;
const PAD = 8;

export interface FxLinePoint {
  x: number;
  y: number;
  quote: FxRateHistoryQuote;
}

export interface FxLineGeometry {
  /** Chronological (oldest first) — the drawable points only. */
  points: FxLinePoint[];
  /** Quotes left out because their rate could not be read as a number. */
  skipped: number;
  /** Original-string labels (exact string comparison, never the float). */
  min: FxRateHistoryQuote | null;
  max: FxRateHistoryQuote | null;
  latest: FxRateHistoryQuote;
  /** All drawable rates are the same magnitude → a flat line in the middle. */
  flat: boolean;
}

const PLAIN_DECIMAL = /^([+-]?)(\d*)(?:\.(\d*))?$/;

/**
 * Exact comparison of two plain decimal strings (`"1300.00000000"` vs
 * `"1298.5"`) — sign, integer digits and fraction digits compared as text, so
 * no precision is lost. Returns `null` when either is not a plain decimal
 * (e.g. exponent form); such a quote is drawn but never labelled.
 */
export function compareDecimalStrings(a: string, b: string): -1 | 0 | 1 | null {
  const pa = PLAIN_DECIMAL.exec(a.trim());
  const pb = PLAIN_DECIMAL.exec(b.trim());
  if (!pa || !pb) return null;
  if (!(pa[2] || pa[3]) || !(pb[2] || pb[3])) return null;
  const norm = (m: RegExpExecArray) => {
    const int = (m[2] ?? '').replace(/^0+/, '');
    const frac = (m[3] ?? '').replace(/0+$/, '');
    const zero = int === '' && frac === '';
    return { neg: m[1] === '-' && !zero, int, frac };
  };
  const x = norm(pa);
  const y = norm(pb);
  if (x.neg !== y.neg) return x.neg ? -1 : 1;
  let cmp: -1 | 0 | 1 = 0;
  if (x.int.length !== y.int.length) {
    cmp = x.int.length < y.int.length ? -1 : 1;
  } else if (x.int !== y.int) {
    cmp = x.int < y.int ? -1 : 1;
  } else {
    const len = Math.max(x.frac.length, y.frac.length);
    const fx = x.frac.padEnd(len, '0');
    const fy = y.frac.padEnd(len, '0');
    if (fx !== fy) cmp = fx < fy ? -1 : 1;
  }
  return x.neg ? ((-cmp || 0) as -1 | 0 | 1) : cmp;
}

/**
 * Builds the line, or `null` when no quote can be drawn (empty series, or
 * every rate unreadable) — the screen then shows the table alone.
 */
export function fxHistoryLine(
  quotesNewestFirst: readonly FxRateHistoryQuote[],
): FxLineGeometry | null {
  const chronological = [...quotesNewestFirst].reverse();
  const drawable: { quote: FxRateHistoryQuote; v: number }[] = [];
  for (const quote of chronological) {
    const v = chartMagnitude(quote.rate);
    if (v !== null) drawable.push({ quote, v });
  }
  if (drawable.length === 0) return null;

  let lo = drawable[0].v;
  let hi = drawable[0].v;
  for (const d of drawable) {
    lo = Math.min(lo, d.v);
    hi = Math.max(hi, d.v);
  }
  const flat = hi === lo;
  const innerW = FX_LINE_WIDTH - PAD * 2;
  const innerH = FX_LINE_HEIGHT - PAD * 2;
  const n = drawable.length;
  const points: FxLinePoint[] = drawable.map((d, i) => ({
    x: n === 1 ? FX_LINE_WIDTH / 2 : PAD + (innerW * i) / (n - 1),
    y: flat ? FX_LINE_HEIGHT / 2 : PAD + innerH * (1 - (d.v - lo) / (hi - lo)),
    quote: d.quote,
  }));

  // Chronological walk; a tie moves the label to the NEWER quote (the most
  // recent time the rate stood at that level).
  let min: FxRateHistoryQuote | null = null;
  let max: FxRateHistoryQuote | null = null;
  for (const { quote } of drawable) {
    if (compareDecimalStrings(quote.rate, quote.rate) === null) continue;
    const vsMin = min ? compareDecimalStrings(quote.rate, min.rate) : -1;
    const vsMax = max ? compareDecimalStrings(quote.rate, max.rate) : 1;
    if (vsMin !== null && vsMin <= 0) min = quote;
    if (vsMax !== null && vsMax >= 0) max = quote;
  }

  return {
    points,
    skipped: quotesNewestFirst.length - n,
    min,
    max,
    latest: drawable[n - 1].quote,
    flat,
  };
}
