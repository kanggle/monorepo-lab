/**
 * The «실적» strip windows every sample domain answers with (ADR-MONO-084,
 * TASK-MONO-807 introduced them in `ecommerce.ts`; TASK-MONO-808 moved them
 * here so the wms sample reuses ONE copy instead of a second hand-typed one).
 *
 * A leaf module: imports nothing, so any fixture may import it without adding
 * an edge into the `dashboards` / `registry` / `index` init cycle (see
 * `wms.ts`'s header).
 */

export interface SampleWindow {
  from: string;
  to: string;
}

export type SamplePeriod = 'week' | 'month';

/**
 * The windows the producer would compute at `SAMPLE_AS_OF` (2026-09-15 20:00 KST,
 * a Tuesday). Written out — `shared/sample/**` may not import `shared/lib/kst-period`
 * (isolation guard) — and pinned against `kstMetricsWindows` by
 * `tests/unit/sample-ecommerce-metrics.test.ts`, so they cannot drift from it.
 */
export const SAMPLE_METRICS_WINDOWS: Readonly<
  Record<SamplePeriod, { current: SampleWindow; previous: SampleWindow; dates: readonly string[] }>
> = {
  week: {
    current: { from: '2026-09-14T00:00:00+09:00', to: '2026-09-15T20:00:00+09:00' },
    previous: { from: '2026-09-07T00:00:00+09:00', to: '2026-09-08T20:00:00+09:00' },
    dates: ['2026-09-14', '2026-09-15'],
  },
  month: {
    current: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-15T20:00:00+09:00' },
    previous: { from: '2026-08-01T00:00:00+09:00', to: '2026-08-15T20:00:00+09:00' },
    dates: Array.from({ length: 15 }, (_, i) => `2026-09-${String(i + 1).padStart(2, '0')}`),
  },
};

/** Half-open `[from, to)` — the producer's window rule. */
export function instantInside(iso: string, w: SampleWindow): boolean {
  const t = Date.parse(iso);
  return t >= Date.parse(w.from) && t < Date.parse(w.to);
}

/** KST calendar date of an instant (fixed +09:00 — no DST). */
export function kstDate(iso: string): string {
  return new Date(Date.parse(iso) + 9 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

/** `?period=` as the producer reads it; anything else → `null` (the producer's 400). */
export function samplePeriod(query: URLSearchParams): SamplePeriod | null {
  const p = query.get('period')?.toLowerCase();
  return p === 'week' || p === 'month' ? p : null;
}
