import { z } from 'zod';
import { ApiError } from '@/shared/api/errors';
import type { OverviewCellStatus } from '@/shared/lib/overview-cell';

/**
 * Shared performance-metrics read (ADR-MONO-084 D2, TASK-MONO-806) — the one
 * response shape every domain overview's `…/metrics?period=week|month` returns
 * (`platform/contracts/performance-metrics-contract.md`). The domain features
 * own the call (their own gateway + path); this module owns the parse and the
 * per-cell outcome, so six overviews cannot drift into six readings of it.
 *
 * ── F5 ── every value is an **integer string** (counts and money minor units
 * alike). Parsed with a regex, never `z.number()`: the console renders money
 * from the string and never coerces it (`shared/lib/money.ts`). This file is on
 * the F5 guard's list (`tests/unit/f5-chart-geometry-exception.test.ts`).
 */

const INTEGER_STRING = z.string().regex(/^-?\d+$/, 'value must be an integer string (F5)');

const WindowSchema = z.object({
  from: z.string().min(1),
  to: z.string().min(1),
});

export const PerformanceMetricSchema = z.object({
  key: z.string().min(1),
  unit: z.enum(['count', 'money']),
  currency: z.string().length(3).nullable(),
  current: INTEGER_STRING,
  previous: INTEGER_STRING,
});
export type PerformanceMetric = z.infer<typeof PerformanceMetricSchema>;

export const PerformanceSeriesPointSchema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  values: z.record(z.string(), INTEGER_STRING),
});
export type PerformanceSeriesPoint = z.infer<typeof PerformanceSeriesPointSchema>;

export const PerformanceMetricsSchema = z
  .object({
    period: z.enum(['week', 'month']),
    timezone: z.string().min(1),
    current: WindowSchema,
    previous: WindowSchema,
    metrics: z.array(PerformanceMetricSchema),
    series: z.array(PerformanceSeriesPointSchema),
  })
  .superRefine((v, ctx) => {
    const keys = new Set<string>();
    for (const m of v.metrics) {
      if (keys.has(m.key)) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, message: `duplicate metric key: ${m.key}` });
      }
      keys.add(m.key);
      if ((m.unit === 'money') !== (m.currency !== null)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: `metric ${m.key}: currency is required for money and absent for count`,
        });
      }
    }
  });
export type PerformanceMetrics = z.infer<typeof PerformanceMetricsSchema>;

/** One overview's performance strip: the parsed response, or why there is none. */
export type PerformanceCell =
  | { status: 'ok'; value: PerformanceMetrics }
  | { status: Exclude<OverviewCellStatus, 'ok'>; value: null };

/**
 * Resolves one domain's metrics read into a cell, the same way every overview
 * resolves its other legs: success → `ok`; `403` → `forbidden`; a body that
 * does not parse, `503`, timeout, network or anything else → `degraded`. A `401`
 * is RE-THROWN so the page performs its whole-session re-login (never a
 * per-cell degrade).
 */
export async function performanceMetricsCell(read: Promise<unknown>): Promise<PerformanceCell> {
  let body: unknown;
  try {
    body = await read;
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) throw err;
    if (err instanceof ApiError && err.status === 403) return { status: 'forbidden', value: null };
    return { status: 'degraded', value: null };
  }
  const parsed = PerformanceMetricsSchema.safeParse(body);
  return parsed.success
    ? { status: 'ok', value: parsed.data }
    : { status: 'degraded', value: null };
}
