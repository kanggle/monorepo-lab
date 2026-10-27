import { describe, expect, it } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_METRICS_WINDOWS } from '@/shared/sample/fixtures/metrics-windows';
import { METRICS_PERIODS } from '@/shared/lib/kst-period';
import { PerformanceMetricsSchema, performanceMetricsCell } from '@/shared/api/performance-metrics';
import { TransactionsResponseSchema } from '@/features/finance-ops/api/types';

/**
 * TASK-MONO-812 — the anonymous visitor's Finance «실적» strip (ADR-MONO-074
 * sample mode). The strip is derived from the SAME transaction rows the 계좌
 * screen lists, so the expectations below are re-derived THROUGH THE ROUTER
 * from that list read by the producer's rule (account-api.md § GET
 * /api/finance/accounts/metrics), never from the fixture's internals.
 */

const KEYS = [
  'topupCount',
  'topupAmount:KRW',
  'transferCount',
  'transferAmount:KRW',
  'captureCount',
  'captureAmount:KRW',
];

const SAMPLE_ACCOUNT_ID = 'sample-account-0001';

function finance(path: string): Response {
  return sampleResponse({ core: 'flat', surface: 'finance', method: 'GET', path });
}

const metricsPath = (period: string) => `/api/finance/accounts/metrics?period=${period}`;

async function metrics(period: string) {
  return PerformanceMetricsSchema.parse(await finance(metricsPath(period)).json());
}

const inside = (iso: string, w: { from: string; to: string }) => {
  const t = Date.parse(iso);
  return t >= Date.parse(w.from) && t < Date.parse(w.to);
};

async function transactions() {
  return TransactionsResponseSchema.parse(
    await finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}/transactions?page=0&size=100`).json(),
  ).data;
}

const COUNTED: Record<string, string> = { TOPUP: 'topup', TRANSFER: 'transfer', CAPTURE: 'capture' };
const SETTLED = ['SETTLED', 'COMPLETED'];

describe('sample finance /api/finance/accounts/metrics (TASK-MONO-812)', () => {
  it('answers the shared contract shape for both periods — the six KRW keys, in order', async () => {
    for (const p of METRICS_PERIODS) {
      const res = finance(metricsPath(p));
      expect(res.status, p).toBe(200);
      const body = await res.json();
      const parsed = PerformanceMetricsSchema.safeParse(body);
      expect(parsed.success, p).toBe(true);
      expect(parsed.data?.period).toBe(p);
      expect(parsed.data?.timezone).toBe('Asia/Seoul');
      expect(parsed.data?.current).toEqual(SAMPLE_METRICS_WINDOWS[p].current);
      expect(parsed.data?.previous).toEqual(SAMPLE_METRICS_WINDOWS[p].previous);
      expect(parsed.data?.metrics.map((m) => m.key)).toEqual(KEYS);
      expect((await performanceMetricsCell(Promise.resolve(body))).status, p).toBe('ok');
    }
  });

  it('routes to the metrics answer — NOT to the account-detail matcher (which would 404 «metrics» as an id)', () => {
    const res = finance(metricsPath('month'));
    expect(res.status).toBe(200);
  });

  it('only aggregate numbers — no account id anywhere in the body', async () => {
    for (const p of METRICS_PERIODS) {
      const text = await finance(metricsPath(p)).text();
      expect(text, p).not.toMatch(/sample-account|txn-sample|accountId|counterparty/);
    }
  });

  it('= the 계좌 screen\'s rows by the producer rule: settled TOPUP/TRANSFER/CAPTURE only (re-derived from the list)', async () => {
    const rows = await transactions();
    // Non-vacuous: the seed really holds a counted row AND the two excluded kinds.
    expect(rows.some((t) => t.type === 'TOPUP' && t.status === 'COMPLETED')).toBe(true);
    expect(rows.some((t) => t.type === 'HOLD')).toBe(true);
    expect(rows.some((t) => t.status === 'FAILED')).toBe(true);

    let sawNonZero = false;
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      const w = SAMPLE_METRICS_WINDOWS[p];
      for (const [side, win] of [['current', w.current], ['previous', w.previous]] as const) {
        for (const [type, prefix] of Object.entries(COUNTED)) {
          const hits = rows.filter(
            (t) => t.type === type && SETTLED.includes(t.status) && t.settledAt && inside(t.settledAt, win),
          );
          const count = String(hits.length);
          const amount = String(hits.reduce((a, t) => a + BigInt(t.money.amount), BigInt(0)));
          if (hits.length > 0) sawNonZero = true;
          expect(m.metrics.find((x) => x.key === `${prefix}Count`)?.[side], `${p} ${side} ${type}`).toBe(count);
          expect(m.metrics.find((x) => x.key === `${prefix}Amount:KRW`)?.[side], `${p} ${side} ${type}`).toBe(amount);
        }
      }
    }
    expect(sawNonZero).toBe(true);

    // The month (hand-summed): 7 top-ups 80,000 + 50,000 + 30,000 + 20,000 + 10,000 + 15,000
    // + 12,000 (TASK-PC-FE-380 pair) = 217,000 원 and 5 transfers 40,000 + 25,000 + 35,000 + 25,000
    // + 12,000 (TASK-PC-FE-380) = 137,000 원; the ACTIVE hold and the FAILED transfer move
    // nothing — the control group, visible on the sample screen.
    const month = await metrics('month');
    expect(month.metrics.map((x) => x.current)).toEqual(['7', '217000', '5', '137000', '0', '0']);
    // The week window (09-14..15 KST): the 09-14 transfer 35,000 + the 09-15 top-up 20,000.
    const week = await metrics('week');
    expect(week.metrics.map((x) => x.current)).toEqual(['1', '20000', '1', '35000', '0', '0']);
    // TASK-PC-FE-380 — the PREVIOUS week window (09-07..08 KST), hand-counted: top-ups
    // 30,000 (txn-0006, 09-07 14:03 KST) + 12,000 (txn-0014, 09-07 17:03 KST) = 42,000 over 2;
    // one transfer 12,000 (txn-0013, 09-08 13:02 KST). The balance pair nets to zero.
    expect(week.metrics.map((x) => x.previous)).toEqual(['2', '42000', '1', '12000', '0', '0']);
    // The previous MONTH window (08-01..15) stays empty.
    expect(month.metrics.map((x) => x.previous)).toEqual(['0', '0', '0', '0', '0', '0']);
  });

  it('the series is every date of the current window, zero-filled, every key, summing to the totals', async () => {
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      expect(m.series.map((s) => s.date), p).toEqual([...SAMPLE_METRICS_WINDOWS[p].dates]);
      for (const key of KEYS) {
        expect(m.series.every((s) => typeof s.values[key] === 'string'), `${p} ${key}`).toBe(true);
        const sum = m.series.reduce((a, s) => a + BigInt(s.values[key]), BigInt(0));
        expect(String(sum), `${p} ${key}`).toBe(m.metrics.find((x) => x.key === key)?.current);
      }
    }
    const month = await metrics('month');
    // 2026-09-01T00:05Z = 09:05 KST on 09-01.
    expect(month.series.find((s) => s.date === '2026-09-01')?.values['topupAmount:KRW']).toBe('80000');
    // TASK-PC-FE-374 density: days with a settled TOPUP/TRANSFER, hand-listed (KST).
    const active = month.series
      .filter((s) => s.values.topupCount !== '0' || s.values.transferCount !== '0')
      .map((s) => s.date);
    expect(active).toEqual([
      '2026-09-01', '2026-09-03', '2026-09-05', '2026-09-06', '2026-09-07', '2026-09-08',
      '2026-09-10', '2026-09-12', '2026-09-14', '2026-09-15',
    ]);
    // TASK-PC-FE-380: 09-07 now holds two top-ups (30,000 + 12,000), 09-08 the 12,000 transfer.
    expect(month.series.find((s) => s.date === '2026-09-07')?.values.topupCount).toBe('2');
    expect(month.series.find((s) => s.date === '2026-09-07')?.values['topupAmount:KRW']).toBe('42000');
    expect(month.series.find((s) => s.date === '2026-09-08')?.values['transferAmount:KRW']).toBe('12000');
    // TASK-PC-FE-378: the chart draws `topupCount` ONLY (FinanceOverviewScreen
    // chartKey) — count THAT metric's non-zero days, separate from the union above.
    const topupDays = month.series.filter((s) => s.values.topupCount !== '0').map((s) => s.date);
    expect(topupDays).toEqual([
      '2026-09-01', '2026-09-03', '2026-09-05', '2026-09-07', '2026-09-12', '2026-09-15',
    ]);
    expect(topupDays.length).toBeGreaterThanOrEqual(6);
    const week = await metrics('week');
    expect(week.series.filter((s) => s.values.topupCount !== '0').map((s) => s.date)).toEqual([
      '2026-09-15',
    ]);
    expect(
      week.series
        .filter((s) => s.values.topupCount !== '0' || s.values.transferCount !== '0')
        .map((s) => s.date),
    ).toEqual(['2026-09-14', '2026-09-15']);
  });

  it('no / unknown period → not answered (the console always sends one)', () => {
    expect(finance('/api/finance/accounts/metrics').status).toBe(503);
    expect(finance('/api/finance/accounts/metrics?period=year').status).toBe(503);
  });

  it('the other finance routes still answer as before', () => {
    expect(finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}`).status).toBe(200);
    expect(finance('/api/finance/accounts/no-such-account').status).toBe(404);
  });
});
