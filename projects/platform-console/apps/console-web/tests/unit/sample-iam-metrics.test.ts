import { describe, expect, it } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_METRICS_WINDOWS } from '@/shared/sample/fixtures/metrics-windows';
import { METRICS_PERIODS } from '@/shared/lib/kst-period';
import { PerformanceMetricsSchema, performanceMetricsCell } from '@/shared/api/performance-metrics';
import { AuditPageSchema } from '@/shared/api/iam-audit-types';

/**
 * TASK-MONO-811 — the anonymous visitor's IAM «실적» strip (ADR-MONO-074 sample
 * mode). The strip is derived from the SAME seed rows the 계정 and 감사 screens
 * show, so the expectations below are re-derived THROUGH THE ROUTER from those
 * list reads, never from the fixture's internals.
 */

const KEYS = ['loginSuccess', 'loginFailure', 'accountLocked', 'newAccount'];

function iam(surface: string, path: string): Promise<Response> {
  return Promise.resolve(sampleResponse({ core: 'iam', surface, method: 'GET', path }));
}

const metricsPath = (period: string) => `/api/admin/metrics?period=${period}&tenantId=sample`;

async function metrics(period: string) {
  return PerformanceMetricsSchema.parse(await (await iam('iam_metrics', metricsPath(period))).json());
}

const inside = (iso: string, w: { from: string; to: string }) => {
  const t = Date.parse(iso);
  return t >= Date.parse(w.from) && t < Date.parse(w.to);
};

async function auditRows(qs: string) {
  const page = AuditPageSchema.parse(
    await (await iam('audit', `/api/admin/audit?${qs}&page=0&size=100`)).json(),
  );
  return page.content as unknown as Array<Record<string, unknown>>;
}

async function accountRows() {
  const page = (await (await iam('accounts', '/api/admin/accounts?page=0&size=100')).json()) as {
    content: Array<{ createdAt: string }>;
  };
  return page.content;
}

describe('sample iam /api/admin/metrics (TASK-MONO-811)', () => {
  it('answers the shared contract shape for both periods — four counts, in order', async () => {
    for (const p of METRICS_PERIODS) {
      const res = await iam('iam_metrics', metricsPath(p));
      expect(res.status, p).toBe(200);
      const body = await res.json();
      const parsed = PerformanceMetricsSchema.safeParse(body);
      expect(parsed.success, p).toBe(true);
      expect(parsed.data?.period).toBe(p);
      expect(parsed.data?.timezone).toBe('Asia/Seoul');
      expect(parsed.data?.current).toEqual(SAMPLE_METRICS_WINDOWS[p].current);
      expect(parsed.data?.previous).toEqual(SAMPLE_METRICS_WINDOWS[p].previous);
      expect(parsed.data?.metrics.map((m) => m.key)).toEqual(KEYS);
      expect(parsed.data?.metrics.every((m) => m.unit === 'count' && m.currency === null)).toBe(true);
      // And through the strip's own cell resolver: an ok strip, not a degraded one.
      expect((await performanceMetricsCell(Promise.resolve(body))).status, p).toBe('ok');
    }
  });

  it('only aggregate numbers — no account id, e-mail or IP anywhere in the body', async () => {
    for (const p of METRICS_PERIODS) {
      const text = await (await iam('iam_metrics', metricsPath(p))).text();
      expect(text, p).not.toMatch(/acc-sample|@|example|ipMasked|accountId|email/);
    }
  });

  it('로그인 성공 / 실패 / 계정 잠금 / 신규 계정 = the list rows in each window (re-derived from the screens)', async () => {
    const logins = await auditRows('source=login_history');
    const locks = await auditRows('actionCode=ACCOUNT_LOCK');
    const accounts = await accountRows();
    // Non-vacuous: the seed really holds one of each login outcome and a lock.
    expect(logins.some((r) => r.outcome === 'SUCCESS')).toBe(true);
    expect(logins.some((r) => r.outcome === 'FAILURE')).toBe(true);
    expect(locks.length).toBeGreaterThan(0);
    expect(accounts.length).toBeGreaterThan(0);

    const instants: Record<string, string[]> = {
      loginSuccess: logins.filter((r) => r.outcome === 'SUCCESS').map((r) => String(r.occurredAt)),
      loginFailure: logins.filter((r) => r.outcome === 'FAILURE').map((r) => String(r.occurredAt)),
      accountLocked: locks.map((r) => String(r.occurredAt)),
      newAccount: accounts.map((a) => a.createdAt),
    };

    let sawNonZero = false;
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      const w = SAMPLE_METRICS_WINDOWS[p];
      for (const [side, win] of [['current', w.current], ['previous', w.previous]] as const) {
        for (const key of KEYS) {
          const want = instants[key].filter((at) => inside(at, win)).length;
          if (want > 0) sawNonZero = true;
          expect(m.metrics.find((x) => x.key === key)?.[side], `${p} ${side} ${key}`).toBe(String(want));
        }
      }
    }
    expect(sawNonZero).toBe(true);

    // The month (hand-counted): 8 successes (09-02 03 06 09 10 12 14 15), 4 failures
    // (09-04 08 12 15), 3 locks (09-07 09-10 09-14); every seed account predates the
    // windows, so 신규 계정 is honestly 0.
    const month = await metrics('month');
    expect(month.metrics.map((x) => x.current)).toEqual(['8', '4', '3', '0']);
    // The week window (09-14..15 KST): 2 successes, 1 failure (UTC 09-14 15:30 = KST 09-15),
    // 1 lock (09-14).
    const week = await metrics('week');
    expect(week.metrics.map((x) => x.current)).toEqual(['2', '1', '1', '0']);
  });

  it('TASK-PC-FE-374 — the bars are spread over the month (KST days listed by hand)', async () => {
    const month = await metrics('month');
    const week = await metrics('week');
    const days = (m: typeof month, key: string) =>
      m.series.filter((s) => s.values[key] !== '0').map((s) => s.date);
    expect(days(month, 'loginSuccess')).toEqual([
      '2026-09-02', '2026-09-03', '2026-09-06', '2026-09-09',
      '2026-09-10', '2026-09-12', '2026-09-14', '2026-09-15',
    ]);
    expect(days(month, 'loginFailure')).toEqual([
      '2026-09-04', '2026-09-08', '2026-09-12', '2026-09-15',
    ]);
    expect(days(month, 'accountLocked')).toEqual(['2026-09-07', '2026-09-10', '2026-09-14']);
    expect(days(week, 'loginSuccess')).toEqual(['2026-09-14', '2026-09-15']);
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
    // 2026-09-12 (KST) carries both logins; 09-10 the lock; quiet days are "0".
    const month = await metrics('month');
    expect(month.series.find((s) => s.date === '2026-09-12')?.values).toMatchObject({
      loginSuccess: '1',
      loginFailure: '1',
    });
    expect(month.series.find((s) => s.date === '2026-09-10')?.values.accountLocked).toBe('1');
    expect(month.series.some((s) => s.values.loginSuccess === '0')).toBe(true);
  });

  it('no / unknown period → not answered (the console always sends one)', async () => {
    expect((await iam('iam_metrics', '/api/admin/metrics?tenantId=sample')).status).toBe(503);
    expect((await iam('iam_metrics', '/api/admin/metrics?period=year&tenantId=sample')).status).toBe(503);
  });

  it('a write is refused like every sample surface', async () => {
    const res = sampleResponse({
      core: 'iam',
      surface: 'iam_metrics',
      method: 'POST',
      path: '/api/admin/metrics?period=month',
    });
    expect(res.status).toBe(403);
  });
});
