import { z } from 'zod';
import {
  PerformanceMetricSchema,
  PerformanceMetricsSchema,
  type PerformanceMetric,
} from '@/shared/api/performance-metrics';

/**
 * The operator overview's per-card **headline metric** (ADR-MONO-084 D7,
 * TASK-PC-FE-368; `console-integration-contract.md` § 2.4.9.1 «Card headline
 * metric»). PURE — no I/O, no session — so the composition (server), the sample
 * fixture and the card renderer all read one definition.
 *
 * One metric per domain, from that domain's own `…/metrics?period=week`
 * response (`platform/contracts/performance-metrics-contract.md`):
 *
 *   iam → loginFailure · wms → qtyShipped · scm → poAmount:<CUR> (each currency)
 *   finance → transferAmount:<CUR> (each currency) · erp → approved ·
 *   ecommerce → revenue
 *
 * ── F5 ── values stay the producer's integer strings; nothing here coerces them.
 * ── Never a 0 for a failure ── a metric that could not be read is
 * `{ status: 'degraded' }` / `{ status: 'forbidden' }`, not a zero-valued `ok`.
 */

export const HEADLINE_PERIOD = 'week' as const;

export type HeadlineDomain = 'iam' | 'wms' | 'scm' | 'finance' | 'erp' | 'ecommerce';

const OkHeadlineSchema = z.object({
  status: z.literal('ok'),
  period: z.literal(HEADLINE_PERIOD),
  // The selected metric(s) only — one entry, or one per currency for money.
  metrics: z.array(PerformanceMetricSchema).min(1),
});
const DegradedHeadlineSchema = z.object({ status: z.literal('degraded') });
const ForbiddenHeadlineSchema = z.object({ status: z.literal('forbidden') });

export const HeadlineSchema = z.discriminatedUnion('status', [
  OkHeadlineSchema,
  DegradedHeadlineSchema,
  ForbiddenHeadlineSchema,
]);
export type Headline = z.infer<typeof HeadlineSchema>;

interface HeadlineSpec {
  /** Which of the response's metrics are this domain's headline. */
  matches: (key: string) => boolean;
  /** The unit that key MUST carry — a drifted unit is a contract break, not a number to show. */
  unit: PerformanceMetric['unit'];
  /** Screen name; a per-currency key (`poAmount:KRW`) is named by its base key + currency. */
  label: string;
}

export const HEADLINE_SPECS: Readonly<Record<HeadlineDomain, HeadlineSpec>> = {
  iam: { matches: (k) => k === 'loginFailure', unit: 'count', label: '로그인 실패' },
  wms: { matches: (k) => k === 'qtyShipped', unit: 'count', label: '출고 수량' },
  scm: { matches: (k) => k.startsWith('poAmount:'), unit: 'money', label: '발주 금액' },
  finance: { matches: (k) => k.startsWith('transferAmount:'), unit: 'money', label: '이체 금액' },
  erp: { matches: (k) => k === 'approved', unit: 'count', label: '승인' },
  ecommerce: { matches: (k) => k === 'revenue', unit: 'money', label: '매출' },
};

/** Screen name for a headline metric of `domain` (currency appended for money). */
export function headlineLabel(
  domain: HeadlineDomain,
  metric: Pick<PerformanceMetric, 'currency'>,
): string {
  const base = HEADLINE_SPECS[domain].label;
  return metric.currency ? `${base} (${metric.currency})` : base;
}

/** procurement wraps its other reads in `{ data, meta }`; accept either form (as `getPoMetrics` does). */
function unwrap(body: unknown): unknown {
  if (body && typeof body === 'object' && !('period' in body) && 'data' in body) {
    return (body as { data: unknown }).data;
  }
  return body;
}

/**
 * A domain's metrics response body → its headline. Anything that is not a valid
 * response carrying the expected key(s) is `degraded` — an absent key is "not
 * measured", never "0".
 */
export function headlineFromMetricsBody(domain: HeadlineDomain, body: unknown): Headline {
  const parsed = PerformanceMetricsSchema.safeParse(unwrap(body));
  if (!parsed.success) return { status: 'degraded' };
  const spec = HEADLINE_SPECS[domain];
  const picked = parsed.data.metrics.filter((m) => spec.matches(m.key));
  if (picked.length === 0 || picked.some((m) => m.unit !== spec.unit)) {
    return { status: 'degraded' };
  }
  return { status: 'ok', period: HEADLINE_PERIOD, metrics: picked };
}
