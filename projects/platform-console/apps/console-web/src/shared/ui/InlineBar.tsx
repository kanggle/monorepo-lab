import type { StatusTone } from './StatusBadge';

/**
 * Shared inline bar primitives (TASK-PC-FE-357) — pure CSS width %, NO chart
 * library, NO `'use client'` (usable from server components), same principles
 * as `RankingBarChart` (TASK-PC-FE-172):
 *  - the bar is decorative (`aria-hidden`); the NUMBER stays as text next to it,
 *  - a non-zero value gets at least a {@link MIN_BAR_PCT} sliver,
 *  - every divide is guarded (max/base <= 0 or non-finite → 0%),
 *  - colours come from the console's semantic {@link StatusTone} vocabulary
 *    (solid fill variants of the StatusBadge palette — no new colour system).
 *
 * Two shapes: {@link ProportionBar} (one value against a max — progress /
 * ranking bars) and {@link StackedBar} (several parts of one whole).
 */

export const MIN_BAR_PCT = 2;

const BAR_TONE_CLASS: Record<StatusTone, string> = {
  success: 'bg-green-500 dark:bg-green-400',
  progress: 'bg-blue-500 dark:bg-blue-400',
  warning: 'bg-amber-500 dark:bg-amber-400',
  danger: 'bg-red-500 dark:bg-red-400',
  neutral: 'bg-muted-foreground/50',
};

/** Solid fill class for a tone (same semantic tones as `StatusBadge`). */
export function barToneClass(tone: StatusTone): string {
  return BAR_TONE_CLASS[tone];
}

function isUsable(n: unknown): n is number {
  return typeof n === 'number' && Number.isFinite(n);
}

/**
 * `value / max` as a width percent: 0 for a non-positive / non-finite value or
 * max (divide-by-zero guard), a {@link MIN_BAR_PCT} sliver for any tiny
 * positive value, clamped to 100 when `value > max`.
 */
export function barPercent(value: number | null | undefined, max: number): number {
  if (!isUsable(value) || !isUsable(max) || value <= 0 || max <= 0) return 0;
  return Math.min(100, Math.max(MIN_BAR_PCT, (value / max) * 100));
}

/** One part of a {@link StackedBar}. */
export interface BarSegment {
  key: string;
  value: number | null | undefined;
  tone: StatusTone;
}

/**
 * The effective whole a stacked bar is scaled against: `max(expected, sum)` —
 * a sum that overshoots `expected` scales the bar to the sum (the overflow is
 * not hidden), a shortfall leaves the remainder of the track empty. `expected`
 * null / non-positive falls back to the sum alone.
 */
export function stackedBase(
  segments: readonly BarSegment[],
  expected?: number | null,
): number {
  const sum = segments.reduce(
    (acc, s) => acc + (isUsable(s.value) && s.value > 0 ? s.value : 0),
    0,
  );
  return Math.max(isUsable(expected) && expected > 0 ? expected : 0, sum);
}

/** Per-segment width percents for {@link StackedBar} (same order as input). */
export function stackedPercents(
  segments: readonly BarSegment[],
  expected?: number | null,
): number[] {
  const base = stackedBase(segments, expected);
  return segments.map((s) => barPercent(s.value, base));
}

const TRACK = 'h-2 overflow-hidden rounded bg-muted';

/** Single value against `max` (clamped to 100%). Decorative — keep the number as text. */
export function ProportionBar({
  value,
  max,
  tone = 'progress',
  className = 'w-24',
  testid,
}: {
  value: number | null | undefined;
  max: number;
  tone?: StatusTone;
  /** Track sizing (default `w-24`). */
  className?: string;
  testid?: string;
}) {
  const pct = barPercent(value, max);
  return (
    <div
      className={`${TRACK} shrink-0 ${className}`}
      aria-hidden="true"
      data-testid={testid}
    >
      <div
        className={`h-2 rounded ${barToneClass(tone)}`}
        style={{ width: `${pct}%` }}
        data-pct={pct}
      />
    </div>
  );
}

/**
 * Several parts of one whole on a single track. `expected` (optional) is the
 * nominal whole; see {@link stackedBase}. Renders `data-overflow="true"` when
 * the parts exceed `expected`. Decorative — keep the numbers as text.
 */
export function StackedBar({
  segments,
  expected,
  className = 'w-full',
  testid,
}: {
  segments: readonly BarSegment[];
  expected?: number | null;
  className?: string;
  testid?: string;
}) {
  const pcts = stackedPercents(segments, expected);
  const overflow =
    isUsable(expected) && expected > 0 && stackedBase(segments) > expected;
  return (
    <div
      className={`flex ${TRACK} ${className}`}
      aria-hidden="true"
      data-testid={testid}
      data-overflow={overflow ? 'true' : undefined}
    >
      {segments.map((s, i) =>
        pcts[i] > 0 ? (
          <div
            key={s.key}
            className={`h-2 border-r border-background last:border-r-0 ${barToneClass(s.tone)}`}
            style={{ width: `${pcts[i]}%` }}
            data-segment={s.key}
            data-pct={pcts[i]}
          />
        ) : null,
      )}
    </div>
  );
}

/** Where one step of a {@link StepBar} stands. */
export type StepState = 'done' | 'current' | 'todo';

/**
 * Step states for a route of `total` steps whose zero-based current step is
 * `position` (TASK-PC-FE-361). `finished` marks every step done (a terminal
 * success). Returns `null` when the route cannot be drawn honestly: `total`
 * not a positive integer, or `position` not an integer inside `[0, total)` —
 * a bad number must never read as a position.
 */
export function stepStates(
  position: number | null | undefined,
  total: number | null | undefined,
  finished = false,
): StepState[] | null {
  if (typeof total !== 'number' || !Number.isInteger(total) || total < 1) return null;
  if (finished) return Array.from({ length: total }, () => 'done' as const);
  if (
    typeof position !== 'number' ||
    !Number.isInteger(position) ||
    position < 0 ||
    position >= total
  ) {
    return null;
  }
  return Array.from({ length: total }, (_, i): StepState =>
    i < position ? 'done' : i === position ? 'current' : 'todo',
  );
}

const STEP_CLASS: Record<StepState, string> = {
  done: 'bg-green-500 dark:bg-green-400',
  current: 'bg-blue-500 dark:bg-blue-400',
  todo: 'bg-muted',
};

/**
 * One segment per step (done / current / todo). Optional `labels` (same length)
 * print under each segment. Purely decorative (`aria-hidden`) — the current
 * position stays available as text next to it. Backward-compatible addition
 * to this module (no existing export changed).
 */
export function StepBar({
  states,
  labels,
  className = 'w-full',
  testid,
}: {
  states: readonly StepState[];
  labels?: readonly string[];
  className?: string;
  testid?: string;
}) {
  return (
    <div
      className={`flex gap-0.5 ${className}`}
      aria-hidden="true"
      data-testid={testid}
    >
      {states.map((s, i) => (
        <div key={i} className="min-w-0 flex-1" data-step={s}>
          <div className={`h-2 rounded-sm ${STEP_CLASS[s]}`} />
          {labels ? (
            <div
              className={`mt-1 truncate text-xs ${
                s === 'current' ? 'font-medium text-foreground' : 'text-muted-foreground'
              }`}
            >
              {labels[i]}
            </div>
          ) : null}
        </div>
      ))}
    </div>
  );
}

/** Small colour key (no text) to put before a label so a stacked bar's colours
 *  map onto the numbers already printed beside it. Decorative. */
export function BarSwatch({ tone }: { tone: StatusTone }) {
  return (
    <span
      aria-hidden="true"
      className={`mr-1 inline-block h-2 w-2 rounded-sm align-middle ${barToneClass(tone)}`}
    />
  );
}
