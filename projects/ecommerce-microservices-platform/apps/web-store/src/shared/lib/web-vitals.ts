/**
 * Core Web Vitals measurement helpers (pure, framework-agnostic).
 *
 * These back the web-store performance baseline: the client reporter
 * (`src/app/web-vitals.tsx`) collects metrics via Next's `useReportWebVitals`
 * and uses {@link buildVitalsPayload} to normalize them before logging (dev) or
 * beaconing to `/api/vitals` (prod). Keeping the rating + shaping logic here
 * (no React, no browser globals) makes the thresholds unit-testable and the
 * single source of truth for "what counts as good".
 *
 * Thresholds are the official Core Web Vitals boundaries (good / poor); the
 * middle band is "needs-improvement".
 * @see https://web.dev/articles/vitals
 */

export type VitalsRating = 'good' | 'needs-improvement' | 'poor';

/** `[good, poor]` upper bounds per metric. ms for timings, unitless for CLS. */
export const WEB_VITALS_THRESHOLDS: Record<string, readonly [number, number]> = {
  LCP: [2500, 4000],
  INP: [200, 500],
  CLS: [0.1, 0.25],
  FCP: [1800, 3000],
  TTFB: [800, 1800],
  FID: [100, 300], // legacy, retained for older browsers
};

/**
 * Rate a metric value against the official Core Web Vitals thresholds.
 *
 * @returns `'good' | 'needs-improvement' | 'poor'`, or `null` for metrics with
 *   no defined threshold (e.g. Next.js custom timings like `Next.js-hydration`).
 *   Boundaries are inclusive of the better bucket: a value exactly equal to the
 *   `good` bound rates `'good'`, exactly equal to the `poor` bound rates
 *   `'needs-improvement'`.
 */
export function rateMetric(name: string, value: number): VitalsRating | null {
  const thresholds = WEB_VITALS_THRESHOLDS[name];
  if (!thresholds) return null;
  const [good, poor] = thresholds;
  if (value <= good) return 'good';
  if (value <= poor) return 'needs-improvement';
  return 'poor';
}

/** Minimal shape consumed from Next's `NextWebVitalsMetric`. */
export interface VitalsMetricLike {
  name: string;
  value: number;
  id: string;
  navigationType?: string;
}

export interface VitalsPayload {
  name: string;
  /** Rounded: integer ms for timings, 3-decimal for the unitless CLS. */
  value: number;
  rating: VitalsRating | null;
  id: string;
  navigationType?: string;
  /** Route path the metric was observed on (set by the caller). */
  path: string;
}

/**
 * Normalize a raw web-vitals metric into a compact, serializable payload:
 * rounds the value (CLS keeps 3 decimals, timings round to whole ms) and
 * attaches the computed rating + the observing route path.
 */
export function buildVitalsPayload(
  metric: VitalsMetricLike,
  path: string,
): VitalsPayload {
  const value =
    metric.name === 'CLS'
      ? Math.round(metric.value * 1000) / 1000
      : Math.round(metric.value);
  return {
    name: metric.name,
    value,
    rating: rateMetric(metric.name, metric.value),
    id: metric.id,
    navigationType: metric.navigationType,
    path,
  };
}
